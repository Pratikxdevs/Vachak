"""Real Hindi->Santali baseline test (IndicTrans2 CT2 INT8).

Guards the heavy import (ctranslate2/transformers/IndicTransToolkit) so that mock-only
test runs never need the model. When the artifact + deps are present, this asserts the
baseline produces a NON-EMPTY, REAL Santali output (never fabricated, never "[mun]").

Run with the export venv that has ctranslate2 installed:
    /home/clutch/Desktop/Vachak/ml/export_venv/bin/python -m pytest \
        ml/translation/mundari/tests/test_baseline_real.py -v
"""

import os
import sys
import unittest

_HERE = os.path.dirname(os.path.abspath(__file__))
# Make `mundari.*` importable regardless of cwd.
_ML_TRANSLATION = os.path.abspath(os.path.join(_HERE, "..", ".."))
if _ML_TRANSLATION not in sys.path:
    sys.path.insert(0, _ML_TRANSLATION)

_ENGINE = None
_SKIP = None
try:
    from mundari.it2_ct2_baseline import IndicTrans2CT2Baseline  # type: ignore

    _ENGINE = IndicTrans2CT2Baseline()
    _ENGINE.load()
except Exception as exc:  # pragma: no cover - env dependent
    _ENGINE = None
    _SKIP = str(exc)


@unittest.skipIf(_ENGINE is None, f"real baseline unavailable: {_SKIP}")
class TestBaselineReal(unittest.TestCase):
    def test_real_translation_nonempty(self):
        out = _ENGINE.translate(["बच्चों, पाँच आम गिनो।"], "hin", "sat")
        self.assertTrue(any(o.strip() for o in out), "translation must be non-empty real output")
        self.assertNotIn("[mun]", out[0])

    def test_known_numbers_pair(self):
        # A clearly-structured classroom sentence the dist-320M baseline can render.
        out = _ENGINE.translate(["एक दो तीन चार पाँच।"], "hin", "sat")
        self.assertTrue(out[0].strip())
        self.assertNotIn("[mun]", out[0])

    def test_src_target_normalization(self):
        # hi -> sat_Olck aliases must resolve and still decode.
        out = _ENGINE.translate(["नमस्ते।"], "hi", "sat_Olck")
        self.assertTrue(out[0].strip())


if __name__ == "__main__":
    unittest.main()
