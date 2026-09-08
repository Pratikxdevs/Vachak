"""Tests for the correction sync queue + backend gating (PHASE 10).

Run offline, no network:

    python backend/sync/tests/test_sync.py
"""
from __future__ import annotations

import os
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(__file__))))
sys.path.insert(0, ROOT)

from backend.sync import (  # noqa: E402
    CorrectionStatus,
    HumanReviewer,
    SyncBackend,
    SyncQueue,
    TeacherCorrection,
    TrainingGate,
)


def _tmp_queue():
    fd, p = tempfile.mkstemp(suffix=".jsonl")
    os.close(fd)
    os.remove(p)
    return SyncQueue(p)


class TestQueue(unittest.TestCase):
    def test_enqueue_persist_roundtrip(self):
        q = _tmp_queue()
        c = TeacherCorrection(source="छः हाथी", target="x", teacher_correction="y", grade=1)
        q.enqueue(c)
        self.assertEqual(q.count(), 1)
        self.assertEqual(q.pending()[0].id, c.id)
        # reopen from same path -> durable
        q2 = SyncQueue(q.path)
        self.assertEqual(q2.count(), 1)
        self.assertEqual(q2.all()[0].teacher_correction, "y")

    def test_status_transitions(self):
        q = _tmp_queue()
        c = q.enqueue(TeacherCorrection(source="a", target="b", teacher_correction="c"))
        q.mark_status([c.id], CorrectionStatus.VERIFIED)
        self.assertEqual(q.all()[0].status, CorrectionStatus.VERIFIED)
        self.assertEqual(q.by_status(CorrectionStatus.VERIFIED)[0].id, c.id)


class TestBackendOfflineGuard(unittest.TestCase):
    def test_offline_blocks_upload(self):
        q = _tmp_queue()
        q.enqueue(TeacherCorrection(source="a", target="b", teacher_correction="c"))
        backend = SyncBackend(internet_available=False)
        res = backend.sync_when_online(q)
        self.assertTrue(res.blocked_offline)
        self.assertEqual(res.uploaded, 0)
        self.assertEqual(q.pending()[0].status, CorrectionStatus.QUEUED)  # untouched

    def test_internet_returns_uploads_to_review(self):
        q = _tmp_queue()
        c = q.enqueue(TeacherCorrection(source="a", target="b", teacher_correction="c"))
        backend = SyncBackend(internet_available=True)
        res = backend.sync_when_online(q)
        self.assertEqual(res.uploaded, 1)
        self.assertEqual(q.all()[0].status, CorrectionStatus.PENDING_HUMAN_REVIEW)
        # not yet verified
        self.assertEqual(backend.export_verified_corpus(q), [])


class TestHumanReviewAndTrainingGate(unittest.TestCase):
    def test_verify_then_export(self):
        q = _tmp_queue()
        c = q.enqueue(TeacherCorrection(source="a", target="b", teacher_correction="c"))
        backend = SyncBackend(internet_available=True)
        backend.sync_when_online(q)
        backend.approve(q, c.id)
        self.assertEqual(q.all()[0].status, CorrectionStatus.VERIFIED)
        corpus = backend.export_verified_corpus(q)
        self.assertEqual(len(corpus), 1)
        self.assertEqual(corpus[0].teacher_correction, "c")

    def test_unverified_never_in_corpus(self):
        q = _tmp_queue()
        c = q.enqueue(TeacherCorrection(source="a", target="b", teacher_correction="c"))
        backend = SyncBackend(internet_available=True)
        backend.sync_when_online(q)  # pending review, not approved
        self.assertEqual(backend.export_verified_corpus(q), [])

    def test_training_gate_blocks_unverified(self):
        q = _tmp_queue()
        c = q.enqueue(TeacherCorrection(source="a", target="b", teacher_correction="c"))
        backend = SyncBackend(internet_available=True)
        backend.sync_when_online(q)
        gate = TrainingGate(human_approved=True)
        ok, reason = gate.can_release_corpus(q)
        self.assertFalse(ok)
        self.assertIn("not human-verified", reason)

    def test_training_gate_blocks_without_human_approval(self):
        q = _tmp_queue()
        c = q.enqueue(TeacherCorrection(source="a", target="b", teacher_correction="c"))
        backend = SyncBackend(internet_available=True)
        backend.sync_when_online(q)
        backend.approve(q, c.id)
        gate = TrainingGate(human_approved=False)  # no explicit approval
        ok, reason = gate.can_release_corpus(q)
        self.assertFalse(ok)
        self.assertIn("not explicitly approved", reason)

    def test_manifest_does_not_train(self):
        q = _tmp_queue()
        c = q.enqueue(TeacherCorrection(source="a", target="b", teacher_correction="c"))
        backend = SyncBackend(internet_available=True)
        backend.sync_when_online(q)
        backend.approve(q, c.id)
        gate = TrainingGate(human_approved=True)
        manifest = gate.request_training_manifest(q)
        self.assertIn("no model trained", manifest["note"])
        self.assertFalse(manifest["eligible"])  # AUTO_TRAIN off: still not trained


if __name__ == "__main__":
    unittest.main(verbosity=2)
