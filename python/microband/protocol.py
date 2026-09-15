"""High-level Band commands built on top of a transport.

Every public method is a single logical operation. `_read` and `_write` are the
only two places that touch the wire, and a lock serializes them so the GUI can
never interleave two transactions on one socket.
"""

from __future__ import annotations

import threading
import time
from datetime import datetime, timezone
from typing import Optional

from . import codec, health, notifications
from .codec import BandStatusError, InvalidPacket
from .constants import FIRMWARE_APPLICATIONS, Facility, SubscriptionType
from .models import DailyMetrics, DeviceInfo, FirmwareComponent
from .transport import RfcommTransport

FIRMWARE_RECORD_SIZE = 19
FIRMWARE_RECORD_COUNT = 3


class BandProtocol:
    def __init__(self, transport: RfcommTransport) -> None:
        self._transport = transport
        self._lock = threading.RLock()

    # --- wire primitives ---------------------------------------------------

    def _read(self, facility: int, code: int, response_length: int, arguments: bytes = b"", timeout: float = 10.0) -> bytes:
        with self._lock:
            packet = codec.command(facility, True, code, response_length, arguments)
            response = self._transport.transact(packet, response_length, timeout=timeout)
            _validate(response.status)
            return response.payload

    def _write(self, facility: int, code: int, transfer: bytes = b"", arguments: bytes = b"", timeout: float = 10.0) -> None:
        with self._lock:
            packet = codec.command(facility, False, code, len(transfer), arguments)
            response = self._transport.transact(packet, 0, transfer, timeout=timeout)
            _validate(response.status)

    # --- handshake / identity ----------------------------------------------

    def check_sdk_compatibility(self) -> None:
        """Announce ourselves as a Windows host speaking SDK protocol version 3."""
        self._write(Facility.JUTIL, 7, arguments=bytes([2, 0, 3, 0]))

    def who_am_i(self) -> str:
        value = self._read(Facility.JUTIL, 3, 1)[0]
        return FIRMWARE_APPLICATIONS.get(value, f"Unknown({value})")

    def get_pcb_id(self) -> int:
        return codec.read_u64(self._read(Facility.CONFIGURATION, 11, 8))

    def get_firmware_components(self) -> list[FirmwareComponent]:
        data = self._read(Facility.JUTIL, 1, FIRMWARE_RECORD_SIZE * FIRMWARE_RECORD_COUNT)
        components = []
        for index in range(FIRMWARE_RECORD_COUNT):
            record = data[index * FIRMWARE_RECORD_SIZE : (index + 1) * FIRMWARE_RECORD_SIZE]
            major = codec.read_u16(record, 6)
            minor = codec.read_u16(record, 8)
            revision = codec.read_u32(record, 10)
            build = codec.read_u32(record, 14)
            components.append(
                FirmwareComponent(
                    name=record[0:5].decode("ascii", "replace").rstrip("\x00"),
                    pcb_id=record[5],
                    version=f"{major}.{minor}.{build}.{revision}",
                )
            )
        return components

    def is_oobe_complete(self) -> bool:
        return any(self._read(Facility.SYSTEM_SETTINGS, 19, 4))

    def inspect(self, bluetooth_name: str = "Microsoft Band", address: str = "") -> DeviceInfo:
        """One round of the reads the app shows right after connecting."""
        components = self.get_firmware_components()
        main = next((c for c in components if c.name == "App"), components[-1])
        direct_pcb = self.get_pcb_id()
        return DeviceInfo(
            bluetooth_name=bluetooth_name,
            address=address,
            pcb_id=direct_pcb or main.pcb_id,
            firmware_application=self.who_am_i(),
            firmware_version=main.version,
            oobe_complete=self.is_oobe_complete(),
            band_time=self.get_utc_time(),
            components=tuple(components),
        )

    # --- time ---------------------------------------------------------------

    def get_utc_time(self) -> datetime:
        return codec.from_filetime(codec.read_u64(self._read(Facility.TIME, 0, 8)))

    def set_utc_time(self, moment: Optional[datetime] = None) -> None:
        moment = moment or datetime.now(timezone.utc)
        self._write(Facility.TIME, 1, codec.u64(codec.to_filetime(moment)))

    # --- sensors ------------------------------------------------------------

    def battery_percent(self, attempts: int = 10, interval: float = 0.3) -> int:
        payload = self._poll_subscription(SubscriptionType.BATTERY, attempts, interval, minimum_length=9)
        value = health.battery_percent(payload) if payload else None
        if value is None:
            raise InvalidPacket("The Band did not return its battery level")
        return value

    def get_daily_metrics(self) -> DailyMetrics:
        metrics = DailyMetrics()
        for subscription in health.DAILY_SUBSCRIPTIONS:
            metrics = metrics.merge(self._read_metric(subscription))
        if not metrics.has_data:
            # Pre-daily-values firmware (e.g. 2.0.3640) only has cumulative counters.
            for subscription in health.LEGACY_SUBSCRIPTIONS:
                metrics = metrics.merge(self._read_metric(subscription))
        return metrics

    def _read_metric(self, subscription: int) -> DailyMetrics:
        try:
            payload = self._poll_subscription(subscription, attempts=5, interval=0.3, minimum_length=1)
        except BandStatusError:
            return DailyMetrics()
        return health.daily_metrics(payload) if payload else DailyMetrics()

    def _poll_subscription(self, subscription: int, attempts: int, interval: float, minimum_length: int) -> bytes:
        """Subscribe, poll until a sample arrives, and always unsubscribe."""
        self._write(Facility.REMOTE_SUBSCRIPTION, 0, arguments=bytes([subscription]) + codec.u32(0))
        try:
            for _ in range(attempts):
                time.sleep(interval)
                length = codec.read_u32(self._read(Facility.REMOTE_SUBSCRIPTION, 2, 4))
                if minimum_length <= length <= 4096:
                    return self._read(Facility.REMOTE_SUBSCRIPTION, 3, length)
            return b""
        finally:
            try:
                self._write(Facility.REMOTE_SUBSCRIPTION, 1, arguments=bytes([subscription]))
            except Exception:
                pass

    # --- notifications ------------------------------------------------------

    def show_notification(self, title: str, body: str) -> None:
        if not title.strip() and not body.strip():
            raise InvalidPacket("Notification title and body are empty")
        payload = notifications.generic_dialog(title, body)
        self._write(
            Facility.NOTIFICATION,
            5,
            transfer=payload,
            arguments=notifications.command_arguments(len(payload)),
        )


def _validate(status: codec.BandStatus) -> None:
    if status.is_error:
        raise BandStatusError(status)
