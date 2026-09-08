# Vachak — Complete UI Rebuild Protocol

## READ THIS FIRST

This is a COMPLETE UI OVERHAUL.

Do not incrementally patch the existing UI.

Do not preserve poor UI architecture simply because it already exists.

Do not create a second UI system on top of the old one.

The goal is to rebuild the presentation layer cleanly while preserving
the application's actual functionality and offline-first architecture.

The final result should look and feel like ONE intentionally designed
application.

---

# 1. Primary Objective

Rebuild the Vachak UI from the ground up using the approved Vachak
Universal Design System.

The new UI should be:

- premium
- editorial
- calm
- spacious
- modern
- educational
- highly readable
- mobile-first
- tablet-friendly
- offline-first
- consistent

The design language is:

> Soft Editorial Minimalism

The visual language combines:

    editorial composition
    +
    soft lavender surfaces
    +
    white space
    +
    strong typography
    +
    black primary actions
    +
    rounded geometry
    +
    restrained accents
    +
    functional native-app interaction

---

# 2. NON-NEGOTIABLE RULE

Do NOT treat the current UI as the design reference.

Treat the current code as the FUNCTIONAL reference.

Treat the approved design documentation and visual references as the
VISUAL reference.

Current implementation may contain:

- obsolete layouts
- unnecessary wrappers
- duplicated components
- poor spacing
- inconsistent shapes
- inappropriate Material defaults
- temporary UI
- placeholder content
- unnecessary abstractions

These may be removed.

---

# 3. Architecture Boundary

The rebuild has TWO distinct layers.

## Preserve

Preserve the functional/domain layer unless a bug prevents the UI from
working.

Preserve:

- translation engines
- ASR
- TTS
- curriculum data
- flashcard data
- worksheet functionality
- Room persistence
- offline functionality
- language packs
- model loading
- audio playback
- existing engine interfaces
- real application data
- business logic

The UI must continue consuming the real application functionality.

---

## Rebuild

The presentation layer may be substantially rewritten.

Rebuild:

- screens
- composables
- layout hierarchy
- UI state presentation
- navigation presentation
- cards
- buttons
- inputs
- filters
- tabs
- bottom navigation
- navigation rail
- sheets
- dialogs
- loading states
- empty states
- error states
- typography presentation
- surface hierarchy
- spacing
- animations

---

# 4. DO NOT PATCH THE OLD UI

Avoid this pattern:

    ExistingScreen
        ↓
    add another Box
        ↓
    add padding
        ↓
    override Material colors
        ↓
    add another Card
        ↓
    special-case one screen
        ↓
    duplicate component
        ↓
    add another boolean

This is code slop.

Instead:

    understand screen
        ↓
    define visual hierarchy
        ↓
    compose from shared primitives
        ↓
    implement cleanly

If an existing composable is structurally bad,
replace it rather than endlessly modifying it.

---

# 5. No Over-Engineering

Do NOT create an enterprise-scale architecture for a small Android app.

Avoid unnecessary:

- factories
- abstract factories
- generic UI builders
- dependency injection frameworks
- presenter layers
- repository wrappers around repositories
- interface wrappers around simple composables
- configuration systems
- event buses
- global state managers
- excessive sealed class hierarchies
- unnecessary generic components

Use the simplest architecture that keeps the code:

- readable
- testable
- reusable
- maintainable

---

# 6. Compose Philosophy

Use idiomatic Jetpack Compose.

Prefer:

    @Composable
    fun Component(...)

with explicit parameters.

Prefer unidirectional state flow where useful.

Keep UI state close to the screen unless it genuinely needs to be shared.

Do not create a ViewModel merely because a screen exists.

Do not create a state abstraction for a boolean.

Do not create a manager for a list.

Do not create a repository for a static UI value.

---

# 7. Component Extraction Rule

Extract a component when:

1. It is reused.
2. It has a meaningful visual identity.
3. It has enough internal complexity to justify isolation.
4. It makes the parent screen significantly easier to understand.

Do NOT extract every five lines into a composable.

