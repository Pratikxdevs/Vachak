"""Unit tests for the Mundari NMT scaffolding. Run without gated data / GPU.

    python -m unittest ml.translation.mundari.tests.test_pipeline

Uses the MockTranslationEngine for a deterministic round-trip and exercises the
quality-check / split / eval stages on a tiny synthetic fixture.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from ml.translation.mundari.adapter import MockTranslationEngine, get_adapter, TranslationAdapter
from ml.translation.mundari.pipeline import (
    MundariConfig, Corpus, quality_checks, make_splits, run_baseline, run_pipeline,
)
from ml.translation.mundari.evaluate import evaluate

FIXTURE = Path(__file__).resolve().parent.parent / "data" / "fixtures" / "dev_fixture.tsv"


def load_fixture() -> Corpus:
    pairs = []
    for line in FIXTURE.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        s, t = line.split("\t")
        pairs.append((s, t))
    return Corpus(pairs)


class TestMockRoundTrip(unittest.TestCase):
    def test_mock_is_not_final(self):
        eng = MockTranslationEngine()
        self.assertIsInstance(eng, TranslationAdapter)
        self.assertFalse(eng.is_final_model)
        self.assertIn("dev-fixture", eng.name)

    def test_mock_deterministic_and_reversible(self):
        eng = MockTranslationEngine()
        out = eng.translate("पाँच आम गिनो।")
        self.assertTrue(out.startswith(MockTranslationEngine.MARKER))
        # round-trip: strip marker recovers the input (proves pipeline plumbing)
        self.assertEqual(out.replace(MockTranslationEngine.MARKER, "").strip(), "पाँच आम गिनो।")

    def test_mock_rejects_null(self):
        with self.assertRaises(ValueError):
            MockTranslationEngine().translate(None)

    def test_factory_mock(self):
        self.assertIsInstance(get_adapter("mock"), MockTranslationEngine)


class TestPipelineStages(unittest.TestCase):
    def setUp(self):
        self.corpus = load_fixture()
        self.cfg = MundariConfig(adapter_kind="mock")

    def test_quality_checks_clean_fixture(self):
        qc = quality_checks(self.corpus)
        self.assertTrue(qc["ok"])
        self.assertEqual(qc["n"], len(self.corpus))

    def test_splits_sum_to_total_and_deterministic(self):
        s1 = make_splits(self.corpus, self.cfg)
        s2 = make_splits(self.corpus, self.cfg)
        self.assertEqual(sum(len(v) for v in s1.values()), len(self.corpus))
        self.assertEqual([len(v) for v in s1.values()], [len(v) for v in s2.values()])

    def test_baseline_returns_one_hyp_per_src(self):
        splits = make_splits(self.corpus, self.cfg)
        hyps = run_baseline(splits["test"], self.cfg)
        self.assertEqual(len(hyps), len(splits["test"]))

    def test_eval_automatic_metrics_computed(self):
        refs = [t for _, t in self.corpus.pairs]
        hyps = [f"{MockTranslationEngine.MARKER} {s}" for s, _ in self.corpus.pairs]
        rep = evaluate(refs, hyps)
        # mock hyps are not the fixture targets, so BLEU is a real (low) number, not TODO
        self.assertEqual(rep.bleu.status, "computed")
        # human/latency/memory remain TODO by design
        self.assertEqual(rep.human.status, "TODO")
        self.assertEqual(rep.latency_ms.status, "TODO")

    def test_full_pipeline_runs_without_data(self):
        result = run_pipeline(self.corpus, self.cfg)
        self.assertIn("eval", result)
        self.assertEqual(result["finetune"]["status"], "TODO")
        self.assertEqual(result["export_onnx"]["status"], "TODO")


if __name__ == "__main__":
    unittest.main(verbosity=2)
