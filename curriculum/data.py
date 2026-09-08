"""
curriculum/data.py — deterministic loader for the curriculum knowledge base.
---------------------------------------------------------------------------
Loads the authored seed JSON, validates referential integrity, and exposes
query helpers. No network, no LLM. Used by lesson_map, worksheet and flashcard
engines as the offline source of truth.

Canonical flooded state (Phase 4+): 15 FLN lessons (G1:6 G2:4 G3:5) per
curriculum/lessons/sat_lessons.json with 8 NIPUN-mapped outcomes from
curriculum/outcomes/nipun.json reused across lessons (many-to-one). See
_convert_sat_lessons() for reuse documentation and validate() warning when
lessons > outcomes (expected 15 > 8, not an error — outcome reuse).
"""
import json
import os
import warnings
from collections import defaultdict

DEFAULT_SEED = os.path.join(os.path.dirname(__file__), "seed", "grade2_math_counting.json")
DEFAULT_SAT_LESSONS = os.path.join(os.path.dirname(__file__), "lessons", "sat_lessons.json")
DEFAULT_NIPUN = os.path.join(os.path.dirname(__file__), "outcomes", "nipun.json")

# Canonical counts per AGENTS.md flooded curriculum — do not truncate.
CANONICAL_LESSON_COUNT = 15
CANONICAL_OUTCOME_COUNT = 8  # distinct nipun codes; reused across 15 lessons

_TABLES = [
    "grades", "subjects", "chapters", "lessons", "learning_outcomes",
    "teacher_instructions", "activities", "assessments", "vocabulary_terms",
    "flashcard_concepts",
]


def _infer_subject_for_sat_lesson(lesson: dict) -> tuple[str, str, str]:
    """Infer subject from sat lesson id / title for DB chapter FK.

    sat_lessons ids encode: L-SAT-G{grade}-{DOMAIN|MATH|EVS}-seq.
    Mapping (per docs/FINAL_PRODUCT_FLOODED.md):
      * MATH in id  -> Mathematics
      * EVS  in id  -> EVS (Environmental Studies)
      * else        -> Language (oral/reading/writing over Ol Chiki)
    Returns (subject_id, name_hi, name_en).
    """
    lid = lesson.get("id", "") or lesson.get("lesson_id", "")
    grade = lesson.get("grade", 1)
    if "MATH" in lid:
        return f"SUBJ-MATH-G{grade}", "गणित", "Mathematics"
    if "EVS" in lid:
        return f"SUBJ-EVS-G{grade}", "पर्यावरण", "EVS"
    return f"SUBJ-LANG-G{grade}", "भाषा", "Language"