Bad:

    HomeTitle()
    HomeSubtitle()
    HomeIcon()
    HomeSpacer()
    HomeLabel()

Good:

    ScreenHeader()
    LessonCard()
    FlashcardCard()
    PrimaryButton()

---

# 8. Component Ownership

Components should have one clear responsibility.

Example:

    PrimaryButton
        → renders primary action

    LessonCard
        → renders lesson information

    FlashcardCard
        → renders flashcard

    ScreenHeader
        → renders screen-level heading/actions

Do not create components that secretly:

- navigate
- access databases
- launch coroutines
- mutate global state
- fetch network data
- modify unrelated screens

Presentation components should remain predictable.

---

# 9. Screen Responsibility

A screen should primarily:

    receive state
        ↓
    compose UI
        ↓
    emit user actions

Avoid putting business logic directly into large composables.

Example:

GOOD:

    onTranslate(text)

BAD:

    LiveScreen()
        ↓
    initializes ASR
        ↓
    loads database
        ↓
    translates
        ↓
    plays audio
        ↓
    changes navigation
        ↓
    updates settings

Keep responsibilities separated.

---

# 10. Universal Design System

All screens MUST use the universal Vachak design system.

Do not create page-specific:

- color palettes
- radius systems
- typography systems
- button systems
- shadows
- icon systems

unless explicitly justified.

The application must look like one product.

---

# 11. Color System

Use the approved lavender palette.

Core:

    Background       #FAF9FF
    Surface          #FFFFFF
    Soft Lavender    #F7F2FF
    Lavender 100    #EEE5FF
    Lavender 200    #E2D2F3
    Lavender 300    #D4BFF0
    Lavender 400    #C4A7E7
    Lavender 500    #A984D6
    Lavender 600    #8B6BB5
    Lavender 700    #70539A
    Deep Lavender   #3E3157

Text:

    Primary          #17151C
    Secondary        #6F6A78

Border:

    #E7E1EF

Primary dark action:

    #171717

White:

    #FFFFFF

---

# 12. Color Psychology

Lavender is the primary visual identity.

Use lavender to communicate:

- calm
- learning
- creativity
- trust
- softness
- intelligence

Do NOT eliminate semantic accent colors.

Semantic colors remain available for:

- success
- warning
- error
- completion
- system state

Do not turn every semantic state into purple.

Brand color and semantic color are different systems.

---

# 13. Typography

Lexend remains the primary typeface.

Typography should feel:

- large
- readable
- confident
- spacious

Avoid:

- tiny text
- excessive bold
- too many typography levels
- decorative fonts

Large headings should be visually important.

---

# 14. Spacing

Use a consistent spacing scale:

    4
    8
    12
    16
    20
    24
    32
    40
    48
    64

Default mobile horizontal margin:

    20–24dp

Prefer:

    24dp

Use whitespace aggressively.

Do not compress the UI simply to fit more information.

---

# 15. Shape System

Use:

    Small controls        10–14dp
    Inputs               16–20dp
    Standard cards       20–24dp
    Large cards          24–32dp
    Sheets               28–32dp
    Buttons              pill / 24–28dp
    Pills                fully rounded

Do not randomly introduce radius values.

---

# 16. Buttons

Primary buttons:

- strong contrast
- rounded/pill
- 48–56dp
- clear label
- generous horizontal padding

Primary actions generally use:

    #171717

with:

    #FFFFFF

text.

Brand-colored primary buttons may be used when appropriate.

Secondary buttons:

- outlined or tonal
- quieter than primary
- same overall geometry

Do not create multiple unrelated button styles.

---

# 17. Cards

Cards are soft information surfaces.

Typical:

    radius: 20–28dp
    padding: 16–24dp

Use cards for meaningful information groups.

Do NOT put every element inside a card.

Avoid:

    Card
      Card
        Card
          Card

Prefer flatter compositions.

---

# 18. Navigation

The navigation system must feel like part of the product.

Phone:

    floating/soft bottom navigation

Tablet:

    navigation rail

Navigation should remain visually subordinate to content.

