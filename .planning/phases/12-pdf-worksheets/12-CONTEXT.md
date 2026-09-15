# Phase 12: PDF Worksheets (Ship santali_organized PDFs) - Context

**Gathered:** 2026-09-15
**Status:** Ready for planning
**Source:** User directive (inline PRD — no discuss-phase; time-critical ship request)

<domain>
## Phase Boundary

Ship REAL worksheets for all 5 grades × 6 chapters = **30 chapter slots**,
from two different sources (user revision 2026-09-16 — supersedes the
PDF-only approach for Grades 1 and 3):

- **Grades 1 and 3 — explicit authored content.** The user extracted zips to
  `/cz/Vachak/1/` (5 chapters) and `/cz/Vachak/3/` (4 complete + 1 pending).
  Each chapter carries `chapter.json` (Hindi + Santali Ol Chiki titles,
  intros, outcomes), `worksheets.json` (10 items, hi/sat_ol prompts + answers
  + webp `image_ref`), `flashcards.json` (30 cards, `front_hi` +
  `back_sat_ol` Ol Chiki + `image_ref`), and `pages/*.webp` illustrations.
  ALL of it is wired in verbatim via a deterministic build-time converter —
  chapter text, worksheets, flashcards, and page art. Status `DRAFT` is
  preserved verbatim (never upgraded to APPROVED without SME sign-off).
- **Grades 2, 4, 5 (+ the two 6th-slots for G1/G3) — smallest-PDF pool.**
  20 slots filled by reference from 3–5 unique smallest PDFs in
  `santali_organized/` (~10–25 MB total), exactly as previously planned.
- Each chapter opens its worksheet **inside the app** (in-app viewer).
  Chapter page keeps its structure: worksheet entry on open + flashcard
  option at the bottom.
- Grade pages list **exactly 6 chapters per grade** (the wired set).
  Legacy extra chapter dirs (e.g. G3's other 25) stay inside the pack file
  untouched and recoverable — only the `pack_summary.json` listing is scoped
  to 6. No content deleted.
- Chapter titles are proper human names from the authored `title.hi` /
  `title.sat_ol` (never null/blank/slug-looking).
</domain>

<decisions>
## Implementation Decisions

### Scope: 30 slots from two sources
- **Explicit set (9–10 chapters):** `/cz/Vachak/1/` → Grade 1 (5 chapters:
  finding-furry-cat, how-many-times, santali-hindi-primer, so-many-toys,
  spend-my-day); `/cz/Vachak/3/` → Grade 3 (celebrating-festivals,
  double-century, fair-share, family-and-friends complete; filling-and-lifting
  JSON still landing — slot reserved, honest-empty until its JSON arrives,
  converter re-run picks it up with zero code changes).
- **6th-slots:** Grade 1's 6th = fun-with-numbers-21-99 (pool PDF fill, title
  from pack); Grade 3's 6th = pool PDF fill. Grade 5's 6 chapters and Grades
  2/4's 6 chapters are pool fills (Grade 5 keeps its 5 existing slugs where
  they exist + pool fills to reach 6).
- **Pool math (revised):** ~20 slots by reference from 3–5 unique smallest
  PDFs (~10–25 MB). The authored `/1` + `/3` content totals ~1.8 MB.
- **Dedup rule unchanged:** pool PDFs referenced, never physically duplicated
  (30 physical copies ≈ 251 MB — forbidden).
- Grade-1 is no longer 1:1 PDFs — its 5 explicit chapters ship authored JSON
  + webp, which is strictly richer than PDF pages.

### Authored-content converter (no runtime rewrite)
- The `/1` + `/3` schema differs from the pack schema `PackContentReader`
  expects. A build-time converter (`scripts/convert_authored_chapters.py`,
  stdlib-only, deterministic) maps it 1:1 into the EXISTING pack layout —
  no `PackContentReader` rewrite for G1/G3. Mapping: chapter.json →
  pack `chapter.json` (+ `text_sat_deva`/`text_hi` composed from sat_ol/hi so
  existing title lines work with zero reader changes, + `image_refs` from
  `pages/*.webp`, + `title_hi`/`title_sat_ol` passthrough keys the reader
  ignores); worksheets.json items → `worksheets/ws_<slug>_bilingual.json`
  `questions[]` (`prompt_sat_deva` = prompt.sat_ol, `answer`, `image_ref`,
  `needs_review: ["DRAFT"]`); flashcards.json cards →
  `flashcards/deck_bilingual.json` `cards[]` (`concept_hi` = front_hi,
  `concept_target` = back_sat_ol Ol Chiki, `image_ref`); `pages/*.webp` ship
  as chapter assets (BitmapFactory decodes webp — no conversion); source
  files ALSO copied verbatim into the chapter dir for provenance.
- **Flashcards exception (explicit user content):** the earlier "don't touch
  flashcards" rule holds for Grades 2/4/5 (placeholder decks stay). For the
  9 explicit G1/G3 chapters ONLY, the supplied `flashcards.json` decks
  (real Ol Chiki backs, DRAFT) REPLACE the auto-extracted placeholders —
  this is user-supplied content, wired verbatim, status preserved.