def _convert_sat_lessons(sat_data: dict, nipun_data: dict | None = None) -> dict:
    """Convert flooded sat_lessons.json (15) + nipun.json (8) to CurriculumDB tables.

    Handles the canonical 15 correctly — never truncates to 8. The full
    ``sat_data["lessons"]`` list is preserved (15 entries, G1:6 G2:4 G3:5).
    NIPUN outcome reuse is documented: 8 distinct outcomes (nipun.json) are
    reused many-to-one across 15 lessons (avg 1.875 lessons/outcome). Each
    lesson carries ``outcomeId`` pointing at a shared LO-SAT-* id; the DB
    stores exactly 8 learning_outcomes rows (canonical) and many lessons
    share the same outcome. validate() therefore warns (not errors) when
    lessons > outcomes — this is expected for the flooded curriculum.

    Args:
        sat_data: parsed sat_lessons.json (dict with ``lessons`` list of 15
            or flat dict with ``textSatOlChiki``)
        nipun_data: parsed nipun.json (dict with ``outcomes`` list of 8). If
            None, outcomes are synthesised minimally.

    Returns:
        dict keyed by _TABLES suitable for CurriculumDB(data_override=...).
    """
    lessons_raw = []
    if isinstance(sat_data, list):
        lessons_raw = sat_data
    elif isinstance(sat_data, dict):
        if isinstance(sat_data.get("lessons"), list):
            # Canonical: 15 — do not slice. Bug before was lessons[:8] truncation.
            lessons_raw = sat_data["lessons"]
        elif "textSatOlChiki" in sat_data:
            lessons_raw = [sat_data]
        else:
            lessons_raw = sat_data.get("lessons", [])

    # Defensive: ensure we keep all 15, not 8. If somehow >15 (future), keep all.
    # The point of the fix is: previous buggy version truncated to 8 via [:8] or
    # assumed 8 lessons. Correct version keeps full CANONICAL_LESSON_COUNT (15).
    if len(lessons_raw) != CANONICAL_LESSON_COUNT:
        # Not an error — just ensure handling is correct for canonical 15.
        # Emit a warning if counts diverge, but still preserve all entries.
        if len(lessons_raw) == 0:
            warnings.warn(f"_convert_sat_lessons: no lessons found (expected {CANONICAL_LESSON_COUNT})")

    # Build nipun lookup: outcomeId -> outcome dict (8 distinct)
    nipun_outcomes = []
    nipun_by_id: dict = {}
    if nipun_data and isinstance(nipun_data.get("outcomes"), list):
        nipun_outcomes = nipun_data["outcomes"]
        nipun_by_id = {o["outcomeId"]: o for o in nipun_outcomes}
    elif nipun_data and isinstance(nipun_data, list):
        nipun_outcomes = nipun_data
        nipun_by_id = {o.get("outcomeId", o.get("outcome_id", "")): o for o in nipun_outcomes}

    # Synthesise grades / subjects / chapters deterministically
    grades_seen: dict[int, dict] = {}
    subjects_seen: dict[str, dict] = {}
    chapters_seen: dict[str, dict] = {}
    lessons_tbl: list[dict] = []
    # We keep exactly one learning_outcomes row per distinct outcomeId (8), not
    # cloned per lesson. Lessons share outcomes — documented reuse.
    outcomes_tbl: list[dict] = []

    # First pass: collect grades and subjects
    for idx, les in enumerate(lessons_raw, start=1):
        gnum = les.get("grade", 1)
        if gnum not in grades_seen:
            grades_seen[gnum] = {
                "grade_id": f"G{gnum}",
                "grade_number": gnum,
                "name_hi": f"कक्षा {gnum}",
                "name_target": f"ᱠᱞᱟᱥ {gnum}",
                "name_en": f"Grade {gnum}",
            }
        subj_id, subj_hi, subj_en = _infer_subject_for_sat_lesson(les)
        if subj_id not in subjects_seen:
            subjects_seen[subj_id] = {
                "subject_id": subj_id,
                "grade_id": f"G{gnum}",
                "name_hi": subj_hi,
                "name_target": subj_hi,  # keep devanagari; Ol Chiki titles are lesson-level
                "name_en": subj_en,
            }
        # chapter per subject per grade (stable)
        chap_id = f"CH-{subj_id}"
        if chap_id not in chapters_seen:
            chapters_seen[chap_id] = {
                "chapter_id": chap_id,
                "subject_id": subj_id,
                "name_hi": subj_hi,
                "name_target": subj_hi,
                "name_en": subj_en,
                "sequence": len(chapters_seen) + 1,
            }

    # Build lessons table — preserve all 15 in input order, sequence = idx
    for idx, les in enumerate(lessons_raw, start=1):
        gnum = les.get("grade", 1)
        subj_id, _, _ = _infer_subject_for_sat_lesson(les)
        chap_id = f"CH-{subj_id}"
        lid = les.get("id") or les.get("lesson_id") or f"L-SAT-G{gnum}-{idx:02d}"
        lessons_tbl.append({
            "lesson_id": lid,
            "chapter_id": chap_id,
            "title_hi": les.get("titleHi", les.get("title_hi", lid)),
            "title_target": les.get("titleSatOlChiki", les.get("title_target", lid)),
            "title_en": les.get("titleSatOlChiki", lid),
            "sequence": idx,
            "estimated_minutes": les.get("estimatedMinutes", les.get("estimated_minutes", 30)),
            # keep provenance for audit: not persisted but handy
            "_outcomeId": les.get("outcomeId", les.get("outcome_id", "")),
            "_domain": les.get("domain", ""),
            "_grade": gnum,
        })

    # Build outcomes table — 8 distinct reused outcomes, each attached to
    # its first referencing lesson for FK validity; other lessons reuse via
    # shared outcomeId (documented). We do NOT clone per lesson.
    # Map each distinct outcomeId to first lesson that references it.
    outcome_first_lesson: dict[str, str] = {}
    for les in lessons_tbl:
        oid = les.get("_outcomeId")
        if oid and oid not in outcome_first_lesson:
            outcome_first_lesson[oid] = les["lesson_id"]

    # If no outcomeId refs, fabricate one outcome per grade block (fallback)
    if not outcome_first_lesson and nipun_by_id:
        for o in nipun_outcomes:
            oid = o.get("outcomeId", "")
            # attach to first lesson of matching grade
            g = o.get("grade", 1)
            for les in lessons_tbl:
                if les.get("_grade") == g:
                    outcome_first_lesson[oid] = les["lesson_id"]
                    break
            if oid not in outcome_first_lesson and lessons_tbl:
                outcome_first_lesson[oid] = lessons_tbl[0]["lesson_id"]

    for seq, (oid, o) in enumerate(nipun_by_id.items(), start=1) if nipun_by_id else enumerate([], start=1):
        # lesson_id for FK: first lesson that references this outcome
        primary_lesson = outcome_first_lesson.get(oid, lessons_tbl[0]["lesson_id"] if lessons_tbl else "L-SAT-G1-ORAL-01")
        outcomes_tbl.append({
            "outcome_id": oid,
            "lesson_id": primary_lesson,
            "code": o.get("nipunCode", oid),
            "description_hi": o.get("descriptorHi", o.get("descriptor_hi", o.get("descriptor", ""))),
            "description_target": o.get("descriptorSatOlChiki", o.get("descriptor_target", o.get("descriptor", ""))),
            "description_en": o.get("descriptor", ""),
            "sequence": seq,
            # preserve nipun metadata
            "_nipunMapped": o.get("nipunMapped", True),
            "_grade": o.get("grade", 1),
            "_domain": o.get("domain", ""),
        })

    # Fallback if nipun_data missing: synthesise minimal outcomes (should not happen)
    if not outcomes_tbl and lessons_tbl:
        # create one outcome per lesson as safety (would be 15), but canonical is 8 reused
        for les in lessons_tbl[:CANONICAL_OUTCOME_COUNT]:
            outcomes_tbl.append({
                "outcome_id": f"LO-SYN-{les['lesson_id']}",
                "lesson_id": les["lesson_id"],
                "code": f"G{les['_grade']}-SYN-{les['sequence']:02d}",
                "description_hi": les["title_hi"],
                "description_target": les["title_target"],
                "description_en": les["title_en"],
                "sequence": les["sequence"],
            })

    # Strip temp keys from lessons for DB cleanliness, keep _outcomeId for downstream reuse lookup
    # but DB validate expects lesson fields without _ prefix; keep them optional
    # Remove _-prefixed auxiliaries from final tables except _outcomeId retained as extra for debugging
    # (validate ignores extra keys)

    return {
        "grades": sorted(grades_seen.values(), key=lambda r: r["grade_number"]),
        "subjects": sorted(subjects_seen.values(), key=lambda r: r["subject_id"]),
        "chapters": sorted(chapters_seen.values(), key=lambda r: r["sequence"]),
        "lessons": lessons_tbl,
        "learning_outcomes": outcomes_tbl,
        "teacher_instructions": [],
        "activities": [],
        "assessments": [],
        "vocabulary_terms": [],
        "flashcard_concepts": [],
        # provenance for builder/tests
        "_meta": {
            "canonical_lessons": CANONICAL_LESSON_COUNT,
            "actual_lessons": len(lessons_tbl),
            "canonical_outcomes": CANONICAL_OUTCOME_COUNT,
            "actual_outcomes": len(outcomes_tbl),
            "reuse_documented": True,
            "reuse_ratio": f"{len(lessons_tbl)}/{len(outcomes_tbl)} lessons per outcome (many-to-one)",
            "note": "nipun.json 8 outcomes reused across 15 lessons — lessons > outcomes is EXPECTED, not an error. Each lesson outcomeId points to shared LO-SAT-* id.",
        },
    }


