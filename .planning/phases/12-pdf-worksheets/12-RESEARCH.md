# Phase 12: PDF Worksheets - Research

**Researched:** 2026-09-15
**Domain:** Offline Android PDF delivery (content-pack PDFs + in-app PdfRenderer viewer on minSdk 28, 2 GB RAM)
**Confidence:** HIGH (all findings measured in-repo or platform-API grounded)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- 5 grades × 6 chapters = 30 chapter slots; PDFs from `santali_organized/`,
  smallest-first.
- Dedup by replication: 3–5 unique small PDFs referenced across 30 slots;
  physically duplicating 30 PDFs is forbidden by the size budget.
- PDFs open INSIDE the app (in-app viewer, not external intent).
- Flashcards untouched (PackDeckView + decks stay as-is).
- Chapter titles proper human names, never null/blank.
- Everything ships AUTO_EXTRACTED with textbook provenance; never APPROVED
  without SME sign-off.

### Claude's Discretion
- Exact 3–5 file shortlist + 30-slot mapping (≤ ~25 MB unique bytes).
- Viewer UI details following existing theme patterns.
- Content-pack vs assets delivery (content pack preferred).
- Grade-5 6th chapter name + PDF/subject mismatch reconciliation.

### Deferred Ideas (OUT OF SCOPE)
- Authoring real worksheets; Mundari content; in-viewer text search;
  new flashcard decks; PDF re-encoding/compression.
</user_constraints>

<architectural_responsibility_map>
## Architectural Responsibility Map

Single-app offline system — all capabilities reside on-device:

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| PDF selection + slot mapping | Build-time script (Python) | — | Deterministic, offline, reproducible; same pattern as build_classN_content.py |
| PDF transport to device | Content `.vachakpack` (existing PackInstaller) | Bundled asset fallback | Pack-first architecture; no APK rebuild for content swaps (AGENTS.md) |
| PDF byte access at runtime | `PackContentReader` (installed → bundled fallback) | — | Single guarded reader; already owns this responsibility |
| PDF rendering | New in-app viewer (`PdfRenderer`, framework API) | External ACTION_VIEW fallback | Offline, zero new deps, no INTERNET surface |
| Chapter/grade lists | Existing GradeScreen/ChapterScreen | — | No new routes; slots ride pack_summary.json |
</architectural_responsibility_map>

<research_summary>
## Summary

Measured the corpus (60 PDFs / ~818 MB), the current pack (6 chapters Grade 1,
11 Grade 2, 31 Grade 3, 10 Grade 4, 5 Grade 5 — already ≥6 except Grade 5), and
the runtime gap: worksheets today are JSON question lists rendered by
`PackWorksheetView`; nothing renders PDF in-app (`WorksheetPdf` only GENERATES
template PDFs and fires external `ACTION_VIEW`). So this phase has three loads:
(1) build-time selection/mapping + pack entries, (2) a `PdfRenderer`-based
in-app viewer hosted in `ChapterWorksheetScreen`, (3) `worksheet_pdf` wiring
through `chapter.json` → `PackContentReader` → UI.

The size question is settled by measurement: 30 distinct smallest PDFs ≈
251 MB — incompatible with the ~770 MB → <500 MB budget war (see 11-CONTEXT).
3 smallest unique PDFs ≈ 9.8 MB; 5 smallest ≈ ~19 MB. Dedup-by-reference is
the only viable delivery, and it is also what the user ordered.

**Primary recommendation (revised 2026-09-16, two sources):** Convert the
authored `/1` + `/3` chapters into the existing pack layout with a
deterministic build-time converter (9–10 explicit G1/G3 slots incl. webp art,
DRAFT preserved — zero reader rewrite); fill the remaining 20 slots
(G2/G4/G5 + G1-6th + G3-6th) by reference from 3–5 unique smallest PDFs
(~10–25 MB); render pool PDFs with a bounded `PdfRenderer` pager and explicit
JSON with existing views + additive image thumbnails. Two plans: content+pack
(converter + pool, no Android code) and reader+viewer (pool-PDF open +
worksheet thumbnails, decks except the 9 supplied ones untouched).
</research_summary>

