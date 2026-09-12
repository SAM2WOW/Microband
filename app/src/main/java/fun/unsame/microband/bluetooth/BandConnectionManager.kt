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
import com.unsame.microband.data.PacketLogDao
import com.unsame.microband.data.ProtocolPacketLog
import com.unsame.microband.notification.BandPhoneNotification
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class BandConnectionManager(
    private val application: Application,
    private val packetLogDao: PacketLogDao,
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

    private var redactNextTransfer = false
    private val transport = RfcommBandTransport { direction, bytes, status ->
        val id = commandId(bytes)
        val shouldRedact = direction == "TX" && id == null && redactNextTransfer
        if (shouldRedact) redactNextTransfer = false
        if (direction == "TX" && id in SENSITIVE_TRANSFER_COMMANDS) redactNextTransfer = true
        if (preferences.protocolLogging.first()) {
            packetLogDao.insert(
                ProtocolPacketLog(
                    timestampMillis = System.currentTimeMillis(),
                    direction = direction,
                    transport = "RFCOMM",
                    commandId = id,
                    payloadLength = bytes.size,
                    hexPayload = if (shouldRedact) "<redacted private payload>" else bytes.hex(),
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

    @android.annotation.SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) = scope.launch {
        connectionMutex.withLock {
            if (mutableState.value is BandConnectionState.Connected && activeDevice == device) return@withLock
            activeDevice = device
            mutableState.value = BandConnectionState.Connecting
            try {
                ensureBonded(device)
                transport.connect(device)
                val name = runCatching { device.name }.getOrNull() ?: "Microsoft Band"
                mutableState.value = BandConnectionState.Connected(BandDeviceInfo(name))
                try {
                    val info = inspectConnectedBand(name)
                    if (info.oobeComplete == true) syncClockInternal(info)
                } catch (exception: Exception) {
                    mutableEvents.emit("Connected. Tap Check Band to retry device checks: ${exception.userMessage()}")
                }
            } catch (exception: Exception) {
                mutableState.value = BandConnectionState.Error(exception.userMessage())
            }
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
        transport.disconnect()
        inspectedInfo = null
        mutableState.value = BandConnectionState.Disconnected
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
        runCatching { protocol.showNotification("Microband", "Notification forwarding is working") }
            .onSuccess { mutableEvents.emit("Test notification sent to Band") }
            .onFailure { mutableEvents.emit(it.userMessage()) }
    }

    fun forwardNotification(notification: BandPhoneNotification, device: BluetoothDevice) = scope.launch {
        if (mutableState.value !is BandConnectionState.Connected) connect(device).join()
        if (mutableState.value !is BandConnectionState.Connected) return@launch
        val content = listOfNotNull(notification.title, notification.body)
            .filter { it.isNotBlank() }
            .joinToString(" — ")
            .ifBlank { "New notification" }
        runCatching {
            protocol.showNotification(notification.sourceLabel.ifBlank { "Phone" }, content)
        }
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

    private fun Throwable.userMessage(): String = when (this) {
        is BandException -> message ?: "Band operation failed"
        else -> message ?: "Band operation failed"
    }

    companion object {
        private val SENSITIVE_TRANSFER_COMMANDS = setOf(0xCC05, 0xC311)
    }
}
