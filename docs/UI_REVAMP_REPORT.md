# Vachak UI Revamp — Ship Report

**Range:** `87c91c9` → `cc617ac` (8 commits) · **Status:** debug APK builds, 64/64 unit tests green
**Theme:** light-first classroom (warm paper + deep pine + marigold), Hindi ⇄ Santali only

## 1. Design system (new foundation)

- `ui/theme/Depth.kt` — single call site for every shadow: pine-tinted spot +
  faint ambient, three levels (Card 3dp / Raised 10dp / Overlay 16dp), plus
  `VachakLayer` surface roles (Page < Section < Card, Well recessed darker).
  All colors resolve from `colorScheme`, so a future dark theme inherits the
  layer relationships.
- `ui/components/Expressive.kt` — `SectionCard` (title + muted subtitle),
  `SelectableCard` (selected = elevated, never just recolored), `InsetWell`
  (recessed tables/progress, zero shadow), `StatusRow` (color **plus** text
  cause), `GuidedEmpty` (what + why + one action, action optional),
  `SteppedLoading` (named steps + real fraction), `BreadcrumbTrail`.
- Motion language: ease-out `cubic-bezier(0.2,0,0,1)` everywhere, press
  exactly `0.96` / 140ms, icon swaps cross-fade (fade + scale 0.25→1, 150ms),
  tabular (monospace) numerals for all timers/counts/timings, concentric
  radii (20dp card − 6dp pad = 14dp field), 1px pure-black 10% image outlines.

## 2. Navigation — classroom-first

- Tabs: **Today → Translate → Learn → More** (was Home/Live/Learn/Library/Settings).
- **Library merged into Learn.** One scroll: header+search → filters → Continue
  hero → Browse by Grade → Worksheets → Flashcard Decks → Saved Items →
  Recently Viewed. Sub-pages with breadcrumbs: `learn/worksheets`,
  `learn/flashcards`, `learn/saved` (existing panes, now self-loading).
  Chapter tree (`learn/grade/…/chapter/…/worksheet|flashcards`) untouched.
- `tools/*` routes stay registered but **redirect** to `learn/*` (deep-link
  compat, route-table tests green). `NavDest.fromRoute` mapping frozen by tests.
- Translate **locked to Hindi ⇄ Santali**: Mundari toggle removed from the
  page (on-device MT only supports `hi→sat_Olck`); entering Translate pins
  the pair; history items keep their own language/voice.

## 3. Screen-by-screen

| Screen | What changed |
|---|---|
| Today (dashboard) | Pine Continue hero (count chip, recessed progress well, marigold CTA), staged entrance (once, ~70ms stagger), search with result counts + inline hits, Done/Ongoing status chips, honest header context (date • offline • x-of-y) |
| Translate | Named pipeline states everywhere, guided empty state, error cards with causes + retry, mic 0.96 press + icon cross-fade, tabular timings, draining taps narrated via snackbar |
| Learn | Unified page (see §2), measured PDF rows, saved-counts card, guided no-match card |
| Grade / Chapter / Study | Breadcrumb headers, chapter **Mark Complete** + Done chips (new `completedChapters` prefs), grade rows refresh on resume, card shadows, outlined gallery art |
| Lesson detail | Breadcrumb header, completion + recents wiring kept, repo-doc citations removed from user-facing text |
| Worksheets / Flashcards / Saved | Breadcrumb sub-headers, self-loading library, GuidedEmpty states, outlined art, tabular counters |
| More (settings) | **Removed:** Sign Out (no auth existed), Offline Content (duplicate), Grade pref (write-only). **Fixed:** hardcoded "312 MB" → measured storage; stale Tools/Settings references; real build SHA in About |
| Packs | Breadcrumb + back, budget card with measured MB, installer StatusRow feedback, GuidedEmpty, elevated active pack |
| Diagnostics | New **Live Log** section: tag filters, last-60 ring buffer, Clear, adb mirror command |
| Splash | Kept (brand tile, honest per-model states) |

## 4. Production hardening (same range)

- **Stop-button wedge fixed** (was: mic looked dead after Stop). Causes: unbounded
  preview MT held the mutex forever; unbounded tail-decode/synth wedged
  `isStopping`; draining taps vanished silently. Fixes: 15s preview / 20s tail /
  60s synth bounds, pipeline dispatcher 1→2 lanes (mutex still serializes
  models), 45s stop watchdog with force-reset, narrated draining taps.
- **APK-wide logging:** `VachakLog` facade in `:core` — all ~230 log calls flow
  to logcat **and** a 300-line ring buffer shown in Diagnostics → Live Log.
  Tag contract: `Vachak-ASR/-VAD/-MT/-TTS/-Latency/-Diag/-Pack/-Stop`.
- **Dev chrome removed:** floating language overlay, floating debug overlay +
  toggle, dead settings rows, user-facing repo-doc references.

## 5. Verification

- `:app:compileDebugKotlin` clean (incl. `:ml`, `:core`)
- 64/64 JVM unit tests green (`:app` + `:ml` + `:core`, incl. `NavDestTest`,
  `NavRouteTest`, ASR/TTS regression suites)
- `:app:assembleDebug` **builds** (`app-debug.apk`)
- Not run here (needs tablet): 10%-speed motion replay, TalkBack pass,
  measured <3s live run per `docs/DEMO.md`

## 6. Ship flags

1. **Debug APK is 708 MB** (both ABIs, unoptimized). Confirm the **release**
   APK (minify + per-ABI splits) and on-device Diagnostics → Storage before
   handing out tablets; budget is 500 MB measured on-device.
2. `animateItem` not used (not public in this Compose version) — lists stay
   static, which is correct for high-frequency surfaces anyway.
3. M3 buttons keep ripple (no press-scale); custom press targets use 0.96.
4. Santali→Hindi *speech* is impossible on-device (no sat ASR/MT) — the UI
   doesn't pretend otherwise; the pair display is the bidirectional surface.
5. `ToolsPanel`/`toolsPanelFromRoute`/`VachakLogger` remain only for tests,
   legacy deep links, and the mirror API.
