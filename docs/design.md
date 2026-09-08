# Vachak — Universal Design System

## 1. Design Direction

Vachak uses a:

> Soft Editorial Minimalist UI

The visual language combines:

- Premium editorial design
- Modern native mobile UI
- Soft, tactile surfaces
- Strong typography
- Generous whitespace
- Modular content surfaces
- Rounded geometry
- Restrained color
- Clear functional hierarchy

The interface should feel:

- Calm
- Premium
- Human
- Educational
- Trustworthy
- Modern
- Lightweight
- Intentional

The interface should NOT feel:

- Like an enterprise dashboard
- Like a government portal
- Like a generic Material 3 template
- Like a dense LMS
- Like a futuristic AI product
- Overly glassmorphic
- Overly colorful
- Overly decorative

---

# 2. Design Philosophy

The design should achieve sophistication through:

1. Typography
2. Spacing
3. Proportion
4. Surface hierarchy
5. Shape
6. Alignment
7. Restraint

Do not depend on:

- gradients
- shadows
- excessive color
- decorative illustrations
- excessive animation

for visual sophistication.

The UI should look designed even when all decorative elements are removed.

---

# 3. Visual Hierarchy

Every interface should establish a clear hierarchy.

Priority:

    Primary content
        ↓
    Primary action
        ↓
    Supporting information
        ↓
    Secondary actions
        ↓
    Metadata
        ↓
    Decoration

Never give every element equal visual importance.

A user should be able to identify the most important information almost
immediately.

---

# 4. Whitespace

Whitespace is a core design element.

Prefer generous spacing over dense information packing.

Use whitespace to separate:

- sections
- concepts
- actions
- cards
- navigation
- content groups

Do not fill empty space merely because it exists.

Empty space is intentional when it improves hierarchy and readability.

---

# 5. Layout Density

Default density:

> Low to Medium

The interface should prioritize comprehension over information density.

Prefer:

    3 important pieces of information

over:

    10 small pieces of information

Use progressive disclosure when additional information is necessary.

---

# 6. Typography

Typography is one of the primary visual anchors.

## Primary Typeface

Use:

> Lexend

Lexend is the canonical Vachak typeface.

It should be used consistently throughout the application.

Avoid mixing multiple unrelated font families.

---

## Typography Hierarchy

Use a restrained hierarchy.

### Display

Approximately:

    36–48sp

Used for major statements and highly prominent content.

### Headline

Approximately:

    28–36sp

Used for major screen-level headings.

### Section Heading

Approximately:

    22–28sp

Used for major content groups.

### Title

Approximately:

    18–22sp

Used for cards and important content.

### Body

Approximately:

    15–17sp

Used for normal readable content.

### Supporting

Approximately:

    13–14sp

Used for descriptions and secondary information.

### Metadata

Approximately:

    11–13sp

Used for timestamps, labels and technical information.

These are guidelines, not rigid values.

---

# 7. Typography Rules

Prefer:

- short headings
- strong hierarchy
- generous line height
- readable paragraph widths
- moderate font weights

Avoid:

- excessive bold text
- all-caps headings
- tiny body text
- excessive typography styles
- decorative typography
- excessive letter spacing

Typography should communicate hierarchy without requiring heavy visual
decoration.

---

# 8. Color Philosophy

Vachak's existing brand palette remains the foundation.

Primary brand:

    Forest Green
    #285943

Secondary accent:

    Palash Orange
    #E65100

Primary background:

    Paper White
    #FFFCF5

Supporting surfaces:

    Paper Dark
    #F5EFE0
    #E8F0E9

Offline semantic color:

    Offline Green
    #2E7D32

Additional semantic colors may include:

    Amber
    #F9A825

    Error Red
    #C62828

Color should primarily communicate:

- brand
- state
- semantic meaning
- emphasis

Color should NOT be the only method used to establish hierarchy.

---

# 9. Color Restraint

Use accent colors sparingly.

Preferred:

    mostly neutral surfaces
    +
    one dominant brand color
    +
    small semantic accents

Avoid:

- rainbow interfaces
- saturated backgrounds
- multiple competing accent colors
- excessive orange
- decorative color everywhere

Accent colors should feel intentional.

