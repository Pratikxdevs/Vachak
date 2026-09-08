# Vachak — Curriculum Screen

## 1. Screen Identity

Curriculum is the main learning-library screen.

Its purpose is to let a teacher:

- browse the curriculum
- filter content
- browse by grade
- discover flashcard decks
- resume learning
- see recently viewed lessons

The mental model is:

    DISCOVER
       ↓
    CHOOSE GRADE / SUBJECT
       ↓
    FIND LESSON
       ↓
    START LEARNING

This is NOT Home.

Do not repeat:

    Good morning,
    Vaibhav

The Curriculum screen establishes its own identity immediately.

---

# 2. Screen Header

Top of screen:

    Curriculum

    Explore lessons and build your class curriculum.

Right side:

    Search
    Offline

The title should be large and dominant.

Approximate:

    Curriculum
    38–44sp
    bold

Subtitle:

    18–20sp
    secondary text

Do not use a greeting.

---

# 3. Search

Search is available from the Curriculum header.

Search should allow discovery of:

- lessons
- subjects
- curriculum topics
- flashcard decks

Search should open a dedicated search surface or transition into an
expanded search field.

Do not permanently occupy large vertical space with the search field.

---

# 4. Offline Status

Display a compact status badge:

    ● Offline    ✓

Use the universal Vachak offline treatment.

Offline should feel normal and trustworthy.

Do NOT make it look like an error.

---

# 5. Category Filters

Immediately below the header:

    [ Filter ]
    [ All ]
    [ Language ]
    [ Mathematics ]
    [ EVS ]
    [ Stories ]
    [ Life Skills ]

Horizontal scrolling.

Selected:

    All

Selected state:

    soft lavender surface
    lavender text
    pill shape

Unselected:

    white surface
    subtle border
    secondary/dark text

Filter button:

    circular / compact

---

# 6. Filter Behavior

Filters affect curriculum content.

Possible filters:

- All
- Language
- Mathematics
- EVS
- Stories
- Life Skills

Do not reload the entire screen visually when a filter changes.

Content should update naturally.

The selected filter must remain visually obvious.

---

# 7. Continue Learning

The first major content section is:

    Continue Learning

Use the same feature-card language established on Home.

Example:

    CONTINUE LEARNING

    Letters & Sounds

    Grade 1 • Language

    Learn the first sounds and their
    corresponding Ol Chiki forms.

    Progress
    3 of 8 lessons

    [ Continue Lesson → ]

The curriculum screen may show the same current lesson as Home because
this is useful contextual information.

However, the Curriculum screen must NOT copy the Home greeting or
Home-specific composition.

---

# 8. Continue Learning Card

Large editorial feature card.

Background:

    #F7F2FF

Radius:

    24–28dp

Composition:

    ┌─────────────────────────────────────────────┐
    │ CONTINUE LEARNING                    •••    │
    │                                             │
    │ Letters & Sounds                illustration│
    │ Grade 1 • Language                         │
    │                                             │
    │ Description                                 │
    │                                             │
    │ [ Progress ]             [ Continue → ]    │
    └─────────────────────────────────────────────┘

The card should be the largest surface on the page.

---

# 9. Feature Illustration

Use a soft educational illustration.

For example:

    Ol Chiki character
    lesson artwork
    language illustration

Place it inside a pale/white rounded area.

The illustration should support the curriculum subject.

It should not overpower the text.

---

# 10. Primary CTA

Continue button:

    Continue Lesson →

Use the global Vachak primary button.

Default:

    #171717

Text:

    #FFFFFF

Shape:

    pill

Height:

    approximately 56–64dp

This should remain one of the strongest actions on the screen.

---

# 11. Browse by Grade

Section:

    Browse by Grade                         View All >

This is a discovery section.

Use horizontally arranged grade cards.

Example:

    Grade 1
    12 lessons

    Grade 2
    14 lessons

    Grade 3
    15 lessons

    Grade 4
    10 lessons

    Grade 5
    12 lessons

---

# 12. Grade Card

Each card contains:

    icon
    Grade number
    lesson count

Example:

    ┌───────────────┐
    │      icon     │
    │               │
    │    Grade 1    │
    │   12 lessons  │
    └───────────────┘

