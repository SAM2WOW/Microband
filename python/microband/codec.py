"""Packet framing and small binary helpers shared by every Band codec."""

from __future__ import annotations

import struct
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from .constants import COMMAND_MARKER, FILETIME_EPOCH_TICKS, STATUS_MARKER, TICKS_PER_SECOND


class BandError(Exception):
    """Base class for every Band failure surfaced to the caller."""


class InvalidPacket(BandError):
    pass


class Disconnected(BandError):
    pass


class BandStatusError(BandError):
    def __init__(self, status: "BandStatus") -> None:
        super().__init__(f"Band rejected the command (status 0x{status.raw:08X})")
        self.status = status


@dataclass(frozen=True)
class BandStatus:
    raw: int

    @property
    def is_error(self) -> bool:
        return bool(self.raw & 0x8000_0000)

    @property
    def facility(self) -> int:
        return (self.raw >> 16) & 0x7FF

    @property
    def code(self) -> int:
        return self.raw & 0xFFFF

    def __str__(self) -> str:
        return f"0x{self.raw:08X} (facility={self.facility}, code={self.code}, error={self.is_error})"


@dataclass(frozen=True)
class RawResponse:
    payload: bytes
    status: BandStatus


def command(facility: int, transferless: bool, code: int, data_length: int, arguments: bytes = b"") -> bytes:
    """Build a Band command header, optionally followed by inline arguments."""
    code_byte = (code & 0x7F) | (0x80 if transferless else 0x00)
    return struct.pack("<HBBI", COMMAND_MARKER, code_byte, facility, data_length) + arguments


def frame(cmd: bytes) -> bytes:
    """Prefix a command with its length; the Band caps a frame at 255 bytes."""
    if len(cmd) > 255:
        raise ValueError("Band command frame is limited to 255 bytes")
    return bytes([len(cmd)]) + cmd


def parse_status(data: bytes) -> BandStatus:
    if len(data) != 6:
        raise InvalidPacket("Status packet must be 6 bytes")
    marker, raw = struct.unpack("<HI", data)
    if marker != STATUS_MARKER:
        raise InvalidPacket("Missing Band status marker")
    return BandStatus(raw)


# --- little-endian scalars -------------------------------------------------

def u16(value: int) -> bytes:
    return struct.pack("<H", value & 0xFFFF)


def u32(value: int) -> bytes:
    return struct.pack("<I", value & 0xFFFF_FFFF)


def i32(value: int) -> bytes:
    return struct.pack("<i", value)


def u64(value: int) -> bytes:
    return struct.pack("<Q", value & 0xFFFF_FFFF_FFFF_FFFF)


def read_u16(data: bytes, offset: int = 0) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def read_u32(data: bytes, offset: int = 0) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def read_u64(data: bytes, offset: int = 0) -> int:
    return struct.unpack_from("<Q", data, offset)[0]


# --- Windows FILETIME ------------------------------------------------------

_UNIX_EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)


def to_filetime(moment: datetime) -> int:
    # Integer arithmetic only: FILETIME ticks exceed the 53-bit float mantissa.
    delta = moment.astimezone(timezone.utc) - _UNIX_EPOCH
    ticks = (delta.days * 86_400 + delta.seconds) * TICKS_PER_SECOND + delta.microseconds * 10
    return ticks + FILETIME_EPOCH_TICKS


def from_filetime(value: int) -> datetime:
    ticks = value - FILETIME_EPOCH_TICKS
    seconds, remainder = divmod(ticks, TICKS_PER_SECOND)
    return _UNIX_EPOCH + timedelta(seconds=seconds, microseconds=remainder // 10)


def guid_le(value: uuid.UUID) -> bytes:
    """Serialize a UUID the way Windows lays out a GUID struct in memory."""
    raw = value.bytes
    return raw[0:4][::-1] + raw[4:6][::-1] + raw[6:8][::-1] + raw[8:16]


def hexdump(data: bytes, limit: int = 48) -> str:
    shown = data[:limit]
    text = " ".join(f"{byte:02X}" for byte in shown)
    return text + (f" … (+{len(data) - limit} bytes)" if len(data) > limit else "")
