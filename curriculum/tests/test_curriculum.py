"""Deterministic unit tests for Phase 3A/3B/3C curriculum knowledge base."""
import json
import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "curriculum"))

from curriculum.data import load  # noqa: E402
from curriculum.lesson_map import build_lesson_package  # noqa: E402


class TestCurriculumData(unittest.TestCase):
    def setUp(self):
        self.db = load()

    def test_validation_passes(self):
        self.assertEqual(self.db.validate(), [])

    def test_navigation_grade2_math_counting(self):
        grades = self.db.grades()
        self.assertTrue(any(g["grade_id"] == "G2" for g in grades))
        subs = self.db.subjects("G2")
        self.assertTrue(any(s["subject_id"] == "SUBJ-MATH-G2" for s in subs))
        chaps = self.db.chapters("SUBJ-MATH-G2")
        self.assertTrue(any(c["chapter_id"] == "CH-COUNT-G2" for c in chaps))
        lessons = self.db.lessons("CH-COUNT-G2")
        self.assertTrue(any(l["lesson_id"] == "L-COUNT-G2" for l in lessons))


class TestLessonMap(unittest.TestCase):
    def setUp(self):
        self.db = load()

    def test_package_structure(self):
        pkg = build_lesson_package("L-COUNT-G2", self.db)
        self.assertEqual(pkg["package_id"], "PKG-L-COUNT-G2")
        self.assertEqual(len(pkg["outcomes"]), 5)
        self.assertEqual(len(pkg["flashcards"]), 10)
        self.assertEqual(len(pkg["vocabulary"]), 6)
        # every outcome has at least one activity and assessment mapping (3B)
        for o in pkg["outcomes"]:
            self.assertGreaterEqual(len(o["activities"]), 1)
            self.assertGreaterEqual(len(o["assessments"]), 1)

    def test_determinism(self):
        a = json.dumps(build_lesson_package("L-COUNT-G2", self.db), sort_keys=True, ensure_ascii=False)
        b = json.dumps(build_lesson_package("L-COUNT-G2", self.db), sort_keys=True, ensure_ascii=False)
        self.assertEqual(a, b)

    def test_activities_and_assessments_linked(self):
        pkg = build_lesson_package("L-COUNT-G2", self.db)
        all_acts = [a for o in pkg["outcomes"] for a in o["activities"]]
        all_ass = [a for o in pkg["outcomes"] for a in o["assessments"]]
        self.assertEqual(len(all_acts), 5)   # 1 authored per outcome
        self.assertEqual(len(all_ass), 5)

    def test_unknown_lesson_raises(self):
        with self.assertRaises(KeyError):
            build_lesson_package("DOES-NOT-EXIST", self.db)


if __name__ == "__main__":
    unittest.main()
