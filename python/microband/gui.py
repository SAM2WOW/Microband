"""Minimal Tkinter front end for talking to a paired Microsoft Band."""

from __future__ import annotations

import tkinter as tk
from datetime import datetime, timezone
from tkinter import ttk
from typing import Optional

from . import codec, discovery
from .models import DailyMetrics, DeviceInfo
from .protocol import BandProtocol
from .transport import RfcommTransport
from .worker import TaskRunner

INFO_FIELDS = (
    ("name", "Name"),
    ("address", "Address"),
    ("firmware", "Firmware"),
    ("application", "Application"),
    ("pcb", "PCB id"),
    ("oobe", "Setup"),
    ("time", "Band time (UTC)"),
    ("battery", "Battery"),
)


class BandApp(ttk.Frame):
    def __init__(self, master: tk.Misc) -> None:
        super().__init__(master, padding=12)
        self.grid(sticky="nsew")
        master.columnconfigure(0, weight=1)
        master.rowconfigure(0, weight=1)
        self.columnconfigure(0, weight=1)
        self.rowconfigure(3, weight=1)

        self._transport = RfcommTransport(observer=self._observe_packet)
        self._band = BandProtocol(self._transport)
        self._runner = TaskRunner(self)
        self._runner.on_busy_changed = self._on_busy_changed

        self._device_var = tk.StringVar()
        self._status_var = tk.StringVar(value="Disconnected")
        self._log_packets_var = tk.BooleanVar(value=False)
        self._info_vars = {key: tk.StringVar(value="—") for key, _ in INFO_FIELDS}
        self._title_var = tk.StringVar(value="Microband")
        self._body_var = tk.StringVar(value="Hello from Python.")

        self._build_connection_panel()
        self._build_info_panel()
        self._build_actions_panel()
        self._build_log_panel()

        self._set_connected(False)
        self.refresh_devices()

    # --- layout ------------------------------------------------------------

    def _build_connection_panel(self) -> None:
        panel = ttk.LabelFrame(self, text="Connection", padding=8)
        panel.grid(row=0, column=0, sticky="ew")
        panel.columnconfigure(1, weight=1)

        ttk.Label(panel, text="Paired device").grid(row=0, column=0, padx=(0, 8), sticky="w")
        self._device_box = ttk.Combobox(panel, textvariable=self._device_var)
        self._device_box.grid(row=0, column=1, sticky="ew")
        ttk.Button(panel, text="Refresh", command=self.refresh_devices).grid(row=0, column=2, padx=4)
        self._connect_button = ttk.Button(panel, text="Connect", command=self.toggle_connection)
        self._connect_button.grid(row=0, column=3)

        ttk.Label(panel, textvariable=self._status_var, foreground="#555").grid(
            row=1, column=0, columnspan=4, sticky="w", pady=(6, 0)
        )

    def _build_info_panel(self) -> None:
        panel = ttk.LabelFrame(self, text="Band", padding=8)
        panel.grid(row=1, column=0, sticky="ew", pady=(10, 0))
        panel.columnconfigure(1, weight=1)
        panel.columnconfigure(3, weight=1)
        for index, (key, label) in enumerate(INFO_FIELDS):
            row, column = divmod(index, 2)
            ttk.Label(panel, text=f"{label}:").grid(row=row, column=column * 2, sticky="w", padx=(0, 6), pady=1)
            ttk.Label(panel, textvariable=self._info_vars[key]).grid(
                row=row, column=column * 2 + 1, sticky="w", padx=(0, 18), pady=1
            )

    def _build_actions_panel(self) -> None:
        panel = ttk.LabelFrame(self, text="Actions", padding=8)
        panel.grid(row=2, column=0, sticky="ew", pady=(10, 0))
        panel.columnconfigure(3, weight=1)

        self._action_buttons = [
            ttk.Button(panel, text="Refresh info", command=self.read_device_info),
            ttk.Button(panel, text="Battery", command=self.read_battery),
            ttk.Button(panel, text="Sync time", command=self.sync_time),
            ttk.Button(panel, text="Daily metrics", command=self.read_metrics),
        ]
        for column, button in enumerate(self._action_buttons):
            button.grid(row=0, column=column, padx=(0, 6), sticky="w")

        ttk.Label(panel, text="Title").grid(row=1, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(panel, textvariable=self._title_var).grid(row=1, column=1, columnspan=2, sticky="ew", pady=(8, 0))
        ttk.Label(panel, text="Body").grid(row=2, column=0, sticky="w")
        ttk.Entry(panel, textvariable=self._body_var).grid(row=2, column=1, columnspan=2, sticky="ew")
        self._notify_button = ttk.Button(panel, text="Send notification", command=self.send_notification)
        self._notify_button.grid(row=2, column=3, sticky="w", padx=(6, 0))
        self._action_buttons.append(self._notify_button)

    def _build_log_panel(self) -> None:
        panel = ttk.LabelFrame(self, text="Log", padding=8)
        panel.grid(row=3, column=0, sticky="nsew", pady=(10, 0))
        panel.columnconfigure(0, weight=1)
        panel.rowconfigure(1, weight=1)

        ttk.Checkbutton(panel, text="Log raw packets", variable=self._log_packets_var).grid(row=0, column=0, sticky="w")
        self._log = tk.Text(panel, height=12, wrap="none", state="disabled", font=("Consolas", 9))
        self._log.grid(row=1, column=0, sticky="nsew")
        scroll = ttk.Scrollbar(panel, orient="vertical", command=self._log.yview)
        scroll.grid(row=1, column=1, sticky="ns")
        self._log.configure(yscrollcommand=scroll.set)

    # --- connection --------------------------------------------------------

    def refresh_devices(self) -> None:
        devices = discovery.list_paired_devices()
        self._device_box["values"] = [str(device) for device in devices]
        if devices and not self._device_var.get():
            band = next((d for d in devices if "band" in d.name.lower()), devices[0])
            self._device_var.set(str(band))
        self.log(f"Found {len(devices)} paired device(s).")

    def toggle_connection(self) -> None:
        if self._transport.is_connected:
            self.disconnect()
        else:
            self.connect()

    def connect(self) -> None:
        try:
            address = discovery.normalize_address(self._device_var.get().split("[")[-1])
        except ValueError:
            self.log("Pick a paired device, or type its MAC address.")
            return
        name = self._device_var.get().split("[")[0].strip() or "Microsoft Band"

        def work() -> DeviceInfo:
            self._transport.connect(address)
            self._band.check_sdk_compatibility()
            return self._band.inspect(name, address)

        self._status_var.set(f"Connecting to {address}…")
        self._runner.submit("Connecting", work, on_success=self._on_connected, on_error=self._on_connect_failed)

    def disconnect(self) -> None:
        self._runner.submit("Disconnecting", self._transport.close, on_success=lambda _: self._on_disconnected())

    def _on_connected(self, info: DeviceInfo) -> None:
        self._set_connected(True)
        self._show_info(info)
        self.log(f"Connected to {info.bluetooth_name} ({info.address}).")

    def _on_connect_failed(self, error: BaseException) -> None:
        self._set_connected(False)
        self._status_var.set("Disconnected")
        self.log(f"Connection failed: {error}")

    def _on_disconnected(self) -> None:
        self._set_connected(False)
        for var in self._info_vars.values():
            var.set("—")
        self.log("Disconnected.")

    def _set_connected(self, connected: bool) -> None:
        self._connect_button.configure(text="Disconnect" if connected else "Connect")
        state = "normal" if connected else "disabled"
        for button in self._action_buttons:
            button.configure(state=state)
        self._status_var.set("Connected" if connected else "Disconnected")

    # --- actions -----------------------------------------------------------

    def read_device_info(self) -> None:
        name = self._info_vars["name"].get()
        address = self._info_vars["address"].get()
        self._run("Reading device info", lambda: self._band.inspect(name, address), self._show_info)

    def read_battery(self) -> None:
        self._run("Reading battery", self._band.battery_percent, self._show_battery)

    def sync_time(self) -> None:
        def work() -> datetime:
            self._band.set_utc_time(datetime.now(timezone.utc))
            return self._band.get_utc_time()

        self._run("Syncing time", work, self._show_time)

    def read_metrics(self) -> None:
        self._run("Reading daily metrics", self._band.get_daily_metrics, self._show_metrics)

    def send_notification(self) -> None:
        title, body = self._title_var.get(), self._body_var.get()
        self._run(
            "Sending notification",
            lambda: self._band.show_notification(title, body),
            lambda _: self.log(f"Notification sent: {title!r}"),
        )

    def _run(self, label, work, on_success) -> None:
        self._runner.submit(label, work, on_success=on_success, on_error=lambda error: self.log(f"{label} failed: {error}"))

    # --- rendering ---------------------------------------------------------

    def _show_info(self, info: DeviceInfo) -> None:
        self._info_vars["name"].set(info.bluetooth_name)
        self._info_vars["address"].set(info.address)
        self._info_vars["firmware"].set(info.firmware_version)
        self._info_vars["application"].set(info.firmware_application)
        self._info_vars["pcb"].set(str(info.pcb_id))
        self._info_vars["oobe"].set("complete" if info.oobe_complete else "not finished")
        self._show_time(info.band_time)
        for component in info.components:
            self.log(f"  firmware {component.name or '?'}: {component.version} (PCB {component.pcb_id})")

    def _show_time(self, moment: datetime) -> None:
        self._info_vars["time"].set(moment.strftime("%Y-%m-%d %H:%M:%S"))
        drift = (datetime.now(timezone.utc) - moment).total_seconds()
        self.log(f"Band clock: {moment:%Y-%m-%d %H:%M:%S} UTC (drift {drift:+.0f}s)")

    def _show_battery(self, percent: int) -> None:
        self._info_vars["battery"].set(f"{percent}%")
        self.log(f"Battery: {percent}%")

    def _show_metrics(self, metrics: DailyMetrics) -> None:
        if not metrics.has_data:
            self.log("No health data available. Wear the Band and try again.")
            return
        scope = "since last reset" if metrics.cumulative_since_reset else "today"
        parts = []
        if metrics.steps is not None:
            parts.append(f"{metrics.steps} steps")
        if metrics.calories is not None:
            parts.append(f"{metrics.calories} kcal")
        if metrics.distance_cm is not None:
            parts.append(f"{metrics.distance_cm / 100_000:.2f} km")
        if metrics.flights_ascended is not None:
            parts.append(f"{metrics.flights_ascended} floors")
        if metrics.uv_exposure is not None:
            parts.append(f"UV {metrics.uv_exposure}")
        self.log(f"Metrics ({scope}): " + ", ".join(parts))

    # --- plumbing ----------------------------------------------------------

    def _on_busy_changed(self, label: Optional[str]) -> None:
        if label:
            self._status_var.set(f"{label}…")
        elif self._transport.is_connected:
            self._status_var.set("Connected")
        else:
            self._status_var.set("Disconnected")

    def _observe_packet(self, direction: str, data: bytes, note: Optional[str]) -> None:
        # Called from the worker thread; hop back onto the Tk loop before logging.
        if direction != "INFO" and not self._log_packets_var.get():
            return
        text = f"{direction} {codec.hexdump(data)}" if data else f"{direction} {note or ''}"
        if data and note:
            text += f"  {note}"
        self._runner.post(lambda: self.log(text.strip()))

    def log(self, message: str) -> None:
        self._log.configure(state="normal")
        self._log.insert("end", f"{datetime.now():%H:%M:%S}  {message}\n")
        self._log.see("end")
        self._log.configure(state="disabled")

    def close(self) -> None:
        self._runner.shutdown()
        self._transport.close()


def main() -> None:
    root = tk.Tk()
    root.title("Microband")
    root.minsize(720, 560)
    app = BandApp(root)
    root.protocol("WM_DELETE_WINDOW", lambda: (app.close(), root.destroy()))
    root.mainloop()