Do not let navigation become a giant Material 3 control.

---

# 19. Responsive Design

The application must support:

- phones
- tablets

Do not simply scale phone UI to tablet.

Phone:

    single-column
    compact
    touch-focused

Tablet:

    editorial multi-column
    wider content
    more whitespace
    secondary content where appropriate

---

# 20. Visual Hierarchy

Every screen needs:

    ONE primary visual focus.

Examples:

    Home → current lesson

    Live → microphone / translation

    Curriculum → curriculum content

    Flashcards → current flashcard

    Worksheet → worksheet

    Settings → profile/settings structure

Do not make everything equally prominent.

---

# 21. Navigation vs Content

Content should always visually dominate navigation.

Navigation:

    quiet

Content:

    strong

Primary action:

    strongest interaction

---

# 22. Animation

Use restrained motion.

Typical UI transitions:

    150–300ms

Use animation for:

- navigation
- state changes
- expansion
- selection
- feedback
- meaningful spatial movement

Do not animate every component.

Avoid decorative animation that increases CPU/GPU load.

---

# 23. Performance

This application is offline-first and intended for constrained Android
hardware.

Do NOT introduce UI effects that meaningfully increase resource usage.

Avoid unnecessary:

- blur
- large shadows
- continuous particle effects
- complex shaders
- video backgrounds
- huge images
- continuous recomposition
- expensive animations

Visual quality must not compromise:

    ASR
    MT
    TTS
    audio playback
    memory budget

---

# 24. State Design

Every screen should explicitly account for:

    Loading
    Empty
    Ready
    Error

where applicable.

Do not leave:

    blank screen
    empty card
    unexplained spinner

as a state.

---

# 25. Real Data

Do not replace real data with fake static content during the redesign.

Use existing application data.

If the underlying data source is currently empty or broken:

    handle the empty state visually

Do not hide the problem by hardcoding fake production data.

Temporary preview data may only be used where explicitly marked and
must not become the final implementation.

---

# 26. Existing Functionality

Do not break working functionality during UI redesign.

Before modifying a screen:

1. Identify its inputs.
2. Identify its outputs.
3. Identify its callbacks.
4. Identify its engine dependencies.
5. Identify its persistent state.
6. Identify its navigation behavior.
7. Identify its loading/error states.

Then redesign the presentation layer.

---

# 27. Navigation Safety

Do not casually rewrite navigation logic.

First understand:

    current route
    destination
    arguments
    back behavior
    deep links
    tablet behavior

Then simplify only where safe.

Do not introduce navigation bugs while redesigning visual components.

---

# 28. File Organization

Prefer a simple structure.

Example:

    ui/
      theme/
        Color.kt
        Type.kt
        Shape.kt
        Theme.kt

      components/
        AppButton.kt
        AppCard.kt
        AppPill.kt
        AppTextField.kt
        ScreenHeader.kt
        OfflineBadge.kt
        BottomNavigation.kt
        NavigationRail.kt

      screens/
        HomeScreen.kt
        LiveScreen.kt
        CurriculumScreen.kt
        ToolsScreen.kt
        SettingsScreen.kt

      navigation/
        NavDest.kt
        VachakApp.kt

Do not create dozens of micro-files without reason.

---

# 29. Theme Ownership

Theme values belong in the theme layer.

Do not scatter:

    Color(...)
    Dp(...)
    TextStyle(...)
    RoundedCornerShape(...)

throughout screens when the value is part of the design system.

Screens should consume theme/design tokens.

---

# 30. Avoid Magic Numbers

Do not fill screens with arbitrary values such as:

    padding(17.dp)
    padding(23.dp)
    height(53.dp)
    offset(11.dp)

unless there is a clear visual reason.

Use the established design scale.

---

# 31. Avoid Boolean Explosion

Do not create:

    isLoading
    isListening
    isProcessing
    isPlaying
    isError
    isEmpty
    isSomethingElse

when a proper UI state model is clearly simpler.

However, do NOT create an enormous abstract state machine for trivial
screens either.

