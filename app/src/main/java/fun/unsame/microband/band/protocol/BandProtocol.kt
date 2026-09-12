package com.unsame.microband.band.protocol

import com.unsame.microband.band.model.BandDeviceInfo
import com.unsame.microband.band.model.BandException
import com.unsame.microband.band.model.FirmwareApplication
import com.unsame.microband.band.model.OobeStage
import com.unsame.microband.band.transport.BandTransport
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BandProtocol(private val transport: BandTransport) {
    private val mutex = Mutex()
    private val firmwareUiMutex = Mutex()

    suspend fun getPcbId(): ULong = read(
        facility = BandConstants.FACILITY_CONFIGURATION,
        code = 11,
        responseLength = 8,
    ).let { BandPacketCodec.readLong(it).toULong() }

    suspend fun whoAmI(): FirmwareApplication = read(
        facility = BandConstants.FACILITY_JUTIL,
        code = 3,
        responseLength = 1,
    ).let { FirmwareApplication.fromWire(it[0].toInt() and 0xFF) }

    suspend fun getFirmwareSummary(): Pair<ULong, String> {
        val data = read(BandConstants.FACILITY_JUTIL, 1, 57)
        val records = (0 until 3).map { index -> data.copyOfRange(index * 19, index * 19 + 19) }
        val main = records.firstOrNull { it.copyOfRange(0, 5).decodeToString().trim('\u0000') == "App" }
            ?: records.last()
        val pcb = (main[5].toInt() and 0xFF).toULong()
        val major = BandPacketCodec.readShort(main, 6)
        val minor = BandPacketCodec.readShort(main, 8)
        val revision = BandPacketCodec.readInt(main, 10).toUInt()
        val build = BandPacketCodec.readInt(main, 14).toUInt()
        return pcb to "$major.$minor.$build.$revision"
    }

    suspend fun isOobeComplete(): Boolean = read(
        BandConstants.FACILITY_SYSTEM_SETTINGS,
        19,
        4,
    ).any { it.toInt() != 0 }

    suspend fun getOobeStage(): OobeStage = read(
        BandConstants.FACILITY_OOBE,
        1,
        2,
    ).let { OobeStage.fromWire(BandPacketCodec.readShort(it)) }

    suspend fun getUtcTime(): Instant = read(
        BandConstants.FACILITY_TIME,
        0,
        8,
    ).let { BandPacketCodec.instantFromWindowsFileTime(BandPacketCodec.readLong(it)) }

    suspend fun getLocalTime(): LocalDateTime = read(
        BandConstants.FACILITY_TIME,
        2,
        16,
    ).let(BandTimeZoneCodec::parseSystemTime)

    suspend fun getProfileBytes(): ByteArray = read(
        facility = BandConstants.FACILITY_PROFILE,
        code = 6,
        responseLength = 397,
        arguments = BandPacketCodec.littleEndianInt(397),
    )

    suspend fun inspect(bluetoothName: String): BandDeviceInfo {
        val directPcb = getPcbId()
        val (firmwarePcb, version) = getFirmwareSummary()
        val oobeComplete = isOobeComplete()
        return BandDeviceInfo(
            bluetoothName = bluetoothName,
            pcbId = if (directPcb == 0uL) firmwarePcb else directPcb,
            firmwareApplication = whoAmI(),
            firmwareVersion = version,
            oobeComplete = oobeComplete,
            // Completed firmware rejects OOBE stage reads with 0xA0AD0000.
            oobeStage = if (oobeComplete) null else getOobeStage(),
            bandTime = getUtcTime(),
            bandLocalTime = getLocalTime(),
        )
    }

    suspend fun setOobeStage(stage: OobeStage) = write(
        BandConstants.FACILITY_OOBE,
        0,
        BandPacketCodec.littleEndianShort(stage.wireValue),
    )

    suspend fun setUtcTime(instant: Instant) = write(
        BandConstants.FACILITY_TIME,
        1,
        BandPacketCodec.littleEndianLong(BandPacketCodec.windowsFileTime(instant)),
    )

    suspend fun navigateToOobeBoot() = write(
        BandConstants.FACILITY_FIREBALL_UI,
        0,
        BandPacketCodec.littleEndianShort(0x0C),
    )

    suspend fun setPlaceholderEphemeris() = write(
        BandConstants.FACILITY_SYSTEM_SETTINGS,
        15,
        ByteArray(130),
    )

    suspend fun setTimeZone(zoneId: ZoneId, instant: Instant) {
        write(
            BandConstants.FACILITY_SYSTEM_SETTINGS,
            11,
            BandTimeZoneCodec.fixedOffsetPayload(zoneId, instant),
        )
    }

    suspend fun setProfile(profile: ByteArray, now: Instant) {
        if (profile.size != 397 || BandPacketCodec.readShort(profile) < 2) {
            throw BandException.InvalidPacket("Unsupported Band 2 profile format")
        }
        val updated = profile.copyOf()
        BandPacketCodec.littleEndianLong(BandPacketCodec.windowsFileTime(now)).copyInto(updated, 2)
        "Microband".toByteArray(Charsets.UTF_16LE).copyOf(32).copyInto(updated, 41)
        updated[104] = 0
        listOf(105, 114, 123, 132).forEach { offset ->
            BandPacketCodec.littleEndianLong(BandPacketCodec.windowsFileTime(now)).copyInto(updated, offset)
        }
        write(BandConstants.FACILITY_PROFILE, 7, updated)
    }

    suspend fun finalizeOobe() = write(BandConstants.FACILITY_OOBE, 2, byteArrayOf())

    suspend fun showNotification(title: String, body: String) {
        if (title.isBlank() && body.isBlank()) {
            throw BandException.InvalidPacket("Notification title and body are empty")
        }
        firmwareUiMutex.withLock {
            val payload = BandNotificationCodec.genericDialog(title, body)
            write(
                facility = BandConstants.FACILITY_NOTIFICATION,
                code = 5,
                transfer = payload,
                arguments = BandNotificationCodec.commandArguments(payload.size),
            )
        }
    }

    suspend fun setTheme(accent: Int) = withFirmwareUiSync {
        write(
            BandConstants.FACILITY_THEME_COLOR,
            0,
            BandPersonalizationCodec.encodeTheme(BandPersonalizationCodec.themeFromAccent(accent)),
        )
    }

    suspend fun setMeTileImage(rgb565: ByteArray) {
        if (rgb565.size != BandPersonalizationCodec.ME_TILE_BYTE_COUNT) {
            throw BandException.InvalidPacket("Band 2 wallpaper must be 310 × 128 pixels")
        }
        withFirmwareUiSync {
            write(
                facility = BandConstants.FACILITY_FIREBALL_UI,
                code = 17,
                transfer = rgb565,
                arguments = BandPacketCodec.littleEndianInt(-1),
                timeoutMillis = 60_000,
            )
        }
    }

    suspend fun clearMeTileImage() = withFirmwareUiSync {
        write(BandConstants.FACILITY_FIREBALL_UI, 6, byteArrayOf(), timeoutMillis = 60_000)
    }

    private suspend fun <T> withFirmwareUiSync(block: suspend () -> T): T = firmwareUiMutex.withLock {
        write(BandConstants.FACILITY_INSTALLED_APP_LIST, 2, byteArrayOf(), timeoutMillis = 60_000)
        try {
            block()
        } finally {
            write(BandConstants.FACILITY_INSTALLED_APP_LIST, 3, byteArrayOf(), timeoutMillis = 60_000)
        }
    }

    private suspend fun read(
        facility: Int,
        code: Int,
        responseLength: Int,
        arguments: ByteArray = byteArrayOf(),
    ): ByteArray = mutex.withLock {
        val packet = BandPacketCodec.command(facility, true, code, responseLength, arguments)
        val response = transport.transact(packet, responseLength)
        validate(response.status)
        response.payload
    }

    private suspend fun write(
        facility: Int,
        code: Int,
        transfer: ByteArray,
        arguments: ByteArray = byteArrayOf(),
        timeoutMillis: Long = 10_000,
    ) = mutex.withLock {
        val packet = BandPacketCodec.command(facility, false, code, transfer.size, arguments)
        validate(transport.transact(packet, 0, transfer, timeoutMillis).status)
    }

    private fun validate(status: BandStatus) {
        if (status.isError) throw BandException.ProtocolStatus(status.raw)
    }
}
