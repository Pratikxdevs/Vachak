import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from backend.api.translation_service import (
    TranslationService, MockTranslationBackend, BaselineIndicTrans2Backend,
    FinetunedSantaliBackend, FinalMundariTranslationBackend,
    TranslationRequest,
)
from backend.api.terminology_validator import TerminologyValidator


class TestBackendSwap(unittest.TestCase):
    """Adapter swap must work with no UI change; selection is by name/env only."""

    def test_mock_backend_runs(self):
        svc = TranslationService.from_name("mock")
        resp = svc.translate(TranslationRequest(text="hello"))
        self.assertTrue(resp.is_fixture)
        self.assertEqual(resp.backend, "mock")
        self.assertEqual(resp.target_text, "[mun] hello")

    def test_from_name_unknown_raises(self):
        with self.assertRaises(ValueError):
            TranslationService.from_name("does-not-exist")

    def test_baseline_is_labeled_not_mundari(self):
        # Construct directly; model dir absent -> clear error, not silent.
        be = BaselineIndicTrans2Backend(model_dir="/nonexistent")
        with self.assertRaises(RuntimeError):
            be.translate(TranslationRequest(text="x"))
    def test_final_is_stub(self):
        be = FinalMundariTranslationBackend()
        with self.assertRaises(NotImplementedError):
            be.translate(TranslationRequest(text="x"))

    def test_satfinal_missing_artifact_raises_clearly(self):
        be = FinetunedSantaliBackend(model_dir="/nonexistent",
                                     tokenizer_dir="/nonexistent")
        with self.assertRaises(RuntimeError):
            be.translate(TranslationRequest(text="x", source="hin",
                                            target="sat"))

    def test_satfinal_routes_by_name(self):
        svc = TranslationService.from_name(
            "satfinal", model_dir="/nonexistent")
        self.assertEqual(svc._backend.name, "satfinal-hin-sat-bidi")

    def test_env_swap(self):
        os.environ["TRANSLATION_BACKEND"] = "mock"
        svc = TranslationService.from_name(None)  # falls back to env
        self.assertEqual(svc._backend.name, "mock")


class TestTerminologyValidator(unittest.TestCase):
    def setUp(self):
        csv_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "terminology.csv")
        self.val = TerminologyValidator(csv_path)

    def test_approved_term_recognized(self):
        rep = self.val.validate("बारहखड़ी पढ़ें", {"grade": 1, "subject": "hindi"})
        self.assertIn("बारहखड़ी", rep.approved_terms_used)

    def test_alternative_suggests_canonical(self):
        rep = self.val.validate("बरखड़ी", {})
        warns = [w for w in rep.warnings if w.kind == "alternative"]
        self.assertTrue(warns)
        self.assertEqual(warns[0].suggestion, "बारहखड़ी")

    def test_unapproved_term_warns(self):
        rep = self.val.validate("slangword", {})
        self.assertTrue(any(w.kind == "unapproved" for w in rep.warnings))

    def test_missing_key_term_warns(self):
        # grade 1 / hindi expects बारहखड़ी, सेब, किताब, लड़का
        rep = self.val.validate("कुछ और", {"grade": 1, "subject": "hindi"})
        self.assertTrue(any(w.kind == "missing_key_term" for w in rep.warnings))

    def test_deterministic(self):
        a = self.val.validate("बरखड़ी", {})
        b = self.val.validate("बरखड़ी", {})
        self.assertEqual([w.token for w in a.warnings], [w.token for w in b.warnings])


if __name__ == "__main__":
    unittest.main()
