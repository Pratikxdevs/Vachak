"""PHASE 1 proof (Python): a real adapter can replace the mock through the same
injection point without changing caller code — mirroring EngineSwapTest.kt.
"""
from __future__ import annotations

import unittest

from shared.schemas.engines import EngineError, Err, LanguagePair, Ok
from shared.schemas.mock_engines import MockTranslationEngine


class FakeRealTranslationEngine:
    """Stand-in for a future IndicTrans2/ONNX adapter. Same interface, real-ish output."""
    def supports(self, pair: LanguagePair) -> bool:
        return pair.source == "hi" and pair.target == "mund"
    def load_model(self, pack_id: str):
        return Ok(None)
    def translate(self, text: str, pair: LanguagePair):
        if not self.supports(pair):
            return Err(EngineError.UNSUPPORTED_LANGUAGE, f"pair {pair}")
        return Ok(f"[REAL-mund] {text}")


def call_ui(translation) -> str:
    """Simulates caller (UI/orchestrator) logic using ONLY the interface."""
    pair = LanguagePair("hi", "mund")
    res = translation.translate("पाठ", pair)
    if isinstance(res, Ok):
        return res.value
    if isinstance(res, Err):
        return f"err: {res.message}"
    raise TypeError("unexpected result type")


class EngineSwapTest(unittest.TestCase):
    def test_mock_and_real_satisfy_same_contract(self):
        out_mock = call_ui(MockTranslationEngine())
        out_real = call_ui(FakeRealTranslationEngine())
        self.assertTrue(out_mock.startswith("[DEV-FIXTURE-mund]"))
        self.assertTrue(out_real.startswith("[REAL-mund]"))
        # Caller logic is identical; only the implementation differs.
        self.assertEqual(MockTranslationEngine().supports(LanguagePair("hi", "mund")), True)
        self.assertEqual(FakeRealTranslationEngine().supports(LanguagePair("hi", "mund")), True)

    def test_unsupported_pair_rejected_uniformly(self):
        r = MockTranslationEngine().translate("x", LanguagePair("en", "fr"))
        self.assertIsInstance(r, Err)
        self.assertEqual(r.code, EngineError.UNSUPPORTED_LANGUAGE)


if __name__ == "__main__":
    unittest.main()
