# Vachak — Complete UI / Product Screen Guide

## 1. Purpose

This document defines the complete UI structure of Vachak.

It answers:

- What screens exist?
- What is each screen responsible for?
- Which screens are primary?
- Which screens are secondary?
- How do users move between them?
- Which experiences deserve their own screen?
- Which experiences should remain within another screen?

This document must be finalized BEFORE individual screen design begins.

Individual files such as:

    home.md
    live.md
    curriculum.md
    flashcards.md
    worksheets.md
    tools.md
    settings.md

must follow this guide.

---

# 2. Product Structure

Vachak has five primary destinations:

    HOME
    LIVE
    LEARN
    TOOLS
    SETTINGS

These are the persistent application-level destinations.

The bottom navigation on phones exposes these five destinations.

Tablet layouts may use a navigation rail instead.

---

# 3. Primary Navigation

## Home

Purpose:

> Personal starting point.

Contains:

- teacher identity
- greeting
- current/next learning activity
- quick actions
- recent lessons
- lightweight progress

Home answers:

    "What should I do next?"

Home is NOT:

- analytics
- curriculum browser
- diagnostics
- settings

---

## Live

Purpose:

> Real-time Hindi ↔ Santali interaction.

Primary experience:

    speak
      ↓
    recognize
      ↓
    translate
      ↓
    read
      ↓
    listen

Live is conversational.

It should feel like a communication tool rather than an AI dashboard.

Live also maintains conversation/history where appropriate.

---

## Learn

Purpose:

> Browse and study the curriculum.

Learn contains:

- curriculum
- subjects
- grades
- lessons
- lesson details
- learning progress
- flashcard entry points where relevant

Learn answers:

    "What can I teach/study?"

---

## Tools

Purpose:

> Teacher utilities.

Tools contains:

- Worksheets
- Flashcards
- other teacher-facing utilities that genuinely belong here

Tools answers:

    "What can I create/use to support teaching?"

---

## Settings

Purpose:

> Application configuration and system information.

Settings contains:

- profile
- language preferences
- content preferences
- offline content
- system configuration
- diagnostics
- help
- about

Settings answers:

    "How do I configure Vachak?"

---

# 4. Navigation Model

Phone:

    ┌─────────────────────────────────────────────┐
    │                                             │
    │                  SCREEN                     │
    │                                             │
    │                                             │
    │                                             │
    │                                             │
    │  Home   Live   Learn   Tools   Settings     │
    └─────────────────────────────────────────────┘

Tablet:

    ┌──────┬──────────────────────────────────────┐
    │      │                                      │
    │ Home │                                      │
    │ Live │              SCREEN                 │
    │ Learn│                                      │
    │ Tools│                                      │
    │ Set.  │                                      │
    │      │                                      │
    └──────┴──────────────────────────────────────┘

The exact navigation visual treatment is defined by the universal
design system.

---

# 5. Screen Taxonomy

There are three types of screens.

## Level 1 — Primary Destinations

These appear in persistent navigation.

    Home
    Live
    Learn
    Tools
    Settings

---

## Level 2 — Feature Screens

These are opened from a primary destination.

    Lesson Detail
    Flashcards
    Worksheets
    Diagnostics
    Manage Packs
    Search

They do not need permanent bottom-navigation slots.

---

## Level 3 — Temporary Surfaces

These are contextual.

    Bottom sheets
    Dialogs
    Confirmation surfaces
    Pickers
    Menus
    Audio controls
    Filter surfaces

They should not become independent navigation destinations unless
navigation history or complexity genuinely requires it.

---

# 6. HOME

Route:

    Home

Primary navigation destination.

## Purpose

Teacher's daily starting point.

## Main content

    Header
    Greeting
    Category filters
    Continue Learning
    Secondary lesson
    Quick Actions
    Recent Lessons

## Important behavior

Home should surface existing work.

It should NOT attempt to expose every application feature.

## Home-specific identity

The greeting belongs ONLY here.

Example:

    Good morning,

    Vaibhav 👋

