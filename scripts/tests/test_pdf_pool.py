"""Wave-0 tests for select_pdf_pool (Phase 12, WS-01/WS-05)."""
from __future__ import annotations

import json
import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

MAP = os.path.join(ROOT, ".planning", "phases", "12-pdf-worksheets",
                   "pdf_slot_mapping.json")
POOL_DIR = os.path.join(ROOT, "curriculum", "class", "pdf_pool")


class PoolTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not os.path.isfile(MAP):
            raise unittest.SkipTest("run scripts/select_pdf_pool.py first")
        cls.doc = json.load(open(MAP, encoding="utf-8"))

    def test_pool_cap(self):
        total = sum(p["size"] for p in self.doc["pool"])
        self.assertLessEqual(total, 25 * 1024 * 1024, f"{total} bytes")

    def test_twenty_slots(self):
        slots = self.doc["slots"]
        # 19 pool slots: G2's fun-at-the-fair placeholder was swapped for the
        # authored fun-at-fair chapter (see swaps[] + authored_mapping_g2).
        self.assertEqual(len(slots), 19)
        per_grade = {}
        for s in slots:
            per_grade[s["grade"]] = per_grade.get(s["grade"], 0) + 1
        self.assertEqual(per_grade.get(2), 5)  # 6th G2 slot is authored fun-at-fair
        self.assertEqual(per_grade.get(4), 6)
        self.assertEqual(per_grade.get(5), 6)
        self.assertEqual(per_grade.get(1), 1)  # G1 6th only
        self.assertEqual(per_grade.get(3), 1)  # G3 6th only

    def test_refs_safe_and_resolve(self):
        import hashlib
        for s in self.doc["slots"]:
            ref = None
            for p in self.doc["pool"]:
                if p["basename"] == s["pool_file"]:
                    ref = p
            self.assertIsNotNone(ref, s)
            self.assertNotIn("..", s["pool_file"])
            self.assertFalse(os.path.isabs(s["pool_file"]))
            disk = os.path.join(POOL_DIR, s["pool_file"])
            self.assertTrue(os.path.isfile(disk), disk)
            h = hashlib.sha256()
            with open(disk, "rb") as f:
                for b in iter(lambda: f.read(1 << 20), b""):
                    h.update(b)
            self.assertEqual(h.hexdigest(), s["sha256"], disk)

    def test_pool_sorted_smallest_first(self):
        sizes = [p["size"] for p in self.doc["pool"]]
        self.assertEqual(sizes, sorted(sizes))


if __name__ == "__main__":
    unittest.main()