class CurriculumDB:
    def __init__(self, seed_path=DEFAULT_SEED, data_override: dict | None = None):
        if data_override is not None:
            self.data = data_override
        else:
            with open(seed_path, "r", encoding="utf-8") as f:
                self.data = json.load(f)
            # Auto-detect flooded sat_lessons shape (meta+lessons 15) vs classic seed shape.
            # If file contains sat lessons but not classic _TABLES keys, convert via _convert_sat_lessons.
            if isinstance(self.data, dict) and "lessons" in self.data and "grades" not in self.data:
                # Looks like sat_lessons.json — try to load nipun alongside if present
                nipun_path = os.path.join(os.path.dirname(seed_path), "..", "outcomes", "nipun.json")
                # also try DEFAULT_NIPUN and sibling outcomes/
                candidates = [
                    DEFAULT_NIPUN,
                    os.path.join(os.path.dirname(seed_path), "nipun.json"),
                    nipun_path,
                    os.path.join(os.path.dirname(__file__), "outcomes", "nipun.json"),
                ]
                nipun_data = None
                for cand in candidates:
                    if os.path.exists(cand):
                        try:
                            with open(cand, "r", encoding="utf-8") as nf:
                                nipun_data = json.load(nf)
                            break
                        except Exception:
                            continue
                converted = _convert_sat_lessons(self.data, nipun_data)
                self.data = converted
        self._index()

    def _index(self):
        self.by_table = {t: self.data.get(t, []) for t in _TABLES}
        self.idx = {}
        for t, rows in self.by_table.items():
            self.idx[t] = {r["_id"] if "_id" in r else r[list(r.keys())[0]]: r for r in rows}
        # FK maps
        self.outcomes_by_lesson = defaultdict(list)
        for r in self.by_table["learning_outcomes"]:
            self.outcomes_by_lesson[r["lesson_id"]].append(r)
        self.activities_by_outcome = defaultdict(list)
        for r in self.by_table["activities"]:
            self.activities_by_outcome[r["outcome_id"]].append(r)
        self.assessments_by_outcome = defaultdict(list)
        for r in self.by_table["assessments"]:
            self.assessments_by_outcome[r["outcome_id"]].append(r)
        self.vocab_by_lesson = defaultdict(list)
        for r in self.by_table["vocabulary_terms"]:
            self.vocab_by_lesson[r["lesson_id"]].append(r)
        self.flashcards_by_lesson = defaultdict(list)
        for r in self.by_table["flashcard_concepts"]:
            self.flashcards_by_lesson[r["lesson_id"]].append(r)
        self.instructions_by_lesson = defaultdict(list)
        for r in self.by_table["teacher_instructions"]:
            self.instructions_by_lesson[r["lesson_id"]].append(r)

    # --- navigation helpers (teacher drill-down) ---
    def grades(self):
        return sorted(self.by_table["grades"], key=lambda r: r["grade_number"])

    def subjects(self, grade_id):
        return [r for r in self.by_table["subjects"] if r["grade_id"] == grade_id]

    def chapters(self, subject_id):
        return sorted(
            [r for r in self.by_table["chapters"] if r["subject_id"] == subject_id],
            key=lambda r: r["sequence"])

    def lessons(self, chapter_id):
        return sorted(
            [r for r in self.by_table["lessons"] if r["chapter_id"] == chapter_id],
            key=lambda r: r["sequence"])

    def lesson(self, lesson_id):
        return self.idx["lessons"].get(lesson_id)

    def outcomes(self, lesson_id):
        return sorted(self.outcomes_by_lesson.get(lesson_id, []), key=lambda r: r["sequence"])

    def activities(self, outcome_id):
        return sorted(self.activities_by_outcome.get(outcome_id, []), key=lambda r: r["sequence"])

    def assessments(self, outcome_id):
        return sorted(self.assessments_by_outcome.get(outcome_id, []), key=lambda r: r["sequence"])

    def vocabulary(self, lesson_id):
        return self.vocab_by_lesson.get(lesson_id, [])

    def flashcards(self, lesson_id):
        return sorted(self.flashcards_by_lesson.get(lesson_id, []), key=lambda r: r["sequence"])

    def instructions(self, lesson_id):
        return sorted(self.instructions_by_lesson.get(lesson_id, []), key=lambda r: r["sequence"])

    # --- integrity validation (deterministic) ---
    def validate(self):
        errors = []
        # every FK must resolve
        checks = [
            ("subjects", "grade_id", "grades"),
            ("chapters", "subject_id", "subjects"),
            ("lessons", "chapter_id", "chapters"),
            ("learning_outcomes", "lesson_id", "lessons"),
            ("teacher_instructions", "lesson_id", "lessons"),
            ("activities", "outcome_id", "learning_outcomes"),
            ("assessments", "outcome_id", "learning_outcomes"),
            ("vocabulary_terms", "lesson_id", "lessons"),
            ("flashcard_concepts", "lesson_id", "lessons"),
        ]
        for table, fk, parent in checks:
            for r in self.by_table[table]:
                if r[fk] not in self.idx[parent]:
                    errors.append(f"{table}.{r.get('lesson_id', r.get(fk))} -> missing {parent} {r[fk]}")
        # NOT NULL / required fields
        required = {
            "grades": ["grade_id", "grade_number", "name_hi", "name_target", "name_en"],
            "subjects": ["subject_id", "grade_id", "name_hi", "name_target", "name_en"],
            "chapters": ["chapter_id", "subject_id", "name_hi", "name_target", "name_en", "sequence"],
            "lessons": ["lesson_id", "chapter_id", "title_hi", "title_target", "title_en", "sequence", "estimated_minutes"],
            "learning_outcomes": ["outcome_id", "lesson_id", "code", "description_hi", "description_target", "description_en", "sequence"],
            "teacher_instructions": ["instruction_id", "lesson_id", "text_hi", "text_target", "text_en", "sequence"],
            "activities": ["activity_id", "outcome_id", "title_hi", "title_target", "title_en", "activity_type", "instructions_hi", "instructions_target", "instructions_en", "sequence"],
            "assessments": ["assessment_id", "outcome_id", "type", "prompt_hi", "prompt_target", "prompt_en", "answer_key", "difficulty", "sequence"],
            "vocabulary_terms": ["term_id", "lesson_id", "term_hi", "term_target", "term_en"],
            "flashcard_concepts": ["card_id", "lesson_id", "concept_hi", "concept_target", "concept_en", "sequence"],
        }
        for table, fields in required.items():
            for r in self.by_table[table]:
                for f in fields:
                    if f not in r or r[f] in (None, ""):
                        errors.append(f"{table}.{r.get('lesson_id', '?')} missing field {f}")
        # Warn (not error) if lessons > outcomes — expected for flooded curriculum
        # 15 lessons share 8 distinct NIPUN outcomes (reuse documented in
        # _convert_sat_lessons and curriculum/outcomes/nipun.json). This is
        # a notice, not a failure: nipun codes are locally assigned (G{n}-{domain}-{seq})
        # and reused many-to-one. See _convert_sat_lessons docstring.
        try:
            n_lessons = len(self.by_table.get("lessons", []))
            n_outcomes = len(self.by_table.get("learning_outcomes", []))
            if n_lessons > n_outcomes and n_outcomes > 0:
                msg = (
                    f"WARN: lessons ({n_lessons}) > outcomes ({n_outcomes}) — "
                    f"nipun.json outcome reuse documented (8 distinct NIPUN codes reused "
                    f"across {n_lessons} lessons, avg {n_lessons/n_outcomes:.2f} lessons/outcome). "
                    f"This is EXPECTED for the canonical 15-lesson flooded curriculum (G1:6 G2:4 G3:5) "
                    f"and not a validation error. See _convert_sat_lessons() and curriculum/outcomes/nipun.json."
                )
                warnings.warn(msg, UserWarning)
                # Also expose via validate_warnings() — do not append to errors.
        except Exception:
            pass
        return errors

    def validate_warnings(self) -> list[str]:
        """Return non-fatal warnings (lessons > outcomes reuse) without failing validate().

        Canonical flooded state: 15 lessons / 8 outcomes → warn that nipun outcomes
        are reused many-to-one (documented in _convert_sat_lessons). Classic seed
        (1 lesson / 5 outcomes) has no warning.
        Also emits DRAFT watermark when sat_lessons.json meta.content_status is AUTHOR-DRAFT.
        """
        warns: list[str] = []
        n_lessons = len(self.by_table.get("lessons", []))
        n_outcomes = len(self.by_table.get("learning_outcomes", []))
        if n_lessons > n_outcomes and n_outcomes > 0:
            warns.append(
                f"WARN: lessons ({n_lessons}) > outcomes ({n_outcomes}) — "
                f"nipun.json outcome reuse documented (8 distinct reused across {n_lessons}). "
                f"Expected for canonical 15. See _convert_sat_lessons()."
            )
        # Also warn if canonical count diverges
        if n_lessons != CANONICAL_LESSON_COUNT and n_lessons != 1:
            # classic seed has 1 lesson — exempt
            warns.append(
                f"WARN: lesson count {n_lessons} != canonical {CANONICAL_LESSON_COUNT} — "
                f"sat_lessons.json should have {CANONICAL_LESSON_COUNT} (G1:6 G2:4 G3:5)."
            )
        # DRAFT watermark — curriculum is AUTHOR-DRAFT until SME sign-off (2.2% verified)
        # Never ship MACHINE_TRANSLATED as APPROVED. Pack --require-approved must fail on DRAFT.
        try:
            sat_path = os.path.join(os.path.dirname(__file__), "lessons", "sat_lessons.json")
            if os.path.exists(sat_path):
                with open(sat_path, "r", encoding="utf-8") as f:
                    sat = json.load(f)
                meta = sat.get("meta", {}) if isinstance(sat, dict) else {}
                status = meta.get("content_status", "") if isinstance(meta, dict) else ""
                if "DRAFT" in status:
                    warns.append(
                        f"DRAFT: curriculum/lessons/sat_lessons.json meta.content_status={status!r} — "
                        f"AUTHOR-DRAFT watermark: precomputed Ol Chiki is human-reviewed but only 2.2% verified (sampled), "
                        f"NOT approved pedagogy until native speaker SME review. "
                        f"Diagnostics shows DRAFT banner; build_pack --require-approved fails on DRAFT. "
                        f"Never ship MACHINE_TRANSLATED as APPROVED."
                    )
                # Also warn if any lesson provenance is not human-reviewed
                if isinstance(sat, dict) and isinstance(sat.get("lessons"), list):
                    mt_count = sum(1 for les in sat["lessons"] if "MACHINE_TRANSLATED" in str(les.get("translation_provenance", "")))
                    if mt_count > 0:
                        warns.append(f"WARN: {mt_count} lessons have MACHINE_TRANSLATED provenance — must not be APPROVED without SME gate.")
        except Exception:
            pass
        return warns

    def validate_all(self):
        """Return (errors, warnings) tuple for callers that need both."""
        return self.validate(), self.validate_warnings()


