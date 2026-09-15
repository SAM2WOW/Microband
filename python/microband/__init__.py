"""A minimal Python client for the Microsoft Band 2 RFCOMM protocol."""

from .codec import BandError, BandStatus, BandStatusError, Disconnected, InvalidPacket
from .models import DailyMetrics, DeviceInfo, FirmwareComponent
from .protocol import BandProtocol
from .transport import RfcommTransport

__all__ = [
    "BandError",
    "BandProtocol",
    "BandStatus",
    "BandStatusError",
    "DailyMetrics",
    "DeviceInfo",
    "Disconnected",
    "FirmwareComponent",
    "InvalidPacket",
    "RfcommTransport",
]
