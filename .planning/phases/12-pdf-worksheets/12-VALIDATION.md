---
phase: 12
slug: pdf-worksheets
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-09-15
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit (Android JVM) + unittest (Python offline) |
| **Config file** | `android/ml/build.gradle.kts` / `android/app/build.gradle.kts` (JVM); none (Python stdlib) |
| **Quick run command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `/usr/bin/python3 -m unittest discover -s curriculum/tests && bash scripts/run_offline_tests.sh && cd android && ./gradlew :app:testDebugUnitTest` |
| **Estimated runtime** | ~180 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick command
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 12-01-T1 | 01 | 1 | WS-01 | T-12-01 | DRAFT-verbatim, Ol Chiki gate, determinism on real inputs | unit | `/usr/bin/python3 -m unittest scripts.tests.test_convert_authored -v` (new) | ❌ W0 | ⬜ pending |
| 12-01-T2 | 01 | 1 | WS-01/WS-06 | T-12-01 | Reserved honest-empty slot, inputs unmodified, 6/grade listing | unit | `/usr/bin/python3 -m unittest discover -s curriculum/tests` | ✅ | ⬜ pending |
| 12-01-T3 | 01 | 2 | WS-01/WS-04/WS-05 | T-12-01/02 | Pool ≤25 MB enforced, sha256 manifest, honest statuses | unit | `/usr/bin/python3 -m unittest scripts.tests.test_pdf_pool -v` (new) + `bash scripts/run_offline_tests.sh` | ❌ W0 | ⬜ pending |
| 12-01-T4 | 01 | 2 | WS-06 | — | 30/30 slot audit (explicit + pool + reserved) | unit | full Python suites | ✅ | ⬜ pending |
| 12-02-T1 | 02 | 1 | WS-02/WS-03 | T-12-03/04 | openWorksheetPdf null-on-missing, capped materialize, JSON backward-compat | unit | `./gradlew :app:testDebugUnitTest --tests '*PackContentReader*'` | ❌ W0 | ⬜ pending |
| 12-02-T2 | 02 | 1 | WS-03 | T-12-04 | Thumbnail null-safe, recycle-on-dispose, deck zero-diff | unit | `./gradlew :app:testDebugUnitTest --tests '*PackWorksheetImage*'` | ❌ W0 | ⬜ pending |
| 12-02-T3 | 02 | 2 | WS-03 | T-12-03 | One live bitmap, recycle-on-change, close-on-dispose | unit | `./gradlew :app:testDebugUnitTest --tests '*PdfViewer*'` | ❌ W0 | ⬜ pending |
| 12-02-T4 | 02 | 2 | WS-05/WS-06 | — | No-INTERNET/import/launch greps, ml untouched, deck-diff proof | unit | `./gradlew :app:testDebugUnitTest` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `scripts/tests/test_convert_authored.py` — stubs for WS-01 (DRAFT-verbatim, Ol Chiki gate, determinism on real /1 /3 inputs)
- [ ] `scripts/tests/test_pdf_pool.py` — stubs for WS-01/WS-05 (pool byte-cap, determinism, missing-PDF loud fail)
- [ ] `android/app/src/test/.../PackContentReaderPdfTest.kt` — stubs for WS-02 (parse/ref resolve, null-on-missing, JSON backward-compat)
- [ ] `android/app/src/test/.../PackWorksheetImageTest.kt` — stubs for WS-03 (questionImageKey boundary table)
- [ ] `android/app/src/test/.../PdfViewerStateTest.kt` — stubs for WS-03 (page-index bounds, cap checks)
- [ ] `pymupdf` present on build machine (`/usr/bin/python3 -c "import pymupdf"`) — pool validation only, never APK

*Framework installs: none — JVM + stdlib unittest already exist.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| All 30 chapter slots open in-app, WiFi OFF (9–10 explicit JSON + 20 pool PDFs) | WS-03 | PdfRenderer paging + pack install + webp art need a device/emulator | Fresh install → Learn → Grades 1–5 → open each of 30 chapters → Worksheet → explicit: check hi+Ol Chiki prompts + thumbnails; pool: page forward/back; record any slot that shows Failed |
| filling-and-lifting reserved slot (if JSON still pending) | WS-01 | Reserved-empty is a visual state | Open G3 filling-and-lifting → worksheet shows honest empty (0 items), chapter does NOT crash; re-check after converter re-run |
| Chapter titles proper names (title.hi / title.sat_ol), none null/blank | WS-06 | Visual copy check | Grade pages 1–5: read all 30 titles; fail on slug-looking/null/blank |
| Flashcards: 9 supplied G1/G3 decks show Ol Chiki backs + art; all other decks unchanged | WS-06 | Regression is behavioral | Open supplied decks (Ol Chiki + art check) + ≥1 untouched deck per pool grade; compare against pre-phase behavior |
| APK has no INTERNET permission; total delta ≈ pool bytes | WS-05 | Permission + size are build artifacts | `grep INTERNET AndroidManifest.xml` empty; `unzip -l` tablet APK: delta vs baseline ≈ pool MB |
| Grade-5 6th chapter opens PDF + honest empty deck | WS-01/WS-06 | New slot has no precedent | Open G5 chapter 6 → worksheet renders; flashcards shows honest empty-state |

*All other phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
