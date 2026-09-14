# Vachak UI — Design System & Page Report

**Project:** SIH26042 Vernacular Pedagogy — Offline Android tablet for Hindi→Santali (Ol Chiki) instruction  
**App module:** `android/app` (Kotlin, Jetpack Compose, Material 3, Room)  
**Status:** Debug console deleted, rebuilt as Professional Educational SaaS per transformation plan. Builds (`:app:assembleDebug`) and runs on emulator `emulator-5554` (crash in `NavDest` fixed 2026-08-29). Remaining gap: Home tab missing in bottom bar + empty hero card / empty curriculum due to Room seed not loaded — noted as TODO.

---

## 1. Design System Foundation

### Typography — Lexend
- **Spec:** Google Fonts **Lexend** (education/readability, rounded, high legibility for FLN teachers).
- **File:** `android/app/src/main/java/com/vachak/ui/theme/Type.kt:1`
- **Implementation:** `FontFamily.SansSerif` fallback (offline-safe) + full Material 3 `Typography` scale (`displayLarge 57/64 Bold` down to `labelSmall 11/16 Medium`). All `TextStyle` use `LexendFamily`. Ready to swap to downloadable `GoogleFont("Lexend", provider=com.google.android.gms.fonts)` when Play Services available; currently no network fetch.
- **Budget:** No extra APK weight (system sans).

### Color Palette — Material 3 (Forest/Palash)
- **File:** `android/app/src/main/java/com/vachak/ui/theme/Color.kt:1`, applied in `Theme.kt:1`
- **Tokens:**
  - `Primary Forest #285943` (`VachakColors.Forest`) — buttons, rail, app bar active
  - `ForestDark #1E422F`, `ForestLight #3A7A5C`
  - `Secondary Palash Orange #E65100` — Live FAB, accent
  - `Surface Paper White #FFFCF5` — background/surface
  - `PaperDark #F5EFE0`, `SurfaceVariant #E8F0E9`
  - `Accent Offline Green #2E7D32` (`OfflineGreen`), `OfflineGreenLight #E8F5E9` — offline badge, budget green
  - `Amber #F9A825` (budget 60-85%), `ErrorRed #C62828` (>85%), `Outline #DDE5DB`, `OutlineVariant #C5CFC1`
- **Theme:** `Theme.kt:1` defines `LightScheme` (primary=Forest, secondary=Palash, background=PaperWhite, outline etc.) and `DarkScheme` (muted), `VachakTheme(darkTheme)` wraps all screens. `MaterialTheme` provides `colorScheme`, `typography`, `shapes`.

### Shapes
- **File:** `Theme.kt:7`
- `VachakShape = Shapes(small=8.dp, medium=16.dp, large=24.dp, extraLarge=28.dp)`
- Cards use `medium` (16dp), sheets `large` (24dp), pill badge `50.dp`. Spec: `Card(shape=Medium, elevation=2.dp)`.

### Icons
- **Spec:** Heroicons v2 (outline-stroke premium) via Compose Heroicons — plan called `io.github.alexzhirkevich:compose-heroicons:2.0.0`. **Actual:** Material Icons Extended `androidx.compose.material:material-icons-extended:1.6.8` (added in `android/app/build.gradle.kts:42`) + AutoMirrored for `MenuBook`. Functionally equivalent outline style, avoids extra dependency, compatible with Compose 1.6.8 / Kotlin 1.9.24. Migration to Heroicons is one-line swap.
- **Usage:** `Icons.Outlined.Home/Mic/Build/Settings/Description/Search/ChevronRight` and `Icons.AutoMirrored.Outlined.MenuBook` (fixed crash from `Icons.Outlined.MenuBook` null on 1.6.8).

### Dependencies (Pro implementation)
- **File:** `android/app/build.gradle.kts:42`
- Added: `ui-text-google-fonts:1.6.8`, `material-icons-extended:1.6.8`, `animation:1.6.8`, `navigation-compose:2.7.7`, `lifecycle-runtime-compose:2.8.6`, `coroutines-android:1.7.3`. Kept `material3:1.2.1`, `foundation:1.6.8`, `room-runtime:2.6.1`. Cupertino `0.1.0-alpha04` retained but not used in new UI (replaced by Material 3 cards).

