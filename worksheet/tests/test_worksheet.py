"""Deterministic unit tests for Phase 4A worksheet engine + 4C localization."""
import json
import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "worksheet"))

from localization.layer import localize, normalize_language  # noqa: E402
from worksheet.engine import WorksheetEngine, WorksheetRequest, to_markdown, render_pdf  # noqa: E402


class TestLocalization(unittest.TestCase):
    def test_modes(self):
        t = {"hi": "सेब", "target": "ᱥᱟᱯ", "en": "apple"}
        self.assertEqual(localize(t, "hi"), "सेब")
        self.assertEqual(localize(t, "target"), "ᱥᱟᱯ")
        self.assertIn("सेब", localize(t, "bilingual"))
        self.assertIn("ᱥᱟᱯ", localize(t, "bilingual"))

    def test_normalize_alias(self):
        self.assertEqual(normalize_language("mundari"), "target")
        self.assertEqual(normalize_language("sat"), "target")
        self.assertEqual(normalize_language("both"), "bilingual")
        self.assertEqual(normalize_language("garbage"), "bilingual")


class TestWorksheetEngine(unittest.TestCase):
    def setUp(self):
        self.eng = WorksheetEngine()

    def test_generate_bilingual(self):
        art = self.eng.generate(WorksheetRequest("G2", "SUBJ-MATH-G2", "G2-M-CNT-01", "easy", "bilingual", 8))
        self.assertIn("worksheet_id", art)
        self.assertGreaterEqual(len(art["questions"]), 1)
        self.assertEqual(len(art["answer_key"]), len(art["questions"]))

    def test_question_types_present(self):
        art = self.eng.generate(WorksheetRequest("G2", "SUBJ-MATH-G2", "G2-M-CNT-01", "easy", "bilingual", 8))
        types = {q["type"] for q in art["questions"]}
        self.assertIn("counting", types)
        self.assertIn("multiple_choice", types)
        self.assertIn("matching", types)
        self.assertIn("image_based", types)

    def test_counting_answer_matches_render(self):
        art = self.eng.generate(WorksheetRequest("G2", "SUBJ-MATH-G2", "G2-M-CNT-01", "easy", "hi", 8))
        for q in art["questions"]:
            if q["type"] == "counting":
                n = len([x for x in q["render"].split(" ") if x])
                self.assertEqual(n, q["answer"])

    def test_mcq_correct_answer_in_options(self):
        art = self.eng.generate(WorksheetRequest("G2", "SUBJ-MATH-G2", "G2-M-CNT-01", "easy", "target", 8))
        for q in art["questions"]:
            if q["type"] == "multiple_choice":
                self.assertIn(q["answer"], q["options"])
                self.assertEqual(len(q["options"]), 4)  # 1 correct + 3 distractors

    def test_language_modes(self):
        for lang in ("hi", "target", "bilingual"):
            art = self.eng.generate(WorksheetRequest("G2", "SUBJ-MATH-G2", "G2-M-CNT-01", "easy", lang, 8))
            self.assertEqual(art["meta"]["language"], lang)

    def test_determinism(self):
        a = json.dumps(self.eng.generate(WorksheetRequest("G2", "SUBJ-MATH-G2", "G2-M-CNT-01", "medium", "bilingual", 8)),
                       sort_keys=True, ensure_ascii=False)
        b = json.dumps(self.eng.generate(WorksheetRequest("G2", "SUBJ-MATH-G2", "G2-M-CNT-01", "medium", "bilingual", 8)),
                       sort_keys=True, ensure_ascii=False)
        self.assertEqual(a, b)

    def test_markdown_and_pdf(self):
        art = self.eng.generate(WorksheetRequest("G2", "SUBJ-MATH-G2", "G2-M-CNT-01", "easy", "bilingual", 8))
        md = to_markdown(art)
        self.assertIn("Teacher Answer Key", md)
        out = os.path.join(ROOT, "sample", "sample_worksheet.pdf")
        render_pdf(art, out)
        self.assertTrue(os.path.getsize(out) > 0)
        # PDF header/trailer sanity
        with open(out, "rb") as f:
            data = f.read()
        self.assertTrue(data.startswith(b"%PDF"))
        self.assertTrue(data.rstrip().endswith(b"%%EOF"))


if __name__ == "__main__":
    unittest.main()
