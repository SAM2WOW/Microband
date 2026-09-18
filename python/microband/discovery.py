"""Best-effort listing of already-paired Bluetooth devices, per platform.

The Band speaks Bluetooth Classic (RFCOMM), so it must be paired in the OS
before this app can reach it. Nothing here pairs a device; we only enumerate
what the OS already knows so the user can pick instead of typing a MAC.
"""

from __future__ import annotations

import re
import subprocess
import sys
from dataclasses import dataclass

_MAC_RE = re.compile(r"(?:[0-9A-F]{2}:){5}[0-9A-F]{2}", re.IGNORECASE)


@dataclass(frozen=True)
class PairedDevice:
    address: str
    name: str

    def __str__(self) -> str:
        return f"{self.name} [{self.address}]"


def normalize_address(text: str) -> str:
    """Accept 001DFD123456, 00-1D-FD-12-34-56 or 00:1d:fd:12:34:56."""
    digits = re.sub(r"[^0-9A-Fa-f]", "", text)
    if len(digits) != 12:
        raise ValueError(f"{text!r} is not a Bluetooth MAC address")
    return ":".join(digits[i : i + 2] for i in (0, 2, 4, 6, 8, 10)).upper()


def list_paired_devices() -> list[PairedDevice]:
    try:
        if sys.platform == "win32":
            devices = _list_windows()
        elif sys.platform == "darwin":
            devices = _list_macos()
        else:
            devices = _list_linux()
    except Exception:
        return []
    return sorted({d.address: d for d in devices}.values(), key=lambda d: d.name.lower())


def _run(args: list[str]) -> str:
    return subprocess.run(
        args,
        capture_output=True,
        text=True,
        timeout=20,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
    ).stdout


def _list_windows() -> list[PairedDevice]:
    script = (
        "Get-PnpDevice -Class Bluetooth -PresentOnly "
        "| Where-Object { $_.InstanceId -match 'DEV_[0-9A-Fa-f]{12}' } "
        "| ForEach-Object { $_.InstanceId + '|' + $_.FriendlyName }"
    )
    output = _run(["powershell", "-NoProfile", "-NonInteractive", "-Command", script])
    devices = []
    for line in output.splitlines():
        instance_id, _, name = line.partition("|")
        match = re.search(r"DEV_([0-9A-Fa-f]{12})", instance_id)
        if match:
            devices.append(PairedDevice(normalize_address(match.group(1)), name.strip() or "Unknown"))
    return devices


def _list_linux() -> list[PairedDevice]:
    output = _run(["bluetoothctl", "devices", "Paired"]) or _run(["bluetoothctl", "devices"])
    devices = []
    for line in output.splitlines():
        parts = line.split(maxsplit=2)
        if len(parts) >= 2 and parts[0] == "Device" and _MAC_RE.fullmatch(parts[1]):
            devices.append(PairedDevice(parts[1].upper(), parts[2].strip() if len(parts) > 2 else "Unknown"))
    return devices


def _list_macos() -> list[PairedDevice]:
    output = _run(["system_profiler", "SPBluetoothDataType"])
    devices = []
    name = None
    for line in output.splitlines():
        stripped = line.strip()
        if stripped.endswith(":") and not stripped.startswith("Address"):
            name = stripped[:-1]
        elif stripped.startswith("Address:") and name:
            match = _MAC_RE.search(stripped)
            if match:
                devices.append(PairedDevice(match.group(0).upper().replace("-", ":"), name))
    return devices
