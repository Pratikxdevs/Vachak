
# Vachak — Settings Screen

## 1. Screen Identity

Settings is the personal account, app preferences, offline management,
and system information area.

It should feel:

- calm
- organized
- personal
- functional
- less visually busy than Home/Curriculum/Tools

Settings is NOT another dashboard.

Do not use:

    Good morning, Vaibhav

Do not add learning cards here.

The user comes here to configure or understand the application.

---

# 2. Core Information Architecture

The Settings screen should contain:

    Profile
    ─────────────────
    Account / Personal Information

    Learning Preferences
    ─────────────────
    Language
    Grade / Curriculum
    Translation Preferences

    Offline & Storage
    ─────────────────
    Language Packs
    Storage
    Offline Content

    App Preferences
    ─────────────────
    Notifications
    Appearance
    Audio

    Support
    ─────────────────
    Help
    About
    Diagnostics

    Account
    ─────────────────
    Sign Out

Keep these groups visually separated.

---

# 3. Header

Top:

    Settings

Optional supporting text:

    Manage your profile and app preferences.

Do not make the header excessively large.

Settings should feel more compact than Home.

---

# 4. Profile Header

At the top of the content:

    [ Avatar ]

    Vaibhav
    Teacher

    Edit Profile →

Use a clean horizontal profile surface.

Example:

    ┌────────────────────────────────────────┐
    │  [avatar]  Vaibhav                     │
    │            Teacher                     │
    │                                        │
    │            Edit Profile →              │
    └────────────────────────────────────────┘

The profile area should feel personal but not like a social-media profile.

---

# 5. Profile Editing

Edit Profile should allow:

    Name
    Profile photo
    Teacher / account information

Do not put sensitive configuration directly on the main Settings page.

Use a dedicated profile editor when needed.

---

# 6. Learning Preferences

Section:

    Learning Preferences

Rows:

    Language
    Grade / Curriculum
    Translation Preferences

Example:

    Language
    Hindi

    Grade / Curriculum
    Grade 1

    Translation Preferences
    Hindi → Santali (Ol Chiki)

Each row:

    icon
    title
    current value
    chevron

---

# 7. Language

Language settings control the application's UI language where supported.

Possible:

    Hindi
    English

If more languages are added later, use a selection screen.

Do not build a complicated language selector inside Settings.

---

# 8. Grade / Curriculum

Allow the teacher to select their default curriculum context.

Example:

    Grade 1

This preference can influence:

- Home recommendations
- Curriculum defaults
- Worksheet defaults
- Flashcard discovery

Do not modify historical progress merely because the default grade
changes.

---

# 9. Translation Preferences

Display:

    Hindi → Santali
    Ol Chiki

This is the application's primary translation context.

Allow language direction/settings to be changed only if the underlying
translation engine supports it.

Do not expose unsupported language pairs.

---

# 10. Offline & Storage

Section:

    Offline & Storage

Rows:

    Language Packs
    Storage
    Offline Content

This section is important because Vachak is offline-first.

---

# 11. Language Packs

Row:

    Language Packs
    Manage downloaded language packs
                                      >

Selecting it:

    Settings → Manage Packs

The actual pack installation UI belongs to Manage Packs.

Do not duplicate pack-management controls here.

---

# 12. Storage

Display a compact summary:

    Storage
    312 MB of 500 MB used
                                  >

Do not expose technical model details here.

Detailed storage/model diagnostics belong to Diagnostics.

---

# 13. Offline Content

Display:

    Offline Content
    Available for offline use

or:

    Offline Content
    3 packs available

This is informational.

If content needs downloading:

    Offline Content
    Some content requires download

Provide an appropriate action.

---

# 14. App Preferences

Section:

    App Preferences

Rows:

    Notifications
    Appearance
    Audio

Use standard settings-row behavior.

---

# 15. Notifications

Allow supported notification preferences.

Example:

    Learning reminders
    ON

    Worksheet generation
    OFF

Only expose preferences that the application actually implements.

Do not create switches for nonexistent notification systems.

---

# 16. Appearance

Appearance controls the visual mode.

Current Vachak design is primarily:

    Light

Possible future modes:

    System
    Light
    Dark

Do not introduce dark mode merely because the settings architecture
could support it.

If dark mode is not implemented, do not show it.

---

# 17. Audio

Audio preferences may include:

    Playback enabled
    Voice speed
    Auto-play translations

Only expose controls backed by actual application behavior.

Keep this section compact.

---

# 18. Support

Section:

    Support

