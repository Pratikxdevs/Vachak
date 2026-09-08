# Vachak — Tools Screen

## 1. Screen Identity

Tools is the teacher productivity hub.

Its purpose is to give the teacher fast access to actions that help
create, prepare, and reuse learning material.

The screen should answer:

> "What can I create or use to make teaching easier?"

Primary tools:

- Worksheets
- Flashcards

Secondary areas:

- Quick Actions
- Saved/generated content
- Recent activity

Tools is NOT a generic "AI tools" marketplace.

Do not fill it with arbitrary features just because they could be
implemented.

---

# 2. Visual Direction

Tools belongs to the same Vachak visual system as:

- Home
- Live
- Curriculum

Use:

- soft lavender
- white
- black
- spacious typography
- rounded surfaces
- restrained shadows
- editorial composition

Tools can be slightly more functional and compact than Home.

---

# 3. Header

Top:

    Tools

    Powerful tools to save time and enhance your teaching.

Right:

    Search
    Offline

Title:

    38–44sp
    bold

Subtitle:

    18–20sp
    secondary

Do NOT use the Home greeting.

Do NOT use:

    Good morning, Vaibhav

---

# 4. Offline Indicator

Compact badge:

    ● Offline   ✓

Use the universal OfflineBadge.

Offline should communicate availability, not failure.

---

# 5. Primary Tools

Section heading:

    Primary Tools

This is the most important section.

Display two large feature cards:

    Worksheets

    Flashcards

On tablet:

    side-by-side

On narrow phone:

    side-by-side only if readable;
    otherwise stack vertically.

---

# 6. Worksheet Feature Card

Card:

    Worksheets

Supporting text:

    Create practice worksheets
    instantly using AI.

Optional badge:

    ✨ AI Powered

Primary action:

    →

The card should have a soft lavender treatment.

Use a worksheet/document illustration.

Do not use a huge illustration that dominates the card.

---

# 7. Flashcard Feature Card

Card:

    Flashcards

Supporting text:

    Generate and use flashcards
    for quick learning.

Optional badge:

    ✨ AI Powered

Primary action:

    →

Flashcards may use a slightly different soft accent surface while
remaining within the universal palette.

The visual can use:

    Hindi glyph
    Ol Chiki glyph
    stacked cards

---

# 8. Primary Tool Interaction

Tapping Worksheets:

    Tools → Worksheets

Tapping Flashcards:

    Tools → Flashcards

Do not embed the entire workflow inside the Tools page.

Tools is the launcher/discovery surface.

---

# 9. Quick Actions

Section:

    Quick Actions

Use compact action cards.

Suggested actions:

    New Worksheet

    Generate Flashcards

    My Saved Items

    Recent Activity

These are shortcuts.

They should be visually smaller than Primary Tools.

---

# 10. Quick Action Card

Each card contains:

    icon

    title

    short description

    arrow

Example:

    New Worksheet

    Create a worksheet
    from scratch

         →

Keep descriptions short.

Do not create large feature cards for quick actions.

---

# 11. Quick Action Grid

Tablet:

    [ New Worksheet ] [ Generate Flashcards ]
    [ Saved Items ]   [ Recent Activity ]

Phone:

    two-column grid if readable.

If the device width is too narrow:

    single column

Never shrink text to force a two-column layout.

---

# 12. Recent Worksheets

Section:

    Recent Worksheets                         View All →

This is a history surface.

Show recently generated worksheets.

Example:

    Vowels Recognition
    Grade 1 • Language
    Generated today, 10:15 AM

    Numbers 1 to 10
    Grade 1 • Mathematics
    Generated yesterday, 4:30 PM

    Parts of Plants
    Grade 2 • EVS
    Generated 2 days ago, 9:20 AM

---

# 13. Worksheet History Row

Structure:

    [document icon]

    Vowels Recognition
    Grade 1 • Language
    Generated today, 10:15 AM

                              [Worksheet] ⋮

Use a compact row.

Do not use individual giant cards.

A shared rounded list surface is preferred.

---

# 14. Worksheet Row Actions

Overflow:

    ⋮

Possible actions:

    Open
    Rename
    Duplicate
    Delete

Only expose actions that actually exist.

Do not build fake functionality.

---

# 15. Recent Flashcard Decks

Section:

    Recent Flashcard Decks                  View All →

Example:

    Hindi Varnamala
    20 cards • Grade 1
    Updated today, 9:45 AM

    Plants Around Us
    12 cards • Grade 2
    Updated yesterday, 3:10 PM

    Number Names
    15 cards • Grade 1
    Updated 2 days ago, 11:00 AM

---

# 16. Flashcard History Row

Structure:

    [deck icon]

    Hindi Varnamala
    20 cards • Grade 1
    Updated today, 9:45 AM

                              [Deck] ⋮