- Worksheet `image_ref` needs one small ADDITIVE runtime extension
  (`PackQuestion.imageRef` + thumbnail in `PackWorksheetView`, reusing
  `decodeDeckImage` discipline) — deck image rendering already exists.
- `filling-and-lifting` (pages-only, JSON pending): converter reserves the
  slot with honest-empty files; no invented questions/cards, ever.

### In-app worksheet opening (both slot kinds)
- **Explicit G1/G3 slots** render from converted JSON via the EXISTING
  `PackWorksheetView` (+ additive image thumbnails) — no PDF involved.
- **Pool slots (20)** open their PDF **inside the app** via
  `android.graphics.pdf.PdfRenderer` (API 21+, minSdk 28 — no new
  dependency, no INTERNET, offline-only).
- `PdfRenderer` needs a seekable `ParcelFileDescriptor`: PDFs served from the
  `.vachakpack` zip asset must be materialized to `cacheDir`/`filesDir` first
  (bounded, same pattern as `PackContentReader.materializeBundledChapter`).
- External `ACTION_VIEW` (`WorksheetPdf.viewIntent`) stays as fallback/share
  only — the primary path is in-app rendering.

### Chapter slot model (6 per grade)
- Grade pages (`GradeScreen`) already render from `pack_summary.json`
  `chapterTitles`. The 30 slots ride the EXISTING pack_summary + chapter-dir
  layout (`curriculum/class/{g}/chapters/{slug}/`), extended with a
  `worksheet_pdf` reference per chapter — no new navigation routes.
- Chapter page (`ChapterScreen`) keeps Read/Practice structure; the
  Worksheets PracticeCard routes to the in-app PDF viewer; Flashcards card
  routes to the existing deck view unchanged.
- Titles come from real pack data; missing lines fall back to pack title +
  "pending review" note (existing `ChapterRow` behavior — preserved).

### Content status honesty (AGENTS.md hard rule)
- Authored `/1` + `/3` content ships with its `status: DRAFT` preserved
  VERBATIM in every emitted file and manifest row — never upgraded, never
  relabeled. UI surfaces the existing "needs review" affordances
  (`needs_review: ["DRAFT"]`, amber labels), never approval signals.
- Pool PDFs are textbook-derived → `status=AUTO_EXTRACTED` as before.
- Textbook/authored provenance (source path + sha256) recorded per chapter in
  `chapter.json` + pack manifest + `THIRD_PARTY_NOTICES.md`.

### Claude's Discretion
- Exact unique-PDF shortlist (3–5 files) and the 20 pool-slot assignments,
  provided total unique bytes ≤ ~25 MB and every slot opens.
- PDF viewer UI details (page pager, zoom-or-fit, night-mode handling),
  following existing `VachakColors` / `tabletHPad` / `cardShadow` patterns.
- Whether PDFs ride the content `.vachakpack` (preferred — no APK rebuild
  for content swaps) vs `assets/`; content pack preferred per pack-first
  architecture (`PackContentReader` already reads both installed + bundled).
- 6th-slot names for Grades 1/3/5 and any PDF/subject mismatch reconciliation.
- Worksheet thumbnail size/placement reusing the deck-art idiom.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Content pipeline (Python, build-time only)
- `/cz/Vachak/1/`, `/cz/Vachak/3/` — USER-SUPPLIED authored chapters (read in
  place, never moved/edited; filling-and-lifting JSON may still land)
- `scripts/convert_authored_chapters.py` (NEW in 12-01) — deterministic
  authored→pack-schema converter + verbatim source copy
- `scripts/build_class1_content.py` — chapter/worksheets/deck builder pattern
  (deterministic, pymupdf, AUTO_EXTRACTED); extend, don't fork
- `scripts/build_pack_summary.py` — pack_summary.json generator (chapterTitles source)
- `scripts/build_content_pack.py` — .vachakpack assembler (add PDF entries + sha256)
- `scripts/extract_book_text.py` — text cleanup rules reuse

### Android reader + UI (runtime)
- `android/app/src/main/java/com/vachak/ui/content/PackContentReader.kt` —
  installed+bundled chapter loading, materialize-to-cache pattern, guarded bundle
- `android/app/src/main/java/com/vachak/ui/screens/GradeScreen.kt` — grade →
  chapter list (30 slots surface here)
- `android/app/src/main/java/com/vachak/ui/screens/ChapterScreen.kt` —
  chapter page (worksheet entry + flashcard entry at bottom)
- `android/app/src/main/java/com/vachak/ui/screens/ChapterStudyScreens.kt` —
  ChapterWorksheetScreen (becomes PDF viewer host) + ChapterDeckScreen (UNTOUCHED)
- `android/app/src/main/java/com/vachak/ui/screens/PackStudyViews.kt` —
  PackWorksheetView (replaced for PDF slots) / PackDeckView (UNTOUCHED)
- `android/app/src/main/java/com/vachak/ui/pdf/WorksheetPdf.kt` — existing
  template-PDF generator; fallback/share path only after this phase

