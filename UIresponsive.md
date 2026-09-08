# UIresponsive — Vachak Responsive System

**Source:** `docs/ui /img` 7 PNGs (`Pastel*`, `Lavender Grid`, `Live Hindi–Santali`) + `docs/ui /home.md` + `cirriculum.md` + `live.md` + `tools.md` + `settings.md` + `ui_fomrat.md`  
**Stack:** `Kotlin/Compose` `VachakTheme` `Lexend + Noto` `840dp` breakpoint  
**Goal:** Every page — `360dp phone → 1280dp tablet → fold` — looks `clean spacious breathable` at correct ratio, no double-stack nav, no oversized hero, mic `40%`.

---

## 0. Root cause (audit `android/app/src/main/java/com/vachak/ui`)

- **Typography overrides:** `HomeScreen.kt:158` `52sp Vaibhav` on `displayLarge 57sp` theme, `CurriculumScreen.kt:120` `38sp`, `ToolsScreen.kt:93` `38sp`, `LiveComponents.kt:56` `20sp` — all hard `fontSize` ignoring `VachakTypography`, no `maxLines/ellipsis` → Hindi wraps 2+ lines, nav labels double-stack.
- **Icon/surface oversize:** spec `Heroicons.kt:23` `20-24dp / 44dp touch`. Actual `Home 64dp avatar / 32dp icon`, `Tools Primary 56dp/28dp`, `Live 140dp breath + 72dp mic + 52dp button = 230dp` voice zone.
- **Double inset:** `AdaptiveScaffold.kt:57` `navigationBarsPadding + 20dp/16dp` + each screen `LazyColumn bottom 96dp` → `204dp` dead space, floating pill `32dp`.
- **No ratio:** `BoxWithConstraints` only flips `hPad 24→32dp`, never scales `font/icon/card height`. `QuickAction 160dp`, `Grade 140×120`, `Flashcard 160×180` fixed.

Reference `Pastel Curriculum` expects `28sp` header, `44dp` avatar, `16sp` body, `20dp` icons, `40%` voice.

---

## 1. Tokens (single source `ui/theme/Type.kt, Color.kt, Theme.kt`)

| Token | Before | After (responsive) | File |
|---|---|---|---|
| `displayLarge` | `57/64` fixed | `32sp/36 phone / 40sp/44 tablet, 700, maxLines=1 ellipsis` | `Type.kt:50` `@Composable fun displayLarge(isTablet)` |
| `displaySmall` | `36/44` | `28/32 phone / 36/44 tablet` | `Type.kt:52` |
| `headlineSmall` | `24/32` | `18/24 phone / 22/28 tablet` | `Type.kt:56` |
| `titleLarge` | `22` | `18/24 phone / 20/28 tablet` | `Type.kt:57` |
| `Avatar` | `64dp/32dp` | `44dp/20dp` (all screens) | `HomeScreen.kt:134`, `SettingsComponents.kt:41` |
| `FilterPill` | `48dp` | `36dp height, 12dp pad` | `HomeComponents.kt:38` |
| `Card radii` | `28dp` stray | `24dp large` (`Theme.kt:14`), `Nav pill 32dp→20dp` | `Theme.kt:14` |
| `Touch` | — | `≥44dp` `16dp` chevron, `20dp` icons | `Heroicons.kt:23` |

No new palette — `FAF9FF / F7F2FF / EEE5FF / A984D6 / 3E3157` (`Color.kt:80`).

---

## 2. Breakpoints & scaffold `ui/navigation/AdaptiveScaffold.kt` + `VachakApp.kt:30`

- Unify `>840dp` vs `>=840dp` → `WindowSizeClass` `Compact <600 | Medium 600-840 | Expanded ≥840` (Material3).
- **Phone bottom:** `Box fillMaxWidth navigationBarsPadding padding 8dp` (not `20/16`), `Row spaced 4dp` `icon 20dp` `labelSmall 11sp maxLines=1 softWrap=false` `minWidth 56dp` → 5 labels single line `320dp`. Remove child `bottom 96dp` (`HomeScreen.kt:124`, `CurriculumScreen.kt:113`, `ToolsScreen.kt:86`, `SettingsScreen.kt:42`) — rely on `Scaffold padding(padding)` only. Saves `~96dp` dead space.
- **Tablet rail:** `NavigationRail fillMaxHeight statusBarsPadding` `indicator Lavender100`, `VerticalDivider`, `maxWidth 720dp` centered column for `Settings/Diagnostics/ManagePacks` (`settings.md:631`).
- **PendingLesson:** `VachakApp.kt:23` `pendingLesson` now passed to `ToolsScreen` or dropped — not ignored.

---

## 3. Home `ui/screens/HomeScreen.kt` + `HomeComponents.kt` (`home.md:25` order)

- `Greeting`: `Good morning 16sp` + `Vaibhav 32sp phone / 40sp tablet Bold maxLines=1 ellipsis` (was `20sp + 52sp`, `64dp` avatar → `44dp/20dp`).
- `ContinueLearningCard`: `96dp visual→72dp phone / 96dp tablet`, `28dp→24dp`, `padding 24→16dp`, `Button 56→48dp`, `description.take(90)` → `maxLines=2` wrap.
- `QuickActionCard`: `160dp fixed→ heightIn(min=120dp) wrap`, `padding 20→16dp`.
- `RecentLessonRow`: `maxLines 1→2` Hindi (`maxLines 2 overflow Ellipsis`) for `worksheet/README.md` i18n length `1.3×`.
- `Filters`: `LazyRow spaced 12dp`, `hPad 24/32` already, no `96dp` bottom.

