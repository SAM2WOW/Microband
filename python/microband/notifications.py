"""Band 2 (Envoy) protobuf payloads for on-device notifications."""

from __future__ import annotations

import uuid

from .codec import guid_le, u16

GENERIC_DIALOG_MESSAGE_TYPE = 104
MESSAGING_MESSAGE_TYPE = 101

SMS_TILE_ID = uuid.UUID("b4edbc35-027b-4d10-a797-1099cd2ad98a")
CALLS_TILE_ID = uuid.UUID("22b1c099-f2be-4bac-8ed8-2d6b0b3c25d1")

TITLE_MAX_BYTES = 40
BODY_MAX_BYTES = 320


def command_arguments(payload_size: int, message_type: int = GENERIC_DIALOG_MESSAGE_TYPE) -> bytes:
    if not 0 <= payload_size <= 0xFFFF:
        raise ValueError("Notification payload is too large")
    return u16(payload_size) + u16(message_type)


def generic_dialog(
    title: str,
    body: str,
    force_dialog: bool = True,
    tile_id: uuid.UUID = SMS_TILE_ID,
) -> bytes:
    out = bytearray()
    _write_message(out, 0x0A, _guid_message(tile_id))
    _write_string(out, 0x12, title, TITLE_MAX_BYTES)
    _write_string(out, 0x1A, body, BODY_MAX_BYTES)
    if force_dialog:
        _write_varint_field(out, 0x20, 1)
    return bytes(out)


# --- minimal protobuf writer ----------------------------------------------

def _guid_message(tile_id: uuid.UUID) -> bytes:
    out = bytearray()
    _write_message(out, 0x0A, guid_le(tile_id))
    return bytes(out)


def _write_varint(out: bytearray, value: int) -> None:
    value &= 0xFFFF_FFFF
    while True:
        byte = value & 0x7F
        value >>= 7
        out.append(byte | (0x80 if value else 0x00))
        if not value:
            return


def _write_varint_field(out: bytearray, tag: int, value: int) -> None:
    out.append(tag)
    _write_varint(out, value)


def _write_message(out: bytearray, tag: int, body: bytes) -> None:
    out.append(tag)
    _write_varint(out, len(body))
    out += body


def _write_string(out: bytearray, tag: int, value: str, max_bytes: int) -> None:
    encoded = truncate_utf8(value.strip(), max_bytes)
    if encoded:
        _write_message(out, tag, encoded)


def truncate_utf8(value: str, max_bytes: int) -> bytes:
    """Trim to `max_bytes` without splitting a character."""
    out = bytearray()
    for character in value:
        encoded = character.encode("utf-8")
        if len(out) + len(encoded) > max_bytes:
            break
        out += encoded
    return bytes(out)