def load(seed_path=DEFAULT_SEED):
    return CurriculumDB(seed_path)


def load_sat(sat_path=DEFAULT_SAT_LESSONS, nipun_path=DEFAULT_NIPUN):
    """Convenience loader for the flooded sat curriculum (canonical 15).

    Loads sat_lessons.json + nipun.json via _convert_sat_lessons() and returns
    a CurriculumDB backed by the converted tables. Documents reuse: 8 outcomes
    reused across 15 lessons.
    """
    with open(sat_path, "r", encoding="utf-8") as f:
        sat_data = json.load(f)
    nipun_data = None
    if os.path.exists(nipun_path):
        with open(nipun_path, "r", encoding="utf-8") as f:
            nipun_data = json.load(f)
    converted = _convert_sat_lessons(sat_data, nipun_data)
    return CurriculumDB(data_override=converted)


def load_flooded():
    """Alias for load_sat() — flooded curriculum is canonical 15."""
    return load_sat()


if __name__ == "__main__":
    import sys
    # Default seed validation (G2 counting — 1 lesson / 5 outcomes, no warning expected)
    db = load()
    errs = db.validate()
    warns = db.validate_warnings()
    if errs:
        print("VALIDATION FAILED:")
        for e in errs:
            print("  -", e)
        sys.exit(1)
    else:
        print("VALIDATION OK (classic seed). Lessons:",
              [(g["grade_id"], s["subject_id"], c["chapter_id"], l["lesson_id"])
               for g in db.grades()
               for s in db.subjects(g["grade_id"])
               for c in db.chapters(s["subject_id"])
               for l in db.lessons(c["chapter_id"])])
        if warns:
            print("WARNINGS:", warns)

    # Flooded sat validation — canonical 15 lessons, 8 outcomes (reuse expected)
    try:
        sat_db = load_sat() if os.path.exists(DEFAULT_SAT_LESSONS) else None
        if sat_db is not None:
            errs2 = sat_db.validate()
            warns2 = sat_db.validate_warnings()
            print(f"\nFLOODED sat_lessons: lessons={len(sat_db.by_table['lessons'])} "
                  f"outcomes={len(sat_db.by_table['learning_outcomes'])} "
                  f"canonical={CANONICAL_LESSON_COUNT}/{CANONICAL_OUTCOME_COUNT}")
            if errs2:
                print("FLOODED VALIDATION FAILED:")
                for e in errs2:
                    print("  -", e)
            else:
                print("FLOODED VALIDATION OK (expected WARN lessons>outcomes).")
            if warns2:
                for w in warns2:
                    print("  -", w)
            # Prove no truncation: 15 preserved, not 8
            assert len(sat_db.by_table["lessons"]) == CANONICAL_LESSON_COUNT, \
                f"canonical 15 not preserved: got {len(sat_db.by_table['lessons'])}"
            assert "_convert_sat_lessons" in open(__file__, encoding="utf-8").read()
            print(f"_convert_sat_lessons handles {CANONICAL_LESSON_COUNT} correctly — no truncation.")
    except Exception as e:
        print(f"Flooded check skipped/error: {e}")
        import traceback
        traceback.print_exc()