Other screens must NOT repeat this greeting.

---

# 7. LIVE

Route:

    Live

Primary navigation destination.

## Purpose

Real-time conversational translation.

## Main experience

    Microphone
    ↓
    transcription
    ↓
    translation
    ↓
    playback

## Additional functionality

Live maintains translation history.

The experience should support a conversation-like history rather than
treating every translation as an isolated operation.

Conceptually:

    Previous conversation
          ↓
    Current conversation
          ↓
    Input / microphone

## Live should contain

- conversation history
- Hindi messages
- Santali translations
- playback controls
- microphone interaction
- manual text input
- translation state

## Live should NOT contain

- model diagnostics
- RAM information
- model versions
- system settings
- curriculum browsing

Those belong elsewhere.

---

# 8. LIVE CONVERSATION DETAIL

Do NOT automatically create a separate permanent navigation destination
for every conversation.

A conversation is a state/history within Live.

Potential structure:

    Live
      ↓
    Current conversation

If persistent conversation management becomes necessary:

    Live
      ↓
    History
      ↓
    Conversation

History should only become a separate screen if the amount of stored
history justifies it.

---

# 9. LEARN

Route:

    Learn

Primary navigation destination.

## Purpose

Curriculum discovery.

The screen should feel like a learning library.

Primary hierarchy:

    Grade
      ↓
    Subject
      ↓
    Lessons
      ↓
    Lesson detail

---

# 10. CURRICULUM SCREEN

The Curriculum screen is the main Learn landing screen.

It contains:

- grade selection
- subject selection
- category/filter controls
- lesson collection
- progress
- recently accessed content

Possible structure:

    Learn

    Grade

    [ Grade 1 ] [ Grade 2 ] ...

    Subjects

    [ Language ]
    [ Mathematics ]
    [ EVS ]
    [ Stories ]

    Lessons

    lesson
    lesson
    lesson

Do not make the curriculum feel like a spreadsheet.

---

# 11. LESSON DETAIL

Route conceptually:

    Learn → Lesson

Purpose:

> Provide the actual learning material.

Lesson detail may contain:

- lesson title
- description
- learning objectives
- lesson content
- audio
- visual material
- activities
- flashcards
- worksheet entry
- progress
- completion

The lesson screen should be focused on teaching/learning.

It should not become another dashboard.

---

# 12. FLASHCARDS

Flashcards are a dedicated learning experience.

They may be entered from:

    Learn → Lesson → Flashcards

or:

    Tools → Flashcards

depending on context.

## Flashcard screen purpose

Focused review.

Primary content:

    ONE FLASHCARD

Secondary:

    progress
    navigation
    audio
    flip/reveal
    shuffle/review

The current flashcard should dominate the screen.

---

# 13. FLASHCARD SESSION

A session should feel different from the curriculum browser.

Curriculum:

    discover

Flashcards:

    practice

Therefore the Flashcard UI should remove unnecessary navigation and
focus attention on the current card.

---

# 14. FLASHCARD DECK / SELECTION

If multiple decks exist, use a lightweight deck-selection screen or
surface.

Possible structure:

    Flashcards

    Continue review

    Decks

    Letters
    Numbers
    Animals
    Family
    ...

Do not create this screen if the current product only has one meaningful
deck.

---

# 15. TOOLS

Route:

    Tools

Primary navigation destination.

Purpose:

> Teacher productivity.

Tools should be a collection of useful teacher workflows.

Current core tools:

    Worksheets
    Flashcards

Additional tools should only be added when there is an actual product
requirement.

Do not create a generic "AI Tools" dashboard full of decorative cards.

---

# 16. WORKSHEETS

Dedicated feature experience.

Flow:

    Tools
      ↓
    Worksheets
      ↓
    Choose lesson/topic
      ↓
    Choose template/options
      ↓
    Generate
      ↓
    Preview
      ↓
    Use / Save / Export

The worksheet generation screen should be task-focused.

---

# 17. WORKSHEET PREVIEW

Preview should be visually close to the final worksheet.

