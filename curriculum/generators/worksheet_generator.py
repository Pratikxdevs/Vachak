"""Worksheet generator — TEMPLATE-BASED only (per AGENTS.md: not AI-generated).

Takes a precomputed Lesson and a template name and emits a deterministic
Worksheet. Placeholder implementation returns a fixed fill-in-the-blank item.
"""
from __future__ import annotations

from curriculum.schemas.lesson_schema import Lesson


def generate(lesson: Lesson, template: str = "fill_blank") -> dict:
    if template == "fill_blank":
        prompt = f"Translate and fill: {lesson.source_text_hi}"
        answer_key = lesson.translated_text
    else:
        prompt = f"Read: {lesson.source_text_hi}"
        answer_key = lesson.translated_text
    return {
        "id": f"W_{lesson.id}",
        "lesson_id": lesson.id,
        "template": template,
        "items": [{"prompt": prompt, "answer_key": answer_key}],
    }
