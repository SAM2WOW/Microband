package com.unsame.microband

import android.bluetooth.BluetoothManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unsame.microband.band.model.BandException
import com.unsame.microband.band.protocol.BandPacketCodec
import com.unsame.microband.band.transport.RfcommBandTransport
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** One-time, operator-driven firmware harness. This is intentionally androidTest-only. */
@RunWith(AndroidJUnit4::class)
class DirectFirmwareUpdateInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val transport = RfcommBandTransport { _, _, _ -> }

    @Test
    fun auditOrFlashBand2() = runBlocking {
        val flash = InstrumentationRegistry.getArguments().getString("flash") == "true"
        val firmware = File(context.filesDir, FIRMWARE_FILE)
        assertTrue("Firmware file was not staged", firmware.isFile)
        assertEquals(FIRMWARE_SIZE, firmware.length())
        assertEquals(FIRMWARE_SHA256, sha256(firmware))

        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        val device = adapter.bondedDevices.single { it.name?.startsWith("MSFT Band 2") == true }
        progress("Connecting to ${device.name} / ${device.address}")
        transport.connect(device)

        val before = identity()
        progress("PREFLIGHT app=${before.runningApp} pcb=${before.pcb} versions=${before.versions.joinToString()}")
        assertEquals("Band must be running the main application", 3, before.runningApp)
        assertTrue("This is not Band 2 / Envoy hardware", before.pcb >= 20)
        assertTrue("Existing firmware assets are invalid", validateInstalledAssets())
        val battery = readBatteryPercent()
        progress("PREFLIGHT battery=$battery% installed-assets=valid package=verified")
        assertTrue("Band battery must be at least 50%", battery >= 50)

        if (!flash) {
            progress("PREFLIGHT PASSED; no destructive command sent")
            transport.disconnect()
            return@runBlocking
        }

        progress("FLASH booting into UpApp")
        bootIntoUpdater()
        reconnect(device, expectedApp = 4, expectedPcb = before.pcb, expectedBootloader = before.versions.first())

        progress("FLASH transferring ${firmware.length()} bytes")
        upload(firmware)
        transport.disconnect()

        val after = reconnect(device, expectedApp = 3, expectedPcb = before.pcb, expectedBootloader = before.versions.first())
        assertEquals("2.0.5202.0", after.applicationVersion)
        assertTrue("Band rejected one or more installed images", validateInstalledAssets())
        progress("FLASH COMPLETE firmware=${after.applicationVersion} installed-assets=valid")
        transport.disconnect()
    }

    private suspend fun identity(): Identity {
        val versionsPayload = read(0x76, 1, 57)
        val versions = (0 until 3).map { index ->
            val record = versionsPayload.copyOfRange(index * 19, index * 19 + 19)
            val name = record.copyOfRange(0, 5).decodeToString().trim('\u0000')
            val pcb = record[5].toInt() and 0xFF
            val major = BandPacketCodec.readShort(record, 6)
            val minor = BandPacketCodec.readShort(record, 8)
            val revision = BandPacketCodec.readInt(record, 10).toUInt()
            val build = BandPacketCodec.readInt(record, 14).toUInt()
            Version(name, pcb, "$major.$minor.$build.$revision")
        }
        return Identity(
            runningApp = read(0x76, 3, 1)[0].toInt() and 0xFF,
            pcb = versions.single { it.name == "App" }.pcb,
            versions = versions,
        )
    }

    private suspend fun validateInstalledAssets(): Boolean =
        BandPacketCodec.readInt(read(0x98, 2, 61), 57) != 0

    private suspend fun readBatteryPercent(): Int {
        writeArgs(0x8F, 0, byteArrayOf(38, 0, 0, 0, 0))
        try {
            repeat(10) {
                delay(300)
                val length = BandPacketCodec.readInt(read(0x8F, 2, 4))
                if (length >= 9) {
                    val payload = read(0x8F, 3, length)
                    var offset = 0
                    while (offset + 4 <= payload.size) {
                        val type = payload[offset].toInt() and 0xFF
                        val sampleSize = BandPacketCodec.readShort(payload, offset + 2)
                        offset += 4
                        if (sampleSize < 0 || offset + sampleSize > payload.size) break
                        if (type == 38 && sampleSize >= 5) return payload[offset].toInt() and 0xFF
                        offset += sampleSize
                    }
                }
            }
            error("Band did not return its battery gauge")
        } finally {
            runCatching { writeArgs(0x8F, 1, byteArrayOf(38)) }
        }
    }

    private suspend fun bootIntoUpdater() {
        val packet = BandPacketCodec.command(0x98, false, 1, 0)
        transport.write(BandPacketCodec.frame(packet))
        runCatching { withTimeout(5_000) { transport.readExact(6) } }
        transport.disconnect()
    }

    private suspend fun upload(file: File) {
        val size = file.length().toInt()
        val packet = BandPacketCodec.command(0x98, false, 0, size)
        transport.write(BandPacketCodec.frame(packet))
        var sent = 0
        file.inputStream().use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                transport.write(if (count == buffer.size) buffer else buffer.copyOf(count))
                sent += count
                if (sent % (256 * 1024) < 8_192) progress("FLASH sent=$sent/$size")
            }
        }
        val status = withTimeout(90_000) { BandPacketCodec.parseStatus(transport.readExact(6)) }
        if (status.isError) throw BandException.ProtocolStatus(status.raw)
        progress("FLASH transfer accepted by Band")
    }

    private suspend fun reconnect(
        device: android.bluetooth.BluetoothDevice,
        expectedApp: Int,
        expectedPcb: Int,
        expectedBootloader: Version,
    ): Identity {
        val deadline = System.currentTimeMillis() + 240_000
        delay(5_000)
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                transport.connect(device)
                val identity = identity()
                assertEquals(expectedApp, identity.runningApp)
                assertEquals(expectedPcb, identity.pcb)
                assertEquals(expectedBootloader.version, identity.versions.first().version)
                progress("FLASH reconnected app=${identity.runningApp} versions=${identity.versions.joinToString()}")
                return identity
            } catch (exception: Throwable) {
                lastError = exception
                runCatching { transport.disconnect() }
                delay(2_000)
            }
        }
        throw AssertionError("Band did not return in the expected mode", lastError)
    }

    private suspend fun read(facility: Int, code: Int, length: Int): ByteArray {
        val result = transport.transact(BandPacketCodec.command(facility, true, code, length), length)
        if (result.status.isError) throw BandException.ProtocolStatus(result.status.raw)
        return result.payload
    }

    private suspend fun writeArgs(facility: Int, code: Int, arguments: ByteArray) {
        val result = transport.transact(BandPacketCodec.command(facility, false, code, 0, arguments), 0)
        if (result.status.isError) throw BandException.ProtocolStatus(result.status.raw)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    private fun progress(message: String) {
        Log.i(TAG, message)
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "$message\n") })
    }

    private data class Version(val name: String, val pcb: Int, val version: String) {
        override fun toString() = "$name:$version"
    }

    private data class Identity(val runningApp: Int, val pcb: Int, val versions: List<Version>) {
        val applicationVersion get() = versions.single { it.name == "App" }.version
    }

    companion object {
        private const val TAG = "MicrobandFirmware"
        private const val FIRMWARE_FILE = "envoy-2.0.5202.0.bin"
        private const val FIRMWARE_SIZE = 1_838_103L
        private const val FIRMWARE_SHA256 = "2473896B8281B2FF81E462374A48BE8A3E8901FB6B2C55AF0FE9125930A60727"
    }
}