**Input:** `Grade→Subject→Lesson` from `ContentEngine.getLessons()` (`sat_lessons.json 15`). **Output:** single-line hero, `44dp` touch, no truncation.

---

## 4. Live `ui/screens/LiveScreen.kt` + `LiveComponents.kt` (`live.md:3` Header→Voice 40%→Conversation→Input)

- `VoiceArea: BoxWithConstraints` `height = maxHeight * (0.40 empty else 0.25) coerce 120..240dp` not `140dp` fixed. `BreathVisualizer minDimension/2.6f` already responsive (`VachakComponents.kt:119`) — use it, `Idle 96dp/56dp` not `140/72`.
- `LiveTranscriptionStrip 20sp→16sp` `lineHeight 22sp` `maxLines 3 heightIn 100dp` (`LiveComponents.kt:31`), `preview 18sp→16sp` (`LiveScreen.kt:478`).
- `Conversation weight1f` gets `60%` screen, `LazyColumn` stable keys, `Hindi/Santali 20sp→16sp wrap` (`LiveComponents.kt:72`).
- `Input`: keep `imePadding` on `Box 325` only, remove double `navigationBarsPadding` (`LiveScreen.kt:496`), `Snackbar bottom 80dp→16dp`.
- `Header` `Live Translation 16sp` `maxLines=1`.

**Input:** `ShortArray PCM16 16k` `AudioRecord` `RECORD_AUDIO` + `Hin text` typed. **Output:** `Hindi (Live)` → `Santali Ol Chiki` `U+1C50` tick + `22050Hz AudioTrack`.

---

## 5. Curriculum `ui/screens/CurriculumScreen.kt` (`cirriculum.md:30`)

- `Header 38sp→28sp phone / 36sp tablet` `maxLines=1`.
- `GradeCard 140×120→120×100 phone / 140×120 tablet` adaptive count `4-6` via `LazyRow` (`cirriculum.md:868`), `Flashcard 160×180→140×160/180×200` (`CurriculumComponents.kt:30,62`).
- Keep `BoxWithConstraints hPad 32` (best), remove extra `Box fillMaxSize` nesting, `search Spacer 12dp` keep.

**Input:** `gradeId→subjectId→chapterId` `curriculum/data.py` FK. **Output:** `Browse by Grade` + `FlashcardDeck` + `Recently Viewed` single surface `Rounded20`.

---

## 6. Tools `ui/screens/ToolsScreen.kt` (`tools.md:25`)

- `Hub 38sp→28sp`, `PrimaryToolCard 56dp/28dp→44dp/20dp`, `glyph 64dp→48dp`, `Button 52dp`.
- `BoxWithConstraints` if `<360dp` stack `Row weight1f → Column` (`tools.md:118`).
- Panes `Column padding24 → LazyColumn verticalScroll` for landscape small height, `Flashcard 260dp→ heightIn 240..320`.
- Fix `ArrowForward→ArrowBack` (`ToolsScreen.kt:240,330`), duplicate import.

**Input:** `ToolsPanel Hub → Worksheets/Flashcards` `lessonId` from `pendingLesson` or `lessons.firstOrNull()`. **Output:** `Worksheet{id, template, assetPath}` + `Flashcard deck`.

---

## 7. Settings `ui/screens/SettingsScreen.kt` + `DiagnosticsScreen.kt` + `ManagePacksScreen.kt`

- Add `BoxWithConstraints hPad 24/32 maxWidth 720dp centered` `statusBarsPadding`, `Header 32sp→28sp` unify, `Group Rounded20 padding 4dp + Row 44dp/20dp` keep.
- `Diagnostics` (`verticalScroll padding16`) → `LazyColumn padding 24/32 bottom 16` + `Scaffold`, `Card fillMaxWidth` mono `ASR 99MB • MT 357M • TTS 41M` wrap or `horizontalScroll`, add `TopAppBar navIcon + BackHandler` to `Settings` (trap fix `VachakApp.kt:56`).
- `ManagePacks` `Button fillMaxWidth→ maxWidth 560dp centered`, `outer Column → BoxWithConstraints` (no nested overflow), `progress freeSpace -1` fallback already.

**Input:** `PackManager.getActivePack*` `Off/` `SHA256`. **Output:** `ManagePacks → install` `SAF` + `Diagnostics latency`.

---

## 8. Execution waves

1. **Tokens** (`Type/Theme/Heroicons`) → 2. **Navigation** (`AdaptiveScaffold` + `VachakApp` + remove `96dp`) → 3. **Home** → 4. **Live 40%** → 5. **Curriculum/Tools/Settings** tablet. Each: `assembleDebug` → screenshot `360dp` + `1280dp` vs `docs/ui /img/Pastel*` `gsd-ui-checker` → `LazyColumn stable` `≥44dp` `Ol Chiki` not clipped.

No new deps, sequential `ReentrantLock`, offline `no INTERNET`.