---

# 10. Surface System

The application should use a small hierarchy of surfaces.

### Surface 0

Application background.

Quietest visual level.

### Surface 1

Primary content surface.

### Surface 2

Cards and interactive surfaces.

### Surface 3

Floating / elevated elements.

### Surface 4

Sheets, dialogs and overlays.

Differences between levels should remain subtle.

Use:

- surface color
- contrast
- spacing
- radius
- very soft elevation

rather than heavy shadows.

---

# 11. Cards

Cards are modular information surfaces.

Cards should feel soft and intentional.

Typical characteristics:

- 20–28dp radius
- 16–24dp internal padding
- subtle surface contrast
- minimal border
- very soft elevation

Cards should be used when content forms a meaningful independent group.

Do NOT put every piece of information inside a card.

---

# 12. Card Hierarchy

Cards should have different levels of importance.

### Primary

Large and visually dominant.

### Secondary

Supporting information.

### Compact

Small functional information.

### Floating

Contextual or temporary information.

Not every card should have the same:

- size
- elevation
- radius
- visual weight

---

# 13. Card Composition

A card should generally follow:

    Label / category
          ↓
    Primary information
          ↓
    Supporting information
          ↓
    Metadata / action

Not every layer is required.

The card should have one clear focal point.

---

# 14. Corner Radius

Use a consistent radius system.

### Small controls

    10–14dp

### Inputs / compact surfaces

    16–20dp

### Standard cards

    20–24dp

### Large surfaces

    24–32dp

### Sheets

    28–32dp

### Pills

    fully rounded

Do not randomly introduce different radii.

---

# 15. Buttons

Buttons should use a strong, simple visual language.

The preferred primary button language is:

> Large + simple + rounded + high contrast

---

## Primary Button

Characteristics:

- approximately 48–56dp height
- pill / highly rounded shape
- centered content
- medium typography
- generous horizontal padding
- strong contrast

Primary buttons should visually resemble:

    ┌──────────────────────────────────┐
    │            Continue              │
    └──────────────────────────────────┘

The button should feel substantial without becoming oversized.

Use the brand primary color for standard Vachak actions.

Near-black may be used for exceptionally strong emphasis where appropriate.

---

# 16. Secondary Buttons

Secondary buttons should be visually quieter.

Preferred:

- outlined
- tonal
- low-contrast surface

Example:

    ┌──────────────────────────────────┐
    │              Cancel              │
    └──────────────────────────────────┘

Primary and secondary buttons should clearly belong to the same design
system.

---

# 17. Button Hierarchy

Use:

### Primary

The one action the user most likely wants.

### Secondary

Useful alternative action.

### Tertiary

Low-emphasis utility action.

Avoid having multiple competing primary buttons.

---

# 18. Icon Buttons

Icon buttons should be simple and consistent.

Typical size:

    40–48dp

Use:

- circular
- rounded-square
- transparent

depending on context.

Icons should never dominate the text/content.

---

# 19. Icons

Use one coherent icon family.

Preferred characteristics:

- outline-oriented
- clean
- geometric
- restrained
- consistent stroke weight

Avoid mixing unrelated icon styles.

Icons should communicate function.

Do not use icons merely to decorate every piece of text.

---

# 20. Pills

Pills are a core Vachak control.

Use for:

- filters
- categories
- states
- modes
- tags
- compact selections
- status
- metadata

Example:

    [ All ] [ Grade 1 ] [ Grade 2 ]

Selected:

    strong contrast

Unselected:

    soft tonal surface

Pills should be functional rather than decorative.

---

# 21. Inputs

Inputs should use soft surfaces rather than conventional harsh rectangles.

Preferred characteristics:

- 48–56dp minimum height
- 16–20dp radius
- subtle background
- minimal border
- clear placeholder
- readable text
- optional leading/trailing icon

Example:

    ┌──────────────────────────────────┐
    │  Search lessons...               │
    └──────────────────────────────────┘

---

# 22. Input States

Every input should have clear:

- default
- focused
- filled
- disabled
- error

states.

Focus should be obvious without relying solely on color.

Do not make focused fields visually aggressive.

---

# 23. Lists

Lists should remain lightweight.

