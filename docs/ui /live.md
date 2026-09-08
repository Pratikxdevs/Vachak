# Vachak — Live Translation Screen

## 1. Screen Identity

Live is Vachak's real-time bilingual conversation workspace.

The screen should feel closer to a modern chat application than a
traditional translator form.

The core mental model is:

    Conversation
        ↓
    Speak or type Hindi
        ↓
    Hindi transcription
        ↓
    Santali translation
        ↓
    Optional audio playback
        ↓
    Conversation history

The user should be able to continue a conversation naturally without
losing previous translations.

---

# 2. Primary Goal

The Live screen must support two input methods:

    Voice
    Manual Hindi text

Both methods produce the same conversation structure.

Voice:

    Hindi speech
        ↓
    live Hindi transcription
        ↓
    completed Hindi message
        ↓
    Santali translation
        ↓
    conversation history

Manual:

    Hindi text
        ↓
    Santali translation
        ↓
    conversation history

The UI should not treat these as two completely different experiences.

---

# 3. Core Experience

The screen is composed of four major areas:

    HEADER
       ↓
    VOICE / INPUT AREA
       ↓
    CONVERSATION HISTORY
       ↓
    TEXT INPUT

Bottom navigation remains persistent.

Conceptually:

    ┌───────────────────────────────────────────┐
    │ ←  Live Translation       History   •••  │
    │    Hindi → Santali (Ol Chiki)            │
    │                                           │
    │            Voice Area                    │
    │                                           │
    │        microphone / listening            │
    │                                           │
    │───────────────────────────────────────────│
    │                                           │
    │ Conversation                             │
    │                                           │
    │ Hindi message                            │
    │ Santali translation                      │
    │                                           │
    │ Hindi message                            │
    │ Santali translation                      │
    │                                           │
    │───────────────────────────────────────────│
    │ Type in Hindi...                     ➤   │
    │───────────────────────────────────────────│
    │ Home   Live   Learn   Tools   Settings   │
    └───────────────────────────────────────────┘

---

# 4. Important Design Principle

The conversation history is NOT secondary debug information.

It is the primary content of the Live screen.

The application should preserve enough vertical space for multiple
long messages.

Do NOT design Live around a single translation card.

Bad:

    microphone
    ↓
    one Hindi card
    ↓
    one Santali card
    ↓
    buttons

Good:

    microphone / input
    ↓
    persistent conversation
    ↓
    multiple Hindi + Santali message pairs

---

# 5. Header

Header:

    ←    Live Translation                    ◷   •••
         Hindi → Santali (Ol Chiki)

Back button:

- 44dp+ touch target
- circular or subtle rounded control
- no heavy container

Title:

    Live Translation

Subtitle:

    Hindi → Santali (Ol Chiki)

Right controls:

    History
    More

History opens conversation history if a separate history surface is
needed.

More may contain:

- Clear conversation
- Language information
- Diagnostics shortcut if appropriate

Do not put technical controls directly in the header.

---

# 6. Offline Indicator

The Live screen should communicate offline availability.

Use a compact badge:

    ● Offline   ✓

Place it near the header.

It should feel like a status indicator rather than a warning.

Do NOT display:

    NO INTERNET!!!

Offline operation is expected behavior.

---

# 7. Voice Area

The voice interaction area sits above the conversation.

It is NOT allowed to consume the majority of the screen once the
conversation becomes long.

The voice area should be able to compact.

---

# 8. Voice Area — Idle

Idle state:

    Listening / microphone control

Example:

        ◉
      microphone

    Tap to speak

Keep it visually prominent when there is no active conversation.

---

# 9. Voice Area — Listening

When the user speaks:

    microphone
        ↓
    active visualizer
        ↓
    live transcription

Display:

    Listening...

and:

    Speak in Hindi

The microphone should visually communicate that it is active.

Use restrained lavender animation.

---

# 10. Live Hindi Transcription

This is critical.

Hindi speech MUST be displayed while the user is speaking.

The user should not need to wait until recording ends to understand
whether Vachak heard them correctly.

Example:

    Listening...

    नमस्ते, आप कैसे हैं?
    आज हम क्या सीखेंगे?

The transcription area should update continuously.

---

# 11. Live Transcription Surface

During speech:

    ┌──────────────────────────────────────────┐
    │ Hindi · Live                             │
    │                                          │
    │ नमस्ते, आप कैसे हैं?                     │
    │ आज हम क्या सीखेंगे?                     │
    │                                          │
    │                       Listening...       │
    └──────────────────────────────────────────┘

