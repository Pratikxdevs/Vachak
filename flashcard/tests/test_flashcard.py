"""Deterministic unit tests for Phase 4B flashcard engine."""
import json
import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "flashcard"))

from flashcard.engine import FlashcardEngine, FlashcardSpec  # noqa: E402


class TestFlashcardEngine(unittest.TestCase):
    def setUp(self):
        self.eng = FlashcardEngine()

    def test_build_from_lesson(self):
        deck = self.eng.build_from_lesson("L-COUNT-G2", "bilingual")
        self.assertEqual(deck["meta"]["card_count"], 10)
        self.assertEqual(len(deck["cards"]), 10)
        for c in deck["cards"]:
            self.assertIn("front", c)
            self.assertIn("back", c)
            self.assertIn("image_ref", c)
            self.assertIn("audio_ref", c)

    def test_language_modes(self):
        deck = self.eng.build_from_lesson("L-COUNT-G2", "hi")
        self.assertEqual(deck["meta"]["language"], "hi")
        c = deck["cards"][0]
        # In 'hi' mode the front is the Hindi numeral
        self.assertEqual(c["front"], "१")

    def test_bilingual_front_back(self):
        deck = self.eng.build_from_lesson("L-COUNT-G2", "bilingual")
        c = deck["cards"][0]
        self.assertIn("१", c["front"])
        self.assertIn("᱑", c["front"])
        # back is reverse pairing
        self.assertIn("᱑", c["back"])
        self.assertIn("१", c["back"])

    def test_single_spec(self):
        spec = FlashcardSpec(concept="apple", image_ref="a.png",
                             hindi_label="सेब", target_label="ᱥᱟᱯ",
                             audio_ref="a.wav", english_label="apple",
                             card_id="X1", sequence=1)
        card = self.eng.build_card(spec, "target")
        self.assertEqual(card["front"], "ᱥᱟᱯ")
        self.assertEqual(card["back"], "सेब")

    def test_determinism(self):
        a = json.dumps(self.eng.build_from_lesson("L-COUNT-G2", "target"), sort_keys=True, ensure_ascii=False)
        b = json.dumps(self.eng.build_from_lesson("L-COUNT-G2", "target"), sort_keys=True, ensure_ascii=False)
        self.assertEqual(a, b)


if __name__ == "__main__":
    unittest.main()
