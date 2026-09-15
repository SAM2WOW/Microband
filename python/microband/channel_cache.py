"""Persists discovered RFCOMM channels across runs, keyed by MAC address.

Without SDP, the first connect to a given Band has to probe channels 1..30
(see `transport.resolve_channel`). Remembering the channel that answered last
time turns every connect after the very first one on this machine into a
direct, instant attempt -- no probing, no PyBluez required.

Storage is a single small JSON file under the user's per-app config
directory. Any I/O failure (missing permissions, read-only filesystem,
corrupt file) is swallowed: the cache is a pure optimization, never a
dependency for correctness -- losing it just means the next connect falls
back to probing.
"""

from __future__ import annotations

import json
import os
import sys
import threading
from pathlib import Path
from typing import Optional

_FILENAME = "channels.json"


def _config_dir() -> Path:
    if sys.platform == "win32":
        base = os.environ.get("LOCALAPPDATA") or (Path.home() / "AppData" / "Local")
        return Path(base) / "Microband"
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "Microband"
    base = os.environ.get("XDG_CONFIG_HOME") or (Path.home() / ".config")
    return Path(base) / "microband"


class ChannelCache:
    """A tiny, thread-safe, disk-backed `address -> RFCOMM channel` map."""

    def __init__(self, path: Optional[Path] = None) -> None:
        self._path = path or (_config_dir() / _FILENAME)
        self._lock = threading.Lock()
        self._data: dict[str, int] = self._load()

    def _load(self) -> dict[str, int]:
        try:
            raw = json.loads(self._path.read_text(encoding="utf-8"))
            return {str(k).upper(): int(v) for k, v in raw.items() if isinstance(v, int)}
        except (OSError, ValueError):
            return {}

    def get(self, address: str) -> Optional[int]:
        with self._lock:
            return self._data.get(address.upper())

    def set(self, address: str, channel: int) -> None:
        with self._lock:
            if self._data.get(address.upper()) == channel:
                return
            self._data[address.upper()] = channel
            self._save()

    def forget(self, address: str) -> None:
        with self._lock:
            if self._data.pop(address.upper(), None) is not None:
                self._save()

    def _save(self) -> None:
        try:
            self._path.parent.mkdir(parents=True, exist_ok=True)
            self._path.write_text(json.dumps(self._data, indent=2, sort_keys=True), encoding="utf-8")
        except OSError:
            pass  # best-effort: a failed write just costs the next process a re-probe


# One cache, shared by every RfcommTransport in this process.
default_cache = ChannelCache()
