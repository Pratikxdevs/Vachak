# Vachak Home — Screen Specification

## Status

REBUILD THIS SCREEN FROM SCRATCH.

This document defines the visual and interaction specification for the
Home screen.

The attached reference image is the PRIMARY visual reference.

Do not copy the reference literally.

Use it to reproduce the same visual language, hierarchy, spacing,
composition, density, and interaction philosophy.

---

# 1. Screen Purpose

Home is the teacher's personal learning dashboard.

It should answer these questions immediately:

1. Who am I?
2. What should I continue learning?
3. What else can I do quickly?
4. What have I recently learned?

The screen should feel like a calm educational workspace, not an
administrative dashboard.

---

# 2. Overall Visual Direction

Use:

Soft Editorial Minimalism.

The screen should feel:

- premium
- spacious
- friendly
- intelligent
- calm
- educational
- modern

Avoid:

- dense dashboards
- excessive borders
- excessive cards
- Material 3 default appearance
- excessive shadows
- gradients everywhere
- decorative UI with no purpose
- tiny typography
- excessive icons

The reference has a strong editorial composition:

    Profile / utility controls
              ↓
        large greeting
              ↓
       category filters
              ↓
       primary lesson
              ↓
       secondary lesson
              ↓
        quick actions
              ↓
        recent lessons
              ↓
       floating navigation

Maintain this hierarchy.

---

# 3. Background

Use the global Vachak background.

    #FAF9FF

The background should remain mostly light and uncluttered.

Use extremely subtle lavender decorative geometry only where it helps
the visual identity.

Decorative elements MUST:

- remain behind content
- never interfere with readability
- never consume interaction space
- never use expensive blur/shader effects

Do not reproduce the reference background decoration pixel-for-pixel.

---

# 4. Top Header

At the top:

    Profile avatar                         Search   Notifications

Profile avatar:

- circular
- approximately 56–72dp
- aligned to the main content margin
- visually prominent but not oversized

Right side:

    Search
    Notifications

Use simple outline icons.

Notification may contain a small status dot.

Do not put text labels beside these icons.

Touch targets must remain >=44dp.

---

# 5. Greeting

This is the primary typographic moment of the page.

Use:

    Good morning,

    Vaibhav 👋

Important:

"Good morning," and "Vaibhav" are NOT the same typographic level.

Example hierarchy:

    Good morning,
        ↓
    VAIBHAV 👋

"Vaibhav" should be dramatically larger and heavier.

The name is the visual anchor of the page.

Use Lexend.

Approximate intent:

    Greeting:
        28–32sp
        medium

    Name:
        56–72sp
        bold
        very tight visual grouping

The exact size may adapt to device width.

Do NOT make the greeting overly decorative.

---

# 6. Greeting Subtitle

Under the name:

    Let's continue your learning journey.

Use:

- secondary text
- approximately 18–22sp
- comfortable line height

Leave generous whitespace after the greeting.

---

# 7. Category Navigation

Immediately below the greeting introduce horizontal learning filters.

Structure:

    [ Filter ] [ All ] [ Language ] [ Mathematics ] [ EVS ] [ Stories ]

The filter control is icon-based.

Categories are horizontally scrollable.

Selected:

    All

Selected appearance:

- soft lavender background
- lavender text
- pill shape

Unselected:

- white surface
- subtle outline
- dark/secondary text

Do NOT use Material 3's default AssistChip appearance.

These should feel like custom editorial filter pills.

Approximate:

    height: 56–60dp
    radius: fully rounded
    horizontal padding: 22–28dp

Horizontal spacing:

    12–16dp

---

# 8. Continue Learning — PRIMARY CONTENT

This is the most important content block on Home.

Use a large feature card.

Example data:

    CONTINUE LEARNING

    Letters & Sounds

    Grade 1 • Language

    Learn the first sounds and their
    corresponding Ol Chiki forms.

The card should be substantially larger than normal lesson cards.

---

# 9. Feature Card Composition

Do NOT make the feature card a generic vertical Card.

