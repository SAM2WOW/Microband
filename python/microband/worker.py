"""A serial background worker whose callbacks land on the Tk main thread.

Bluetooth calls block, and Tk widgets may only be touched from the thread that
created them. Everything the GUI asks of the Band goes through `TaskRunner`:
jobs run one at a time on a worker thread (which also keeps the single socket
free of interleaved transactions), and each result is handed back via the Tk
event loop.
"""

from __future__ import annotations

import queue
import threading
from dataclasses import dataclass
from typing import Any, Callable, Optional

OnSuccess = Callable[[Any], None]
OnError = Callable[[BaseException], None]


@dataclass
class _Job:
    label: str
    work: Callable[[], Any]
    on_success: Optional[OnSuccess]
    on_error: Optional[OnError]


class TaskRunner:
    def __init__(self, widget: Any, poll_ms: int = 40) -> None:
        self._widget = widget
        self._poll_ms = poll_ms
        self._jobs: "queue.Queue[Optional[_Job]]" = queue.Queue()
        self._results: "queue.Queue[Callable[[], None]]" = queue.Queue()
        self._busy_label: Optional[str] = None
        self.on_busy_changed: Callable[[Optional[str]], None] = lambda label: None
        self._thread = threading.Thread(target=self._run, name="band-worker", daemon=True)
        self._thread.start()
        self._widget.after(self._poll_ms, self._drain)

    @property
    def busy_label(self) -> Optional[str]:
        return self._busy_label

    def submit(
        self,
        label: str,
        work: Callable[[], Any],
        on_success: Optional[OnSuccess] = None,
        on_error: Optional[OnError] = None,
    ) -> None:
        self._jobs.put(_Job(label, work, on_success, on_error))

    def post(self, callback: Callable[[], None]) -> None:
        """Schedule `callback` on the Tk thread from anywhere."""
        self._results.put(callback)

    def shutdown(self) -> None:
        self._jobs.put(None)

    # --- worker thread -----------------------------------------------------

    def _run(self) -> None:
        while True:
            job = self._jobs.get()
            if job is None:
                return
            self._results.put(lambda label=job.label: self._set_busy(label))
            try:
                value = job.work()
            except BaseException as error:  # reported to the UI, never swallowed
                self._results.put(lambda error=error, job=job: self._fail(job, error))
            else:
                self._results.put(lambda value=value, job=job: self._succeed(job, value))
            finally:
                self._results.put(lambda: self._set_busy(None))

    # --- Tk thread ---------------------------------------------------------

    def _drain(self) -> None:
        while True:
            try:
                callback = self._results.get_nowait()
            except queue.Empty:
                break
            callback()
        self._widget.after(self._poll_ms, self._drain)

    def _set_busy(self, label: Optional[str]) -> None:
        self._busy_label = label
        self.on_busy_changed(label)

    @staticmethod
    def _succeed(job: _Job, value: Any) -> None:
        if job.on_success:
            job.on_success(value)

    @staticmethod
    def _fail(job: _Job, error: BaseException) -> None:
        if job.on_error:
            job.on_error(error)