<standard_stack>
## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `android.graphics.pdf.PdfRenderer` | Framework (API 21+, minSdk 28 ✓) | In-app PDF page rasterization | Zero deps, offline, no permission; the platform answer for render-in-app |
| `PackInstaller` / `PackManager` (existing) | In-repo | Side-load content pack, sha256 verify | Already the content transport; PDFs ride free |
| `pymupdf` (build-time) | Already used by build_classN scripts | PDF validation/page-count at pack time | Deterministic build tooling, dev-machine only |
| `ZipInputStream` + cacheDir materialize | Existing pattern (`materializeBundledChapter`) | Extract single PDF from bundled pack | `PdfRenderer` needs seekable fd — cannot stream from asset zip |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `FileProvider` + `ACTION_VIEW` (existing `WorksheetPdf.viewIntent`) | Framework | Fallback open/share | Only when in-app render fails for a file |
| `pack_summary.json` chapterTitles | Existing contract | 30 slots surface on Grade pages | Extend counts with pdf flag, don't fork |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `PdfRenderer` | PdfiumAndroid / AndroidPdfViewer / WebView+pdf.js | Native deps (+MBs, JNI risk) or JS engine + INTERNET-adjacent surface; rejected — framework renderer is enough for page-at-a-time reading |
| Content-pack PDFs | `assets/` PDFs in APK | APK rebuild per content swap; pack-first arch says no |
| Reference dedup | 30 physical PDF copies | ~251 MB vs ~10–25 MB; rejected by budget + user order |

**Installation:** none — framework API + existing in-repo code only.
</standard_stack>

<architecture_patterns>
## Architecture Patterns

### System Architecture Diagram

```
santali_organized/*.pdf (60 files, 818 MB)
  │  build-time, offline, deterministic
  ▼
select_smallest.py logic (inside pack builder)
  │  top 3–5 by bytes, sha256, pymupdf page-count validate
  ▼
curriculum/class/pdf_pool/*.pdf (3–5 UNIQUE files, ~10–25 MB)
  + curriculum/class/{g}/chapters/{slug}/chapter.json  (+ worksheet_pdf ref)
  ▼
build_pack_summary.py → pack_summary.json (30 slots, pdf-backed flags)
  ▼
build_content_pack.py → sat_Olck-v0.3.0.vachakpack (curriculum_files[] sha256)
  │  SAF side-load, PackInstaller verifies
  ▼
device: filesDir/packs/<id>/curriculum/  ──or──  bundled asset fallback
  ▼
PackContentReader.openWorksheetPdf(grade, slug) → File (materialized to cache)
  │  null on missing (never throws to UI)
  ▼
ChapterWorksheetScreen → PdfViewer (PdfRenderer, 1 page bitmap at a time)
  │  failure → honest empty/failed state + external-viewer fallback button
  ▼
ChapterScreen PracticeCard (unchanged entry) / PackDeckView (UNTOUCHED)
```

### Recommended Project Structure
```
scripts/select_pdf_pool.py        # NEW: smallest-N selection + 30-slot mapping.json
curriculum/class/pdf_pool/        # NEW (build output): 3–5 unique PDFs
curriculum/class/{g}/chapters/{slug}/chapter.json  # + worksheet_pdf, pdf_sha256
android/.../ui/content/PackContentReader.kt  # + openWorksheetPdf(), PdfRef
android/.../ui/pdf/PdfViewer.kt   # NEW: PdfRenderer pager composable
android/.../ui/screens/ChapterStudyScreens.kt  # host viewer (deck UNTOUCHED)
```

### Pattern 1: Bounded PdfRenderer page loop
**What:** Open `ParcelFileDescriptor`, render ONE page to an `RGB_565` bitmap
sized to the view, `recycle()` the previous bitmap on page change, `close()`
renderer + fd in `DisposableEffect`.
**When to use:** Every PDF view on a 2 GB device — never hold N page bitmaps.
**Example:**
```kotlin
// Source: Android framework docs, android.graphics.pdf.PdfRenderer
val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
val renderer = PdfRenderer(fd)
val page = renderer.openPage(index)
val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
page.close() // renderer.close() + fd.close() on dispose
```

