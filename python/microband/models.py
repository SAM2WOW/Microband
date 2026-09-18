"""Plain data returned by the protocol layer."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional


@dataclass(frozen=True)
class FirmwareComponent:
    name: str
    pcb_id: int
    version: str


@dataclass(frozen=True)
class DeviceInfo:
    bluetooth_name: str
    address: str
    pcb_id: int
    firmware_application: str
    firmware_version: str
    oobe_complete: bool
    band_time: datetime
    components: tuple[FirmwareComponent, ...] = ()


@dataclass(frozen=True)
class DailyMetrics:
    steps: Optional[int] = None
    calories: Optional[int] = None
    distance_cm: Optional[int] = None
    flights_ascended: Optional[int] = None
    elevation_gain_cm: Optional[int] = None
    uv_exposure: Optional[int] = None
    cumulative_since_reset: bool = False

    @property
    def has_data(self) -> bool:
        return any(
            value is not None
            for value in (
                self.steps,
                self.calories,
                self.distance_cm,
                self.flights_ascended,
                self.elevation_gain_cm,
                self.uv_exposure,
            )
        )

    def merge(self, other: "DailyMetrics") -> "DailyMetrics":
        """Overlay non-empty fields of `other` onto this snapshot."""
        return DailyMetrics(
            steps=other.steps if other.steps is not None else self.steps,
            calories=other.calories if other.calories is not None else self.calories,
            distance_cm=other.distance_cm if other.distance_cm is not None else self.distance_cm,
            flights_ascended=other.flights_ascended if other.flights_ascended is not None else self.flights_ascended,
            elevation_gain_cm=other.elevation_gain_cm if other.elevation_gain_cm is not None else self.elevation_gain_cm,
            uv_exposure=other.uv_exposure if other.uv_exposure is not None else self.uv_exposure,
            cumulative_since_reset=other.cumulative_since_reset or self.cumulative_since_reset,
        )