Use a soft lavender surface.

The text must be large enough to read while speaking.

Do NOT show individual ASR tokens flashing rapidly.

Update the sentence naturally.

---

# 12. Long Speech

The user may speak long sentences or paragraphs.

The transcription surface MUST support:

- multiple lines
- long sentences
- paragraph wrapping
- scrolling when necessary

Never truncate useful speech.

Do NOT use:

    "Namaste, aap kaise hain?..."

when more text exists.

The user should be able to see the full transcription.

---

# 13. Conversation History

Conversation history occupies the primary content region.

Example:

    Conversation · Today, 10:25 AM

    Hindi
    नमस्ते, आप कैसे हैं? आज हम क्या सीखेंगे?

    Santali
    ᱡᱚᱦᱟᱨ...

    Hindi
    आज हम 'अ' से अनार के बारे में जानेंगे।

    Santali
    ᱟ...

Each translation exchange forms one conversational unit.

---

# 14. Message Pairing

A single user interaction creates:

    Hindi message
         +
    Santali translation

They should visually belong together.

Do not make the interface look like a generic messaging app where
Hindi and Santali are unrelated bubbles.

Use clear language labels.

Example:

    Hindi (Live)
    10:21 AM

    नमस्ते, आप कैसे हैं?

    ─────────────────

    Santali (Ol Chiki)
    10:21 AM

    ᱡᱚᱦᱟᱨ...

---

# 15. Hindi Message

Hindi messages use the brand's soft lavender treatment.

Header:

    Hindi (Live)

Optional:

    10:21 AM

Body:

    Hindi sentence

Optional secondary representation:

    Romanized text

The Romanized text should be visually subordinate.

It must never replace the original Hindi.

---

# 16. Santali Message

Santali translation uses a slightly different tonal treatment.

Header:

    Santali (Ol Chiki)

Body:

    Ol Chiki translation

Optional:

    Romanized Santali

The Ol Chiki text should be the primary representation.

Romanization is secondary.

---

# 17. Long Messages

Messages are expected to contain long educational sentences.

Cards must support:

    1 line
    2 lines
    5 lines
    10+ lines

without breaking the layout.

Use natural text wrapping.

Do not impose fixed card heights.

Avoid:

    maxLines = 2

for conversation content.

Do not use ellipsis for the primary message.

---

# 18. Message Actions

Each completed translation can expose lightweight actions.

Hindi:

    🔊 Play / read Hindi
    Copy

Santali:

    🔊 Play Santali
    Copy

Actions should remain subtle.

Do not show a row of five giant buttons on every message.

Use icons with accessible content descriptions.

---

# 19. Playback

Santali playback is an important action.

The user should be able to hear the generated Santali audio.

Default:

    speaker icon

Playing:

    animated / active speaker state

Stop:

    stop icon

Only one playback action should visually dominate at a time.

---

# 20. Translation State

When Hindi transcription completes:

    Hindi message
         ↓
    Translating...

Then:

    Santali message

During translation, use a lightweight inline loading state.

Example:

    Santali (Ol Chiki)

    Translating...

Do not replace the whole conversation with a loading screen.

---

# 21. Failed Translation

If translation fails:

    Santali

    Couldn't translate this message.

    [ Try Again ]

The original Hindi message MUST remain.

Never delete the user's speech because translation failed.

---

# 22. Failed Recognition

If ASR fails:

    Couldn't recognize that speech.

    [ Try Again ]

The conversation should remain intact.

Do not erase previous messages.

---

# 23. Manual Hindi Input

Manual input is ALWAYS available.

At the bottom:

    ┌─────────────────────────────────────────────┐
    │  ⌨   Type in Hindi...              Hindi › │
    └─────────────────────────────────────────────┘

with:

    Send / Translate button

Manual input must remain accessible even while conversation history
becomes long.

---

# 24. Manual Input Interaction

Tapping the input opens the keyboard.

The input should support:

- Hindi Unicode
- long sentences
- multiple lines
- paste
- cursor editing

Placeholder:

    Type in Hindi...

Do NOT use:

    Enter translation...

The user is entering source Hindi.

---

# 25. Manual Input + Voice

Voice and manual input should coexist.

Do not create:

    Voice Mode

and:

    Text Mode

as two unrelated screens.

Instead:

    Voice
    Type

are two input methods for the same conversation.

---

# 26. Input Mode Selector