---

## 2. Navigation — Adaptive + Animated

### Files
- `android/app/src/main/java/com/vachak/ui/navigation/NavDest.kt:1` — sealed class `NavDest(route,label,icon,selectedIcon)` with objects `Home(live), Live, Curriculum, Tools, Settings(System), ManagePacks`. `all = listOf(Home,Live,Curriculum,Tools,Settings)` (ManagePacks is extra, reachable only via Settings → Manage Packs).
- `android/app/src/main/java/com/vachak/ui/navigation/AdaptiveScaffold.kt:1` — `AdaptiveScaffold(current,onNavigate,isTablet,content)`.
- `android/app/src/main/java/com/vachak/ui/VachakApp.kt:1` — `BoxWithConstraints` → `isTablet = maxWidth > 840.dp`, holds `current` state + `pendingLesson`, wraps `VachakTheme`, calls `AdaptiveScaffold` + `when(current)` switch.

### Behavior
- **Phone:** `Scaffold(bottomBar={NavigationBar})` with `NavigationBarItem` per `NavDest.all` (icon `Icon(if(selected) selectedIcon else icon)`, label `Text(label)`). Spec: 5 items.
- **Tablet:** `Row { NavigationRail(72dp) + VerticalDivider + Box(weight1f) }` with `NavigationRailItem`.
- **Transition:** `AnimatedContent(targetState=current, transitionSpec=slideIntoContainer(Left,300)+fadeIn togetherWith slideOut+fadeOut)` — no standard jumps.
- **Current bug:** Bottom bar shows 4 items (Live, Curriculum, Tools, System) — **Home missing** in screenshots. Root cause: `Icons.Outlined.Home` initialization nuance or `NavDest` object init failure left `Home` null (fixed MenuBook, Home still missing). TODO: verify `Home` icon and `all` list building; currently `filterNotNull()` guard prevents crash but hides gap.
- **ManagePacks:** Not in bottom bar; `VachakApp.kt:56` handles `NavDest.ManagePacks -> ManagePacksScreen()` navigated from `SettingsScreen onManagePacks`.

---

## 3. Shared Components (`ui/components/VachakComponents.kt:1`, 222 lines)

| Component | File:Line | Props | Spec mapping |
|---|---|---|---|
| **OfflineBadge** | `VachakComponents.kt:27` | `Surface(shape=50, color=OfflineGreenLight) { Row(Icon CheckCircle, Text "Offline" labelSmall SemiBold) }` | TopAppBar right pill, `VachakColors.OfflineGreen` |
| **VachakSection** | `VachakComponents.kt:48` | `title, icon, collapsible, collapsed, onToggle, content:ColumnScope` — `Column { Row(title+icon, Toggle) , if(!collapsed) Card(medium, elevation1) }` | Replaces `CupertinoSection`; groups diagnostics (Connectivity, Budget etc.) |
| **FocusCard** | `VachakComponents.kt:86` | `title, subtitle, nipunLabel, onContinue` — `ElevatedCard(medium, 2dp) { NIPUN badge (Surface Forest 8dp) + titleLarge + bodyMedium + OutlinedButton PlayArrow }` | Home hero: single Current Lesson card |
| **BreathVisualizer** | `VachakComponents.kt:118` | `isListening:Boolean` — `rememberInfiniteTransition` scale 0.85→1.15 (1200ms Reverse) + alpha 0.25→0.08 + `Canvas` 160dp circles (PalashOrange pulse when listening, Forest when idle) | Live Mode visualizer (replaces status list) |
| **LessonCard** | `VachakComponents.kt:148` | `title, grade, subject, onClick` — `OutlinedCard(medium) { Row(Surface 48dp icon, Column titleSmall, Grade·Subject bodySmall, ChevronRight) }` icon mapping: Math→Calculate, Oral→RecordVoiceOver, default MenuBook | Curriculum Browser |
| **BudgetIndicator** | `VachakComponents.kt:184` | `progress:Float, label` — `Column { Row(label, percent), LinearProgressIndicator(progress, height8dp, clip50, color tier Green<60 Amber<85 Red>85, track surfaceVariant) }` | Diagnostics Resource Budget |
| **DualLangCard** | `VachakComponents.kt:206` | `label, text` — `Surface(medium, tonal2, surfaceVariant 0.6) { label upper 1.2sp + bodyLarge text or "—" }` | Live dual pane Hindi/Santali |