Use the same list language as worksheets.

Do not create a completely different history component.

---

# 17. Saved Items

If saved content exists:

    My Saved Items

should provide access to:

- saved worksheets
- saved flashcard decks
- saved lesson resources

This can become a dedicated screen if the amount of saved content grows.

Do not create a dedicated screen solely for an empty placeholder.

---

# 18. Recent Activity

Recent Activity may include:

- worksheet generated
- flashcard deck created
- worksheet opened
- deck reviewed

Keep it lightweight.

It should not become a notification center.

---

# 19. Tools Screen Hierarchy

The visual hierarchy is:

    Tools
      ↓
    Primary Tools
      ↓
    Quick Actions
      ↓
    Recent Worksheets
      ↓
    Recent Flashcard Decks

Primary tools receive the most visual weight.

Recent content receives the least.

---

# 20. Tools vs Curriculum

Curriculum answers:

    "What can I learn?"

Tools answers:

    "What can I create/use?"

Do not duplicate the curriculum browser here.

Tools can surface curriculum context when creating something, but
Curriculum remains the source of lesson discovery.

---

# 21. Tools vs Home

Home:

    current learning
    quick access
    recent learning

Tools:

    teacher productivity
    creation
    generated resources

Do not copy the Home layout into Tools.

---

# 22. Worksheets Entry

When opening Worksheets:

    Tools
      ↓
    Worksheets

The worksheet workflow is:

    Choose lesson/topic
          ↓
    Choose worksheet type
          ↓
    Configure
          ↓
    Generate
          ↓
    Preview
          ↓
    Save / Export / Use

The generation experience gets its own screen.

---

# 23. Flashcards Entry

When opening Flashcards:

    Tools
      ↓
    Flashcards

The flashcard workflow is:

    Choose deck/topic
          ↓
    Review deck
          ↓
    Start session
          ↓
    Flip / answer
          ↓
    Progress
          ↓
    Complete

The actual Flashcard Session is NOT embedded in Tools.

---

# 24. AI Label

"AI Powered" can be used for genuinely generated functionality.

Use it sparingly.

It should not become decorative branding.

If a feature is actually deterministic/precomputed, do not label it
AI-generated.

---

# 25. Generation States

For generation workflows, show:

    Preparing...
    Generating...
    Ready

Do not block the entire Tools screen with a spinner.

Generation state belongs to the workflow screen.

---

# 26. Empty State

If there are no recent worksheets:

    No worksheets yet

    Create your first practice worksheet.

    [ Create Worksheet ]

If there are no flashcard decks:

    No flashcard decks yet

    Start with a curriculum topic.

    [ Explore Curriculum ]

---

# 27. Loading State

Loading should preserve the layout.

Use lightweight skeletons for:

- primary cards
- quick actions
- recent lists

Avoid giant centered spinners.

---

# 28. Error State

If recent content cannot load:

    Couldn't load recent items

    [ Try Again ]

The Primary Tools section should remain usable.

Do not make the entire screen fail because history failed.

---

# 29. Search

Search can search:

- worksheets
- flashcard decks
- saved resources

Search should be compact.

Do not introduce global application search behavior here unless the
global search specification supports it.

---

# 30. Bottom Navigation

Persistent navigation:

    Home
    Live
    Learn
    Tools
    Settings

Selected:

    Tools

Use the universal floating navigation treatment.

Selected item:

    soft lavender pill
    lavender icon
    selected label

Other destinations:

    muted

---

# 31. Phone Layout

Preferred order:

    Header

    Primary Tools

    Quick Actions

    Recent Worksheets

    Recent Flashcard Decks

    Bottom Navigation

Use a single vertical scroll.

Horizontal space may be used for:

    two primary feature cards

or:

    two-column quick actions

when readability permits.

---

# 32. Tablet Layout

Tablet should use horizontal space intentionally.

Primary tools:

    [ Worksheets ] [ Flashcards ]

Quick actions:

    4-column or 2x2 grid

Recent content:

    wide shared list surfaces

Do not simply stretch phone cards across the entire screen.

---

# 33. Typography

Page title:

    38–44sp

Section heading:

    24–28sp

Feature title:

    24–30sp

Quick action title:

    18–21sp

List title:

    17–20sp

Supporting text:

    15–18sp

Metadata:

    13–16sp

Maintain the universal Lexend system.

---

# 34. Color

Use the universal Vachak palette.

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

Do not introduce a Tools-specific palette.

---

# 35. Semantic Accent Colors

Different tools may have subtle accent variations.

For example:

    Worksheets → lavender

    Flashcards → warm/pale accent

But these should remain extremely subtle.

The application should still read as one lavender-based product.

---

# 36. Cards

Primary tool cards:

    24–28dp radius

Quick actions:

    18–22dp radius

List surface:

    20–24dp radius