Optional compact controls:

    [ 🎙 Voice ]    [ ⌨ Type Hindi ]

When Voice is selected:

    microphone becomes primary.

When Type is selected:

    text field becomes primary.

The conversation history remains unchanged.

---

# 27. Send / Translate

Manual Hindi input uses the primary action:

    Translate →

The action should be:

- black
- white text
- pill-shaped

or use the established Vachak primary action treatment.

On very narrow screens, an icon-only circular send button may be used.

---

# 28. Voice Interaction

Preferred behavior:

### Idle

Tap microphone:

    begin listening

### Listening

Display:

    live Hindi transcription

### Stop

On stop/release:

    finalize Hindi
        ↓
    translate
        ↓
    append conversation pair

### Playback

User can play Santali afterward.

---

# 29. Hold-to-Speak

A press-and-hold interaction may be supported.

When the user holds the microphone:

    microphone expands/activates
        ↓
    background softly obscures
        ↓
    live transcription appears
        ↓
    release
        ↓
    translation begins

The transition must remain lightweight.

Do NOT use a huge full-screen modal unless necessary.

---

# 30. Progressive Voice UI

During active recording:

    Background
        ↓
    subtle frosted/softened treatment

    Microphone
        ↓
    dominant

    Live transcription
        ↓
    visible

The effect should communicate:

> Vachak is listening to you.

Do not make the UI look like a completely different application.

---

# 31. Conversation Scroll

Conversation history is vertically scrollable.

Newest content should appear at the bottom.

After a new translation completes:

    automatically reveal the newest exchange

unless the user is intentionally reading older content.

Do not aggressively jump the user to the bottom while they are manually
scrolling through history.

---

# 32. Conversation Header

Above history:

    Conversation

Optional:

    Today, 10:25 AM

Right:

    Clear History

Clear History must require confirmation.

Example:

    Clear this conversation?

    This cannot be undone.

    Cancel     Clear

Do not accidentally delete the entire history with one tap.

---

# 33. History Persistence

Conversation history should be persistent.

When the user leaves Live and returns:

    previous conversation remains available

The UI should not reset to an empty translator unless the user explicitly
clears the conversation.

History persistence belongs to the data layer.

The UI only displays it.

---

# 34. Conversation Sessions

If conversations become large, history may be grouped by session/date.

Example:

    Today

    Conversation
    10:21 AM

    Yesterday

    Conversation
    16:40 PM

Do not implement a complex chat-management system until the product
actually needs it.

---

# 35. Empty Conversation

Initial Live state:

    microphone

    Speak in Hindi

    Your translations will appear here.

Do not display fake example messages in production.

---

# 36. Empty State Composition

Preferred:

        microphone

      Speak in Hindi

    Your translations will
       appear here.

    [ Type in Hindi ]

This keeps the screen useful before the first interaction.

---

# 37. Conversation Density

The screen must support a LOT of content.

Prioritize:

    readability
    vertical efficiency
    clear language separation

Avoid:

    giant empty spaces between messages
    huge decorative elements
    oversized headers
    unnecessary card padding

The voice area should shrink as the conversation grows.

---

# 38. Dynamic Voice Area

When conversation is empty:

    voice area = prominent

When conversation contains messages:

    voice area = compact

When actively listening:

    voice area = expanded temporarily

Conceptually:

    EMPTY

    [ LARGE MIC ]

    Conversation empty


    ACTIVE CONVERSATION

    [ compact mic / status ]

    Conversation
    message
    message
    message

This is essential for long sessions.

---

# 39. Compact Listening State

When history is long, listening should NOT push the entire conversation
off-screen.

Use:

    compact microphone
    + live transcription strip

Example:

    ┌────────────────────────────────────────┐
    │ ● Listening...                         │
    │ नमस्ते, आज हम पढ़ेंगे...               │
    └────────────────────────────────────────┘

Then the new conversation item appears.

---

# 40. Typography

Primary conversation text should be large and readable.

Hindi:

    22–28sp

Santali:

    22–28sp

Secondary Romanization:

    15–18sp

Language label:

    14–16sp

Timestamp:

    12–14sp

Do not reduce primary text simply to fit more messages.

---

# 41. Ol Chiki Typography

Ol Chiki must be treated as a first-class script.

Verify:

- correct font fallback
- adequate glyph size
- adequate line height
- no clipping
- no unexpected tofu boxes

Do not assume the system default font is sufficient.

---

# 42. Colors

Use the Vachak universal palette.

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