---

## 4. Pages — Page-by-Page Overhaul

### A. Home (Hero Dashboard) — `ui/screens/HomeScreen.kt:1` (140 lines)
- **Header:** `Row` — `Text("Vachak" titleLarge Bold Forest)` left, `OfflineBadge()` right.
- **Greeting:** `Column { Text("Good morning, Teacher." headlineSmall) + Text("Santali (Ol Chiki) • FLN • Offline" bodySmall) }`
- **Focus Card:** Loads `EngineProvider → ContentEngine.getLessons()` first lesson as `focusLesson` via `LaunchedEffect`. `FocusCard(title, subtitle=sourceTextHi, nipunLabel="Foundational Literacy", onContinue)` -> `onContinueLesson(lesson)` navigates to Curriculum (deep-link via `pendingLesson`). If null → `ElevatedCard` spinner.
- **Quick Actions:** `Row(weight1f) { Button(Forest, Icon Mic, "Live Translate") -> onNavigateLive, FilledTonalButton(Icon Description, "Worksheets") -> onNavigateTools }`
- **Recent Activity:** `VachakSection("Recent Activity", collapsible, collapsed, onToggle)` — shows `recentTitles.take(3)` via `ListItem` or empty `No recent lessons…`.
- **Hint:** `Surface(OfflineGreenLight) { Row("✓", "WiFi OFF ready • All inference local • Sequential pipeline") }`
- **Status:** Launched and screenshot shows header/greeting/actions/section but **hero card blank** (dot) + bottom nav missing Home + no recent lessons. Room DB not yet seeded on emulator (curriculum lessons not loaded).

### B. Live Mode — `ui/screens/LiveScreen.kt:1` (199 lines)
- **Top:** `Scaffold(topBar=TopAppBar("Live Translation"))`, `FloatingActionButton Large(PalashOrange / ErrorRed when listening) { Icon Mic/Stop or CircularProgress when translating }`
- **Visualizer:** `Box(180dp) { BreathVisualizer(isListening||isTranslating) }` + `Text` status.
- **Dual Pane:** `Row(weight1f) { DualLangCard("Hindi (ASR)", hindiText) + DualLangCard("Santali — Ol Chiki (MT)", santaliText) }` with `Surface tonalElevation` per spec.
- **Input:** `OutlinedTextField(hindiText)` + `Row { FilledTonalButton Translate, OutlinedButton VolumeUp Play Audio }`
- **Pipeline (sequential, RAM limit):** `runPipeline(simulatedHindi)` on `Dispatchers.IO` → `engine.translation.translate(hindi, LanguagePair("hi","mund"))` (MT ≤500ms simulated 180ms delay) → `engine.tts.synthesize(translated,"mund")` → `tryPlayPcm` via `AudioTrack(16000, MONO, PCM16, MODE_STATIC)` + `withContext(Main)` updates UI. Button toggles `isListening` → stop triggers pipeline. Latency `System.currentTimeMillis()-start` badge `✓ <3s` else `⚠ ≥3s` + breakdown `ASR ≤1s • MT ≤0.5s • TTS ≤1s`.
- **Screenshot gap:** Live tab was not captured (taps still on System). Needs retest after Home fix.

### C. Curriculum Browser — `ui/screens/CurriculumScreen.kt:1` (117 lines)
- **Header:** `Text(Curriculum headlineSmall) + bodySmall "FLN lessons • Precomputed Santali translations • Offline"`
- **Search:** `OutlinedTextField(query, onValueChange, placeholder Search lessons…, leadingIcon Search, shape small)`
- **Filter Tabs:** `Row { FilterChip All/G1/G2/G3/Refresh }` with `selectedGrade` state; `load()` filters via `ContentEngine.getLessons().filter grade` or `listLessons`.
- **List:** `LazyColumn { items(filtered) { LessonCard(title, grade, subject=Math/Oral/Language, onClick->onOpenLesson) } }` + empty `No lessons found.` card.
- **Status:** Screenshots show `No lessons found.` — same Room seed issue as Home.

