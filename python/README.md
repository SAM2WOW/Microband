# Microband — Python / Tkinter client

A minimal desktop client for the Microsoft Band 2, mirroring the protocol used
by the Android app in this repository.

```
python main.py        # Tkinter GUI
python cli.py         # headless smoke test (pick a paired device)
python cli.py 58:82:A8:CD:1E:75 -v    # explicit MAC, dump every packet
```

No third-party dependencies: the transport is `socket` from the standard
library, the UI is `tkinter`. Python 3.9+.

## The Band is not a BLE device

It exposes a **Bluetooth Classic RFCOMM** service
(`A502CA9A-2BA5-413C-A4E0-13804E47B38F`), so `bleak` cannot reach it — BLE
GATT and RFCOMM are different stacks. Python's stdlib gives us RFCOMM sockets
on Windows and Linux, which is enough.

**The Band must already be paired in the OS**; this client never pairs. The one
thing the stdlib lacks is SDP, so the RFCOMM channel is resolved by:

1. PyBluez (`pip install pybluez`), if installed — a real SDP lookup by UUID;
2. otherwise probing channels 1–30 until one accepts the connection.

Probing takes a few seconds on the first connect. `RfcommTransport.connect()`
returns the channel that answered, so you can pass it back to skip the probe.

## Layout

| Module | Responsibility |
| --- | --- |
| `constants.py` | Facility ids, subscription ids, markers — no logic |
| `codec.py` | Command framing, status parsing, LE scalars, FILETIME, GUIDs |
| `notifications.py` | Band 2 (Envoy) protobuf notification payloads |
| `health.py` | Decoders for the remote-subscription sample stream |
| `models.py` | `DeviceInfo`, `DailyMetrics`, `FirmwareComponent` dataclasses |
| `transport.py` | RFCOMM socket, `transact()`, packet observer |
| `protocol.py` | `BandProtocol` — one method per logical Band operation |
| `discovery.py` | Paired-device listing per OS (PowerShell / bluetoothctl / system_profiler) |
| `worker.py` | `TaskRunner`: serial background thread, callbacks on the Tk thread |
| `gui.py` | The Tkinter window; holds no protocol knowledge |

The layering is strict in one direction: `gui → worker → protocol → transport →
codec`. Nothing below `gui.py` imports Tkinter, so the protocol stack is
reusable from a script, a test, or another front end.

## Using it as a library

```python
from microband import BandProtocol, RfcommTransport

with RfcommTransport() as transport:
    transport.connect("58:82:A8:CD:1E:75")
    band = BandProtocol(transport)
    band.check_sdk_compatibility()      # required handshake before anything else
    print(band.inspect())
    print(band.battery_percent(), "%")
    band.set_utc_time()
    band.show_notification("Microband", "Hello from Python.")
```

## Concurrency

`BandProtocol` serializes transactions with a lock, and `TaskRunner` runs one
job at a time, so the single socket is never interleaved. Timeouts are plain
socket timeouts (`settimeout`), which is why this is simpler than the Android
transport — there, a blocking `BluetoothSocket.connect()` has to be unblocked
by closing the socket from a watchdog thread.

## Implemented

Connect / disconnect, SDK handshake, firmware identity, PCB id, OOBE state,
clock read and sync, battery, daily metrics (steps, calories, distance,
floors, UV), and generic-dialog notifications.

Not ported: tiles, wallpaper and theme, firmware update, OOBE flow, Cortana
and keyboard. The codecs above are the pieces those build on; see
`app/src/main/java/fun/unsame/microband/band/protocol/` for the reference
implementations.
