# Worksheet & Flashcard Engine — Phases 4A / 4B / 4C (SIH26042 / Vachak)

Deterministic, offline generators that turn curriculum data into printable,
multilingual classroom material. **No LLM, no network, no heavy dependencies**
(stdlib only; PDF is hand-rolled).

- **4A** Worksheet engine → JSON + Markdown + minimal PDF; types: multiple-choice,
  matching, counting, fill-in-blank, image-based, with a teacher answer key.
- **4B** Flashcard engine → deck JSON (concept, image ref, Hindi label, target
  label, audio ref).
- **4C** Localization layer → Hindi / target / bilingual for every artifact.

> Target language is configurable via `localization/languages.json`
> (`target_language_code`). Default **Santali / Ol Chiki**; the task brief names
> Mundari — set `"unr"` and re-seed to target Mundari.

## Modules

| Path | Phase | Purpose |
|------|-------|---------|
| `worksheet/engine.py` | 4A | `WorksheetEngine`, `WorksheetRequest`, `to_markdown`, `render_pdf` |
| `worksheet/pdf.py` | 4A | Dependency-free PDF writer |
| `flashcard/engine.py` | 4B | `FlashcardEngine`, `FlashcardSpec` |
| `localization/layer.py` | 4C | `localize()`, `normalize_language()`, `language_label()` |
| `localization/languages.json` | 4C | Language config (hi / target / bilingual) |

## Setup

Python 3.8+, standard library only.

```bash
# from repo root
python3 worksheet/engine.py        # prints a bilingual worksheet + writes /tmp/ws_sample.json
python3 flashcard/engine.py        # prints a flashcard deck + writes /tmp/deck_sample.json

# Run all deterministic tests
python3 -m unittest worksheet.tests.test_worksheet flashcard.tests.test_flashcard curriculum.tests.test_curriculum -v
```

## API / Interface Contract

### Worksheet (4A)
```python
from worksheet.engine import WorksheetEngine, WorksheetRequest
eng = WorksheetEngine()                       # loads curriculum seed
req = WorksheetRequest(
    grade="G2", subject="SUBJ-MATH-G2",
    learning_outcome="G2-M-CNT-01",           # outcome code OR outcome_id
    difficulty="easy",                        # easy|medium|hard
    language="bilingual",                     # hi|target|bilingual
    num_items=8)
artifact = eng.generate(req)                  # dict (JSON-serializable)
md       = eng.to_markdown(artifact)          # Markdown string
render_pdf(artifact, "out.pdf")              # minimal PDF
```
`artifact` shape:
```json
{
  "worksheet_id": "WS-G2-M-CNT-01-bilingual-easy",
  "meta": {"grade","subject","learning_outcome","difficulty","language","language_label","num_items","generator","fixture_note"},
  "questions": [
    {"id","type":"counting|multiple_choice|matching|fill_in_blank|image_based",
     "prompt","options"?,"render"?,"image_ref"?,"answer","pairs"?}, ...
  ],
  "answer_key": [ {"id","answer"}, ... ]
}
```
**Determinism:** identical `WorksheetRequest` ⇒ byte-identical `artifact`.

### Flashcard (4B)
```python
from flashcard.engine import FlashcardEngine, FlashcardSpec
fe = FlashcardEngine()
deck = fe.build_from_lesson("L-COUNT-G2", language="bilingual")   # full deck
card = fe.build_card(FlashcardSpec(concept="apple", image_ref="a.png",
            hindi_label="सेब", target_label="ᱥᱟᱯ", audio_ref="a.wav"), "target")
```
`deck` shape: `{"deck_id","meta":{"language","card_count",...},"cards":[{card_id,sequence,front,back,image_ref,audio_ref,raw}]}`.

### Localization (4C)
```python
from localization.layer import localize
localize({"hi":"सेब","target":"ᱥᱟᱯ","en":"apple"}, "bilingual")
# -> "सेब  —  ᱥᱟᱯ"
```

## Sample / Demo Data
- `worksheet/sample/ws_G2_Math_Counting_{bilingual,hi,target}.{json,md,pdf}`
- `flashcard/sample/deck_G2_Math_Counting_bilingual.json`

## Known Limitations
- Generated worksheets are **DEV FIXTURES**, not SME-verified pedagogy.
- The hand-rolled PDF uses the viewer's built-in fonts; **Devanagari / Ol Chiki
  glyphs may not render** without font embedding (embed a subset font in
  production — out of scope here, by design to stay dependency-free). Markdown
  and JSON render correctly everywhere.
- Image-based / flashcard questions reference **offline asset paths only**
  (`assets/...`); the binaries ship separately (prebuilt per AGENTS.md).
- Difficulty only affects the counting upper bound and (future) item pool size.
- Mundari targeting requires re-seeding `curriculum/seed` with Mundari content.