Use subtle tonal accents.

Grade cards do not need large descriptions.

The user should understand the purpose instantly.

---

# 13. Grade Color Treatment

Use the universal palette.

Most grade cards:

    pale lavender

Some may use subtle semantic accent tints where useful.

Do not make every grade a different bright color.

Color variation should remain restrained.

---

# 14. Grade Interaction

Selecting:

    Grade 1

opens/filter curriculum to Grade 1.

Selecting:

    View All

opens the complete grade browser if necessary.

The current selected grade should remain visible.

---

# 15. Flashcards Section

Section:

    Flashcards ✨                         View All >

This is a discovery section for flashcard decks.

The section should feel more playful than the curriculum list while
remaining within the same design system.

---

# 16. Flashcard Deck Cards

Example:

    Ol Chiki
    Vowels
    18 cards

    Hindi
    Varnamala
    20 cards

    Number
    Names
    15 cards

    Plants
    Around Us
    12 cards

    My Family
    Words
    16 cards

Cards are horizontally scrollable.

---

# 17. Flashcard Card Composition

Example:

    ┌────────────────────┐
    │                    │
    │       [glyph]      │
    │                    │
    │    Ol Chiki        │
    │      Vowels        │
    │                    │
    │    18 cards    📖  │
    └────────────────────┘

The glyph/visual is the focal point.

Deck title is secondary.

Card count is tertiary.

---

# 18. Flashcard Deck Interaction

Tapping a deck opens the Flashcard experience.

The deck should retain context:

    Curriculum
       ↓
    Flashcard Deck
       ↓
    Flashcard Session

When the user exits the session:

    return to the previous context

Do not dump the user back to Home.

---

# 19. Flashcard Carousel

On phones:

    horizontal scrolling

On tablets:

    show more cards simultaneously

Optional pagination indicator:

    ━━━  ━━

Keep the indicator subtle.

Do not make it look like a marketing carousel.

---

# 20. Recently Viewed

Section:

    Recently Viewed                         View All >

This is a compact learning history.

Example:

    Letters & Sounds
    Grade 1 • Language
                              3/8     →

    Numbers 1 to 10
    Grade 1 • Mathematics
                              50%     →

    Plants Around Us
    Grade 2 • EVS
                              Completed →

---

# 21. Recently Viewed Surface

Use ONE shared list surface.

Do not create three independent floating cards.

Structure:

    ┌────────────────────────────────────────────┐
    │ lesson row                                 │
    ├────────────────────────────────────────────┤
    │ lesson row                                 │
    ├────────────────────────────────────────────┤
    │ lesson row                                 │
    └────────────────────────────────────────────┘

Radius:

    20–24dp

---

# 22. Recently Viewed Row

Each row contains:

    lesson icon
    title
    grade
    subject
    progress/status
    navigation arrow

Example:

    [icon]  Letters & Sounds
            Grade 1 • Language

                              3/8
                              progress
                                         >

Keep title hierarchy strong.

Metadata should be quieter.

---

# 23. Progress States

Supported states:

### Not Started

    Not started

### In Progress

    50%

or:

    3/8

### Completed

    ✓ Completed

Completed should be visually obvious but not visually dominant.

---

# 24. Curriculum Filtering

Filtering should affect:

    Continue Learning
    lesson discovery
    recently viewed where applicable

Do not unnecessarily hide unrelated sections unless the selected filter
clearly applies to them.

For example:

    Flashcards

may have their own content filtering.

---

# 25. Grade Browser

If View All is selected for grades:

    Browse Grades

    Grade 1
    Grade 2
    Grade 3
    ...

This can become a dedicated screen only if there are enough grades/content
to justify it.

Do not create unnecessary screens for five static cards.

---

# 26. Full Curriculum Browser

If the user selects:

    View All

for curriculum content, use a dedicated curriculum browsing surface.

Possible hierarchy:

    Grade
       ↓
    Subject
       ↓
    Lesson

The user should always understand the current context.

Example:

    Grade 1
      / Language
      / Mathematics
      / EVS

---

# 27. Lesson Entry

Every lesson card/row should provide a clear path into:

    Lesson Detail