Rows:

    Help
    Diagnostics
    About Vachak

---

# 19. Help

Help should provide:

- basic usage guidance
- common questions
- offline usage information
- translation usage
- worksheet/flashcard guidance

Keep this as a dedicated screen if content becomes substantial.

---

# 20. Diagnostics

Diagnostics is an advanced/system surface.

Selecting:

    Diagnostics →

opens the existing diagnostic experience.

Do NOT put:

- RAM usage
- model hashes
- latency benchmarks
- model sizes
- engine logs

directly into Settings.

The existing architecture explicitly keeps diagnostics as the sole
surface for model size, latency, RAM, and related system information.
:contentReference[oaicite:0]{index=0}

---

# 21. About

Display:

    Vachak

    Version 1.0.0

Optional:

    Offline-first learning assistant
    Built for classroom use

Include:

    Privacy
    Licenses
    Open-source acknowledgements

only if these are actually implemented.

---

# 22. Account

Section:

    Account

Row:

    Sign Out

Sign out should be visually separated from ordinary preferences.

Do not use a giant destructive red button.

Use a standard settings-row action.

If confirmation is required:

    Sign out?

    Are you sure you want to sign out?

    Cancel       Sign Out

---

# 23. Settings Row

Universal structure:

    ┌─────────────────────────────────────────────┐
    │  [icon]   Title                             │
    │           Current value / description    >  │
    └─────────────────────────────────────────────┘

Recommended:

    height: 64–72dp

Icon:

    24dp

Touch target:

    minimum 44dp

Use dividers sparingly.

---

# 24. Section Headers

Section headings should be compact.

Example:

    Learning Preferences

    ─────────────────────────

    Language                         Hindi       >

Do not place every section inside a giant floating card.

Settings should resemble a refined settings list.

---

# 25. Surface Strategy

Preferred:

    white background
    subtle lavender section surfaces
    rounded profile card
    grouped settings lists

Avoid:

    giant gradients
    excessive glassmorphism
    large illustrations
    oversized feature cards

Settings should be the calmest screen in the application.

---

# 26. Search

Settings generally does not need prominent global search.

If the number of settings becomes large:

    compact Search Settings

can be introduced.

Do not add search just because Home/Curriculum has it.

---

# 27. Offline Badge

The header may show:

    ● Offline ✓

Use the same OfflineBadge component used throughout the application.

Do not create a Settings-specific offline indicator.

---

# 28. Bottom Navigation

Persistent navigation:

    Home
    Live
    Learn
    Tools
    Settings

Selected:

    Settings

Selected item:

    soft lavender pill
    lavender icon
    selected label

Unselected items:

    muted gray

The navigation should remain identical to every other primary screen.

---

# 29. Navigation Structure

Main Settings:

    Settings
        ↓
        Profile
        Learning Preferences
        Offline & Storage
        App Preferences
        Support
        Account

Secondary screens:

    Profile
    Language
    Grade / Curriculum
    Translation Preferences
    Manage Packs
    Notifications
    Audio
    Help
    Diagnostics
    About

Do not create separate screens unless the setting requires actual
interaction/content.

---

# 30. Phone Layout

Single vertical scroll:

    Header

    Profile

    Learning Preferences

    Offline & Storage

    App Preferences

    Support

    Account

    Bottom Navigation

Use generous vertical spacing.

---

# 31. Tablet Layout

Use available width without creating unnecessary complexity.

Preferred:

    centered content column

or:

    left settings navigation
    right detail panel

Only introduce a split layout if there are enough settings to justify it.

For the current MVP, a centered content column is preferable.

---

# 32. Typography

Page title:

    38–42sp

Section heading:

    18–22sp

Setting title:

    17–19sp

Setting value:

    14–16sp

Supporting text:

    14–16sp

Profile name:

    24–28sp

Keep Lexend consistent with the rest of Vachak.

---

# 33. Color System

Use the existing universal Vachak palette.

Background:

    #FAF9FF

Surface:

    #FFFFFF

Soft Lavender:

    #F7F2FF

Lavender:

    #EEE5FF

Accent:

    #A984D6

Deep Lavender:

    #3E3157

Primary Text:

    #17151C

Secondary Text:

    #6F6A78

Primary CTA:

    #171717

Do not introduce a Settings-specific palette.

---

# 34. Accent Usage

Lavender should primarily indicate:

    selected state
    navigation state
    links
    active settings
    informational emphasis

Do not color every icon lavender.

A restrained monochrome icon treatment is preferred.

---

# 35. Destructive Actions

