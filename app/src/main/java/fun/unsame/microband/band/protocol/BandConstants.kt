package com.unsame.microband.band.protocol

import java.util.UUID

object BandConstants {
    val RFCOMM_SERVICE_UUID: UUID = UUID.fromString("A502CA9A-2BA5-413C-A4E0-13804E47B38F")
    const val COMMAND_MARKER: Int = 0x2EF9
    const val STATUS_MARKER: Int = 0xA6FE

    const val FACILITY_TIME = 0x75
    const val FACILITY_JUTIL = 0x76
    const val FACILITY_CONFIGURATION = 0x78
    const val FACILITY_REMOTE_SUBSCRIPTION = 0x8F
    const val FACILITY_SRAM_FIRMWARE_UPDATE = 0x98
    const val FACILITY_OOBE = 0xAD
    const val FACILITY_FIREBALL_UI = 0xC3
    const val FACILITY_FIREBALL_APPS = 0xD3
    const val FACILITY_PROFILE = 0xC5
    const val FACILITY_SYSTEM_SETTINGS = 0xCA
    const val FACILITY_NOTIFICATION = 0xCC
    const val FACILITY_PERSISTED_STATISTICS = 0xCE
    const val FACILITY_INSTALLED_APP_LIST = 0xD4
    const val FACILITY_THEME_COLOR = 0xD8
}
