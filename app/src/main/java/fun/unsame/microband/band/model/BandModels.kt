package com.unsame.microband.band.model

import java.time.Instant
import java.time.LocalDateTime

data class BandDeviceInfo(
    val bluetoothName: String,
    val pcbId: ULong? = null,
    val firmwareApplication: FirmwareApplication? = null,
    val firmwareVersion: String? = null,
    val oobeComplete: Boolean? = null,
    val oobeStage: OobeStage? = null,
    val bandTime: Instant? = null,
    val bandLocalTime: LocalDateTime? = null,
) {
    val isBand2: Boolean get() = pcbId?.let { it >= 20uL } == true
}

enum class FirmwareApplication(val wireValue: Int) {
    OneBL(1), TwoUp(2), App(3), UpApp(4), Unknown(-1);

    companion object {
        fun fromWire(value: Int) = entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

enum class OobeStage(val wireValue: Int, val userLabel: String) {
    AskPhoneType(0, "Choose phone type"),
    DownloadMessage(1, "Waiting for phone"),
    WaitingOnPhoneToEnterCode(2, "Enter pairing code"),
    WaitingOnPhoneToAcceptPairing(3, "Accept pairing"),
    PairingSuccess(4, "Paired"),
    CheckingForUpdate(5, "Checking Band"),
    StartingUpdate(6, "Preparing Band"),
    UpdateComplete(7, "Band prepared"),
    WaitingOnPhoneToCompleteOobe(8, "Ready to finish"),
    PressActionButton(9, "Press the Band button"),
    Error(10, "Setup error"),
    PairMessage(11, "Pairing"),
    PreStateCharging(100, "Charge the Band"),
    PreStateLanguageSelect(101, "Select a language on the Band"),
    Unknown(-1, "Unknown setup state");

    companion object {
        fun fromWire(value: Int) = entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

sealed interface BandConnectionState {
    data object Unassociated : BandConnectionState
    data object Disconnected : BandConnectionState
    data object Connecting : BandConnectionState
    data class Connected(val device: BandDeviceInfo) : BandConnectionState
    data class Error(val reason: String) : BandConnectionState
}

sealed class BandException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class BluetoothPermission : BandException("Bluetooth permission is missing")
    class NotPaired : BandException("The Band is not associated")
    class PairingFailed(message: String = "Pairing with the Band failed") : BandException(message)
    class ConnectionFailed(cause: Throwable? = null) : BandException("Unable to connect to the Band", cause)
    class Disconnected : BandException("The Band disconnected")
    class Timeout : BandException("The Band did not respond in time")
    class InvalidPacket(message: String = "Invalid response from the Band") : BandException(message)
    class DeviceUnsupported : BandException("The connected hardware is not a Microsoft Band 2")
    class WrongFirmware : BandException("The Band is not running its main application")
    class AlreadyConfigured : BandException("This Band is already configured")
    class LanguageRequired : BandException("Select a language on the Band before continuing")
    class ProtocolStatus(val code: UInt) : BandException("The Band returned error 0x${code.toString(16)}")
}

data class BandProfile(
    val deviceName: String,
    val telemetryEnabled: Boolean,
    val lastSync: Instant?,
    val locale: String?,
)
