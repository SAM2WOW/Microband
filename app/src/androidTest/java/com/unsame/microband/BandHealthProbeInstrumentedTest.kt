package com.unsame.microband

import android.bluetooth.BluetoothManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unsame.microband.band.protocol.BandHealthCodec
import com.unsame.microband.band.protocol.BandPacketCodec
import com.unsame.microband.band.transport.RfcommBandTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only hardware probe for the Band's persisted health summaries. */
@RunWith(AndroidJUnit4::class)
class BandHealthProbeInstrumentedTest {
    @Test
    fun readPersistedSleepSummary() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        val device = adapter.bondedDevices.single { it.name?.startsWith("MSFT Band 2") == true }
        val transport = RfcommBandTransport { _, _, _ -> }
        transport.connect(device)
        try {
            val result = transport.transact(
                BandPacketCodec.command(0xCE, true, 4, BandHealthCodec.SLEEP_STATISTICS_SIZE),
                BandHealthCodec.SLEEP_STATISTICS_SIZE,
            )
            report("SLEEP status=0x${result.status.raw.toUInt().toString(16)} payload=${result.payload.toHex()}")
            assertFalse("Band rejected the persisted sleep query", result.status.isError)
            val sleep = BandHealthCodec.sleep(result.payload)
            report("SLEEP decoded=$sleep")
        } finally {
            transport.disconnect()
        }
    }

    private fun report(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("stream", "$message\n") },
        )
    }

    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}