---

# 43. Message Color System

Hindi:

    soft lavender surface

Santali:

    very pale lavender / near-white surface

Do not use strong purple backgrounds.

The distinction between Hindi and Santali should come primarily from:

- label
- icon
- typography
- subtle tonal difference

not loud color.

---

# 44. Navigation

Persistent bottom navigation:

    Home
    Live
    Learn
    Tools
    Settings

Live selected.

Use the universal Vachak floating navigation style.

The navigation must remain above the keyboard appropriately.

When the keyboard is open, avoid obscuring the text input.

---

# 45. Keyboard Behavior

When manual input is focused:

    keyboard opens
    input remains visible
    conversation scrolls appropriately

The bottom navigation may temporarily move/recede according to standard
IME behavior.

Do not allow:

    keyboard
    +
    navigation
    +
    input
    +
    conversation

to overlap each other.

---

# 46. Input Above Navigation

Preferred hierarchy:

    conversation
        ↓
    input bar
        ↓
    bottom navigation

When keyboard opens:

    conversation
        ↓
    input bar
        ↓
    keyboard

Navigation may temporarily disappear or reposition if required.

---

# 47. Header Persistence

The header should remain compact.

Do not consume 20–25% of the screen with the header.

Live is primarily about the conversation.

---

# 48. Search / History

If persistent conversations eventually become numerous, the History
button may open:

    Conversation History

with:

    Today
    Yesterday
    Earlier

Each conversation:

    title / first sentence
    timestamp
    message count

However, this should be implemented only if the product requires
multiple independent conversation sessions.

---

# 49. No Internet Search

Live is NOT an internet search interface.

Do not introduce:

    Searching the internet...

or web result cards.

The Live screen is an offline translation experience.

---

# 50. Offline Architecture

Live must remain fully compatible with:

    ASR
    MT
    TTS

offline operation.

The UI should not assume a network connection.

---

# 51. Existing Engine Boundary

Do not rewrite:

- ASR engine
- translation engine
- TTS engine
- audio playback architecture
- model loading
- offline data layer

just to implement this UI.

The UI should consume the existing interfaces.

---

# 52. State Model

The Live UI should conceptually support:

    Empty
    Listening
    Transcribing
    Translating
    Ready
    Playing
    Error

Do not expose technical engine terminology to the user.

Map technical states to user-facing language.

Example:

    ASR running
        →
    Listening...

    Translation engine running
        →
    Translating...

    TTS running
        →
    Playing...

---

# 53. Loading Behavior

Do not show a full-screen spinner.

Translation happens inline.

Example:

    Hindi message

    Santali
    Translating...

Then the translation replaces the temporary state.

---

# 54. Message Actions

Each message may expose:

    Copy
    Play

Additional actions may live under overflow.

Do not show every possible action by default.

Keep the conversation visually clean.

---

# 55. Accessibility

All important information must be understandable without color.

Ensure:

- microphone has content description
- playback has content description
- copy has content description
- language labels are textual
- state is communicated with text
- touch targets >=44dp
- large text remains usable
- Ol Chiki remains readable

---

# 56. Animation

Use subtle animation.

Microphone:

    breathing / pulse

Message:

    fade / short slide

Translation:

    lightweight reveal

Playback:

    subtle active indicator

Do not animate the entire conversation.

Do not continuously animate every message.

---

# 57. Performance

The conversation can become very long.

Do NOT render an unbounded list using a regular Column.

Use:

    LazyColumn

for conversation history.

Use stable keys for message items.

Do not repeatedly rebuild the entire conversation when a new transcript
token arrives.

Live transcription should update efficiently.

---

# 58. Long Conversation Performance

For large histories:

- use LazyColumn
- avoid nested scrolling
- avoid expensive blur per message
- avoid huge images
- avoid complex Canvas effects
- avoid unnecessary recomposition
- keep message composables lightweight

The UI must remain responsive during ASR and translation.

---

# 59. Data Model Expectations

A conversation item conceptually contains:

    id
    timestamp
    hindiText
    santaliText
    inputMethod
    translationState
    playbackState
    errorState

Do not duplicate the same message into unrelated UI state structures.

Use the existing domain/data architecture where available.

---

# 60. Manual Input Persistence

Typed Hindi should not disappear if translation is temporarily processing.

If translation fails:

    preserve the entered text

Allow:

    retry

without requiring the user to type it again.

---

# 61. Error Recovery

Errors should be local.

Bad:

    Translation failed
    [ Restart Live ]

