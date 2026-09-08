"""Local correction sync queue (PHASE 10).

Stores teacher corrections on-device as a JSONL file. No network calls. The
queue is the single source of truth that the backend drains when connectivity
returns. Durable across app restarts (file-backed).
"""
from __future__ import annotations

import json
import threading
from pathlib import Path
from typing import Iterator, Optional

from .correction import CorrectionStatus, TeacherCorrection


class SyncQueue:
    def __init__(self, path: Optional[str] = None) -> None:
        self.path = Path(path) if path else Path("corrections.queue.jsonl")
        self._lock = threading.Lock()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            self.path.write_text("", encoding="utf-8")

    # -- writes -----------------------------------------------------------
    def enqueue(self, c: TeacherCorrection) -> TeacherCorrection:
        if c.status is CorrectionStatus.QUEUED:
            pass
        with self._lock:
            with self.path.open("a", encoding="utf-8") as f:
                f.write(json.dumps(c.to_dict(), ensure_ascii=False) + "\n")
        return c

    def mark_uploaded(self, ids: list[str]) -> None:
        self._rewrite(lambda c: c if c.id not in ids else _set(c, CorrectionStatus.UPLOADED))

    def mark_status(self, ids: list[str], status: CorrectionStatus) -> None:
        self._rewrite(lambda c: c if c.id not in ids else _set(c, status))

    # -- reads ------------------------------------------------------------
    def __iter__(self) -> Iterator[TeacherCorrection]:
        with self._lock:
            for line in self.path.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    yield TeacherCorrection.from_dict(json.loads(line))

    def pending(self) -> list[TeacherCorrection]:
        return [c for c in self if c.status is CorrectionStatus.QUEUED]

    def by_status(self, status: CorrectionStatus) -> list[TeacherCorrection]:
        return [c for c in self if c.status is status]

    def all(self) -> list[TeacherCorrection]:
        return list(self)

    def count(self) -> int:
        return sum(1 for _ in self)

    def clear(self) -> None:
        with self._lock:
            self.path.write_text("", encoding="utf-8")

    # -- internal ---------------------------------------------------------
    def _rewrite(self, fn) -> None:
        with self._lock:
            lines = []
            for line in self.path.read_text(encoding="utf-8").splitlines():
                if not line.strip():
                    continue
                c = fn(TeacherCorrection.from_dict(json.loads(line)))
                lines.append(json.dumps(c.to_dict(), ensure_ascii=False))
            self.path.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")


def _set(c: TeacherCorrection, status: CorrectionStatus) -> TeacherCorrection:
    c.status = status
    return c


__all__ = ["SyncQueue"]