For destructive actions:

    Sign Out
    Delete local data
    Remove language pack

Use a subtle warning/destructive treatment.

Do not make the entire screen red.

Confirmation dialogs should be simple.

---

# 36. Loading State

Settings should rarely require loading.

For data-dependent sections:

    use lightweight skeleton rows

Do not show a full-screen spinner.

---

# 37. Error State

If profile/settings data fails:

    Couldn't load your settings

    [ Try Again ]

Keep navigation functional.

Do not crash the Settings screen because one preference failed.

---

# 38. Accessibility

All interactive rows:

    minimum 44dp touch target

Every icon-only button:

    content description

Text must remain readable against:

    white
    #FAF9FF
    lavender surfaces

Hindi and Ol Chiki text must use appropriate fallback fonts.

---

# 39. Architecture

SettingsScreen must not:

- access Room directly
- initialize engines
- perform network calls
- contain pack-installation logic
- contain benchmark logic
- contain model-management logic

It consumes application state and emits actions.

Conceptually:

    SettingsScreen
          ↓
       action
          ↓
    application layer
          ↓
        result
          ↓
        UI state

---

# 40. Existing System Functionality

The existing Settings/System implementation already contains:

- Language Packs / Manage Packs
- Connectivity / Offline Mode
- Storage/resource budget
- RAM budget
- latency benchmark
- model information
- SHA-256 display

These capabilities should remain available, but technical details should
stay inside the Diagnostics/System surface rather than cluttering the
main Settings redesign. :contentReference[oaicite:1]{index=1}

---

# 41. Diagnostics Boundary

Main Settings:

    "How do I configure Vachak?"

Diagnostics:

    "How is Vachak performing?"

This distinction is important.

Settings should remain approachable to a teacher.

Diagnostics can remain technical.

---

# 42. Reusable Components

Use:

    ScreenHeader
    OfflineBadge
    SettingsSection
    SettingsRow
    SettingsValueRow
    ProfileCard
    BottomNavigation
    ConfirmationDialog

Do not create duplicated versions for each section.

---

# 43. Component Structure

Conceptually:

    SettingsScreen
        ├── SettingsHeader
        ├── ProfileCard
        ├── LearningPreferencesSection
        ├── OfflineStorageSection
        ├── AppPreferencesSection
        ├── SupportSection
        └── AccountSection

Secondary screens handle actual editing.

---

# 44. No Code Slop

Do NOT:

- create a generic "UniversalSettingsEngine"
- create dozens of setting-specific composables
- introduce a settings repository solely for UI
- duplicate navigation logic
- duplicate OfflineBadge
- duplicate theme tokens
- add dependencies
- move business logic into composables

Prefer simple data-driven rows.

Example concept:

    SettingsRow(
        icon = ...,
        title = ...,
        value = ...,
        onClick = ...
    )

---

# 45. Navigation Behavior

Opening:

    Profile

must return to:

    Settings

Opening:

    Diagnostics

must return to:

    Settings

Opening:

    Manage Packs

must return to:

    Settings

Do not unexpectedly send the user to Home.

---

# 46. Persistence

Preference changes should persist through the existing application
settings mechanism.

The UI should reflect the persisted value after returning to Settings.

Do not maintain fake UI-only settings.

---

# 47. Visual Hierarchy

The strongest elements:

    Profile
    section headers

Medium:

    setting rows

Lowest:

    supporting metadata

Settings should never compete visually with:

    Home
    Live
    Curriculum

It is a utility surface.

---

# 48. Definition of Done

[ ] Settings header

[ ] Profile card

[ ] Learning Preferences

[ ] Offline & Storage

[ ] App Preferences

[ ] Support

[ ] Account

[ ] Language navigation

[ ] Grade navigation

[ ] Translation preference navigation

[ ] Manage Packs navigation

[ ] Diagnostics navigation

[ ] About navigation

[ ] Sign out flow

[ ] Offline status

[ ] Loading state

[ ] Error state

[ ] Confirmation dialogs

[ ] Bottom navigation

[ ] Phone layout verified

[ ] Tablet layout verified

[ ] Existing diagnostics functionality preserved

[ ] Existing pack-management functionality preserved

[ ] No duplicate design system

[ ] No unnecessary dependencies

[ ] No business logic inside UI

[ ] Visual QA completed

---

# 49. Final Principle

Settings should feel like:

    "My Vachak"

not:

    "Another Vachak dashboard."

The user should be able to answer three questions immediately:

    Who am I?
    What does Vachak currently use?
    What can I change?

Everything else should remain one tap away.