Prefer:

    icon    Title                         >
            Supporting information

Use dividers sparingly.

Do not turn every list row into an independent card.

Use cards when the item represents an independent content object.

Use rows when the items form a continuous collection.

---

# 24. Sections

Sections establish hierarchy without requiring additional cards.

Preferred:

    Section heading
    Supporting description

    grouped content

Use sections to break large screens into understandable conceptual
groups.

Do not over-section small amounts of information.

---

# 25. Borders

Borders should be subtle.

Use borders only when they improve:

- separation
- control recognition
- accessibility
- interaction clarity

Avoid decorative borders.

Do not outline every card.

---

# 26. Elevation

Elevation should be almost invisible.

Normal cards:

    approximately 0–2dp

Floating surfaces:

    approximately 2–4dp

Dialogs / sheets:

    higher separation when necessary

Avoid:

- dramatic shadows
- dark shadows
- glowing shadows
- excessive blur

The interface should feel lightweight.

---

# 27. Organic Visual Language

Vachak may use subtle organic geometry as a visual identity layer.

Examples:

- partial circles
- arcs
- curved lines
- soft abstract forms
- cropped geometric shapes

These elements should primarily appear behind content.

They should:

- extend beyond boundaries
- remain partially cropped
- use low visual weight
- avoid interfering with interaction
- never reduce readability

Decoration should feel editorial rather than ornamental.

---

# 28. Imagery

Images should be treated as part of the composition.

Prefer:

- large crops
- rounded image surfaces
- circular avatars
- full-bleed imagery where appropriate
- soft/asymmetric cropping

Avoid:

- tiny decorative thumbnails
- unnecessary image borders
- excessive stock imagery
- generic corporate illustrations

---

# 29. Illustration Style

Illustration should be:

- simple
- human
- educational
- soft
- culturally respectful
- restrained

Avoid generic "AI startup" artwork.

Avoid excessive 3D renders.

Avoid overly futuristic imagery.

---

# 30. Navigation Language

Navigation components should follow the same visual system as the rest
of the application.

Navigation should be:

- quiet
- compact
- recognizable
- consistent
- subordinate to content

Navigation should not become the visual focus of a screen.

---

# 31. Floating Elements

Floating elements may be used for:

- primary creation actions
- contextual actions
- temporary status
- overlays

Use them sparingly.

Floating UI should have a clear functional reason.

Never add floating elements merely because they look modern.

---

# 32. Responsive Behavior

The visual system must work across:

- Android phones
- Android tablets
- larger displays

Do not simply scale the phone UI upward.

As available space increases:

- increase whitespace
- increase content width appropriately
- introduce secondary columns when useful
- preserve readable line lengths
- maintain hierarchy

The design should remain intentional at every size.

---

# 33. Mobile

Mobile should prioritize:

- single-column content
- large touch targets
- clear hierarchy
- generous margins
- simple navigation
- accessible typography

Default horizontal content margin:

    approximately 20–24dp

---

# 34. Tablet

Tablet should make better use of horizontal space.

Prefer:

- constrained reading widths
- larger editorial compositions
- secondary content areas
- asymmetric layouts where appropriate

Do NOT stretch every card across the entire screen.

---

# 35. Touch Targets

Interactive controls should generally provide:

    approximately 44dp minimum touch target

Spacing between controls should prevent accidental activation.

Visual compactness must never compromise touch usability.

---

# 36. Motion

Motion should be:

- subtle
- purposeful
- short
- smooth

Typical transition range:

    150–300ms

Use animation for:

- state changes
- navigation
- expansion
- selection
- feedback
- spatial relationships

Avoid animation that exists only for spectacle.

---

# 37. Loading

Loading states should preserve the intended visual structure where
possible.

Prefer:

- contextual skeletons
- subtle progress indicators
- restrained spinners

Avoid:

    huge empty card
    +
    tiny spinner

The user should understand what is loading.

---

# 38. Empty States

Empty states should never look like broken UI.

An empty state should communicate:

    What is empty?
    ↓
    Why?
    ↓
    What can the user do?

Preferred structure:

    Short heading

    Brief explanation

    [ Primary action ]

Avoid excessive illustrations or text.

---

# 39. Error States