It should support:

- page preview
- content inspection
- regenerate where applicable
- save
- export/share where supported

Do not make preview look like a settings screen.

---

# 18. SETTINGS

Route:

    Settings

Primary navigation destination.

Settings is a structured configuration screen.

Top-level structure:

    Profile

    Preferences

    Language

    Content

    Offline Content

    System

    Help

    About

Do not put every setting into one enormous list.

Group settings logically.

---

# 19. PROFILE

Profile is a subsection of Settings.

Contains:

- teacher identity
- name
- avatar
- relevant profile information

Profile should remain simple.

Do not turn it into a social-media profile.

---

# 20. OFFLINE CONTENT / MANAGE PACKS

This is a system/content-management experience.

Purpose:

> Manage locally available learning resources.

Contains:

- installed packs
- available packs
- pack size
- installation state
- update state
- remove/download actions where supported

This screen should communicate storage/download state clearly.

It should NOT dominate the normal teacher workflow.

---

# 21. DIAGNOSTICS

Diagnostics belongs under Settings/System.

Purpose:

> Technical verification.

Possible information:

- ASR status
- MT status
- TTS status
- model information
- latency
- memory
- storage
- engine state

This screen is for:

- testing
- troubleshooting
- development
- technical support

Do NOT expose diagnostic information on Home.

Do NOT expose it prominently in Live.

---

# 22. SEARCH

Search is a contextual utility.

It does not necessarily need a permanent navigation destination.

Search can be launched from:

    Home
    Learn
    Live history
    Tools

depending on actual requirements.

Search should search only meaningful Vachak content.

Potential scope:

    lessons
    curriculum
    flashcards
    worksheets
    conversations

Do not build global search until there is enough content to justify it.

---

# 23. NOTIFICATIONS

Notifications should NOT become a permanent navigation destination unless
the product develops a genuine notification system.

If notifications are limited to:

- pack installation
- generation completion
- system status

use a contextual notification surface.

Avoid building a social notification center.

---

# 24. ONBOARDING

Onboarding is outside the normal application navigation.

Flow:

    Launch
      ↓
    Welcome
      ↓
    Language/content setup
      ↓
    Teacher setup
      ↓
    Offline pack setup
      ↓
    Home

Onboarding should only appear when required.

Returning users should go directly to Home.

---

# 25. FIRST-RUN CONTENT SETUP

If offline content is not installed:

    Home
      ↓
    Content unavailable
      ↓
    Install / select content
      ↓
    Home

Do not make users repeatedly configure content.

Once installed, it should behave as normal application state.

---

# 26. MODALS / SHEETS

Use sheets for:

- filter selection
- quick configuration
- contextual actions
- confirmation
- compact selection

Use full screens for:

- complex workflows
- substantial content
- learning sessions
- editing
- diagnostics

Rule:

> If the user needs to concentrate on it, give it a screen.

---

# 27. Back Navigation

General rule:

    Primary destination
        ↓
    Feature screen
        ↓
    Detail/session

Back should return to the previous meaningful context.

Examples:

    Home → Lesson → Back → Home

    Learn → Lesson → Back → Learn

    Tools → Worksheet → Back → Tools

Do not dump the user at Home after every action.

---

# 28. Context Preservation

When navigating into a feature:

    preserve selected lesson
    preserve selected grade
    preserve selected subject
    preserve relevant session state

Do not force users to repeatedly select the same context.

Example:

    Learn
      ↓
    Grade 1
      ↓
    Language
      ↓
    Letters & Sounds
      ↓
    Flashcards

Flashcards should know which lesson/deck was selected.

---

# 29. Persistent Navigation Rule

Only these are permanent navigation destinations:

    Home
    Live
    Learn
    Tools
    Settings

Everything else is contextual.

This prevents navigation from becoming overcrowded.

---

# 30. Screen Naming

Use product language rather than implementation language.

GOOD:

    Live
    Learn
    Worksheets
    Flashcards
    Settings
    Diagnostics