Avoid excessive shadows.

Use elevation only where it improves hierarchy.

---

# 37. Icons

Use consistent outline iconography.

Worksheet:

    document / file

Flashcards:

    cards / book

New worksheet:

    document + plus

Generate flashcards:

    cards + sparkle

Saved:

    folder

Recent:

    clock

Do not mix unrelated icon styles.

---

# 38. Animation

Use restrained motion:

    150–300ms

Primary tool press:

    subtle scale/feedback

Navigation:

    short transition

Opening tool:

    short spatial transition

Avoid:

- bouncing cards
- floating animations
- particle effects
- continuous shimmer
- heavy blur

---

# 39. Performance

Tools should remain lightweight.

Do not render:

- worksheet previews inside every row
- large document thumbnails
- animated illustrations
- expensive blur effects

Recent items should be represented primarily by metadata and icons.

---

# 40. Data

Do not hardcode:

    Vowels Recognition
    Numbers 1 to 10
    Parts of Plants

These are reference examples only.

Use real worksheet and flashcard data.

The UI should derive:

- title
- grade
- subject
- timestamp
- card count
- worksheet type
- generated state

from the application layer.

---

# 41. Architecture

ToolsScreen must not:

- initialize engines directly
- access Room directly
- perform generation directly inside UI
- contain worksheet business logic
- contain flashcard business logic

Use the existing engine/application boundary.

Conceptually:

    ToolsScreen
        ↓
    action
        ↓
    application/engine
        ↓
    result
        ↓
    UI state

---

# 42. Existing Functionality

The current implementation already exposes:

- Worksheets
- Flashcards
- worksheet template selection
- worksheet generation
- generated worksheet result
- flashcard deck listing
- flashcard navigation

The redesign must preserve these capabilities.

The current implementation uses a tabbed Worksheets/Flashcards surface;
the new design intentionally replaces that with a more discoverable
Tools hub. :contentReference[oaicite:0]{index=0}

Do not remove functionality merely because the old tab layout is being
removed.

---

# 43. Navigation Context

When opening Worksheets from Tools:

    Tools → Worksheets

Back:

    Worksheets → Tools

When opening Flashcards:

    Tools → Flashcards

Back:

    Flashcards → Tools

Do not return to Home unless Home was the actual entry point.

---

# 44. Shared Components

Prefer:

    ScreenHeader
    OfflineBadge
    PrimaryButton
    ToolFeatureCard
    QuickActionCard
    RecentItemRow
    SectionHeader
    BottomNavigation

Do not create:

    ToolsWorksheetCard
    ToolsFlashcardCard

if a shared component can represent the same visual pattern.

---

# 45. Component Boundaries

A reasonable conceptual structure:

    ToolsScreen
        ├── ToolsHeader
        ├── PrimaryToolsSection
        │     ├── WorksheetToolCard
        │     └── FlashcardToolCard
        ├── QuickActionsSection
        ├── RecentWorksheetsSection
        └── RecentFlashcardsSection

Keep these boundaries meaningful.

Do not create dozens of micro-composables.

---

# 46. No Code Slop

Do not:

- create duplicate design components
- create generic tool factories
- create unnecessary ViewModels
- create a new navigation system
- create new repositories
- create new dependencies
- put business logic inside composables
- copy HomeScreen structure and rename variables

The screen should be simple.

---

# 47. Visual QA

After implementation:

    build
      ↓
    run
      ↓
    screenshot
      ↓
    compare with design
      ↓
    fix
      ↓
    screenshot again

Verify:

- card proportions
- section spacing
- typography
- navigation
- touch targets
- scrolling
- phone layout
- tablet layout
- empty states
- loading states
- real data

---

# 48. Definition of Done

[ ] Tools header implemented

[ ] Offline badge implemented

[ ] Worksheets feature card works

[ ] Flashcards feature card works

[ ] Quick Actions work

[ ] Recent Worksheets works

[ ] Recent Flashcard Decks works

[ ] View All works where implemented

[ ] Empty states work

[ ] Loading states work

[ ] Error states work

[ ] Worksheet navigation works

[ ] Flashcard navigation works

[ ] Bottom navigation works

[ ] Real data is used

[ ] No fake production content

[ ] No duplicate component system

[ ] No unnecessary dependencies

[ ] Existing worksheet functionality preserved

[ ] Existing flashcard functionality preserved

[ ] Phone layout verified

[ ] Tablet layout verified

[ ] Visual QA completed

---

# 49. Final Principle

Tools should feel like:

    "My teaching toolbox."

Not:

    "A collection of random AI features."

The strongest hierarchy is:

    TOOLS
       ↓
    What can I create?
       ↓
    Worksheets / Flashcards
       ↓
    What did I create recently?

The teacher should be able to open Tools and start a useful action
within seconds.
