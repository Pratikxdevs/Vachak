"""Correction sync backend + human-review gate (PHASE 10).

CRITICAL SAFETY PROPERTY
------------------------
Teacher corrections are NEVER used to auto-train a model. The path is strictly:

    device queue  --(internet returns)-->  backend  --human review-->  verified corpus
                                                                          |
                                                                          +--> (explicit, separate
                                                                               training pipeline only)

The app is offline-first. `SyncBackend` will refuse to upload while offline and
will refuse to export any unverified correction into a training corpus. A
`TrainingGate` independently blocks training unless EVERY pushed correction is
human-verified AND an explicit human approval flag is set.

This module is intentionally side-effect free: it serializes state, it does not
open sockets. Network transport is the responsibility of the offline package
installer / side-load flow (see EngineContracts.SyncManager).
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable, Optional

from .correction import CorrectionStatus, TeacherCorrection
from .queue import SyncQueue


class OfflineError(RuntimeError):
    """Raised when a sync is attempted while the device is offline."""


class TrainBlocked(RuntimeError):
    """Raised when training is requested on unverified / unapproved data."""


@dataclass
class SyncResult:
    uploaded: int
    blocked_offline: bool = False
    note: str = ""


class SyncBackend:
    """Drain the local queue to the backend when connectivity returns.

    `internet_available` is injected so tests can simulate 'internet returns'.
    In production this is wired to the offline package installer, never a live
    HTTP client (AGENTS.md: no runtime network calls).
    """

    def __init__(self, internet_available: bool = False,
                 reviewer: Optional["HumanReviewer"] = None) -> None:
        self._internet = internet_available
        self.reviewer = reviewer or HumanReviewer()

    def set_internet(self, available: bool) -> None:
        self._internet = available

    def internet_available(self) -> bool:
        return self._internet

    def sync_when_online(self, queue: SyncQueue) -> SyncResult:
        """Upload queued corrections only if connectivity has returned."""
        if not self._internet:
            return SyncResult(uploaded=0, blocked_offline=True,
                              note="offline: corrections stay on device")
        pending = queue.pending()
        if not pending:
            return SyncResult(uploaded=0, note="queue empty")
        # Mark uploaded, then into human review.
        ids = [c.id for c in pending]
        queue.mark_status(ids, CorrectionStatus.UPLOADED)
        queue.mark_status(ids, CorrectionStatus.PENDING_HUMAN_REVIEW)
        return SyncResult(uploaded=len(pending), note="queued for human review")

    # -- human review surface (called by the review console, not the app) --
    def approve(self, queue: SyncQueue, correction_id: str) -> TeacherCorrection:
        c = self._get(queue, correction_id)
        queue.mark_status([correction_id], CorrectionStatus.VERIFIED)
        return self._get(queue, correction_id)

    def reject(self, queue: SyncQueue, correction_id: str) -> TeacherCorrection:
        queue.mark_status([correction_id], CorrectionStatus.REJECTED)
        return self._get(queue, correction_id)

    def export_verified_corpus(self, queue: SyncQueue) -> list[TeacherCorrection]:
        """Training corpus = VERIFIED ONLY. Unverified never leaves here."""
        return queue.by_status(CorrectionStatus.VERIFIED)

    def _get(self, queue: SyncQueue, cid: str) -> TeacherCorrection:
        for c in queue:
            if c.id == cid:
                return c
        raise KeyError(cid)


class HumanReviewer:
    """Placeholder for the external human-in-the-loop reviewer.

    In production this is a dashboard; here it simply records approvals so the
    export path is explicit and auditable.
    """

    def decide(self, correction: TeacherCorrection) -> bool:
        # Default: require an explicit, non-empty teacher correction.
        return bool(correction.teacher_correction.strip())


class TrainingGate:
    """Guards against auto-training on unverified teacher corrections.

    Two independent conditions must hold before any training corpus is released:
      1. Every correction in the queue that is being used is VERIFIED.
      2. A human operator has explicitly approved training (flag set out of band).
    """

    AUTO_TRAIN_ENABLED = False  # HARD OFF. Do not flip in the app. Training is a
                                # deliberate, separate pipeline step.

    def __init__(self, human_approved: bool = False) -> None:
        self.human_approved = human_approved

    def can_release_corpus(self, queue: SyncQueue) -> tuple[bool, str]:
        unverified = [c for c in queue
                      if c.status not in (CorrectionStatus.VERIFIED,)]
        if unverified:
            return False, f"{len(unverified)} correction(s) not human-verified"
        if not self.human_approved:
            return False, "training not explicitly approved by a human operator"
        if not self.AUTO_TRAIN_ENABLED:
            # Even with approval, auto-train is disabled; training must be run
            # by the explicit pipeline, never implicitly by the app.
            return False, "AUTO_TRAIN disabled; use explicit training pipeline"
        return True, "ok"

    def request_training_manifest(self, queue: SyncQueue) -> dict:
        """Build a manifest describing what COULD be trained. Does NOT train."""
        ok, reason = self.can_release_corpus(queue)
        verified = queue.by_status(CorrectionStatus.VERIFIED)
        return {
            "eligible": ok,
            "reason": reason,
            "verified_count": len(verified),
            "auto_train_enabled": self.AUTO_TRAIN_ENABLED,
            "note": "manifest only; no model trained. Run ml/translation "
                    "finetune_simple.py on the verified corpus explicitly.",
        }


__all__ = [
    "SyncBackend",
    "SyncResult",
    "OfflineError",
    "TrainBlocked",
    "HumanReviewer",
    "TrainingGate",
]
