package com.unsame.microband.band.model

data class BandFirmwareComponent(
    val name: String,
    val pcbId: Int,
    val version: String,
)

data class BandFirmwareIdentity(
    val runningApplication: FirmwareApplication,
    val components: List<BandFirmwareComponent>,
) {
    val pcbId: Int get() = components.firstOrNull { it.name == "App" }?.pcbId ?: 0
    val applicationVersion: String get() = components.firstOrNull { it.name == "App" }?.version.orEmpty()
    val bootloaderVersion: String get() = components.firstOrNull { it.name == "1BL" }?.version.orEmpty()
}

enum class FirmwareUpdateStage {
    Idle, Downloading, Checking, EnteringUpdater, Transferring, Rebooting, Verifying, Complete, Failed
}

data class FirmwareUpdateStatus(
    val stage: FirmwareUpdateStage = FirmwareUpdateStage.Idle,
    val percent: Int = 0,
    val message: String = "",
) {
    val isRunning: Boolean get() = stage in setOf(
        FirmwareUpdateStage.Downloading,
        FirmwareUpdateStage.Checking,
        FirmwareUpdateStage.EnteringUpdater,
        FirmwareUpdateStage.Transferring,
        FirmwareUpdateStage.Rebooting,
        FirmwareUpdateStage.Verifying,
    )
}

data class BandTileInfo(
    val id: String,
    val name: String,
    val order: Int,
    val settingsMask: Int,
    internal val wireData: ByteArray,
)

data class BandTileCatalog(
    val installed: List<BandTileInfo> = emptyList(),
    val available: List<BandTileInfo> = emptyList(),
    val capacity: Int = 0,
)