Use the simplest state representation that accurately models the screen.

---

# 32. Avoid Giant Composables

A screen composable should be readable.

If a screen becomes hundreds of lines of deeply nested layout code,
identify meaningful visual sections and extract them.

But do not split every trivial element into a separate function.

Target:

> readable screen-level composition.

---

# 33. Avoid Duplicate UI

Before creating a new:

- button
- card
- pill
- header
- list item
- badge

check whether an existing universal component can be reused.

If it cannot, determine whether the existing component is wrong.

Prefer improving the shared component over duplicating it.

---

# 34. Avoid Premature Generalization

Do not create:

    GenericCard<T>
    GenericSection<T>
    UniversalRenderer<T>
    DynamicComponentFactory<T>

just because two screens currently look similar.

Generalize only when the visual/behavioral pattern is genuinely shared.

---

# 35. Preview Strategy

Every major reusable component should have a Compose Preview where
practical.

Important previews:

- primary button
- secondary button
- card
- pill
- lesson card
- flashcard
- language card
- navigation
- empty state
- error state
- loading state

Previews should use representative content.

---

# 36. Visual QA

Do not consider a screen complete because it compiles.

After implementing each screen:

1. Build.
2. Run.
3. Capture screenshot.
4. Compare against the intended design.
5. Check spacing.
6. Check typography.
7. Check clipping.
8. Check touch targets.
9. Check dark/light surfaces.
10. Check tablet layout.
11. Check loading/empty/error states.

Then iterate.

---

# 37. Screenshot-Driven Development

For major screens, implementation should follow:

    Design
      ↓
    Build
      ↓
    Screenshot
      ↓
    Compare
      ↓
    Fix
      ↓
    Screenshot again

Do not attempt to perfectly design everything in code without visual
verification.

---

# 38. Design References

Reference images establish:

- composition language
- spacing
- visual hierarchy
- surface treatment
- button language
- typography scale
- card proportions
- navigation treatment
- color relationships

They do NOT establish:

- exact component names
- exact layouts
- exact copy
- exact illustrations
- exact dimensions

Do not copy another product literally.

---

# 39. Home-Specific Rule

Home should be the most editorial screen.

It should prioritize:

    greeting
    ↓
    current/next lesson
    ↓
    quick actions
    ↓
    recent activity

The greeting should be:

    Good morning,
    VAIBHAV

with the name as the stronger typographic element.

Do not repeat the greeting across other screens.

---

# 40. Live-Specific Rule

Live should be a focused interaction workspace.

Primary hierarchy:

    microphone
        ↓
    listening/transcription
        ↓
    translation
        ↓
    audio

It should feel closer to a conversational interface than a technical
dashboard.

Conversation/history should be persisted where the product requires it.

---

# 41. Curriculum-Specific Rule

Curriculum should behave like a learning library.

Use:

- filters
- lesson surfaces
- grade grouping
- flashcard discovery
- progress
- recently accessed content

Do not turn it into an enterprise table.

---

# 42. Flashcard-Specific Rule

Flashcards should feel tactile and focused.

The current card should dominate.

Support:

- next/previous
- flip
- progress
- audio where available
- shuffle
- review
- completion

Do not surround the card with unnecessary controls.

---

# 43. Tools-Specific Rule

Tools should prioritize:

- discovery
- creation
- preview
- execution
- result

Avoid making tools look like developer utilities.

They are teacher-facing.

---

# 44. Settings-Specific Rule

Settings should be structured and calm.

Use:

    profile
    ↓
    account/preferences
    ↓
    language/content
    ↓
    offline packs
    ↓
    system
    ↓
    help/about

Use lightweight list rows.

Do not put every setting inside a giant card.

---

# 45. Technical Diagnostics

Technical information should be visually separated from teacher-facing
experiences.

Things like:

- model size
- latency
- RAM
- storage
- engine status
- model versions

belong in System/Diagnostics.

Do not expose them prominently on Home or Live.

---

# 46. Offline-First Principle

The interface must communicate that offline operation is normal.