Good:

    Hindi message
        ↓
    Santali
    Couldn't translate
    [ Retry ]

Previous conversation remains usable.

---

# 62. Clear Conversation

Clear History is destructive.

Use confirmation.

After clearing:

    Empty Live state

Do not clear stored conversations accidentally when the user presses
"Clear" on a message.

---

# 63. Responsive Layout

## Phone

Primary structure:

    Header

    compact/prominent voice area

    conversation

    input

    bottom navigation

## Tablet

Use additional horizontal space.

Possible:

    ┌─────────────────────────────────────────────────┐
    │ Header                                          │
    │                                                 │
    │ Voice / current transcription                   │
    │                                                 │
    │ ┌─────────────────────────────────────────────┐ │
    │ │ Conversation                                │ │
    │ │                                             │ │
    │ │ Hindi                                       │ │
    │ │ Santali                                     │ │
    │ │                                             │ │
    │ └─────────────────────────────────────────────┘ │
    │                                                 │
    │ Input                                           │
    └─────────────────────────────────────────────────┘

Do not force a two-column conversation layout unless it materially
improves readability.

---

# 64. Tablet Conversation Option

For large tablet widths, a two-column language comparison MAY be used:

    ┌──────────────────────┬──────────────────────┐
    │ Hindi                │ Santali              │
    │                      │                      │
    │ long text            │ long translation     │
    │                      │                      │
    └──────────────────────┴──────────────────────┘

But this should remain a responsive variation of the same conversation
model.

Do not create a separate tablet feature.

---

# 65. Screen Composition Priority

Priority order:

    1. Conversation
    2. Live transcription
    3. Microphone
    4. Manual input
    5. Playback actions
    6. Header utilities
    7. Navigation

When screen space becomes limited, decorative elements disappear first.

Never sacrifice conversation readability.

---

# 66. What NOT to Do

Do NOT create:

- a giant microphone that permanently occupies half the screen
- fixed-height translation cards
- truncated long messages
- separate Voice and Text screens
- fake chat history
- fake translations
- internet search UI
- giant technical status panels
- model information in conversation
- excessive gradients
- excessive shadows
- excessive animation
- giant floating action buttons
- five different button styles

---

# 67. Visual Character

The screen should feel like:

    a calm bilingual conversation

not:

    a speech recognition demo

not:

    a translation API tester

not:

    a dashboard

not:

    a generic chatbot

The user should feel that Vachak is helping them communicate and teach.

---

# 68. Final Interaction

Ideal session:

    Open Live

        ↓

    Tap / hold microphone

        ↓

    Speak Hindi

        ↓

    See Hindi transcription LIVE

        ↓

    Stop speaking

        ↓

    Hindi message is committed

        ↓

    Santali translation appears

        ↓

    User can play Santali audio

        ↓

    New exchange is added to conversation

        ↓

    User speaks again

        ↓

    Conversation grows downward

        ↓

    User can also type Hindi manually at ANY point

        ↓

    Conversation persists after leaving Live

---

# 69. Definition of Done

Live is complete when:

[ ] Voice input works

[ ] Manual Hindi input works

[ ] Live Hindi transcription is visible

[ ] Long Hindi text wraps correctly

[ ] Long Santali text wraps correctly

[ ] Ol Chiki is readable

[ ] Translation appears inline

[ ] Conversation history persists

[ ] New messages append correctly

[ ] Old messages remain accessible

[ ] User can scroll history

[ ] New messages auto-scroll appropriately

[ ] Playback works

[ ] Copy works

[ ] Clear conversation works safely

[ ] Error states preserve user content

[ ] Loading happens inline

[ ] Voice area compacts with long history

[ ] Keyboard behavior is correct

[ ] Bottom navigation does not overlap content

[ ] Phone layout works

[ ] Tablet layout works

[ ] Offline indicator works

[ ] No network dependency is introduced

[ ] No engine/domain code was unnecessarily rewritten

[ ] No duplicate UI system was created

[ ] No unnecessary dependencies were added

[ ] Screen visually matches the Vachak design system

---

# 70. Final Principle

Live is not a translator screen with history attached.

Live IS the conversation.

The microphone and manual input are simply two ways of adding a new
conversation exchange.

The visual hierarchy must therefore prioritize:

    CONVERSATION
         ↑
    TRANSCRIPTION
         ↑
    INPUT
         ↑
    NAVIGATION

while maintaining the Vachak lavender / white / black visual identity.
