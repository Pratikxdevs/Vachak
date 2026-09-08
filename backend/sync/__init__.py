"""Vachak teacher-correction sync (PHASE 10).

Public surface:
  * correction — TeacherCorrection data model + CorrectionStatus
  * queue      — durable on-device JSONL queue (no network)
  * backend    — SyncBackend (upload only when internet returns) + HumanReviewer
                 + TrainingGate (NEVER auto-train on unverified corrections)

See README.md in this folder. This module is offline-first: it performs no
network I/O itself; transport is delegated to the offline package installer.
"""
from .backend import (
    HumanReviewer,
    OfflineError,
    SyncBackend,
    SyncResult,
    TrainBlocked,
    TrainingGate,
)
from .correction import CorrectionStatus, TeacherCorrection
from .queue import SyncQueue

__all__ = [
    "TeacherCorrection",
    "CorrectionStatus",
    "SyncQueue",
    "SyncBackend",
    "SyncResult",
    "OfflineError",
    "TrainBlocked",
    "HumanReviewer",
    "TrainingGate",
]