### Pattern 2: Guarded bundle-style loader (repo house style)
**What:** `suspend fun openWorksheetPdf(): File?` — installed pack first,
bundled `.vachakpack` scan second, cache materialize third; every step in
`runCatching`, null = missing (UI shows reason, never crashes). Mirrors
`loadChapterBundle` / `readWorksheetBlocking` exactly.
**When to use:** All new pack reads in this phase.

### Anti-Patterns to Avoid
- **Rendering all pages into a bitmap list:** OOMs 2 GB tablets on a 60-page
  primer — one live bitmap, always.
- **Streaming PdfRenderer straight from the asset zip:** impossible (needs
  seekable fd) — materialize to cache first (bounded: 1 PDF, ≤ ~30 MB cap).
- **Replacing the JSON worksheet path:** PDF slots EXTEND the chapter bundle
  (`worksheet_pdf` ref alongside questions); chapters without the ref render
  exactly as today — no flag-day.
- **Physical 30× PDF duplication:** 251 MB vs 10–25 MB referenced; budget kill.
</architecture_patterns>

<dont_hand_roll>
## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PDF rasterization | Custom decoder / WebView+JS | `PdfRenderer` framework API | Security-reviewed, zero deps, correct Indic shaping via Skia text path |
| Pack transport/verify | New downloader/copier | `PackInstaller` + manifest sha256 | Already guards zip-slip + hash; new code = new vulns |
| Page-image memory | ARGB_8888 full-res pages | RGB_565 + inSampleSize-style downscale + recycle | 2 GB RAM: halved bitmap memory is the difference between reading and OOM |
| Chapter routing | New nav graph entries | Existing learn/grade/{g}/chapter/{slug}/worksheet route | GradeScreen/ChapterScreen already enumerate slots from pack_summary |

**Key insight:** This phase is 80% plumbing through EXISTING pipes
(pack → reader → chapter screens). The only new code is a thin renderer.
Any new transport, routing, or decoding stack is pure risk.
</dont_hand_roll>

<common_pitfalls>
## Common Pitfalls

### Pitfall 1: PdfRenderer vs asset-zip streaming
**What goes wrong:** Crash/`IOException` trying to open renderer on a zip entry stream.
**Why it happens:** `PdfRenderer` requires a seekable fd.
**How to avoid:** Materialize the single PDF to `cacheDir` first (cap ~30 MB,
verify sha256 against manifest), then open.
**Warning signs:** Any `ParcelFileDescriptor` built over an `InputStream`.

### Pitfall 2: Bitmap OOM on large textbook pages
**What goes wrong:** Full-res ARGB_8888 page bitmaps kill 2 GB devices.
**Why it happens:** Textbook pages are print-resolution; ×4 bytes/px adds up.
**How to avoid:** RGB_565, render at display width (not PDF native dpi),
one live bitmap, `recycle()` on page change/dispose.
**Warning signs:** `OutOfMemoryError` in gallery/deck-adjacent code paths.

### Pitfall 3: Stale build-script paths
**What goes wrong:** Pack builder finds zero PDFs (writes empty pool).
**Why it happens:** `build_class1_content.py` references pre-reorg paths
(`Class 1-2/...`) but the tree is now `santali_organized/Class-N/...`.
**How to avoid:** New selection script globs the CURRENT tree and fails
loud (non-zero exit + file list) when a listed PDF is missing.
**Warning signs:** Pack builds "succeed" with 0-byte pool or missing refs.

### Pitfall 4: Grade-5 chapter-count mismatch
**What goes wrong:** Grade 5 shows 5 chapters, user demanded 6.
**Why it happens:** Pack has 5 G5 chapters; Class-5 has only 2 PDFs.
**How to avoid:** Create the 6th G5 chapter dir reusing a pool PDF by ref
(same mechanism as every other slot) with a proper name + honest deck-empty state.
**Warning signs:** `pack_summary.json` grade-5 chapter count ≠ 6.