Use an editorial composition.

Conceptually:

    ┌─────────────────────────────────────────────┐
    │ CONTINUE LEARNING                  •••      │
    │                                             │
    │ Letters & Sounds             ┌──────────┐  │
    │ Grade 1 • Language            │          │  │
    │                               │  visual  │  │
    │ Description                   │  / Ol    │  │
    │                               │  Chiki   │  │
    │                               └──────────┘  │
    │                                             │
    │ [ progress ]             [ Continue → ]    │
    └─────────────────────────────────────────────┘

Desktop/tablet may use wider horizontal composition.

Phone should preserve the same hierarchy without forcing tiny content.

---

# 10. Feature Card Color

Primary feature card:

    Soft Lavender

Use:

    #F7F2FF

with subtle lavender tonal variation.

Do not use a strong saturated purple background.

The card should feel almost white with a lavender tint.

---

# 11. Feature Card Typography

Label:

    CONTINUE LEARNING

Use:

- lavender
- uppercase
- small/medium
- letter spacing

Title:

    Letters & Sounds

Use:

- approximately 36–42sp
- bold
- primary text

Metadata:

    Grade 1 • Language

Use:

- 18–22sp
- secondary text

Description:

    Learn the first sounds and their
    corresponding Ol Chiki forms.

Use:

- approximately 18sp
- readable line height
- secondary text

---

# 12. Feature Illustration

The right side of the feature card should contain a soft visual
representation of the lesson.

For example:

- Ol Chiki character
- educational illustration
- lesson artwork

It should sit inside a soft translucent/white rounded container.

Do NOT generate complicated illustrations programmatically.

If the actual asset exists in the project, use it.

If no asset exists, implement a clean placeholder surface rather than
inventing a dependency or large asset.

The visual must never dominate the lesson title.

---

# 13. Progress

Feature card contains progress information.

Example:

    Progress

    3 of 8 lessons

    ━━━━━━━━━

Use a small book/learning icon.

Progress bar:

- thin
- lavender
- rounded
- subtle background track

The progress component should remain readable at small widths.

---

# 14. Primary CTA

Primary action:

    Continue Lesson →

Use the global primary button.

Visual:

    black background
    white text
    pill shape

Approximate height:

    56–64dp

This is one of the strongest visual elements on the screen.

Do not use purple for this primary CTA unless the global design system
explicitly changes it.

The reference's black CTA is intentional.

---

# 15. Feature Card Menu

Top-right:

    •••

Use a small unobtrusive overflow action.

It should NOT compete with the title.

---

# 16. Secondary Lesson

Immediately below the feature card:

    Numbers and Counting
    Grade 1 • Mathematics                         >

Use a compact lesson row.

This is NOT another giant card.

It should be a lightweight surface.

Suggested structure:

    [book icon]  Numbers and Counting
                 Grade 1 • Mathematics          [ > ]

Use:

- very light lavender background
- 20–24dp radius
- approximately 80–100dp height

---

# 17. Quick Actions

Section heading:

    Quick Actions                           View All >

Use strong typography.

Two primary action cards:

    Live Translate
    Translate Hindi to
    Santali instantly

and:

    Worksheets
    Generate practice
    worksheets

Each card should have:

- icon
- title
- short description
- circular arrow action

Cards may use different subtle semantic/brand tints.

Do NOT make them visually identical if their actions have different
importance.

---

# 18. Quick Action Card Layout

Desktop/tablet:

    ┌──────────────────────┐   ┌──────────────────────┐
    │ icon                 │   │ icon                 │
    │                      │   │                      │
    │ Live Translate    →  │   │ Worksheets         → │
    │ Translate Hindi...   │   │ Generate practice... │
    └──────────────────────┘   └──────────────────────┘

On narrow phones:

    stack vertically if necessary.

Do NOT squeeze both cards until text becomes unreadable.

---

# 19. Recent Lessons

Section heading:

    Recent Lessons                         View All >

