package com.unsame.microband.bluetooth

import android.app.Application
import android.bluetooth.BluetoothDevice
import com.unsame.microband.band.model.BandConnectionState
import com.unsame.microband.band.model.BandDeviceInfo
import com.unsame.microband.band.model.BandException
import com.unsame.microband.band.oobe.BandOobeManager
import com.unsame.microband.band.oobe.BandOobeStep
import com.unsame.microband.band.protocol.BandPacketCodec.hex
import com.unsame.microband.band.protocol.BandProtocol
import com.unsame.microband.band.transport.RfcommBandTransport
import com.unsame.microband.data.MicrobandPreferences
import com.unsame.microband.data.HealthSnapshotDao
import com.unsame.microband.data.HealthDailyDao
import com.unsame.microband.data.HealthDailyEntity
import com.unsame.microband.data.PacketLogDao
import com.unsame.microband.data.ProtocolPacketLog
import com.unsame.microband.data.toEntity
import com.unsame.microband.data.toModel
import com.unsame.microband.band.model.BandHealthSnapshot
import com.unsame.microband.band.model.BandFirmwareIdentity
import com.unsame.microband.band.model.FirmwareUpdateStage
import com.unsame.microband.band.model.FirmwareUpdateStatus
import com.unsame.microband.band.model.FirmwareApplication
import com.unsame.microband.band.model.BandTileCatalog
import com.unsame.microband.band.model.BandTileInfo
import com.unsame.microband.notification.BandPhoneNotification
import com.unsame.microband.notification.BandNotificationKind
import com.unsame.microband.band.protocol.BandNotificationCodec
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class BandConnectionManager(
    private val application: Application,
    private val packetLogDao: PacketLogDao,
    private val healthSnapshotDao: HealthSnapshotDao,
    private val healthDailyDao: HealthDailyDao,
    private val preferences: MicrobandPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<BandConnectionState>(BandConnectionState.Disconnected)
    val state: StateFlow<BandConnectionState> = mutableState.asStateFlow()
    private val mutableOobeStep = MutableStateFlow(BandOobeStep.Inspect)
    val oobeStep: StateFlow<BandOobeStep> = mutableOobeStep.asStateFlow()
    private val mutableSetupInProgress = MutableStateFlow(false)
    val setupInProgress: StateFlow<Boolean> = mutableSetupInProgress.asStateFlow()
    private val mutableEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = mutableEvents.asSharedFlow()
    val recentLogs = packetLogDao.observeRecent().stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val healthSnapshot = healthSnapshotDao.observe().map { it?.toModel() }
        .stateIn(scope, SharingStarted.Eagerly, null)
    val healthHistory = healthDailyDao.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val mutableHealthSyncInProgress = MutableStateFlow(false)
    val healthSyncInProgress: StateFlow<Boolean> = mutableHealthSyncInProgress.asStateFlow()
    private val mutableFirmwareUpdate = MutableStateFlow(FirmwareUpdateStatus())
    val firmwareUpdate: StateFlow<FirmwareUpdateStatus> = mutableFirmwareUpdate.asStateFlow()
    private val mutableTiles = MutableStateFlow(BandTileCatalog())
    val tiles: StateFlow<BandTileCatalog> = mutableTiles.asStateFlow()

    private var redactNextTransfer = false
    private var redactNextReceive = false
    @Volatile private var suppressPacketLogging = false
    private val transport = RfcommBandTransport { direction, bytes, status ->
        val id = commandId(bytes)
        val shouldRedactTransfer = direction == "TX" && id == null && redactNextTransfer
        val shouldRedactReceive = direction == "RX" && redactNextReceive
        if (shouldRedactTransfer) redactNextTransfer = false
        if (shouldRedactReceive) redactNextReceive = false
        if (direction == "TX" && id in SENSITIVE_TRANSFER_COMMANDS) redactNextTransfer = true
        if (direction == "TX" && id in SENSITIVE_RESPONSE_COMMANDS) redactNextReceive = true
        if (!suppressPacketLogging && preferences.protocolLogging.first()) {
            packetLogDao.insert(
                ProtocolPacketLog(
                    timestampMillis = System.currentTimeMillis(),
                    direction = direction,
                    transport = "RFCOMM",
                    commandId = id,
                    payloadLength = bytes.size,
                    hexPayload = if (shouldRedactTransfer || shouldRedactReceive) "<redacted private payload>" else bytes.hex(),
                    parsedStatus = status,
                ),
            )
        }
    }
    private val protocol = BandProtocol(transport)
    private val connectionMutex = Mutex()
    private val oobeManager = BandOobeManager(preferences)
    private var activeDevice: BluetoothDevice? = null
    private var inspectedInfo: BandDeviceInfo? = null
    @Volatile private var keepConnected = false

    init {
        scope.launch {
            while (true) {
                delay(30_000)
                if (keepConnected && activeDevice != null) {
                    runCatching { ensureLiveConnection() }
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) = scope.launch {
        keepConnected = true
        val newlyConnected = connectionMutex.withLock {
            activeDevice = device
            if (mutableState.value is BandConnectionState.Connected && runCatching { protocol.getUtcTime() }.isSuccess) return@withLock false
            runCatching { connectLocked(device) }.fold(
                onSuccess = { true },
                onFailure = { mutableState.value = BandConnectionState.Error(it.userMessage()); false },
            )
        }
        if (newlyConnected) syncAfterConnect()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun connectLocked(device: BluetoothDevice) {
        mutableState.value = BandConnectionState.Connecting
        ensureBonded(device)
        transport.connect(device)
        val name = runCatching { device.name }.getOrNull() ?: "Microsoft Band"
        mutableState.value = BandConnectionState.Connected(BandDeviceInfo(name))
        // The official SDK negotiates protocol version before requesting sensors.
        // Older firmware may reject this command, so a failed negotiation must not
        // prevent basic time/notification operations.
        runCatching { protocol.checkSdkCompatibility() }
        try {
            inspectConnectedBand(name)
        } catch (exception: Exception) {
            mutableEvents.emit("Connected. Device checks will retry automatically: ${exception.userMessage()}")
        }
    }

    private suspend fun syncAfterConnect() {
        val info = inspectedInfo ?: return
        if (info.oobeComplete != true) return
        runCatching { syncClockInternal(info) }
        mutableHealthSyncInProgress.value = true
        try {
            runCatching { refreshHealthInternal() }
                .onFailure { mutableEvents.emit("Connected, but health sync failed: ${it.userMessage()}") }
        } finally {
            mutableHealthSyncInProgress.value = false
        }
    }

    private suspend fun ensureLiveConnection() {
        val device = activeDevice ?: throw BandException.NotPaired()
        connectionMutex.withLock {
            val live = mutableState.value is BandConnectionState.Connected &&
                runCatching { protocol.getUtcTime() }.isSuccess
            if (!live) {
                transport.disconnect()
                connectLocked(device)
            }
        }
    }

    private suspend fun <T> withReconnect(block: suspend () -> T): T {
        ensureLiveConnection()
        return try {
            block()
        } catch (first: Exception) {
            if (first is BandException.ProtocolStatus || first is BandException.InvalidPacket) throw first
            transport.disconnect()
            mutableState.value = BandConnectionState.Disconnected
            ensureLiveConnection()
            block()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun ensureBonded(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return

        val started = device.bondState == BluetoothDevice.BOND_BONDING || device.createBond()
        if (!started) throw BandException.PairingFailed("Android could not start Band pairing")

        try {
            withTimeout(60_000) {
                var sawBonding = device.bondState == BluetoothDevice.BOND_BONDING
                while (true) {
                    when (device.bondState) {
                        BluetoothDevice.BOND_BONDED -> return@withTimeout
                        BluetoothDevice.BOND_BONDING -> sawBonding = true
                        BluetoothDevice.BOND_NONE -> if (sawBonding) {
                            throw BandException.PairingFailed(
                                "Band pairing failed. If it was previously paired, clear the old phone pairing on the Band, then retry.",
                            )
                        }
                    }
                    delay(250)
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw BandException.PairingFailed("Band pairing timed out; confirm the code on the phone and Band")
        }
    }

    fun disconnect() = scope.launch {
        keepConnected = false
        connectionMutex.withLock {
            transport.disconnect()
            inspectedInfo = null
            mutableState.value = BandConnectionState.Disconnected
        }
    }

    suspend fun clearProtocolLog() = packetLogDao.clear()

    fun inspect() = scope.launch {
        val current = mutableState.value as? BandConnectionState.Connected
            ?: return@launch mutableEvents.emit("Connect to your Band first")
        runCatching { inspectConnectedBand(current.device.bluetoothName) }
            .onSuccess {
                mutableEvents.emit(
                    when {
                        it.oobeComplete == true -> "Setup complete"
                        it.isBand2 -> "Microsoft Band 2 verified"
                        else -> "Unsupported Band hardware"
                    },
                )
            }
            .onFailure {
                mutableState.value = BandConnectionState.Error(it.userMessage())
            }
    }

    fun finishSetup() = scope.launch {
        if (mutableSetupInProgress.value) return@launch
        val device = activeDevice ?: return@launch mutableEvents.emit("Associate and connect a Band first")
        val info = inspectedInfo ?: return@launch mutableEvents.emit("Check the Band before finishing setup")
        mutableSetupInProgress.value = true
        try {
            oobeManager.prepare(protocol, info) { mutableOobeStep.value = it }
            mutableOobeStep.value = BandOobeStep.Verifying
            preferences.setOobeStep(BandOobeStep.Verifying)
            transport.disconnect()
            delay(1_000)
            transport.connect(device)
            val verified = protocol.inspect(info.bluetoothName)
            if (verified.oobeComplete != true) throw BandException.InvalidPacket("Setup did not complete; it is safe to retry")
            recordInspection(verified)
            syncClockInternal(verified)
            mutableEvents.emit("Your Band is ready")
        } catch (exception: Exception) {
            val recovered = recoverCompletedSetup(device, info.bluetoothName)
            if (recovered?.oobeComplete == true) {
                recordInspection(recovered)
                runCatching { syncClockInternal(recovered) }
                mutableEvents.emit("Your Band is ready")
            } else {
                mutableOobeStep.value = BandOobeStep.Inspect
                mutableState.value = BandConnectionState.Error(exception.userMessage())
            }
        } finally {
            mutableSetupInProgress.value = false
        }
    }

    fun syncClock() = scope.launch {
        val current = mutableState.value as? BandConnectionState.Connected
            ?: return@launch mutableEvents.emit("Connect to your Band first")
        runCatching { syncClockInternal(current.device) }
            .onSuccess { mutableEvents.emit("Band time synchronized") }
            .onFailure { mutableEvents.emit(it.userMessage()) }
    }

    fun sendTestNotification() = scope.launch {
        if (mutableState.value !is BandConnectionState.Connected) {
            mutableEvents.emit("Connect to your Band first")
            return@launch
        }
        runCatching { protocol.showMessage("Microband", "Notification forwarding is working", Instant.now()) }
            .onSuccess { mutableEvents.emit("Test notification sent to Band") }
            .onFailure { mutableEvents.emit(it.userMessage()) }
    }

    fun refreshHealthData() = scope.launch {
        if (mutableState.value !is BandConnectionState.Connected) {
            mutableEvents.emit("Connect to your Band to sync health data")
            return@launch
        }
        mutableHealthSyncInProgress.value = true
        runCatching { refreshHealthInternal() }
            .onSuccess { mutableEvents.emit("Band health data synchronized") }
            .onFailure { mutableEvents.emit(it.userMessage()) }
        mutableHealthSyncInProgress.value = false
    }

    fun refreshTiles() = scope.launch {
        runCatching { withReconnect { protocol.getTileCatalog() } }
            .onSuccess { mutableTiles.value = it }
            .onFailure { mutableEvents.emit("Could not read Band tiles: ${it.userMessage()}") }
    }

    fun applyTiles(tiles: List<BandTileInfo>) = scope.launch {
        runCatching {
            withReconnect {
                protocol.setTiles(tiles)
                protocol.getTileCatalog()
            }.also { mutableTiles.value = it }
        }.onSuccess { mutableEvents.emit("Band tiles updated") }
            .onFailure { mutableEvents.emit("Could not update Band tiles: ${it.userMessage()}") }
    }

    @android.annotation.SuppressLint("MissingPermission")
    suspend fun runLatestFirmwareUpdate() {
        if (mutableFirmwareUpdate.value.isRunning) return
        val device = activeDevice ?: throw BandException.NotPaired()
        try {
            updateFirmwareState(FirmwareUpdateStage.Downloading, 1, "Downloading verified Band 2 firmware")
            val file = downloadVerifiedFirmware { percent ->
                updateFirmwareState(FirmwareUpdateStage.Downloading, (percent * 20 / 100).coerceAtLeast(1), "Downloading firmware • $percent%")
            }
            keepConnected = false
            connectionMutex.withLock {
                performFirmwareUpdate(device, file)
            }
        } catch (exception: Exception) {
            updateFirmwareState(FirmwareUpdateStage.Failed, mutableFirmwareUpdate.value.percent, exception.userMessage())
            mutableEvents.emit("Firmware update stopped: ${exception.userMessage()}")
            val recovered = runCatching {
                transport.disconnect()
                transport.connect(device)
                protocol.getFirmwareIdentity()
            }.getOrNull()
            if (recovered?.runningApplication == FirmwareApplication.App) {
                keepConnected = true
                runCatching { recordInspection(protocol.inspect(runCatching { device.name }.getOrNull() ?: "Microsoft Band")) }
            } else if (recovered != null) {
                mutableFirmwareUpdate.value = mutableFirmwareUpdate.value.copy(
                    message = "${exception.userMessage()} Band is in ${recovered.runningApplication}; keep it charged and retry.",
                )
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun performFirmwareUpdate(device: BluetoothDevice, file: File) {
        updateFirmwareState(FirmwareUpdateStage.Checking, 22, "Checking Band identity and battery")
        transport.disconnect()
        transport.connect(device)
        var identity = protocol.getFirmwareIdentity()
        val expectedPcb = identity.pcbId
        val expectedBootloader = identity.bootloaderVersion
        if (expectedPcb < 20) throw BandException.DeviceUnsupported()

        if (identity.runningApplication == FirmwareApplication.App) {
            if (identity.applicationVersion == LATEST_FIRMWARE_VERSION && protocol.firmwareAssetsValid()) {
                updateFirmwareState(FirmwareUpdateStage.Complete, 100, "Band already has the latest firmware")
                keepConnected = true
                recordInspection(protocol.inspect(device.name ?: "Microsoft Band"))
                return
            }
            if (!protocol.firmwareAssetsValid()) throw BandException.InvalidPacket("Existing Band firmware assets are invalid")
            val battery = protocol.batteryPercent()
            if (battery < 50) throw BandException.InvalidPacket("Charge the Band to at least 50% before updating")
            updateFirmwareState(FirmwareUpdateStage.EnteringUpdater, 25, "Restarting Band in update mode")
            protocol.bootIntoFirmwareUpdater()
            transport.disconnect()
            identity = reconnectFirmware(device, FirmwareApplication.UpApp, expectedPcb, expectedBootloader)
        }

        if (identity.runningApplication == FirmwareApplication.TwoUp) {
            updateFirmwareState(FirmwareUpdateStage.Transferring, 28, "Repairing Band updater")
            uploadFirmwareTracked(file, 28, 52)
            transport.disconnect()
            identity = reconnectFirmware(device, FirmwareApplication.UpApp, expectedPcb, expectedBootloader)
        }
        if (identity.runningApplication != FirmwareApplication.UpApp) {
            throw BandException.InvalidPacket("Band did not enter its firmware updater")
        }

        updateFirmwareState(FirmwareUpdateStage.Transferring, 30, "Installing firmware")
        uploadFirmwareTracked(file, 30, 86)
        transport.disconnect()
        updateFirmwareState(FirmwareUpdateStage.Rebooting, 88, "Waiting for Band to restart")
        identity = reconnectFirmware(device, FirmwareApplication.App, expectedPcb, expectedBootloader)
        updateFirmwareState(FirmwareUpdateStage.Verifying, 96, "Verifying installed firmware")
        if (identity.applicationVersion != LATEST_FIRMWARE_VERSION) {
            throw BandException.InvalidPacket("Band restarted with firmware ${identity.applicationVersion}")
        }
        if (!protocol.firmwareAssetsValid()) throw BandException.InvalidPacket("Band reported invalid firmware assets")

        val info = protocol.inspect(device.name ?: "Microsoft Band")
        recordInspection(info)
        keepConnected = true
        updateFirmwareState(FirmwareUpdateStage.Complete, 100, "Firmware ${identity.applicationVersion} installed")
        mutableEvents.emit("Band firmware updated to ${identity.applicationVersion}")
    }

    private suspend fun uploadFirmwareTracked(file: File, start: Int, end: Int) {
        suppressPacketLogging = true
        try {
            protocol.uploadFirmware(file) { transferPercent ->
                val overall = start + (end - start) * transferPercent / 100
                updateFirmwareState(FirmwareUpdateStage.Transferring, overall, "Installing firmware • $transferPercent%")
            }
        } finally {
            suppressPacketLogging = false
        }
    }

    private suspend fun reconnectFirmware(
        device: BluetoothDevice,
        expectedApplication: FirmwareApplication,
        expectedPcb: Int,
        expectedBootloader: String,
    ): BandFirmwareIdentity {
        val deadline = System.currentTimeMillis() + 240_000
        delay(5_000)
        var last: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                transport.connect(device)
                val identity = protocol.getFirmwareIdentity()
                if (identity.runningApplication != expectedApplication || identity.pcbId != expectedPcb || identity.bootloaderVersion != expectedBootloader) {
                    throw BandException.InvalidPacket("Unexpected Band identity after restart")
                }
                return identity
            } catch (exception: Exception) {
                last = exception
                runCatching { transport.disconnect() }
                delay(2_000)
            }
        }
        throw BandException.InvalidPacket("Band did not return from update mode: ${last?.message ?: "timeout"}")
    }

    private fun updateFirmwareState(stage: FirmwareUpdateStage, percent: Int, message: String) {
        mutableFirmwareUpdate.value = FirmwareUpdateStatus(stage, percent.coerceIn(0, 100), message)
    }

    private fun downloadVerifiedFirmware(onProgress: (Int) -> Unit): File {
        val directory = File(application.cacheDir, "firmware").apply { mkdirs() }
        val target = File(directory, "envoy-$LATEST_FIRMWARE_VERSION.bin")
        if (target.isFile && target.length() == LATEST_FIRMWARE_SIZE && sha256(target) == LATEST_FIRMWARE_SHA256) {
            onProgress(100)
            return target
        }
        val temporary = File(directory, "download.part")
        val connection = URL(LATEST_FIRMWARE_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.connect()
        if (connection.responseCode !in 200..299) throw BandException.InvalidPacket("Firmware download failed (${connection.responseCode})")
        val expectedLength = connection.contentLengthLong
        var received = 0L
        connection.inputStream.use { input ->
            temporary.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    received += count
                    if (expectedLength > 0) onProgress((received * 100 / expectedLength).toInt())
                }
            }
        }
        connection.disconnect()
        if (temporary.length() != LATEST_FIRMWARE_SIZE || sha256(temporary) != LATEST_FIRMWARE_SHA256) {
            temporary.delete()
            throw BandException.InvalidPacket("Downloaded firmware failed cryptographic verification")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        onProgress(100)
        return target
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

    private suspend fun refreshHealthInternal() {
        val fresh = withReconnect { protocol.getHealthSnapshot() }
        val cached = healthSnapshot.value
        val sameDay = cached?.syncedAt?.atZone(ZoneId.systemDefault())?.toLocalDate() ==
            fresh.syncedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        val merged = fresh.copy(
            stepsToday = fresh.stepsToday ?: cached?.stepsToday?.takeIf { sameDay },
            lastRun = fresh.lastRun ?: cached?.lastRun,
            lastWorkout = fresh.lastWorkout ?: cached?.lastWorkout,
            lastSleep = fresh.lastSleep ?: cached?.lastSleep,
        )
        healthSnapshotDao.upsert(merged.toEntity())
        fresh.daily?.takeIf { it.hasData && !it.cumulativeSinceReset }?.let { daily ->
            healthDailyDao.upsert(
                HealthDailyEntity(
                    localDate = LocalDate.now().toString(),
                    syncedAt = fresh.syncedAt.toEpochMilli(),
                    steps = daily.steps,
                    calories = daily.calories,
                    distanceCentimeters = daily.distanceCentimeters,
                    flightsAscended = daily.flightsAscended,
                    elevationGainCentimeters = daily.elevationGainCentimeters,
                    uvExposure = daily.uvExposure,
                ),
            )
        }
    }

    fun forwardNotification(
        notification: BandPhoneNotification,
        kind: BandNotificationKind,
        device: BluetoothDevice,
    ) = scope.launch {
        if (mutableFirmwareUpdate.value.isRunning) return@launch
        activeDevice = device
        keepConnected = true
        runCatching { withReconnect {
            when (kind) {
                BandNotificationKind.MESSAGE -> protocol.showMessage(
                    sender = notification.title?.takeIf { it.isNotBlank() }
                        ?: notification.sourceLabel.ifBlank { "Phone" },
                    body = notification.body?.takeIf { it.isNotBlank() }
                        ?: notification.title?.takeIf { it.isNotBlank() }
                        ?: "New notification from ${notification.sourceLabel}",
                    timestamp = notification.timestamp,
                    messageId = stableNotificationId(notification.notificationKey),
                )
                else -> protocol.showCall(
                    caller = notification.title?.takeIf { it.isNotBlank() }
                        ?: notification.sourceLabel.ifBlank { "Phone" },
                    callId = stableNotificationId(notification.notificationKey),
                    timestamp = notification.timestamp,
                    type = when (kind) {
                        BandNotificationKind.INCOMING_CALL -> BandNotificationCodec.CallType.Incoming
                        BandNotificationKind.ANSWERED_CALL -> BandNotificationCodec.CallType.Answered
                        BandNotificationKind.MISSED_CALL -> BandNotificationCodec.CallType.Missed
                        BandNotificationKind.HANGUP_CALL -> BandNotificationCodec.CallType.Hangup
                        BandNotificationKind.VOICEMAIL -> BandNotificationCodec.CallType.Voicemail
                        BandNotificationKind.MESSAGE -> error("handled above")
                    },
                )
            }
        } }.onFailure { mutableEvents.tryEmit("Notification forwarding failed: ${it.userMessage()}") }
    }

    fun setThemeColor(accent: Int) = scope.launch {
        if (mutableState.value !is BandConnectionState.Connected) {
            mutableEvents.emit("Connect to your Band first")
            return@launch
        }
        runCatching { protocol.setTheme(accent) }
            .onSuccess { mutableEvents.emit("Band theme updated") }
            .onFailure { mutableEvents.emit(it.userMessage()) }
    }

    fun setWallpaper(rgb565: ByteArray) = scope.launch {
        if (mutableState.value !is BandConnectionState.Connected) {
            mutableEvents.emit("Connect to your Band first")
            return@launch
        }
        runCatching { protocol.setMeTileImage(rgb565) }
            .onSuccess { mutableEvents.emit("Band wallpaper updated") }
            .onFailure { mutableEvents.emit(it.userMessage()) }
    }

    fun clearWallpaper() = scope.launch {
        if (mutableState.value !is BandConnectionState.Connected) {
            mutableEvents.emit("Connect to your Band first")
            return@launch
        }
        runCatching { protocol.clearMeTileImage() }
            .onSuccess { mutableEvents.emit("Band wallpaper cleared") }
            .onFailure { mutableEvents.emit(it.userMessage()) }
    }

    private suspend fun inspectConnectedBand(bluetoothName: String): BandDeviceInfo {
        val info = protocol.inspect(bluetoothName)
        recordInspection(info)
        return info
    }

    private suspend fun recordInspection(info: BandDeviceInfo) {
        inspectedInfo = info
        mutableState.value = BandConnectionState.Connected(info)
        if (info.oobeComplete == true) {
            mutableOobeStep.value = BandOobeStep.Complete
            preferences.setOobeStep(BandOobeStep.Complete)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun recoverCompletedSetup(device: BluetoothDevice, bluetoothName: String): BandDeviceInfo? {
        runCatching { protocol.inspect(bluetoothName) }.getOrNull()?.let { return it }
        return runCatching {
            transport.disconnect()
            delay(1_000)
            transport.connect(device)
            protocol.inspect(bluetoothName)
        }.getOrNull()
    }

    private suspend fun syncClockInternal(deviceInfo: BandDeviceInfo): BandDeviceInfo {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        protocol.setUtcTime(now)
        protocol.setTimeZone(zone, now)
        val bandUtc = protocol.getUtcTime()
        val bandLocal = protocol.getLocalTime()
        if (kotlin.math.abs(ChronoUnit.SECONDS.between(now, bandUtc)) > 10) {
            throw BandException.InvalidPacket("Band UTC clock verification failed")
        }
        val expectedLocal = LocalDateTime.ofInstant(now, zone)
        if (kotlin.math.abs(ChronoUnit.MINUTES.between(expectedLocal, bandLocal)) > 1) {
            throw BandException.InvalidPacket("Band timezone verification failed")
        }
        return deviceInfo.copy(bandTime = bandUtc, bandLocalTime = bandLocal).also {
            inspectedInfo = it
            mutableState.value = BandConnectionState.Connected(it)
        }
    }

    private fun commandId(bytes: ByteArray): Int? {
        val offset = if (bytes.size >= 9 && bytes[0].toInt() and 0xFF == bytes.size - 1) 1 else 0
        return if (bytes.size >= offset + 4 && bytes[offset] == 0xF9.toByte() && bytes[offset + 1] == 0x2E.toByte()) {
            ((bytes[offset + 3].toInt() and 0xFF) shl 8) or (bytes[offset + 2].toInt() and 0xFF)
        } else null
    }

    private fun stableNotificationId(key: String): Int = (key.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    private fun Throwable.userMessage(): String = when (this) {
        is BandException -> message ?: "Band operation failed"
        else -> message ?: "Band operation failed"
    }

    companion object {
        const val LATEST_FIRMWARE_VERSION = "2.0.5202.0"
        private const val LATEST_FIRMWARE_SIZE = 1_838_103L
        private const val LATEST_FIRMWARE_SHA256 = "2473896B8281B2FF81E462374A48BE8A3E8901FB6B2C55AF0FE9125930A60727"
        private const val LATEST_FIRMWARE_URL = "https://raw.githubusercontent.com/MicrosoftBandDev/archive/main/Firmware/Band%202%20(2.0.5202.0)/envoy-2.0.5202.0.bin"
        private val SENSITIVE_TRANSFER_COMMANDS = setOf(0xCC05, 0xC311)
        private val SENSITIVE_RESPONSE_COMMANDS = setOf(0x8F83, 0xCE82, 0xCE83, 0xCE84)
    }
}