Lesson detail is responsible for the actual lesson experience.

Curriculum is responsible for discovery.

Do not put the entire lesson content inside Curriculum.

---

# 28. Curriculum Screen Is Not a Lesson Screen

Curriculum should answer:

    "What can I learn?"

Lesson should answer:

    "Let's learn this."

Do not overload Curriculum with:

- full lesson content
- exercises
- long explanations
- full audio player
- flashcard session controls

---

# 29. Scroll Behavior

The entire page scrolls vertically.

Use:

    LazyColumn

for long curriculum content.

Horizontal sections may use:

    LazyRow

Do not nest vertical LazyColumns.

---

# 30. Vertical Structure

Recommended order:

    Header

    Category Filters

    Continue Learning

    Browse by Grade

    Flashcards

    Recently Viewed

    Bottom Navigation

This order should remain consistent.

---

# 31. Content Density

Curriculum contains more information than Home.

Therefore:

- cards can be slightly smaller
- sections can be denser
- whitespace remains generous
- typography remains large

Do not make every section enormous.

The user should be able to browse several lessons without excessive
scrolling.

---

# 32. Bottom Navigation

Persistent navigation:

    Home
    Live
    Learn
    Tools
    Settings

The selected destination is:

    Learn

If the product-facing label is:

    Curriculum

the navigation label may remain:

    Learn

or:

    Curriculum

Choose ONE globally and keep it consistent.

Do not alternate between both labels across screens.

---

# 33. Curriculum Navigation Icon

Selected:

    lavender pill/surface

Icon:

    book

Label:

    Learn / Curriculum

Unselected items remain muted.

---

# 34. Search State

When search is activated:

    search field
    ↓
    results

Results can include:

    Lessons
    Flashcards
    Topics

Use the same Vachak list/card language.

Do not build a generic web-search UI.

---

# 35. Empty State

If no curriculum content matches:

    Nothing here yet

    Try another subject or grade.

    [ Clear Filters ]

Do not show an empty white screen.

---

# 36. Loading State

Use skeleton placeholders that preserve the final layout.

Example:

    feature-card skeleton

    grade-card skeleton

    flashcard skeleton

    lesson-row skeleton

Avoid a giant centered spinner.

---

# 37. Error State

If curriculum data cannot load:

    Couldn't load curriculum

    [ Try Again ]

Do not remove navigation.

Do not crash the entire application.

---

# 38. Offline State

If content is locally available:

    ● Offline   ✓

If required content is not available locally:

    Available offline
    or
    Download required content

Do not assume every curriculum item is automatically available.

---

# 39. Responsive Design

## Phone

Use:

    single column

Horizontal:

    grade carousel
    flashcard carousel

Continue Learning:

    full width

Recently Viewed:

    full width

---

## Tablet

Use additional width.

Continue Learning may become wider.

Grade cards may display more items.

Flashcards may display 4–6 cards.

Recent lessons can use wider rows.

Do not simply scale the phone screenshot.

---

# 40. Tablet Composition

Possible:

    Header

    Filters

    Continue Learning

    ┌──────────────────────────────────────┐
    │ Browse by Grade                      │
    │ [ ] [ ] [ ] [ ] [ ]                  │
    └──────────────────────────────────────┘

    ┌──────────────────────────────────────┐
    │ Flashcards                           │
    │ [ ] [ ] [ ] [ ]                     │
    └──────────────────────────────────────┘

    Recently Viewed

Use available horizontal space without creating unnecessary columns.

---

# 41. Typography

Header:

    38–44sp

Section headings:

    24–28sp

Feature title:

    34–42sp

Card title:

    18–22sp

Metadata:

    15–18sp

Supporting text:

    16–18sp

Keep typography consistent with Home.

---

# 42. Color System

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

Primary text:

    #17151C

Secondary text:

    #6F6A78

Primary CTA:

    #171717

Do not introduce a new Curriculum-specific palette.

---

# 43. Interaction Priority

Highest:

    Continue Lesson

Then:

    Grade selection
    Lesson selection
    Flashcard deck

Then:

    Search
    View All
    Recently Viewed

Navigation remains persistent but visually quiet.

---

