"""Decoders for the Band's remote-subscription sample stream."""

from __future__ import annotations

from typing import Iterator, Optional

from .codec import read_u16, read_u32
from .constants import SubscriptionType
from .models import DailyMetrics


def iter_samples(payload: bytes) -> Iterator[tuple[int, bytes]]:
    """Walk the (type, reserved, size) framed samples in a subscription payload."""
    offset = 0
    while offset + 4 <= len(payload):
        sample_type = payload[offset]
        size = read_u16(payload, offset + 2)
        offset += 4
        if offset + size > len(payload):
            return
        yield sample_type, payload[offset : offset + size]
        offset += size


def battery_percent(payload: bytes) -> Optional[int]:
    for sample_type, body in iter_samples(payload):
        if sample_type == SubscriptionType.BATTERY and len(body) >= 5:
            return body[0]
    return None


def daily_metrics(payload: bytes) -> DailyMetrics:
    metrics = DailyMetrics()
    for sample_type, body in iter_samples(payload):
        metrics = metrics.merge(_decode_sample(sample_type, body))
    return metrics


def _decode_sample(sample_type: int, body: bytes) -> DailyMetrics:
    size = len(body)
    # Firmware with daily-value subscriptions reports today's totals directly.
    if sample_type == SubscriptionType.PEDOMETER_DAILY and size >= 8:
        return DailyMetrics(steps=read_u32(body, 4))
    if sample_type == SubscriptionType.CALORIES_DAILY and size >= 8:
        return DailyMetrics(calories=read_u32(body, 4))
    if sample_type == SubscriptionType.DISTANCE_DAILY and size >= 17:
        return DailyMetrics(distance_cm=read_u32(body, 13))
    if sample_type == SubscriptionType.ELEVATION_DAILY and size >= 42:
        return DailyMetrics(flights_ascended=read_u32(body, 34), elevation_gain_cm=read_u32(body, 38))
    if sample_type == SubscriptionType.UV_DAILY and size >= 5:
        return DailyMetrics(uv_exposure=read_u32(body, 1))
    # Early Band 2 firmware only exposes cumulative counters since the last reset.
    if sample_type == SubscriptionType.PEDOMETER and size >= 13:
        return DailyMetrics(steps=read_u32(body, 0), cumulative_since_reset=True)
    if sample_type == SubscriptionType.CALORIES and size >= 20:
        return DailyMetrics(calories=read_u32(body, 0), cumulative_since_reset=True)
    if sample_type == SubscriptionType.DISTANCE and size >= 22:
        return DailyMetrics(distance_cm=read_u32(body, 0), cumulative_since_reset=True)
    return DailyMetrics()


DAILY_SUBSCRIPTIONS = (
    SubscriptionType.PEDOMETER_DAILY,
    SubscriptionType.CALORIES_DAILY,
    SubscriptionType.DISTANCE_DAILY,
    SubscriptionType.ELEVATION_DAILY,
    SubscriptionType.UV_DAILY,
)

LEGACY_SUBSCRIPTIONS = (
    SubscriptionType.PEDOMETER,
    SubscriptionType.CALORIES,
    SubscriptionType.DISTANCE,
)