Errors should be calm and actionable.

Preferred structure:

    What happened?

    Short explanation.

    [ Retry / Fix ]

Technical information should remain secondary unless the user needs it.

---

# 40. Accessibility

Accessibility is part of the visual system.

Maintain:

- readable text
- sufficient contrast
- large touch targets
- clear selected states
- visible focus states
- semantic controls
- readable Hindi
- readable Ol Chiki
- no color-only communication

Never sacrifice readability for aesthetics.

---

# 41. Offline Visual Language

Offline capability is part of Vachak's identity.

Offline should feel like:

> "Ready to work."

not:

> "Something is wrong."

Offline indicators should therefore be:

- visible
- reassuring
- compact
- non-alarming

Do not use warning/error styling for normal offline operation.

---

# 42. Component Consistency

All screens must share the same:

- typography
- spacing scale
- radius scale
- button language
- input language
- surface hierarchy
- icon language
- animation behavior

A new screen should look like it belongs to Vachak even if it introduces
a new feature.

---

# 43. Reusable Component Philosophy

Build visual primitives once and reuse them.

Examples:

    Surface
    Card
    Button
    SecondaryButton
    IconButton
    Pill
    Input
    Section
    Badge
    ListItem
    Avatar
    Progress
    Sheet

Page-specific components may compose these primitives.

Avoid creating isolated visual systems for individual pages.

---

# 44. Material 3 Usage

Material 3 may remain the underlying implementation system.

However:

> Material 3 is an implementation foundation, not the visual identity.

Do not allow default Material 3 styling to dictate the final appearance.

Customize:

- shapes
- colors
- typography
- spacing
- elevation
- component treatment

to match the Vachak design language.

---

# 45. Design Independence

The design system must remain valid if:

- the color palette changes
- the content changes
- the number of cards changes
- a screen has no illustration
- a screen has no image
- a screen has very little information

The design should be based on structure, not decoration.

---

# 46. Visual Restraint

When deciding between:

    more decoration

and:

    more whitespace

prefer whitespace.

When deciding between:

    more colors

and:

    stronger hierarchy

prefer hierarchy.

When deciding between:

    more components

and:

    simpler composition

prefer simpler composition.

---

# 47. Anti-Patterns

Do NOT:

- copy reference screenshots literally
- reproduce another application's components
- use excessive glassmorphism
- use excessive gradients
- use neon colors
- use heavy shadows
- use excessive borders
- put every element inside a card
- nest cards unnecessarily
- use five different button styles
- use inconsistent corner radii
- make every element a pill
- overuse floating elements
- overuse icons
- use tiny text
- create dense dashboards
- fill whitespace unnecessarily
- use decoration behind important text
- use color as the only hierarchy mechanism
- sacrifice accessibility for visual aesthetics

---

# 48. Reference Interpretation

The visual references establish a DESIGN LANGUAGE, not a component library.

Extract from the references:

- generous whitespace
- large typography
- soft surfaces
- rounded geometry
- modular cards
- strong primary buttons
- quiet secondary buttons
- pill controls
- minimal icons
- subtle elevation
- restrained decoration
- editorial composition

Do NOT extract:

- exact layouts
- exact components
- exact colors
- exact text
- exact illustrations
- exact navigation
- exact screen structure

The application must remain distinctly Vachak.

---

# 49. Design Personality

The desired impression is:

> "A premium educational product designed with care."

Not:

> "A dashboard assembled from components."

The UI should feel authored.

Every visual decision should have a purpose.

---

# 50. Final Universal Rule

For every new component or screen, evaluate it using these questions:

1. Is the primary information immediately clear?
2. Is there enough whitespace?
3. Does typography establish hierarchy?
4. Does the component belong to the Vachak surface system?
5. Is the primary action obvious?
6. Is the component unnecessarily complex?
7. Is color being overused?
8. Is decoration competing with content?
9. Does it remain readable and accessible?
10. Would this still look good if all decoration were removed?

If the answer to the last question is NO,
the design relies too heavily on decoration.

The final product should derive its visual quality from:

    Typography
        +
    Spacing
        +
    Proportion
        +
    Surface
        +
    Shape
        +
    Restraint

rather than visual effects.