Use:

    Offline
    Ready
    Downloaded
    Available locally

rather than alarm-style:

    No Internet
    Connection Failed

unless an actual network dependency exists.

---

# 47. Accessibility

Do not sacrifice accessibility for visual similarity.

Maintain:

- readable typography
- adequate contrast
- 44dp+ touch targets
- clear focus states
- readable Hindi
- readable Ol Chiki
- text + visual state indicators
- scalable text

---

# 48. Cleanup Requirement

As part of the overhaul, identify and remove:

- dead composables
- unused imports
- obsolete UI components
- duplicate components
- unreachable UI code
- temporary debug UI
- unused theme tokens
- abandoned design experiments
- commented-out UI blocks
- unnecessary dependencies introduced only for old UI

Do not blindly delete anything from the engine/domain layer.

---

# 49. Dependency Discipline

Do not add a dependency simply to implement a visual effect that Compose
already supports.

Before adding a dependency ask:

1. Is it necessary?
2. Is the functionality unavailable in the current stack?
3. Does it justify APK size and maintenance cost?
4. Does it work offline?
5. Does it work with the existing Compose/Kotlin versions?

If the answer is no, do not add it.

---

# 50. No Fake Architecture

Do not create abstractions whose only purpose is to make the code look
"professional."

Professional code is:

- simple
- explicit
- readable
- boring where appropriate
- predictable

Not:

- excessively abstract
- excessively generic
- excessively layered

---

# 51. Implementation Order

Do NOT redesign every screen simultaneously.

Use this sequence:

    Phase 1
    Audit current UI

    Phase 2
    Establish theme/tokens

    Phase 3
    Build universal components

    Phase 4
    Rebuild navigation shell

    Phase 5
    Rebuild Home

    Phase 6
    Rebuild Live

    Phase 7
    Rebuild Curriculum

    Phase 8
    Rebuild Flashcards

    Phase 9
    Rebuild Tools

    Phase 10
    Rebuild Settings/System

    Phase 11
    Tablet optimization

    Phase 12
    Global visual QA

---

# 52. Before Coding

Before modifying code, inspect:

- current source tree
- existing screens
- theme
- navigation
- reusable components
- state models
- engine interfaces
- data models
- persistence
- dependencies

Then produce a short internal implementation plan.

Do NOT immediately start rewriting files.

---

# 53. First Deliverable

Before writing the new UI, identify:

### KEEP

Existing functionality/components that are structurally sound.

### REBUILD

UI components/screens that should be replaced.

### DELETE

Dead or obsolete UI code.

### REFACTOR

Components that are useful but poorly structured.

### DO NOT TOUCH

Engine/domain code that does not need UI changes.

This audit should be concise.

Do not produce a 100-page architecture document.

---

# 54. Definition of Done

The UI overhaul is complete only when:

- all intended screens use one visual system
- no old UI styling remains accidentally
- no duplicate component systems exist
- navigation is consistent
- phone layouts work
- tablet layouts work
- loading states work
- empty states work
- error states work
- real data is displayed
- offline operation remains intact
- translation remains intact
- ASR remains intact
- TTS remains intact
- flashcards remain functional
- worksheets remain functional
- no unnecessary dependencies were introduced
- dead UI code has been removed
- code remains readable

---

# 55. Most Important Rule

DO NOT optimize for the number of files changed.

Optimize for:

> A clean, coherent UI architecture with the minimum amount of code
> necessary to implement the intended product.

If rebuilding a component requires deleting 200 lines and replacing them
with 80 clean lines, do it.

If an existing component is already correct, keep it.

If two components should become one shared primitive, consolidate them.

If abstraction does not reduce complexity, do not introduce it.

---

# 56. Final Principle

The final Vachak codebase should feel like it was designed and built
intentionally from the beginning.

Not:

    old UI
    +
    new UI
    +
    patches
    +
    overrides
    +
    special cases

Instead:

    Design System
          ↓
    Shared Components
          ↓
    Screen Composition
          ↓
    Real Application State
          ↓
    Clean Product
