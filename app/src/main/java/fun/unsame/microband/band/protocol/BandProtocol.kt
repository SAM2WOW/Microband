package com.unsame.microband.band.protocol

import com.unsame.microband.band.model.BandDeviceInfo
import com.unsame.microband.band.model.BandException
import com.unsame.microband.band.model.BandHealthSnapshot
import com.unsame.microband.band.model.BandDailyMetrics
import com.unsame.microband.band.model.BandFirmwareComponent
import com.unsame.microband.band.model.BandFirmwareIdentity
import com.unsame.microband.band.model.BandTileCatalog
import com.unsame.microband.band.model.BandTileInfo
import com.unsame.microband.band.model.FirmwareApplication
import com.unsame.microband.band.model.OobeStage
import com.unsame.microband.band.transport.BandTransport
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

class BandProtocol(private val transport: BandTransport) {
    private val mutex = Mutex()
    private val firmwareUiMutex = Mutex()

    suspend fun checkSdkCompatibility() = write(
        facility = BandConstants.FACILITY_JUTIL,
        code = 7,
        transfer = byteArrayOf(),
        // Windows platform, reserved byte, SDK protocol version 3.
        arguments = byteArrayOf(2, 0, 3, 0),
    )

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

    suspend fun getFirmwareIdentity(): BandFirmwareIdentity {
        val data = read(BandConstants.FACILITY_JUTIL, 1, 57)
        val components = (0 until 3).map { index ->
            val record = data.copyOfRange(index * 19, index * 19 + 19)
            BandFirmwareComponent(
                name = record.copyOfRange(0, 5).decodeToString().trim('\u0000'),
                pcbId = record[5].toInt() and 0xFF,
                version = "${BandPacketCodec.readShort(record, 6)}.${BandPacketCodec.readShort(record, 8)}.${BandPacketCodec.readInt(record, 14).toUInt()}.${BandPacketCodec.readInt(record, 10).toUInt()}",
            )
        }
        return BandFirmwareIdentity(whoAmI(), components)
    }

    suspend fun firmwareAssetsValid(): Boolean =
        BandPacketCodec.readInt(read(BandConstants.FACILITY_SRAM_FIRMWARE_UPDATE, 2, 61), 57) != 0

    suspend fun batteryPercent(): Int {
        val type = 38
        write(BandConstants.FACILITY_REMOTE_SUBSCRIPTION, 0, byteArrayOf(), byteArrayOf(type.toByte(), 0, 0, 0, 0))
        try {
            repeat(10) {
                delay(300)
                val length = BandPacketCodec.readInt(read(BandConstants.FACILITY_REMOTE_SUBSCRIPTION, 2, 4))
                if (length >= 9) {
                    val payload = read(BandConstants.FACILITY_REMOTE_SUBSCRIPTION, 3, length)
                    var offset = 0
                    while (offset + 4 <= payload.size) {
                        val sampleType = payload[offset].toInt() and 0xFF
                        val size = BandPacketCodec.readShort(payload, offset + 2)
                        offset += 4
                        if (size < 0 || offset + size > payload.size) break
                        if (sampleType == type && size >= 5) return payload[offset].toInt() and 0xFF
                        offset += size
                    }
                }
            }
            throw BandException.InvalidPacket("The Band did not return its battery level")
        } finally {
            runCatching { write(BandConstants.FACILITY_REMOTE_SUBSCRIPTION, 1, byteArrayOf(), byteArrayOf(type.toByte())) }
        }
    }

    suspend fun bootIntoFirmwareUpdater() = mutex.withLock {
        transport.write(BandPacketCodec.frame(BandPacketCodec.command(BandConstants.FACILITY_SRAM_FIRMWARE_UPDATE, false, 1, 0)))
        runCatching { kotlinx.coroutines.withTimeout(5_000) { transport.readExact(6) } }
    }

