"""
curriculum/lesson_map.py — Phase 3B
---------------------------------------------------------------------------
Deterministic lesson -> learning outcome -> activity -> assessment mapping.

HARD RULE: no LLM, no runtime heuristic scoring. The mapping is a pure graph
traversal over the curriculum DB using the explicit `sequence` order and the
FK relationships. Given a lesson_id, the function returns an ordered, stable
"lesson package" that a teacher can render offline.

Determinism contract:
  * Same lesson_id + same seed JSON  =>  byte-identical package.
  * Activities/assessments are pulled by FK and ordered by `sequence`.
  * If multiple activities exist for an outcome, ALL are included (ordered).
  * Assessment selection is deterministic: every assessment attached to an
    outcome is included, ordered by `sequence` then `assessment_id`.
"""
try:
    from .data import CurriculumDB, load
except ImportError:  # allow running as a script
    from data import CurriculumDB, load  # type: ignore


def build_lesson_package(lesson_id, db=None):
    """
    Build a structured lesson package from a lesson_id.

    :returns: dict with keys: lesson, outcomes (list of dicts each containing
              activities and assessments), teacher_instructions, vocabulary,
              flashcards, plus a stable `package_id`.
    """
    db = db or load()
    lesson = db.lesson(lesson_id)
    if lesson is None:
        raise KeyError(f"Unknown lesson_id: {lesson_id}")

    outcomes = []
    for o in db.outcomes(lesson_id):
        outcomes.append({
            "outcome_id": o["outcome_id"],
            "code": o["code"],
            "description_hi": o["description_hi"],
            "description_target": o["description_target"],
            "description_en": o["description_en"],
            "activities": [
                {
                    "activity_id": a["activity_id"],
                    "title_hi": a["title_hi"],
                    "title_target": a["title_target"],
                    "title_en": a["title_en"],
                    "activity_type": a["activity_type"],
                    "instructions_hi": a["instructions_hi"],
                    "instructions_target": a["instructions_target"],
                    "instructions_en": a["instructions_en"],
                }
                for a in db.activities(o["outcome_id"])
            ],
            "assessments": [
                {
                    "assessment_id": a["assessment_id"],
                    "type": a["type"],
                    "prompt_hi": a["prompt_hi"],
                    "prompt_target": a["prompt_target"],
                    "prompt_en": a["prompt_en"],
                    "answer_key": a["answer_key"],
                    "difficulty": a["difficulty"],
                }
                for a in db.assessments(o["outcome_id"])
            ],
        })

    package = {
        "package_id": f"PKG-{lesson_id}",
        "lesson": {
            "lesson_id": lesson["lesson_id"],
            "title_hi": lesson["title_hi"],
            "title_target": lesson["title_target"],
            "title_en": lesson["title_en"],
            "estimated_minutes": lesson["estimated_minutes"],
        },
        "teacher_instructions": [
            {
                "instruction_id": t["instruction_id"],
                "text_hi": t["text_hi"],
                "text_target": t["text_target"],
                "text_en": t["text_en"],
            }
            for t in db.instructions(lesson_id)
        ],
        "outcomes": outcomes,
        "vocabulary": [
            {
                "term_hi": v["term_hi"],
                "term_target": v["term_target"],
                "term_en": v["term_en"],
                "definition_hi": v.get("definition_hi"),
                "definition_target": v.get("definition_target"),
                "definition_en": v.get("definition_en"),
            }
            for v in db.vocabulary(lesson_id)
        ],
        "flashcards": [
            {
                "card_id": fc["card_id"],
                "concept_hi": fc["concept_hi"],
                "concept_target": fc["concept_target"],
                "concept_en": fc["concept_en"],
                "image_ref": fc.get("image_ref"),
                "audio_ref": fc.get("audio_ref"),
            }
            for fc in db.flashcards(lesson_id)
        ],
    }
    return package


def find_lesson_by_path(db, grade_id, subject_id, chapter_id, lesson_id=None):
    """Resolve a teacher drill-down path; returns list of lesson_ids or one."""
    lessons = db.lessons(chapter_id) if chapter_id else []
    if lesson_id:
        return [l["lesson_id"] for l in lessons if l["lesson_id"] == lesson_id]
    return [l["lesson_id"] for l in lessons]


if __name__ == "__main__":
    db = load()
    pkg = build_lesson_package("L-COUNT-G2", db)
    print("package_id:", pkg["package_id"])
    print("outcomes:", len(pkg["outcomes"]))
    print("flashcards:", len(pkg["flashcards"]))
    print("vocab:", len(pkg["vocabulary"]))