# 44. Animation

Use restrained motion.

When filters change:

    subtle content transition

When opening a lesson:

    short transition

When selecting a flashcard deck:

    short transition

Avoid:

- bouncing cards
- rotating cards in the browser
- continuous animations
- heavy blur
- decorative particles

---

# 45. Performance

Curriculum may contain many items.

Use:

    LazyColumn
    LazyRow

where appropriate.

Use stable keys.

Avoid expensive recomposition.

Do not render hundreds of lesson cards simultaneously if only a few are
visible.

---

# 46. Data

Do not hardcode:

    Letters & Sounds
    Numbers and Counting
    Plants Around Us

These are reference examples.

Use actual curriculum data.

The UI should derive:

- lesson name
- grade
- subject
- description
- progress
- lesson count
- completion state
- flashcard deck data

from the existing application layer.

---

# 47. Architecture Boundary

The screen must not directly:

- access Room
- initialize engines
- perform network requests
- load models
- perform translation
- generate worksheets

The screen consumes application state and emits actions.

---

# 48. Reuse

Reuse universal components:

    ScreenHeader
    FilterPill
    PrimaryButton
    LessonCard
    LessonRow
    ProgressIndicator
    OfflineBadge
    BottomNavigation

Do not create Curriculum-only versions of these unless their visual
behavior genuinely differs.

---

# 49. No Code Slop

Do NOT:

- duplicate Home components
- create giant generic curriculum abstractions
- create unnecessary repositories
- create unnecessary ViewModels
- introduce a new navigation framework
- introduce a new design system
- introduce new dependencies

Keep implementation straightforward.

---

# 50. Screen-Specific Components

Meaningful boundaries may include:

    CurriculumScreen
    CurriculumHeader
    CategoryFilters
    ContinueLearningCard
    GradeCarousel
    GradeCard
    FlashcardDeckCarousel
    FlashcardDeckCard
    RecentlyViewedSection
    RecentlyViewedRow

These are conceptual boundaries.

Do not automatically create one file per component.

---

# 51. Navigation Context

When entering a lesson:

    Curriculum → Lesson

Back:

    Lesson → Curriculum

When entering flashcards:

    Curriculum → Flashcards

Back:

    Flashcards → Curriculum

Preserve:

- selected grade
- selected subject
- selected filter
- selected deck

where practical.

---

# 52. Home vs Curriculum

Home:

    "What should I do next?"

Curriculum:

    "What can I learn?"

Home emphasizes:

    current lesson
    quick actions
    recent activity

Curriculum emphasizes:

    discovery
    grades
    subjects
    lesson library
    flashcard discovery

Do not make the two screens identical.

---

# 53. Visual Relationship to Home

Curriculum MUST look like the same application as Home.

Shared:

- background
- lavender palette
- typography
- buttons
- cards
- navigation
- spacing
- iconography

Different:

- content hierarchy
- header
- information density
- learning-library composition

---

# 54. Definition of Done

[ ] Header matches Vachak design system

[ ] No Home greeting appears

[ ] Search works

[ ] Offline badge works

[ ] Filters work

[ ] Continue Learning works

[ ] Grade browsing works

[ ] Flashcard browsing works

[ ] Recently Viewed works

[ ] Lesson navigation works

[ ] Flashcard navigation works

[ ] Loading state works

[ ] Empty state works

[ ] Error state works

[ ] Long curriculum lists scroll correctly

[ ] Horizontal carousels work

[ ] Phone layout works

[ ] Tablet layout works

[ ] Bottom navigation works

[ ] Real curriculum data is used

[ ] No fake production data is introduced

[ ] No duplicate UI system exists

[ ] No unnecessary dependencies added

[ ] No engine/domain code unnecessarily changed

[ ] Screen has been visually inspected after implementation

---

# 55. Final Principle

Curriculum is Vachak's learning library.

It should feel like:

    Browse → Discover → Choose → Learn

not:

    Dashboard → Dashboard → Dashboard

The page should be information-rich without feeling dense.

The visual hierarchy should always remain:

    Continue Learning
          ↓
    Browse Curriculum
          ↓
    Practice / Flashcards
          ↓
    Recent Learning