    suspend fun uploadFirmware(file: File, onProgress: (Int) -> Unit) = mutex.withLock {
        val total = file.length().toInt()
        transport.write(BandPacketCodec.frame(BandPacketCodec.command(BandConstants.FACILITY_SRAM_FIRMWARE_UPDATE, false, 0, total)))
        var sent = 0
        file.inputStream().use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                transport.write(if (count == buffer.size) buffer else buffer.copyOf(count))
                sent += count
                onProgress(sent * 100 / total)
            }
        }
        val status = kotlinx.coroutines.withTimeout(90_000) { BandPacketCodec.parseStatus(transport.readExact(6)) }
        validate(status)
    }

    suspend fun getTileCatalog(): BandTileCatalog {
        val capacity = BandPacketCodec.readInt(read(BandConstants.FACILITY_INSTALLED_APP_LIST, 22, 4)).coerceIn(1, 32)
        val responseLength = 4 + capacity * BandTileCodec.RECORD_SIZE
        val installed = BandTileCodec.decode(read(BandConstants.FACILITY_INSTALLED_APP_LIST, 18, responseLength))
        val defaults = BandTileCodec.decode(read(BandConstants.FACILITY_INSTALLED_APP_LIST, 19, responseLength))
        return BandTileCatalog(installed, defaults.filter { candidate -> installed.none { it.id == candidate.id } }, capacity)
    }

    suspend fun setTiles(tiles: List<BandTileInfo>) {
        require(tiles.isNotEmpty()) { "At least one Band tile is required" }
        require(tiles.distinctBy { it.id }.size == tiles.size) { "Band tiles must be unique" }
        val catalog = getTileCatalog()
        require(tiles.size <= catalog.capacity) { "The Band supports ${catalog.capacity} tiles" }
        val installedIds = catalog.installed.mapTo(mutableSetOf()) { it.id }
        val defaultIds = (catalog.installed + catalog.available).mapTo(mutableSetOf()) { it.id }
        require(tiles.all { it.id in defaultIds }) { "Only built-in Band tiles can be added" }

        withFirmwareUiSync {
            for (current in catalog.installed.filter { candidate -> tiles.none { it.id == candidate.id } }) {
                unregisterTile(current)
            }
            for (tile in tiles.filter { it.id !in installedIds }) {
                registerDefaultTile(tile)
            }
            val payload = BandTileCodec.encode(tiles)
            write(
                BandConstants.FACILITY_INSTALLED_APP_LIST,
                1,
                transfer = payload,
                arguments = BandPacketCodec.littleEndianInt(tiles.size),
                timeoutMillis = 60_000,
            )
        }
    }

    private suspend fun registerDefaultTile(tile: BandTileInfo) {
        val payload = tile.wireData.copyOfRange(0, 16) + BandPacketCodec.littleEndianInt(0)
        write(BandConstants.FACILITY_FIREBALL_APPS, 0, payload, timeoutMillis = 60_000)
    }

    private suspend fun unregisterTile(tile: BandTileInfo) {
        write(
            BandConstants.FACILITY_FIREBALL_APPS,
            1,
            tile.wireData.copyOfRange(0, 16),
            timeoutMillis = 60_000,
        )
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

    suspend fun showMessage(sender: String, body: String, timestamp: Instant, messageId: Int = 0) {
        firmwareUiMutex.withLock {
            val payload = BandNotificationCodec.sms(sender, body, timestamp, messageId)
            write(
                facility = BandConstants.FACILITY_NOTIFICATION,
                code = 5,
                transfer = payload,
                arguments = BandNotificationCodec.commandArguments(payload.size, BandNotificationCodec.MESSAGING_MESSAGE_TYPE),
            )
        }
    }

    suspend fun showCall(caller: String, callId: Int, timestamp: Instant, type: BandNotificationCodec.CallType) {
        firmwareUiMutex.withLock {
            val payload = BandNotificationCodec.call(caller, callId, timestamp, type)
            write(
                facility = BandConstants.FACILITY_NOTIFICATION,
                code = 5,
                transfer = payload,
                arguments = BandNotificationCodec.commandArguments(payload.size, BandNotificationCodec.MESSAGING_MESSAGE_TYPE),
            )
        }
    }

    suspend fun getHealthSnapshot(): BandHealthSnapshot {
        var successfulReads = 0
        val daily = getDailyMetrics()
        if (daily.hasData) successfulReads++
        val run = runCatching {
            BandHealthCodec.run(read(BandConstants.FACILITY_PERSISTED_STATISTICS, 2, BandHealthCodec.RUN_STATISTICS_SIZE))
        }.getOrNull().also { if (it != null) successfulReads++ }
        val workout = runCatching {
            BandHealthCodec.workout(read(BandConstants.FACILITY_PERSISTED_STATISTICS, 3, BandHealthCodec.WORKOUT_STATISTICS_SIZE))
        }.getOrNull().also { if (it != null) successfulReads++ }
        val sleep = runCatching {
            BandHealthCodec.sleep(read(BandConstants.FACILITY_PERSISTED_STATISTICS, 4, BandHealthCodec.SLEEP_STATISTICS_SIZE))
        }.getOrNull().also { if (it != null) successfulReads++ }
        if (successfulReads == 0) {
            throw BandException.InvalidPacket("The Band returned no usable health data. Keep it connected and worn, then retry.")
        }
        return BandHealthSnapshot(Instant.now(), daily.steps, daily.takeIf { it.hasData }, run, workout, sleep)
    }

    private suspend fun getDailyMetrics(): BandDailyMetrics {
        var combined = BandDailyMetrics()
        listOf(
            BandHealthCodec.PEDOMETER_WITH_DAILY_VALUES,
            BandHealthCodec.CALORIES_WITH_DAILY_VALUES,
            BandHealthCodec.DISTANCE_WITH_DAILY_VALUES,
            BandHealthCodec.ELEVATION_WITH_DAILY_VALUES,
            BandHealthCodec.UV_WITH_DAILY_VALUES,
        ).forEach { type ->
            runCatching { readDailyMetric(type) }.getOrNull()?.let { value ->
                combined = BandDailyMetrics(
                    steps = value.steps ?: combined.steps,
                    calories = value.calories ?: combined.calories,
                    distanceCentimeters = value.distanceCentimeters ?: combined.distanceCentimeters,
                    flightsAscended = value.flightsAscended ?: combined.flightsAscended,
                    elevationGainCentimeters = value.elevationGainCentimeters ?: combined.elevationGainCentimeters,
                    uvExposure = value.uvExposure ?: combined.uvExposure,
                )
            }
        }
        if (!combined.hasData) {
            // Early Band 2 firmware (including 2.0.3640) predates the daily-value
            // subscriptions. Its regular sensors still expose honest cumulative
            // counters, which are useful but must not be presented as today's data.
            listOf(BandHealthCodec.PEDOMETER, BandHealthCodec.CALORIES, BandHealthCodec.DISTANCE).forEach { type ->
                runCatching { readDailyMetric(type) }.getOrNull()?.let { value ->
                    combined = BandDailyMetrics(
                        steps = value.steps ?: combined.steps,
                        calories = value.calories ?: combined.calories,
                        distanceCentimeters = value.distanceCentimeters ?: combined.distanceCentimeters,
                        cumulativeSinceReset = true,
                    )
                }
            }
        }
        return combined
    }

    private suspend fun readDailyMetric(type: Int): BandDailyMetrics? {
        write(
            facility = BandConstants.FACILITY_REMOTE_SUBSCRIPTION,
            code = 0,
            transfer = byteArrayOf(),
            arguments = byteArrayOf(type.toByte()) + BandPacketCodec.littleEndianInt(0),
        )
        try {
            repeat(5) {
                delay(300)
                val length = BandPacketCodec.readInt(
                    read(BandConstants.FACILITY_REMOTE_SUBSCRIPTION, 2, 4),
                )
                if (length in 1..4_096) {
                    return BandHealthCodec.dailyMetrics(
                        read(BandConstants.FACILITY_REMOTE_SUBSCRIPTION, 3, length),
                    ).takeIf { it.hasData }
                }
            }
            return null
        } finally {
            runCatching {
                write(
                    facility = BandConstants.FACILITY_REMOTE_SUBSCRIPTION,
                    code = 1,
                    transfer = byteArrayOf(),
                    arguments = byteArrayOf(type.toByte()),
                )
            }
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
