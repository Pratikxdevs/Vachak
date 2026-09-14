# Vachak Curriculum — Frozen SIH Specification

**Status: FROZEN.** Scope is the SIH problem statement only: minimum **one** tribal
language → **Santhali (Ol Chiki)**. No Ho, no Mundari, no English engine for the
prototype. The schema stays extensible (`target_language_code`) for later languages.

**Hard rules (from AGENTS.md, non-negotiable):**
- Curriculum translations are **precomputed**, never computed on device.
- Worksheets are **template-based**, never AI-generated.
- Flashcards are **prebuilt assets**, never generated.
- Never ship `MACHINE_TRANSLATED` content as approved pedagogy. Author-drafted
  seed content (see `curriculum/seed/provenance.json`) is **AUTHOR-DRAFT** until a
  native-speaker SME signs off.
- Fully offline after initial sync (Room/SQLite on device).

---

## 1. Structure (exactly this, nothing more)

```text
Vachak Curriculum
├── Grade 1 ──┐
├── Grade 2   │  each grade:
├── Grade 3   │   ├── Foundational Literacy
├── Grade 4   │   └── Foundational Numeracy
└── Grade 5 ──┘
```

Each domain holds:

```text
Literacy / Numeracy
├── Learning Outcomes
├── Lessons
├── Activities
├── Assessment Prompts
├── Worksheets
└── Flashcards
```

**Explicitly out of scope:** English curriculum, EVS/general knowledge engine, LMS,
attendance, marketplace, chatbot/AI-tutor, gamification, ERP, Classes 6–12,
three-language implementation, any cloud dependency during classroom use.

---

## 2. Learning Outcome is the backbone

```text
NIPUN Bharat Learning Outcome
             │
             ▼
          Lesson
             │
      ┌──────┼──────┐
      ▼      ▼      ▼
   Script Activity Assessment
      │      │      │
      └──────┼──────┘
             ▼
     Hindi → Santhali
             │
       ┌─────┴─────┐
       ▼           ▼
   Worksheet    Flashcards
```

Worksheets and flashcards must be **aligned to NIPUN Bharat learning outcomes** —
never random generation. Current outcome inventory: `curriculum/outcomes/nipun.json`
(author-drafted descriptors aligned to public NIPUN FLN competencies, locally assigned
codes `G{grade}-{domain}-{seq}`; SME verification required before shipping).

---

## 3. Lesson = atomic teaching unit

```text
Lesson
├── lesson script   (what the teacher says/does)
├── activities      (what children do)
├── assessment prompts (how the teacher checks understanding)
├── worksheet       (practice, generated from the lesson)
└── flashcards      (visual reinforcement, generated from the lesson)
```

Lesson ID convention: `G{grade}-{LITERACY|NUMERACY}-{TOPIC}-{seq}`
(e.g. `G2-MATH-COUNT-001` → prefer `G2-NUM-COUNT-001`; keep one convention).

---

## 4. Language model

Every textual object ships in two versions:

```text
Hindi → Santhali (text, precomputed)
```

Spoken content:

```text
Hindi text → Santhali translation → Santhali TTS (when voice ships)
```

Per object:

```text
Lesson Script : Hindi text + Santhali text + Santhali audio
Activity      : Hindi instruction + Santhali instruction + Santhali audio
Assessment    : Hindi prompt + Santhali prompt + Santhali audio
```

---

## 5. Grade 1–5 domains

```text
GRADE 1..5 (each): Literacy + Numeracy
```

Topics are NOT invented: each grade's authoritative NIPUN learning outcomes
determine its lessons. `curriculum/seed/` holds the current author-drafted seeds
(`seed_lessons.json`, `grade2_math_counting.json`, `lesson_package_L-COUNT-G2.json`,
`seed.sql` + `export_sql.py` → Room).

---

## 6. Data model (Room)

```text
grades(id, name)
domains(id, grade_id, name)                     -- literacy | numeracy
learning_outcomes(id, grade_id, domain_id, nipun_code, description_hi, source)
lessons(id, learning_outcome_id, title_hi, sequence, duration, source)
lesson_scripts(id, lesson_id, text_hi, text_sat, audio_sat)
activities(id, lesson_id, title_hi, instruction_hi, instruction_sat, audio_sat, materials, sequence)
assessment_prompts(id, lesson_id, prompt_hi, prompt_sat, audio_sat, expected_response)
worksheets(id, lesson_id, learning_outcome_id, title, version, content)
flashcards(id, lesson_id, learning_outcome_id, image, front_hi, back_sat, audio_sat)
```