Below:

    My Family
    Grade 1 • Language                         Completed   >

    Numbers 1 to 10
    Grade 1 • Mathematics                         50%      >

    Shapes Around Us
    Grade 1 • Mathematics                      In Progress >

These are list rows, not giant cards.

---

# 20. Recent Lesson Row

Each row:

    [icon]  Title
            Grade • Subject

                              Status
                                  >

Use subtle separators.

Do not create a separate Card around every row.

Use one shared list surface if a surface is needed.

---

# 21. Progress States

Completed:

    check icon
    Completed

In progress:

    circular progress indicator
    50%

Not started:

    Not started

The status should be visually secondary to the lesson title.

---

# 22. Bottom Navigation

Home uses the universal Vachak bottom navigation.

Five destinations:

    Home
    Live
    Learn
    Tools
    Settings

The reference style is:

- floating
- white
- rounded pill/sheet
- subtle shadow/elevation
- horizontally distributed
- visually detached from screen edges

Home selected:

    lavender filled pill
    white/appropriate icon
    lavender/dark selected label

Other items:

    muted icon
    muted label

Navigation must remain compact.

Do NOT use the stock full-width Material NavigationBar appearance.

---

# 23. Bottom Navigation Position

Leave enough content inset so the final lesson row is not hidden behind
navigation.

Use safe-area/window insets.

Navigation should visually float above content.

---

# 24. Scrolling

Home content is vertically scrollable.

The bottom navigation remains persistent.

Top header may remain static unless the global navigation system specifies
otherwise.

Avoid nested scrolling containers.

Preferred structure:

    Scaffold
        ↓
    Box
        ↓
    LazyColumn
        ↓
    content sections

Do not create unnecessary LazyColumn inside LazyColumn.

---

# 25. Content Order

The exact Home order is:

    Profile / utility header

    Greeting

    Greeting subtitle

    Category filters

    Continue Learning

    Secondary lesson

    Quick Actions

    Recent Lessons

    Bottom Navigation

Do not move Quick Actions above Continue Learning.

Continue Learning is the primary purpose of Home.

---

# 26. Responsive Behavior

## Phone

Use one column.

Feature card may transition to:

    title/content
        +
    illustration
        +
    progress
        +
    CTA

depending on available width.

Do not allow horizontal clipping.

---

## Tablet

Use the additional width intentionally.

Possible composition:

    wider feature card
    larger illustration
    two-column quick actions
    wider recent lesson rows

Do NOT simply stretch the phone UI.

---

# 27. Data

Do not hardcode the displayed lesson into the production UI.

The example:

    Letters & Sounds

is a representative visual/data example.

Use the actual current lesson from the existing curriculum engine.

The screen should derive:

- title
- grade
- subject
- description
- progress
- lesson count
- completion state

from real application data.

Do not replace broken data loading with fake data.

---

# 28. Empty State

If there is no current lesson:

Show a proper empty state.

Example:

    Ready to start learning?

    Explore the curriculum to begin your first lesson.

    [ Explore Curriculum ]

Do NOT render:

- blank card
- "null"
- lone dot
- infinite spinner

---

# 29. Loading State

During lesson loading:

Show a lightweight skeleton or intentional loading surface.

Do not use a giant centered spinner.

The layout should remain structurally stable while content loads.

---

# 30. Error State

If curriculum loading fails:

Show:

    Couldn't load your lessons

    [ Try Again ]

Keep the rest of Home usable.

Do not crash the entire screen.

---

# 31. Interaction

Continue Lesson:

    Home → selected lesson / Curriculum

Secondary lesson:

    opens lesson

Category:

    changes curriculum filtering

Quick Action:

    Live Translate → Live

    Worksheets → Tools / Worksheet

View All:

    navigates to the appropriate full list

Recent lesson:

    opens selected lesson

Search:

    opens search

Notification:

    opens notifications/activity if implemented

Profile:

    opens Settings/profile

---

# 32. Motion

Use subtle transitions.

Recommended:

    150–300ms

Examples:

- filter selection
- navigation
- card press
- progress update
- opening lesson

