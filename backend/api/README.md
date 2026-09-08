# backend/api — Translation Service (Phase 6)

`POST /translate` exposing swappable translation backends + deterministic terminology
validation. No UI change required to switch backend.

## Backends (swap via env `TRANSLATION_BACKEND`)
| Name | Class | Status |
|------|-------|--------|
| `mock` | `MockTranslationBackend` | DEV FIXTURE — deterministic, no model. Default. |
| `baseline` | `BaselineIndicTrans2Backend` | Hindi→Santali via IndicTrans2 CT2. **Clearly labeled NOT Mundari.** Stand-in. |
| `final` | `FinalMundariTranslationBackend` | STUB — Mundari model not trained yet. |

`INDICTRANS2_MODEL_DIR` selects the baseline model dir. `TRANSLATION_BACKEND`
controls selection with zero UI change.

## Run
```bash
# stdlib server (no deps):
cd backend/api && python3 -c "import sys; sys.path.insert(0,'.'); from backend.api import app; app.serve_stdlib()"
# or with FastAPI if installed:
uvicorn backend.api.app:create_fastapi_app --reload

# REAL baseline (needs ctranslate2/transformers/sentencepiece/IndicTransToolkit):
TRANSLATION_BACKEND=baseline \
INDICTRANS2_MODEL_DIR=/home/clutch/Desktop/Vachak/models/indictrans2_ct2_int8 \
INDICTRANS2_TOKENIZER_DIR=/home/clutch/Desktop/Vachak/models/indictrans2_bart \
python3 -m backend.api.app   # or your server entrypoint
```
The baseline engine lives at `ml/translation/mundari/it2_ct2_baseline.py`. Heavy deps are
imported lazily on first `translate()`, so the `mock` backend still runs with zero model deps.

## Request
```json
POST /translate
{ "text": "आज हम बारहखड़ी सीखेंगे।",
  "source": "hin", "target": "sat",
  "context": { "grade": 1, "subject": "hindi", "learningOutcome": "alphabet" } }
```
`source` accepts `hin`/`hi`; `target` accepts `sat`/`sat_Olck` (real Santali). `mun` is
accepted but the baseline emits Santali and labels it NOT Mundari in `note`.
## Response
```json
{ "sourceText": "...", "targetText": "...", "confidence": 0.0,
  "terminologyWarnings": [ { "token": "...", "kind": "alternative", "suggestion": "...", "message": "..." } ],
  "backend": "mock", "isFixture": true, "note": "..." }
```
See `API_CONTRACT.md` for the full schema.

## Terminology validator (6B)
Deterministic, **no LLM**. Loads `terminology.csv` (approved glossary). Flags:
`alternative` (non-standard spelling), `unapproved` (banned term), `missing_key_term`
(context-expected curriculum term absent). The bundled CSV is a **DEV FIXTURE** (Hindi
stand-in); replace with the human-approved Mundari glossary before classroom use.

## Tests
```bash
python3 -m unittest discover -s backend/api/tests -p "test_*.py"
```

## Known limitations
- `mock` is not real translation; never ship as pedagogy.
- `baseline` produces Santali (**sat_Olck**), **not Mundari**; labeled in `note`/`isFixture`.
  It reuses the cloned IndicTrans2 tokenizer + ctranslate2 over the exported INT8 checkpoint
  (ai4bharat/indictrans2-indic-indic-dist-320M). Some outputs may be low quality / off-script;
  it is a pipeline-integration stand-in only.
- `final` is a stub raising `NotImplementedError`.
- Vocabulary CSV is a placeholder; terminology correctness depends on the real glossary.

## License / provenance
- IndicTrans2: MIT (AI4Bharat). Cloned repo `/home/clutch/Desktop/Vachak/IndicTrans2` (LICENSE preserved).
- IndicTransTokenizer (`tokenization_indictrans.py`) + `IndicTransToolkit`: MIT (AI4Bharat), reused, not modified.
- Baseline weights: `models/indictrans2_ct2_int8` — CTranslate2 INT8 export (via `ctranslate2`
  `TransformersConverter`) of `ai4bharat/indictrans2-indic-indic-dist-320M` (MIT). This is
  **Hindi→Santali, NOT Mundari**.
- No LICENSE/NOTICE files stripped or modified. Provenance also recorded in
  `THIRD_PARTY_NOTICES.md` (do not edit that file directly; report additions to maintainers).