BAD:

    TranslationEngineScreen
    MLScreen
    DatabaseScreen
    GeneratorScreen
    ASRScreen

Implementation names belong in code.

User-facing names belong in the product.

---

# 31. Information Architecture

The complete structure:

    Vachak
    │
    ├── Home
    │   ├── Continue Learning
    │   ├── Quick Actions
    │   └── Recent Lessons
    │
    ├── Live
    │   ├── Current Conversation
    │   └── Conversation History
    │
    ├── Learn
    │   ├── Curriculum
    │   ├── Lesson
    │   └── Flashcards
    │
    ├── Tools
    │   ├── Worksheets
    │   │   └── Worksheet Preview
    │   └── Flashcards
    │       └── Flashcard Session
    │
    └── Settings
        ├── Profile
        ├── Preferences
        ├── Language
        ├── Offline Content
        ├── Diagnostics
        ├── Help
        └── About

---

# 32. Avoid Duplicate Destinations

Flashcards are available contextually from Learn and potentially Tools.

This does NOT mean two separate Flashcard implementations.

There must be ONE Flashcard experience.

The entry context can determine:

- selected deck
- selected lesson
- return destination

but the underlying UI should remain shared.

Same principle applies to worksheets.

---

# 33. Screen Priority

Implementation/design priority:

## Tier 1 — Core Demo / Daily Use

    Home
    Live
    Learn / Curriculum
    Lesson
    Flashcards

## Tier 2 — Teacher Productivity

    Tools
    Worksheets
    Worksheet Preview

## Tier 3 — System

    Settings
    Offline Content
    Diagnostics
    Help
    About

## Tier 4 — Conditional

    Onboarding
    Search
    Conversation History
    Notifications

Only implement Tier 4 when the product actually requires the experience.

---

# 34. Design Priority

Visual effort should follow product importance.

Highest effort:

    Home
    Live
    Curriculum
    Lesson
    Flashcards

Moderate:

    Worksheets
    Tools

Restrained:

    Settings
    Diagnostics
    About

Do not spend more visual complexity on Settings than on Live.

---

# 35. Page Design Files

Once this guide is approved, create:

    design/
        UI_GUIDE.md
        UNIVERSAL_DESIGN.md

        pages/
            HOME.md
            LIVE.md
            LEARN.md
            LESSON.md
            FLASHCARDS.md
            TOOLS.md
            WORKSHEETS.md
            SETTINGS.md
            DIAGNOSTICS.md
            MANAGE_PACKS.md

Not every page needs to be implemented immediately.

The files define the intended product structure.

---

# 36. Implementation Order

Do NOT implement all screens simultaneously.

Use:

    1. Universal Design System
    2. Navigation Shell
    3. Home
    4. Live
    5. Learn / Curriculum
    6. Lesson
    7. Flashcards
    8. Tools
    9. Worksheets
    10. Settings
    11. Offline Content
    12. Diagnostics
    13. Tablet optimization
    14. Global QA

---

# 37. Product Rule

Every screen must have one clear purpose.

If a screen cannot be described in one sentence,
it probably contains too much.

Examples:

    Home:
    "Help the teacher decide what to do next."

    Live:
    "Translate Hindi and Santali conversationally."

    Learn:
    "Find and start lessons."

    Lesson:
    "Teach/study one lesson."

    Flashcards:
    "Practice one deck."

    Worksheets:
    "Create and use a worksheet."

    Settings:
    "Configure Vachak."

    Diagnostics:
    "Verify the technical system."

---

# 38. Final Navigation Principle

The user should always understand:

    Where am I?
    What can I do here?
    What happens if I press this?
    Where will Back take me?

The UI should never require the user to understand the underlying
software architecture.

---

# 39. Final Product Principle

Vachak should feel like:

    one coherent educational product

rather than:

    Home
    + translator
    + curriculum app
    + worksheet generator
    + flashcard app
    + settings utility

Every experience must share the same:

    visual language
    navigation language
    interaction language
    typography
    component system
    spacing
    tone

while still giving each major workflow its own personality.
