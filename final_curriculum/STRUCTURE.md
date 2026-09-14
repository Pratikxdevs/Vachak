# STRUCTURE.md — Exact Schemas (copy-paste)

Conventions: `T(x)` below means bilingual pair `{"hi": "x-hindi", "sat_ol": "�x-ol-chiki"}`.
IDs are stable strings. `sequence` starts at 1. Omit keys not listed — readers ignore extras but validators warn.

## grades.json

```json
[
  {"grade": 1, "name": {"hi": "कक्षा 1", "sat_ol": "ᱠᱞᱟᱥ ᱑"}},
  {"grade": 2, "name": {"hi": "कक्षा 2", "sat_ol": "ᱠᱞᱟᱥ ᱒"}},
  {"grade": 3, "name": {"hi": "कक्षा 3", "sat_ol": "ᱠᱞᱟᱥ ᱓"}},
  {"grade": 4, "name": {"hi": "कक्षा 4", "sat_ol": "ᱠᱞᱟᱥ ᱔"}},
  {"grade": 5, "name": {"hi": "कक्षा 5", "sat_ol": "ᱠᱞᱟᱥ ᱕"}}
]
```

## subjects.json

`subject_id` = `SUBJ-{SHORT}-G{grade}`. SHORT = MATH, EVS, LANG, PE, ARTS, GEN.

```json
[
  {"subject_id": "SUBJ-MATH-G3", "grade": 3, "name": {"hi": "गणित", "sat_ol": "ᱮᱞᱮᱠᱷ"}},
  {"subject_id": "SUBJ-EVS-G3", "grade": 3, "name": {"hi": "पर्यावरण", "sat_ol": "ᱯᱟᱨᱤᱵᱮᱥ"}}
]
```

## chapters/{grade}/{slug}/chapter.json

`chapter_id` = `CH-G{grade}-{SLUG-UPPER}`. `slug` = lowercase, hyphenated, matches folder name.

```json
{
  "chapter_id": "CH-G3-FOOD-WE-EAT",
  "grade": 3,
  "subject_id": "SUBJ-EVS-G3",
  "slug": "food-we-eat",
  "source_pdf": "Class 3/Environmental Studies/assignments/Class_3__Food_We_Eat__Santhali_.pdf",
  "title": {"hi": "हम जो खाते हैं", "sat_ol": "ᱟᱞᱮᱞᱮ ᱡᱚᱢᱟ"},
  "intro": {"hi": "इस पाठ में ... (2-3 वाक्य)", "sat_ol": "ᱱᱚᱶᱟ ᱯᱟᱲᱦᱟᱣ ᱨᱮ ..."},
  "outcomes": [
    {"code": "G3-EVS-01", "sequence": 1, "text": {"hi": "छात्र ... सकेंगे।", "sat_ol": "ᱯᱟᱹᱴᱷᱣᱟᱹ ... ᱫᱟᱲᱮᱭᱟᱜ-ᱟ।"}}
  ],
  "teacher_note": {"hi": "पहले ... कराएं।", "sat_ol": "ᱢᱟᱲᱟᱝ ... ᱦᱚᱪᱚᱭ ᱢᱟ।"},
  "vocab": [
    {"hi": "भोजन", "sat_ol": "ᱡᱚᱢᱟᱜ"}
  ],
  "estimated_minutes": 40,
  "status": "DRAFT"
}
```

Field rules: `title/intro/teacher_note` both sides required for APPROVED. `outcomes` 3-5 items, `code` unique per chapter (`G{grade}-{SHORT}-{NN}`). `vocab` 5-10 items. `estimated_minutes` 30/40/45.

## worksheets.json

Template slots only — worksheet generator renders these, never invents questions. `type` closed set: `trace | fill_blank | match | counting`.

```json
{
  "worksheet_id": "WS-G3-food-we-eat",
  "chapter_id": "CH-G3-FOOD-WE-EAT",
  "status": "DRAFT",
  "items": [
    {
      "id": "Q1", "type": "fill_blank", "sequence": 1,
      "prompt": {"hi": "रिक्त स्थान भरें: 47 के बाद ____ आता है।", "sat_ol": "ᱫᱟᱹᱵᱤ ᱯᱩᱨᱟᱹᱣ: ᱔᱗ ᱛᱟᱭᱚᱢ ____ ᱦᱤᱡᱩᱜ-ᱟ।"},
      "answer": "48",
      "image_ref": null
    },
    {
      "id": "Q2", "type": "match", "sequence": 2,
      "prompt": {"hi": "मिलान करें।", "sat_ol": "ᱡᱩᱲᱟᱹᱣ ᱢᱮ।"},
      "pairs": [{"hi": "भोजन", "sat_ol": "ᱡᱚᱢᱟᱜ"}, {"hi": "पानी", "sat_ol": "ᱫᱟᱜ"}],
      "answer": "भोजन↔ᱡᱚᱢᱟᱜ; पानी↔ᱫᱟᱜ",
      "image_ref": "pages/p03_bottom.webp"
    },
    {
      "id": "Q3", "type": "trace", "sequence": 3,
      "prompt": {"hi": "लिखकर अभ्यास करें।", "sat_ol": "ᱚᱞ ᱠᱟᱛᱮ ᱪᱮᱫ ᱢᱮ।"},
      "trace_text": {"hi": "गिनती", "sat_ol": "ᱞᱮᱠᱷᱟ"},
      "answer": "traced",
      "image_ref": null
    }
  ]
}
```

Rules: 4-9 items. `answer` NEVER null (old data failed here — every FIB had null). `trace` needs `trace_text`, `match` needs `pairs` (2-5) + explicit `answer` mapping, `counting` needs integer `answer`. `image_ref` = `pages/<file>` or null.

## flashcards.json

Prebuilt deck. 10-20 cards. Chapter words — never generic 1-10 filler (old yoga/food decks both taught 1-10).

```json
{
  "deck_id": "DECK-G3-food-we-eat",
  "chapter_id": "CH-G3-FOOD-WE-EAT",
  "status": "DRAFT",
  "cards": [
    {"card_id": "FC-01", "sequence": 1, "front_hi": "भोजन", "back_sat_ol": "ᱡᱚᱢᱟᱜ", "image_ref": "pages/p02_top.webp"},
    {"card_id": "FC-02", "sequence": 2, "front_hi": "पानी", "back_sat_ol": "ᱫᱟᱜ", "image_ref": null}
  ]
}
```

Rules: `front_hi` + `back_sat_ol` required (no nulls). `image_ref` optional. No `audio_ref` — pack pipeline renders TTS from `back_sat_ol`.
