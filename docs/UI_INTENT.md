# Vachak UI Build Intent — Cupertino on Android (Kotlin only)

> **Mode:** Operate — teacher completes a task in <3s on a sunlit 2GB tablet.  
> **Pre-flight:** impeccable `shape` before build. This doc is the green-light.

---

## 1) Why Cupertino on Android?

`compose-cupertino` (cloned at `/compose-cupertino`, `0.2.0-alpha05`, Apache-2.0) is an iOS-native look. We **do not** ship its Multiplatform binary directly — it requires Kotlin 2.0 + Compose 1.7, while Vachak is pinned to Kotlin 1.9 + Compose 1.6.8 + minSdk 28 for the 2GB tablet and sherpa-onnx AAR. Instead we:

- Cloned the repo for reference (as requested)
- Extracted its **patterns**: grouped `CupertinoSection`, `CupertinoListItem` dividers, translucent `Haze` tab bar, pill `SegmentedControl`, `ActivityIndicator`, large titles
- Re-implemented a **lightweight Cupertino layer** on top of `material3` using `VachakTheme` tokens (Forest/Palash/Paper) — APK cost ~0, no version conflict, same visual language, fully previewable in Android Studio via `@Preview`.

If we later upgrade to Kotlin 2.0, swap `VachakCupertino*` wrappers for `io.github.alexzhirkevich.cupertino.*` 1:1 (API is mirrored).

---

## 2) Surfaces & Navigation (5 tabs + Auth + Splash)

```
Auth (OTP stub) → Splash (model check) → AppShell
                                        ├─ Home (dashboard + quick-start + recent)
                                        ├─ Live (hero — voice→voice)
                                        ├─ Curriculum (Grade→Subject→LessonDetail)
                                        ├─ Tools (Worksheets + Flashcards)
                                        └─ System (Offline/RAM/Latency/Packs)
                                        ← Cupertino Tab Bar (phone) / Sidebar Rail (tablet ≥840dp)
```

- **Phone (<840dp):** bottom `CupertinoTabView` — translucent blur, 5 items, Live is center-elevated mic pill.
- **Tablet:** left `NavigationRail` (72dp) + 2-pane content (list 360dp | detail flex). Preview via `@Preview(device="spec:width=1280dp,height=800dp,dpi=240")`.
- **Auth:** first-run screen, 10-digit mobile + 6-digit OTP cells, `Skip (offline demo)` — respects hard rule `no INTERNET permission`, wires to future `AuthEngine` interface.

---

## 3) Design Tokens (sunlight-readable, WCAG AA)

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| Primary Forest | #285943 | #A7D1AB | CTA, rail, NIPUN badge |
| Palash Orange | #E65100 | #FFB77A | Live mic, accent, grade pill |
| Paper | #FFFCF5 | #111412 | background |
| Surface | #FFFFFF | #1A1C19 | cards |
| SurfaceVariant | #E0E8E0 | #333834 | grouped sections |
| Offline | #2E7D32 / #E8F5E9 | #B9F6CA / #1B5E20 | persistent pill |
| Error | #B00020 | #FFB4AB | ASR fail banner |

Typography: `VachakTypography` — `bodyLarge 18sp/26` (Hindi), `olChikiLarge 20sp/28 medium` (Ol Chiki) for dense glyphs. All cards have 2px top stroke: saffron=Hi, green=Ol Chiki.

---

## 4) Component Map (Cupertino → Vachak)

| Cupertino (reference) | VachakCupertino (our wrapper) | Where |
|-----------------------|-------------------------------|-------|
| `CupertinoButton` filled/plain | `VachakButton` / `VachakGhostButton` | All CTAs, mic, replay |
| `CupertinoSection` + `SectionItem` | `CupertinoSection` (our impl) | System, Curriculum groups |
| `CupertinoSearchTextField` | `VachakSearch` | Curriculum filter |
| `CupertinoSegmentedControl` | `VachakSegment` | Grade/Subject, Hi↔Ol Chiki toggle |
| `CupertinoActivityIndicator` | `VachakLoader` | Pipeline stages |
| `CupertinoNavigationBar` (haze) | `VachakTopBar` + `OfflineBadge` | Every screen |
| `CupertinoTabView` | `VachakTabBar` + `VachakRail` | Shell |
| `CupertinoCard` | `VachakCard` (20dp, grouped shadow) | Lessons, dual-pane, flashcards |
| `CupertinoGauge` | `VachakGauge` | System RAM/storage |
| `CupertinoTextField` + OTP | `VachakOTPField` | Auth |

Each wrapper ships with `@Preview(showBackground=true)` × light/dark × tablet.

---

## 5) Data Contracts (reuse existing engines)

- No new network. UI reads `EngineProvider` interfaces only (`EngineContracts.kt:46`, `EngineProvider.kt:17`).
- Curriculum: `MockCurriculumEngine` → later Room; preview uses `seed/seed_lessons.json` + `lesson_package_L-COUNT-G2.json`.
- Worksheets: `WorksheetEngine.generate()` template-based; UI never AI-generates.
- Flashcards: `FlashcardEngine.listDeck()` prebuilt WebP; placeholder `asset://`.
- Auth: new `AuthEngine` stub (see below) — OTP verified locally, skipped offline.

---

## 6) Build Plan (files to create)

```
android/app/src/main/java/com/vachak/ui/
 ├─ theme/Theme.kt                   ✅ (done)
 ├─ cupertino/VachakCupertino.kt     → section/card/button/segment/badge/gauge/loader/search
 ├─ screens/AuthScreen.kt            → mobile + OTP + skip
 ├─ screens/HomeScreen.kt            → dashboard
 ├─ screens/LiveScreen.kt            → hero + dual-pane + latency + history
 ├─ screens/CurriculumScreen.kt      → browser + detail
 ├─ screens/ToolsScreen.kt           → worksheets + flashcards (pager)
 ├─ screens/SystemScreen.kt          → diagnostics + packs
 ├─ VachakApp.kt                     → shell + navigation state + adaptive
 └─ MainActivity.kt                  (wire VachakTheme + VachakApp)
android/app/build.gradle.kts          → keep material3 1.2.1 (no cupertino binary yet), add adaptive icons if needed
```

---

## 7) Preview Matrix (what reviewers see in Split)

Every Composable gets 3 previews:

```kotlin
@Preview(showBackground=true, name="Light")
@Preview(showBackground=true, uiMode=Configuration.UI_MODE_NIGHT_YES, name="Dark")
@Preview(showBackground=true, device="spec:width=1280dp,height=800dp,dpi=240", name="Tablet")
```

Plus Interactive preview on `LiveScreen` for pulsing mic.

---

## 8) Impeccable Checks Before Ship

- **critique:** hierarchy — hero mic > dual-pane > latency > history (F-pattern)
- **audit:** a11y — 48dp targets, `contentDescription`, TalkBack splits Hi/Ol Chiki, font scale 80–200%
- **harden:** error states for ASR fail, empty curriculum, missing pack, storage full
- **adapt:** phone 360dp, tablet 1280dp, font scale, dark/light
- **optimize:** no Lottie, no parallel models, sequential pipeline ≤3s, skeleton loaders only

---

## 9) Green Light

Intent approved. Proceed to `todo 3 → 4 → 5`. No React, Kotlin-only, Cupertino-inspired via lightweight wrappers referencing the cloned repo. Build beautifully, then run one batched preview audit (light/dark/phone/tablet) per impeccable craft floor.