### D. Tools — `ui/screens/ToolsScreen.kt:1` (161 lines)
- **Tabs:** `SecondaryTabRow { Tab Worksheets(Description) + Flashcards(Style) }` with `tab` state.
- **Worksheets (Pane):** Loads first lesson, `ElevatedCard` lesson detail (titleHi, textHi, translatedText), `LazyRow { AssistChip(templateType) }` from `ContentEngine.getWorksheets(lessonId)` or fallback trace/fill, `Button Generate Worksheet` → `engine.worksheet.generate(lessonId, template)` → `Worksheet(items[0].answerKey is assetPath)` shown in `Surface`. Shows `Generating…` spinner.
- **Flashcards (Pane):** `engine.flashcard.listDeck(lessonId)` → `cards`, `idx`, `flipped` state; `ElevatedCard(height220dp, large, onClick flip) { Hindi/Santali, concept, imageAsset }`, `Row { OutlinedButton Previous, Text "i/N", Button Next }`, `FilledTonalButton Show Hindi/Santali`. Prebuilt assets `flashcard/assets/*.png`.
- **Status:** Screenshot shows tabs but empty content below (lesson null) — same seed.

### E. System/Diagnostics (Pro) — `ui/screens/SettingsScreen.kt:1` (149 lines)
- **Layout:** `Column verticalScroll 16dp padding, headlineSmall "System" + bodySmall`
- **Sections via `VachakSection`:**
  - **Language Packs** (top, extra): Card with `Manage Packs` `Button(FileOpen)` → `NavDest.ManagePacks`.
  - **Connectivity:** `VachakSection(Wifi) { Row(CheckCircle 32dp, Offline Mode + No network calls, Surface OFFLINE ✓ pill) + ListItem Sync }`
  - **Resource Budget:** `BudgetIndicator(0.62 Storage 312/500)` (amber 62%), `0.48 RAM sequential`, `0.35 Models cached`, `Safety margin 38%` — colors green/amber/red tiers.
  - **Latency Budget:** `BenchmarkReport(asrMs,mtMs,ttsMs,totalMs)` from `engine.benchmark.run("hi","mund","नमस्ते")` → `✓ <3s` + `Sequential: ASR → MT → TTS`.
  - **Model Info:** `ListItem` rows (Hindi ASR 42MB, IndicTrans2 148MB int8, Santali TTS 36MB, VAD 1.2MB) + `TextButton Show SHA-256` → `Surface Mono` with sha strings.
  - **App Info:** `Surface(tone1) { Vachak 0.1.0 • SIH26042 • Jharkhand • No INTERNET permission • GPL-3.0 Piper }`
- **Screenshot:** Verified working (6:00 screenshot, System selected, all sections visible, progress bars correct).

### F. Manage Packs — `ui/ManagePacksScreen.kt:1` (174 lines, package `com.vachak.ui`)
- **Purpose:** Offline pack installer (no network) via `PackManager`/`PackInstaller` + `PackDatabase` Room.
- **UI:** `Scaffold(TopAppBar Manage Packs) { Column { headlineSmall, bodySmall, Card(storage used/Free, LinearProgress, packsDir), Button Install from file (OpenDocument SAF), status Surface Mono, Text Installed packs(N), if empty Surface No packs… else LazyColumn ElevatedCard(language vVersion, id Mono, size, installedAt, path, manifestSha, Row Set Active) }, divider, engines reload note, No INTERNET note }`
- **Accessed via:** `SettingsScreen onManagePacks -> VachakApp.kt:55 current=ManagePacks`.

---

## 5. App Shell & Entry

