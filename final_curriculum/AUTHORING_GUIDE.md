# AUTHORING_GUIDE.md — PDF -> Chapter in ~1 Hour

You have `santali_organized/` PDFs. You produce `final_curriculum/chapters/{grade}/{slug}/`. No scripts, no extraction tools needed.

## Step 0 — Pick next PDF

Start with FLN core (demo-critical): Class 1-2 primer + math, Class 3 math + EVS. PE/Arts last.
One PDF = one chapter. Name the slug from the PDF: `Class_3__Food_We_Eat__Santhali_.pdf` -> `food-we-eat`, folder `chapters/3/food-we-eat/`.

Grade-correction list (known misfiles — use TRUE grade):
* `Class 4/Physical Education & Well Being/books/Class_1__Chapter_11_...` -> grade 1, not 4.
* `Class 5/EVS/books/Class_1__Chapter_10_...` -> grade 1, not 5.
* Grade-4 PE chapters (`Throwing_and_Catching`, `Kicking_and_Receiving`, `Local_and_Traditional_Games`, `Yoga_for_Daily_Life`, `Yoga_Sadhana`, `Strike_the_Shuttlecock`, `Little_Steps`) found under Class 3/5 folders -> grade 4.

## Step 1 — chapter.json (30 min)

Open the PDF, skim all pages. Write:

1. `title` — chapter name, bilingual. Hindi from the book header, Ol Chiki translated by you.
2. `intro` — 2-3 sentences: what the chapter teaches. Not a page transcription.
3. `outcomes` — 3-5, each phrased `छात्र ... सकेंगे। / �... ᱫᱟᱲᱮᱭᱟᱜ-ᱟ।` Code them `G{grade}-{SHORT}-{01..}`.
4. `teacher_note` — 2-4 sentences: how to teach with real objects, what to avoid. Concrete, not generic.
5. `vocab` — 5-10 key words actually in the chapter. If the chapter is about food, vocab is food words — never numbers.
6. `estimated_minutes` — 30, 40, or 45.

Ol Chiki rules:
* Type Ol Chiki directly (keyboard: Ol Chiki layout / Keyman). Never paste Devanagari Santali as `sat_ol`. Never paste Krishna mojibake (`tks+ekd~`).
* Numerals in `sat_ol` use Ol Chiki digits ᱐᱑᱒᱓᱔᱕᱖᱗᱘᱙, not 0-9, not Devanagari ०-९.
* Unsure of a word? Leave that chapter `status: "DRAFT"` and flag it — do not guess-spell APPROVED content.

## Step 2 — pages/ (15 min, see IMAGE_SPEC.md)

Export 6-10 half-page images. Pick pages with teaching value (illustrations, key exercises), skip cover/publishing boilerplate.

## Step 3 — worksheets.json (10 min)

4-9 items grounded in THIS chapter. Allowed types only: `trace | fill_blank | match | counting`. Each item: bilingual prompt + real answer. Use one `image_ref` where the question needs the picture (e.g. count objects in `p03_bottom.webp`). Worksheet with `answer: null` is rejected — this was the #1 defect of the old data.

## Step 4 — flashcards.json (5 min)

10-20 cards from the chapter's `vocab` + key phrases. `front_hi` -> `back_sat_ol`. Attach `image_ref` where a picture helps. No number-deck filler: a yoga chapter teaches yoga words.

## Anti-patterns (all found in old data — do not repeat)

* Bare `"___..."` / `"................"` prompts with no stem.
* 5 identical FILL_BLANK in a row.
* MATCH pairs of single aksharas (`का़/ला़/गि`) or stopwords (`मे/आर/को़`).
* Copying sample text with control chars (`�`, `\u001a`) — retype clean.
* Marking anything APPROVED you didn't speaker-check.
