"""Bluetooth Classic (RFCOMM) transport for the Band.

The Band is not a BLE peripheral: it exposes a custom RFCOMM service, so
`bleak` cannot talk to it. Python's stdlib `socket` module supports
AF_BLUETOOTH/BTPROTO_RFCOMM on Windows and Linux, which is all we need --
except SDP lookup, so the channel is resolved via PyBluez when installed and
probed otherwise.
"""

from __future__ import annotations

import socket
import threading
import time
from typing import Callable, Optional

from . import codec
from .channel_cache import ChannelCache, default_cache
from .codec import BandStatus, Disconnected, RawResponse
from .constants import RFCOMM_SERVICE_UUID

PacketObserver = Callable[[str, bytes, Optional[str]], None]

# Channels tried when SDP is unavailable. The Band's service normally lands low.
_PROBE_CHANNELS = tuple(range(1, 31))
PROBE_TIMEOUT = 3.0


def _rfcomm_supported() -> bool:
    return hasattr(socket, "AF_BLUETOOTH") and hasattr(socket, "BTPROTO_RFCOMM")


def resolve_channel(address: str) -> Optional[int]:
    """Resolve the Band's RFCOMM channel over SDP, if PyBluez is available."""
    try:
        import bluetooth  # type: ignore
    except ImportError:
        return None
    services = bluetooth.find_service(uuid=str(RFCOMM_SERVICE_UUID), address=address)
    for service in services:
        if service.get("protocol") == "RFCOMM" and service.get("port"):
            return int(service["port"])
    return None


class RfcommTransport:
    """A single Band connection. Not safe for concurrent use; see BandProtocol."""

    def __init__(self, observer: Optional[PacketObserver] = None, channel_cache: Optional[ChannelCache] = None) -> None:
        self._observer = observer or (lambda direction, data, note: None)
        self._socket: Optional[socket.socket] = None
        self._lock = threading.Lock()
        # Disk-backed, so a channel discovered by any previous run -- not just
        # this process -- lets every later connect skip straight past probing.
        self._channel_cache = channel_cache or default_cache

    # --- lifecycle ---------------------------------------------------------

    @property
    def is_connected(self) -> bool:
        return self._socket is not None

    def connect(self, address: str, channel: Optional[int] = None, timeout: float = 15.0) -> int:
        """Connect to `address`, returning the RFCOMM channel that answered."""
        if not _rfcomm_supported():
            raise Disconnected("This Python build has no Bluetooth RFCOMM socket support")
        self.close()

        # A remembered or SDP-resolved channel is tried first, on its own, with
        # the full timeout -- it should just work. Only if that fails (or
        # nothing is remembered) do we fall back to the slower channel-by-channel
        # probe, which also clears out a stale remembered channel so we don't
        # keep retrying a bad value on every future connect.
        trusted = channel or self._channel_cache.get(address) or resolve_channel(address)
        if trusted is not None:
            self._observer("INFO", b"", f"trying remembered RFCOMM channel {trusted}")
            # Even the trusted attempt is capped at PROBE_TIMEOUT: a real reply
            # (success or refusal) is normally near-instant, and a channel that's
            # gone genuinely silent is exactly the case that must not leave the
            # caller staring at "Connecting..." with nothing logged for `timeout`
            # seconds -- it should drop into the visible, incremental probe below.
            result = self._try_connect(address, trusted, min(timeout, PROBE_TIMEOUT), settle_timeout=timeout)
            if result is not None:
                self._channel_cache.set(address, trusted)
                return result
            if channel is not None:
                raise Disconnected(f"Unable to reach {address} on channel {channel}")
            self._channel_cache.forget(address)

        # Probing must stay fast per channel: an unreachable Band would otherwise
        # stall for minutes with the caller seeing nothing but "Connecting...".
        # Every attempt is short, and reported to the observer so progress is visible.
        remaining = [c for c in _PROBE_CHANNELS if c != trusted]
        deadline = time.monotonic() + PROBE_TIMEOUT * len(remaining)
        for index, candidate in enumerate(remaining):
            if time.monotonic() >= deadline:
                break
            self._observer("INFO", b"", f"probing RFCOMM channel {candidate} ({index + 1}/{len(remaining)})")
            result = self._try_connect(address, candidate, PROBE_TIMEOUT, settle_timeout=timeout)
            if result is not None:
                self._channel_cache.set(address, candidate)
                return result

        raise Disconnected(f"Unable to reach {address}: no RFCOMM channel answered")

    def _try_connect(self, address: str, channel: int, timeout: float, settle_timeout: Optional[float] = None) -> Optional[int]:
        """Attempt one channel; return it on success, or None (never raises OSError)."""
        sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        sock.settimeout(timeout)
        try:
            sock.connect((address, channel))
        except OSError:
            sock.close()
            return None
        sock.settimeout(settle_timeout if settle_timeout is not None else timeout)
        self._socket = sock
        self._observer("INFO", b"", f"connected to {address} on RFCOMM channel {channel}")
        return channel

    def close(self) -> None:
        sock, self._socket = self._socket, None
        if sock is not None:
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            sock.close()

    def __enter__(self) -> "RfcommTransport":
        return self

    def __exit__(self, *exc_info: object) -> None:
        self.close()

    # --- raw I/O -----------------------------------------------------------

    def _require_socket(self) -> socket.socket:
        if self._socket is None:
            raise Disconnected("The Band is not connected")
        return self._socket

    def write(self, data: bytes) -> None:
        self._require_socket().sendall(data)
        self._observer("TX", data, None)

    def read_exact(self, length: int) -> bytes:
        if length <= 0:
            return b""
        sock = self._require_socket()
        chunks = bytearray()
        while len(chunks) < length:
            try:
                chunk = sock.recv(length - len(chunks))
            except OSError as error:
                self.close()
                raise Disconnected(f"The Band connection dropped: {error}") from error
            if not chunk:
                self.close()
                raise Disconnected("The Band disconnected")
            chunks += chunk
        data = bytes(chunks)
        self._observer("RX", data, None)
        return data

    # --- transactions ------------------------------------------------------

    def transact(
        self,
        command: bytes,
        response_length: int = 0,
        transfer: bytes = b"",
        timeout: float = 10.0,
    ) -> RawResponse:
        """Send one command (plus optional bulk transfer) and read its reply."""
        with self._lock:
            sock = self._require_socket()
            previous = sock.gettimeout()
            sock.settimeout(timeout)
            try:
                self.write(codec.frame(command))
                if transfer:
                    self.write(transfer)
                payload = self.read_exact(response_length)
                status = codec.parse_status(self.read_exact(6))
                self._observer("STATUS", b"", str(status))
                return RawResponse(payload, status)
            finally:
                if self._socket is not None:
                    self._socket.settimeout(previous)

    def write_bulk(self, data: bytes, chunk_size: int = 8192, on_progress: Optional[Callable[[int], None]] = None) -> None:
        """Stream a large payload (firmware, wallpaper) after its command header."""
        sent = 0
        for offset in range(0, len(data), chunk_size):
            self.write(data[offset : offset + chunk_size])
            sent += chunk_size
            if on_progress:
                on_progress(min(100, sent * 100 // max(1, len(data))))

    def read_status(self, timeout: float = 10.0) -> BandStatus:
        sock = self._require_socket()
        previous = sock.gettimeout()
        sock.settimeout(timeout)
        try:
            return codec.parse_status(self.read_exact(6))
        finally:
            if self._socket is not None:
                self._socket.settimeout(previous)