- **Files:** `ui/MainActivity.kt:1` (ComponentActivity) `setContent { VachakApp(EngineProvider.real(this)) }` — auth bypassed per plan. `ui/VachakApp.kt:1` (62 lines) holds `current` + `pendingLesson`, `VachakTheme`, `BoxWithConstraints`, `AdaptiveScaffold`.
- **Engine:** `engine/EngineProvider.kt:1` exposes `TranslationEngine, ASREngine, TTSEngine, CurriculumEngine, WorksheetEngine, FlashcardEngine, LanguagePackManager, SyncManager, BenchmarkRunner`. `real(context)` wires `IndicTrans2Adapter(context)`, `SherpaAsrAdapter(context)`, `SherpaTtsAdapter(context)`, `ContentEngine(context)`.
- **Build:** `android/app/build.gradle.kts:44` `compileSdk34 minSdk28 target34 ndk arm64-v8a+x86_64 (emulator), packaging pickFirsts libonnxruntime.so, compose 1.6.8 kotlin 1.5.14 Java17, dependencies above. APK `android/app/build/outputs/apk/debug/app-debug.apk` 489M (models bundled).

---

## 6. What Still Needs Build / Fix

| Area | Current | Needed |
|---|---|---|
| **Home tab** | Bottom bar 4/5 (Home missing, screenshot 6:00 Home content visible but tab not highlighted/present) | Fix `NavDest.Home` icon/init (filterNotNull already, but still missing); ensure `all` =5 and `AdaptiveScaffold` shows Home; highlight selected correctly. |
| **Hero card content** | Blank card with • (lesson title empty) | Seed `ContentEngine` Room DB: `AppDatabase.prepopulateFromAssets` should run on first launch; verify emulator assets `curriculum/lessons/sat_lessons.json` exists and is read via `SherpaAssets`/`prepopulate`. |
| **Curriculum list** | “No lessons found” | Same DB seed; ensure `ContentEngine.getLessons()` returns lessons after `ensurePrepopulated()` runBlocking. |
| **Tools content** | Empty | Depends on lesson load. |
| **Lexend Google Fonts** | Fallback `SansSerif` | Optional: add downloadable font cert `res/values/fonts_certs.xml` + `ui-text-google-fonts` provider, or bundle `res/font/lexend.ttf` for true offline. |
| **Heroicons** | Using Material Extended as proxy | Swap to `compose-heroicons:2.0.0` if premium stroke desired (one-line). |
| **Icons deprecation** | `MenuBook` deprecation warnings | Already migrated to `AutoMirrored`; fix remaining `MenuBook` warnings complete. |
| **Models size** | 489M APK (duplicated assets between `app/src/main/assets` and `content/ml` library_assets) | Deduplicate: keep only one source or enable `pack` system (Manage Packs) to externalize models. |

---

## 7. File Map (Single Source of Truth)

```
android/app/src/main/java/com/vachak/ui/
├── MainActivity.kt:1          # entry, bypass auth
├── VachakApp.kt:1             # theme + adaptive scaffold + nav state
├── theme/
│   ├── Color.kt:1
│   ├── Type.kt:1
│   └── Theme.kt:1
├── navigation/
│   ├── NavDest.kt:1
│   └── AdaptiveScaffold.kt:1
├── components/
│   └── VachakComponents.kt:1
├── screens/
│   ├── HomeScreen.kt:1
│   ├── LiveScreen.kt:1
│   ├── CurriculumScreen.kt:1
│   ├── ToolsScreen.kt:1
│   └── SettingsScreen.kt:1
├── ManagePacksScreen.kt:1     # at ui/ (import com.vachak.ui.ManagePacksScreen)
└── ml/adapter/, engine/       # adapters keep UI unchanged
```

**Screenshots (emulator-5554, 6:00):** Home (blank card), System (working with budgets), Curriculum (no lessons), Tools (empty) — captured `/tmp/vachak_screen*.png`.

---

## 8. How to Run / Verify (Offline Demo Acceptance)

```bash
cd android && ./gradlew assembleDebug  # JAVA_HOME=/tmp/jdk17
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.vachak/.ui.MainActivity
# Demo: app opens → lesson loads → WiFi OFF → translate lesson → play Santali audio →
# generate worksheet → flashcards → push-to-talk Hindi → show recognized text → show Santali → play audio → show <3s latency → diagnostics. No INTERNET permission.
```

**Log to check:** `adb logcat --pid=$(adb shell pidof com.vachak) | grep Vachak` (shows `Vachak-MT`, `Vachak-ASR`, `Vachak-VAD`).

---

*Generated 2026-08-29 — reflects built APK and emulator screenshots; update this file when Home tab / DB seed fixes land.*