### Constraints
- `AGENTS.md` — offline-only, AUTO_EXTRACTED honesty, no INTERNET, sequential
  inference, provenance requirements
- `.planning/phases/11-apk-diet/11-CONTEXT.md` — APK budget reality (~770 MB
  fresh-build projection vs <500 MB target; every MB justified)
- `android/app/src/main/AndroidManifest.xml` — no INTERNET permission (must stay)
</canonical_refs>

<specifics>
## Specific Ideas

- Measured 2026-09-15 (`find santali_organized -name '*.pdf' -printf '%s %p' | sort -n`):
  total corpus = 60 PDFs / ~818 MB. Smallest candidates:
  1. `Class-4/.../Class_4__Ticking_Clocks_and_Turning_Calendar__Santhali_.pdf` (3.2 MB)
  2. `Class-1/.../Class_1__Chapter_13_So_Many_Toys...pdf` (3.2 MB)
  3. `Class-2/.../Class_2__Playing_with_Lines__Santhali_.pdf` (3.3 MB)
  4. `Class-4/.../Class_4__Elephants_Tigers__and_Leopards__Santhali_.pdf` (4.0 MB)
  5. `Class-1/.../Class_1__Chapter_11_How_Many_Times...pdf` (5.0 MB)
- Per-grade smallest-6 available for Grades 1–4; Class-5 has only 2 PDFs
  (9.2 MB Arts Music, 12.7 MB Arts Objects) → Grade 5 reuses pool by reference.
- 30 distinct smallest PDFs = ~251 MB: does NOT fit the APK budget — dedup
  (3–5 unique files, ~10–25 MB) is the accepted mechanism, user-approved.
### Authored input (measured 2026-09-16)
- `/cz/Vachak/1/`: 5 chapters × (chapter.json + worksheets.json 10 items +
  flashcards.json 30 cards + 4–8 webp) — finding-furry-cat, how-many-times,
  santali-hindi-primer, so-many-toys, spend-my-day. Slugs match pack G1 slugs.
- `/cz/Vachak/3/`: celebrating-festivals + double-century complete (10/30/8);
  fair-share + family-and-friends JSON-complete (10/30) with empty `pages/`;
  filling-and-lifting pages-only (JSON pending — slot reserved, honest-empty).
- Authored total ≈ 1.8 MB. All files `status: DRAFT`. `sat_ol`/`back_sat_ol`
  carry real Ol Chiki (U+1C50 block, verified in sample).
- Pool still serves 20 slots (G2×6, G4×6, G5×6, G1-6th, G3-6th): smallest
  candidates unchanged (3.2 MB Ticking-Clocks G4, 3.2 MB So-Many-Toys G1,
  3.3 MB Playing-with-Lines G2, 4.0 MB Elephants G4, 5.0 MB How-Many-Times G1).
- User verbatim (revised): "wire these in for grade 1 and 3 … for the rest
  of the grades, you can just wire in some PDFs".
</specifics>

<deferred>
## Deferred Ideas

- Authoring worksheets from scratch — supplied for G1/G3, pool PDFs cover
  the rest; no new authoring in this phase.
- Mundari content — Phase-2 phrasebook track, untouched here.
- PDF text extraction/search inside the viewer — viewer is render-only.
- New flashcard decks for pool-backed chapters — placeholder decks stay as-is
  (may be empty with honest empty-state; no new decks authored). G1/G3 decks
  come from the supplied flashcards.json (user content, DRAFT).
- Re-encoding/compressing PDFs or webp for size (quality risk) — reference
  as-shipped; no transcoding in this phase.
</deferred>

<requirement_ids>
## Requirement IDs (used in PLAN frontmatter)

- **WS-01** — 30 chapter slots (5 grades × 6) each resolve to shipped
  content: 9–10 explicit G1/G3 chapters via converter (+ reserved
  filling-and-lifting slot), 20 pool-PDF slots by reference (G2/G4/G5 +
  G1-6th + G3-6th). `pack_summary.json` lists exactly 6 per grade.
- **WS-02** — Reader resolves explicit JSON (existing path) + `worksheet_pdf`
  refs (installed → bundled → cache), null-on-missing, never crashes.
- **WS-03** — Worksheets open in-app: converted JSON via PackWorksheetView
  (+ thumbnails), pool PDFs via PdfRenderer pager; honest Failed state +
  external fallback.
- **WS-04** — Content pack v0.3.0 ships converted chapters + pool with sha256
  manifest; DRAFT preserved, AUTO_EXTRACTED for pool-derived.
- **WS-05** — Size/offline discipline: pool ≤ 25 MB, authored ≈ 2 MB, no
  INTERNET, no new deps, sequential inference untouched.
- **WS-06** — Decks intact except the 9 explicit user-supplied G1/G3 decks;
  proper human titles from title.hi/title.sat_ol; JSON worksheet path
  preserved as fallback for chapters without explicit content.
</requirement_ids>

---

*Phase: 12-pdf-worksheets*
*Context gathered: 2026-09-15 via inline directive (discuss-phase skipped, time-critical)*