### Pitfall 5: Approval-status laundering
**What goes wrong:** PDF chapters render without review-pending signals.
**Why it happens:** New `worksheet_pdf` path bypasses the AUTO_EXTRACTED notices.
**How to avoid:** Bundle notices for PDF slots ("Textbook PDF — speaker review
pending"), manifest `content_status` unchanged, provenance per chapter.
**Warning signs:** Any UI string implying a PDF chapter is approved/curated.
</common_pitfalls>

<code_examples>
## Code Examples

### Bounded single-PDF materialize from bundled pack
```kotlin
// Source: PackContentReader.materializeBundledChapter pattern (in-repo)
context.assets.open("packs/$packFile").use { raw ->
  java.util.zip.ZipInputStream(raw).use { zin ->
    var e = zin.nextEntry
    while (e != null) {
      if (!e.isDirectory && e.name == wantedEntry) {
        writeCacheFile(base, e.name, zin.readBytes cappedAt 30MB)
        break
      }
      zin.closeEntry(); e = zin.nextEntry
    }
  }
}
```

### PdfRenderer lifecycle in Compose
```kotlin
// Source: Android developer docs — PdfRenderer sample, adapted to house style
DisposableEffect(pdfFile) {
  val fd = ParcelFileDescriptor.open(pdfFile, MODE_READ_ONLY)
  val renderer = PdfRenderer(fd)
  onDispose { runCatching { renderer.close() }; runCatching { fd.close() } }
}
```

### Build-time smallest-N selection (deterministic)
```python
# Source: measured 2026-09-15 via find -printf '%s %p' | sort -n
pdfs = [(os.path.getsize(p), p) for p in glob('santali_organized/**/*.pdf')]
pdfs.sort()  # byte-identical input => byte-identical mapping
pool, mapping = pdfs[:5], assign_slots(pdfs[:5])  # every slot → pool ref
```
</code_examples>

<sota_updates>
## State of the Art (2024-2025)

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| External viewer intent for PDFs | In-app `PdfRenderer` pager | Long-standing API (21+); Compose hosting is now routine | No new dep needed for read-only paging |
| Per-chapter duplicated assets | Content-pack + reference dedup | Repo's own `pdf_pool`-style asset dedup precedent (`assets/` img dedup in build scripts) | Same trick, applied to PDFs |
| Template-generated worksheets | Textbook PDFs as worksheet layer | This phase (time constraint) | JSON path kept as fallback, not deleted |

**New tools/patterns to consider:** none — framework API suffices.
**Deprecated/outdated:** `WorksheetPdf.viewIntent` as PRIMARY open path
(kept as fallback/share only).
</sota_updates>

## Addendum A — Authored G1/G3 content (measured 2026-09-16, supersedes PDF-only for G1/G3)

User-supplied zips extracted to `/cz/Vachak/1/` and `/cz/Vachak/3/`. NOT PDFs:
authored chapter bundles, total ~1.8 MB / 79 files.

### Inventory

| Dir | Chapters | worksheets.json | flashcards.json | pages/*.webp | Status |
|-----|----------|-----------------|-----------------|--------------|--------|
| `/1/` | 5 (finding-furry-cat, how-many-times, santali-hindi-primer, so-many-toys, spend-my-day) | 10 items each | 30 cards each | 4–8 each | all DRAFT |
| `/3/` celebrating-festivals, double-century | 2 complete | 10 items each | 30 cards each | 8 each | DRAFT |
| `/3/` fair-share, family-and-friends | 2 JSON-complete | 10 items each | 30 cards each | 0 (empty dirs) | DRAFT |
| `/3/` filling-and-lifting | 0 (slot reserved) | pending | pending | 0 (empty dir) | JSON landing |

G1 slugs match pack G1 slugs 1:1 except the pack's 6th (fun-with-numbers-21-99,
not supplied → pool fill). G3's 6-slot demo set = 4 complete + filling-and-lifting
(reserved honest-empty until JSON lands) + 1 pool fill.

### Source schema (authored) → pack schema (reader-expected) mapping

| Authored (`/1/<slug>/`, `/3/<slug>/`) | Pack (`curriculum/class/{g}/chapters/<slug>/`) | Notes |
|---------------------------------------|-----------------------------------------------|-------|
| `chapter.json` (chapter_id, grade, subject_id, slug, source_pdf, title{hi,sat_ol}, intro{hi,sat_ol}, outcomes[], teacher_note, vocab, estimated_minutes, status) | `chapter.json` (chapter_id, subject<=subject_id, status<=DRAFT verbatim, text_encoding, text_sat_deva<=join(intro.sat_ol, outcomes[].text.sat_ol), text_hi<=join(hi counterparts), title_hi<=title.hi, title_sat_ol<=title.sat_ol, image_refs[]<=pages/*.webp, provenance{source_dir, source_pdf, sha256}) | `text_sat_deva`/`text_hi` composition is what feeds existing `readChapterTitleDeva`/title-lines with ZERO reader changes; `title_*` are additive keys the reader ignores |
| `worksheets.json` items[{id, type, sequence, prompt{hi,sat_ol}, answer, image_ref}] | `worksheets/ws_<slug>_bilingual.json` {questions:[{id, type, prompt_sat_deva<=prompt.sat_ol, prompt_hi<=prompt.hi, answer, image_ref, render_count:0, needs_review:["DRAFT"]}]} | `image_ref` values (`pages/p01_top.webp`) resolve against the chapter dir — needs the additive `PackQuestion.imageRef` reader+UI extension (12-02) |
| `flashcards.json` cards[{card_id, sequence, front_hi, back_sat_ol, image_ref}] | `flashcards/deck_bilingual.json` {cards:[{card_id, concept_hi<=front_hi, concept_target<=back_sat_ol, image_ref, needs_review:["DRAFT"]}]} | Deck image rendering ALREADY exists (`PackCard.imageRef` + `decodeDeckImage`) — populating refs is enough; these 9 decks replace auto-extracted placeholders for G1/G3 only |
| `pages/*.webp` | `pages/*.webp` verbatim + `assets` NOT duplicated (single copy, referenced) | `BitmapFactory.decodeFile` decodes WEBP on minSdk 28 — no conversion; gallery path (`image_refs` → `decodeImage`, RGB_565, ≤4) already handles them |
| (all source files) | copied verbatim alongside (`source/` subdir per chapter) | Provenance: byte-identical inputs recoverable from the pack |

### Revised size math

Authored `/1`+`/3` ≈ 1.8 MB (nothing to dedup — already tiny). Pool now serves
20 slots (G2×6, G4×6, G5×6, G1-6th, G3-6th) from the same 3–5 smallest PDFs
(~10–25 MB). Total content delta ≈ 12–27 MB — still an order of magnitude under
the rejected 251 MB distinct-file design.

### Converter hazards (new pitfalls)

- **filling-and-lifting arrives mid-flight:** converter MUST be re-runnable and
  treat missing JSON as honest-empty slot (empty questions/cards + notice),
  never invented content. Second run with JSON present upgrades the slot.
- **fair-share/family-and-friends have zero pages:** converter emits
  `image_refs: []` + notice (worksheets/decks render text-only); no crash, no
  placeholder art.
- **Status laundering:** source `status: "DRAFT"` flows verbatim into every
  emitted file + manifest row; the converter asserts no emitted status string
  equals `APPROVED` (fail build otherwise).
- **Slug drift:** `/1`+`/3` slugs MUST equal the pack_summary slugs for G1/G3
  demo sets (assert in converter test) — GradeScreen routes by slug.

## Validation Architecture

> Nyquist gate input (§5.5): executor samples with the existing suites —
> no new framework. Quick = JVM unit tests for touched modules; full =
> offline Python suites + JVM tests.

- Quick per-task: `./gradlew :ml:testDebugUnitTest :app:testDebugUnitTest`
  (touched: PackContentReaderTest-style JVM tests, new PdfViewer/parser tests).
- Full per-wave: `/usr/bin/python3 -m unittest discover -s curriculum/tests`
  + `scripts/run_offline_tests.sh` + JVM tests above.
- Manual-only: on-tablet open of all 30 PDF slots (WiFi OFF) + <3 s unaffected
  (no inference path touched) + diagnostics bytes; emulator PdfRenderer paging
  (page forward/back, rotation, low-memory) — cannot be unit-automated.
- Budgets enforced as assertions, not estimates: unique pool bytes ≤ 25 MB in
  the builder (fail build), chapter.json `worksheet_pdf` present for 30/30
  slots (pack test), zero INTERNET permission diff (manifest grep).

<open_questions>
## Open Questions

1. **Exact 3 vs 5 unique PDFs (pool now serves 20 slots, not 30)**
   - What we know: 3 smallest ≈ 9.8 MB; G1-6th (fun-with-numbers) and G3-6th
     prefer same-grade pool members when available.
   - Recommendation: planner picks 5 (one anchor per grade where possible,
     else pool reuse) capped at 25 MB; mapping.json records the choice +
     per-slot reason.

2. **Does GradeScreen need a pdf badge?**
   - What we know: `ChapterRow` subtitle shows counts from pack_summary.
   - Recommendation: planner's discretion — a "PDF" affordance is nice but
     optional; chapter must open regardless.

3. **Bundled-asset pack size after adding pool**
   - What we know: `sat_Olck-v0.2.0.vachakpack` content size unmeasured here;
     +~10–25 MB pool is the delta.
   - Recommendation: builder prints pool + total bytes (like build_content_pack
     already does); 12-02 asserts install + open on emulator.
</open_questions>

<sources>
## Sources

### Primary (HIGH confidence)
- In-repo measurement 2026-09-15: `find santali_organized -name '*.pdf'
  -printf '%s %p' | sort -n` (60 files, 818 MB; smallest-30 ≈ 251 MB;
  smallest-3 ≈ 9.8 MB)
- `PackContentReader.kt` (885 lines, read in full) — installed/bundled/cache
  patterns, guarded bundle semantics
- `GradeScreen.kt` / `ChapterScreen.kt` / `ChapterStudyScreens.kt` /
  `PackStudyViews.kt` / `WorksheetPdf.kt` (read in full) — slot surface,
  worksheet host, deck (untouched), external-intent fallback
- `scripts/build_class1_content.py` (structure + stale-path hazard),
  `scripts/build_content_pack.py`, `pack_summary.json` (grade chapter lists)
- Android framework: `PdfRenderer` (API 21+, seekable-fd requirement) —
  platform knowledge, minSdk 28 per AGENTS.md

### Secondary (MEDIUM confidence)
- `.planning/phases/11-apk-diet/11-CONTEXT.md` — APK budget (~770 MB
  projection vs <500 MB target)

### Tertiary (LOW confidence)
- None — no web sources used; all findings measured or in-repo.
</sources>

<metadata>
## Metadata

**Research scope:**
- Core technology: PdfRenderer in-app viewer + content-pack PDF delivery
- Ecosystem: existing pack pipeline (build_classN, pack_summary, vachakpack)
- Patterns: PackContentReader guarded loading, cache materialize, RGB_565 discipline
- Pitfalls: seekable-fd, OOM, stale paths, G5 count, status honesty

**Confidence breakdown:**
- Standard stack: HIGH - framework API + in-repo precedent
- Architecture: HIGH - files read in full, data flow traced
- Pitfalls: HIGH - measured evidence + code-level hazards identified
- Code examples: HIGH - adapted from in-repo + platform docs

**Research date:** 2026-09-15
**Valid until:** 2026-10-15 (stable: framework API + frozen PDF corpus)
**Note:** gsd-phase-researcher subagent unavailable in this runtime; research
performed inline by the orchestrator with the same evidence bar (every size
claim measured, every file claim read). Planner should treat confidence as
stated — evidence is cited, not assumed.
</metadata>

---

*Phase: 12-pdf-worksheets*
*Research completed: 2026-09-15*
*Ready for planning: yes*
