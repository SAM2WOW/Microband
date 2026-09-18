"""Headless smoke test: `python cli.py [MAC]`.

Useful to verify pairing and the RFCOMM channel before opening the GUI.
"""

from __future__ import annotations

import sys

from microband import BandProtocol, RfcommTransport
from microband.codec import hexdump
from microband.discovery import list_paired_devices, normalize_address


def pick_address(arguments: list[str]) -> str:
    if arguments:
        return normalize_address(arguments[0])
    devices = list_paired_devices()
    if not devices:
        raise SystemExit("No paired Bluetooth devices found. Pass a MAC address explicitly.")
    for index, device in enumerate(devices):
        print(f"  [{index}] {device}")
    choice = input("Device number: ").strip()
    return devices[int(choice)].address


def main(argv: list[str]) -> int:
    verbose = "-v" in argv[1:]
    address = pick_address([argument for argument in argv[1:] if not argument.startswith("-")])

    def observe(direction: str, data: bytes, note: str | None) -> None:
        if verbose or direction == "INFO":
            print(f"  {direction} {hexdump(data) if data else note or ''}")

    transport = RfcommTransport(observer=observe)
    with transport:
        channel = transport.connect(address)
        print(f"Connected on RFCOMM channel {channel}")
        band = BandProtocol(transport)
        band.check_sdk_compatibility()
        info = band.inspect(address=address)
        print(f"Firmware  : {info.firmware_version} ({info.firmware_application})")
        print(f"PCB id    : {info.pcb_id}")
        print(f"Setup     : {'complete' if info.oobe_complete else 'not finished'}")
        print(f"Band time : {info.band_time:%Y-%m-%d %H:%M:%S} UTC")
        print(f"Battery   : {band.battery_percent()}%")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
