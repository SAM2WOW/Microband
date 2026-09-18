"""Wire-level constants for the Microsoft Band 2 RFCOMM protocol."""

from __future__ import annotations

import uuid

RFCOMM_SERVICE_UUID = uuid.UUID("A502CA9A-2BA5-413C-A4E0-13804E47B38F")

COMMAND_MARKER = 0x2EF9
STATUS_MARKER = 0xA6FE

# Windows FILETIME epoch (1601-01-01) expressed in 100 ns ticks since 1970-01-01.
FILETIME_EPOCH_TICKS = 116_444_736_000_000_000
TICKS_PER_SECOND = 10_000_000


class Facility:
    TIME = 0x75
    JUTIL = 0x76
    CONFIGURATION = 0x78
    REMOTE_SUBSCRIPTION = 0x8F
    KEYBOARD = 0x9F
    SRAM_FIRMWARE_UPDATE = 0x98
    OOBE = 0xAD
    FIREBALL_UI = 0xC3
    FIREBALL_APPS = 0xD3
    PROFILE = 0xC5
    SYSTEM_SETTINGS = 0xCA
    NOTIFICATION = 0xCC
    PERSISTED_STATISTICS = 0xCE
    INSTALLED_APP_LIST = 0xD4
    THEME_COLOR = 0xD8
    CORTANA = 0xDD


class SubscriptionType:
    """Sensor / statistic ids used by the remote subscription facility."""

    DISTANCE = 13
    PEDOMETER = 19
    BATTERY = 38
    CALORIES = 46
    CALORIES_DAILY = 107
    DISTANCE_DAILY = 108
    PEDOMETER_DAILY = 109
    ELEVATION_DAILY = 110
    UV_DAILY = 111


FIRMWARE_APPLICATIONS = {
    1: "Bootloader",
    2: "UpdaterApp",
    3: "App",
}