---

## 7. Worksheet generation (template-based, deterministic, offline)

```text
Learning Outcome → Lesson → Concepts → Question Generator
→ Worksheet Template → Hindi + Santhali → PDF / printable
```

Never `LLM → "make me a worksheet"`.

Question types by domain:

Literacy: picture→word, word→picture, letter/sound recognition, word matching,
sentence completion, reading comprehension, true/false, oral response.
Numeracy: count objects, identify number, number sequence, before/after,
greater/less, matching quantity, addition, subtraction, shapes, patterns,
measurement.

```text
Learning Outcome → allowed question types → generate N → validate
→ translate (precomputed/glossary) → teacher review → worksheet
```

Every worksheet: HEADER (Vachak, grade, domain, lesson, LO) + QUESTIONS
(each: Hindi, Santhali, optional image, answer, type, LO) + FOOTER
(teacher / date / score). Genuinely bilingual.

---

## 8. Flashcard generation (same engine, visual renderer)

```text
Learning Outcome → lesson concepts → flashcard generator → visual template
(front_hi + back_sat + image + audio_sat + LO/lesson ids)
```

Generated **from the curriculum**, never independently.

---

## 9. Teacher workflow (app maps 1:1)

```text
Grade → Domain → Lesson → Teach 📖 | Activity 🎯 | Assessment 📝 | Worksheet 📄 | Flashcards 🃏
```

- **Teach:** Hindi script → Santhali → 🔊 audio
- **Activity:** Hindi + Santhali instructions → 🔊 audio
- **Assessment:** prompt + Santhali → teacher asks child
- **Worksheet:** generate (5/10/15 Qs) → Hindi + Santhali → printable PDF
- **Flashcards:** concept cards → images + Hindi + Santhali + audio

---

## 10. Where AI belongs

```text
Authoritative LO + lesson content (controlled)
  → AI-assisted localization (translation)
  → Teacher validation → Approved content
  → Worksheet / Flashcard generators (deterministic templates)
```

AI assists localization only. Authoritative outcomes and source stay controlled.

---

## 11. Offline architecture

```text
Curriculum Builder → .vachakcontent → Tablet → SQLite/Room
(Grade, Domain, LOs, Lessons, Activities, Assessments, Worksheets,
Flashcards, Hindi, Santhali, Audio, Images — no internet)
```

---

## 12. Books (source textbooks, Grades 1–5)

Lesson source text is authored **from the physical Grade 1–5 Hindi FLN textbooks
the team holds** — never invented. Each seed lesson records its source book +
chapter in `source` (see `curriculum/seed/provenance.json` pattern).

| Grade | Literacy source book (TO-CONFIRM exact edition) | Numeracy source book (TO-CONFIRM exact edition) |
|-------|--------------------------------------------------|--------------------------------------------------|
| 1 | Hindi Rimjhim / Bal Bharati G1 (confirm title) | Ganit Ka Jadu / Bal Bharati Math G1 (confirm title) |
| 2 | Hindi Rimjhim G2 (confirm title) | Ganit Ka Jadu G2 (confirm title) |
| 3 | Hindi Rimjhim G3 (confirm title) | Ganit Ka Jadu G3 (confirm title) |
| 4 | Hindi Rimjhim G4 (confirm title) | Ganit Ka Jadu G4 (confirm title) |
| 5 | Hindi Rimjhim G5 (confirm title) | Ganit Ka Jadu G5 (confirm title) |

Rule: every `lessons.source` cites `book + chapter/page`. Lessons without a
source citation stay `AUTHOR-DRAFT` and do not ship. (Table titles above are the
standard NCERT/Jharkhand SCERT FLN series — replace with the exact editions held
before seeding.)

---

## 13. Judge diagram

```text
                  NIPUN BHARAT
                LEARNING OUTCOME
                       │
                       ▼
              ┌─────────────────┐
              │     LESSON      │
              └────────┬────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
   LESSON SCRIPT    ACTIVITY      ASSESSMENT
        │              │              │
        └──────────────┼──────────────┘
                       ▼
                HINDI → SANTHALI
                       │
              ┌────────┴────────┐
              ▼                 ▼
         TEXT + AUDIO       VISUALS
              │                 │
              ▼                 ▼
        BILINGUAL           VISUAL
        WORKSHEET          FLASHCARDS
```

Next concrete artifact: Grade 1–5 curriculum manifest (`curriculum.json`/SQLite
seed) where every lesson has a source citation + NIPUN outcome mapping.