Do NOT use:

- parallax
- continuous animation
- heavy blur
- particle effects
- large spring animations

The app runs offline on constrained hardware.

---

# 33. Touch Targets

All interactive controls:

    >=44dp

Prefer:

    48dp+

for primary controls.

Do not make the reference's visual compactness an excuse for tiny
touch targets.

---

# 34. Accessibility

Verify:

- Hindi text readability
- Ol Chiki readability
- sufficient contrast
- scalable text
- content descriptions
- touch targets
- screen-reader semantics

Never encode important information using color alone.

---

# 35. Implementation Constraints

Use the existing Vachak design system.

Do NOT introduce:

- a new color system
- a new typography system
- a new navigation framework
- a new state architecture
- a new dependency

just for this screen.

Use existing shared components where appropriate.

If a shared component does not match the design, improve the shared
component rather than creating a duplicate.

---

# 36. Code Quality

HomeScreen should be readable at a glance.

Preferred conceptual structure:

    HomeScreen()
        ├── HomeHeader()
        ├── GreetingSection()
        ├── CategoryFilters()
        ├── ContinueLearningCard()
        ├── SecondaryLessonRow()
        ├── QuickActionsSection()
        └── RecentLessonsSection()

These are conceptual boundaries.

Do not blindly create a separate file for every function.

Keep reusable visual primitives in ui/components.

Keep Home-specific composition in HomeScreen.

---

# 37. No Business Logic In UI

HomeScreen must NOT:

- initialize engines
- directly create database instances
- perform network calls
- contain curriculum business rules
- contain translation logic
- contain persistence logic

Receive state and emit actions.

---

# 38. Performance

Do not introduce expensive visual effects.

No:

- runtime blur
- large image processing
- animated gradients
- continuous Canvas effects
- excessive shadows

Home must remain cheap to render.

---

# 39. Visual QA Checklist

Before declaring Home complete:

### Layout

[ ] Header aligned correctly

[ ] Greeting has correct hierarchy

[ ] Name is dominant typography

[ ] Filters are horizontally usable

[ ] Continue Learning dominates the page

[ ] Quick Actions are clearly secondary

[ ] Recent Lessons are compact

[ ] Bottom navigation does not obscure content

### Visual

[ ] Lavender system is consistent

[ ] Black primary CTA is strong

[ ] No accidental Forest/green theme remains

[ ] No stock Material 3 visual artifacts

[ ] Cards use consistent radius

[ ] Typography is consistent

[ ] Spacing follows design system

### Functional

[ ] Real lesson data loads

[ ] Loading works

[ ] Empty state works

[ ] Error state works

[ ] Continue Lesson works

[ ] Filters work

[ ] Quick Actions work

[ ] Recent lessons open correctly

[ ] Navigation works

### Responsive

[ ] Phone works

[ ] Tablet works

[ ] No clipping

[ ] No text overflow

[ ] No navigation overlap

---

# 40. IMPORTANT IMPLEMENTATION RULE

Do not stop at "the code compiles."

The screen is NOT DONE until it has been visually inspected.

Required workflow:

    implement
        ↓
    compile
        ↓
    run
        ↓
    screenshot
        ↓
    compare against reference
        ↓
    fix spacing/typography/layout
        ↓
    screenshot again

Repeat until the screen visually belongs to the same design system.

---

# 41. Do Not Modify Other Screens

For this task:

DO:

    rebuild Home

MAY:

    modify shared theme/components if required by Home

DO NOT:

    redesign Live
    redesign Curriculum
    redesign Flashcards
    redesign Tools
    redesign Settings

Those screens will be handled separately.

---

# 42. Final Quality Bar

The finished Home screen should look like:

    an intentional premium educational product

not:

    a Material 3 demo
    + some purple colors
    + rounded cards.

The reference's strongest characteristics are:

    typography
    whitespace
    hierarchy
    soft surfaces
    large primary content
    restrained controls
    floating navigation
    editorial composition

Prioritize those characteristics above decorative details.
