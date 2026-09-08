"""Curriculum lesson schema (FLN outcomes, NIPUN mapping).

Curriculum content is PRECOMPUTED and shipped in language packs — never
generated on-device. This module is the validation/serialization contract
shared with the Android CurriculumEngine.
"""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import List


@dataclass
class Outcome:
    id: str
    description: str
    nipun_mapped: bool


@dataclass
class Lesson:
    id: str
    title: str
    grade: int
    source_text_hi: str
    translated_text: str
    precomputed: bool = True
    outcomes: List[Outcome] = field(default_factory=list)

    def to_json(self) -> str:
        return json.dumps(asdict(self), ensure_ascii=False, indent=2)

    @classmethod
    def from_json(cls, text: str) -> "Lesson":
        return cls(**json.loads(text))


# JSON Schema (machine-readable) for tooling/validation.
LESSON_JSON_SCHEMA = {
    "type": "object",
    "required": ["id", "title", "grade", "source_text_hi", "translated_text", "precomputed"],
    "properties": {
        "id": {"type": "string"},
        "title": {"type": "string"},
        "grade": {"type": "integer"},
        "source_text_hi": {"type": "string"},
        "translated_text": {"type": "string"},
        "precomputed": {"type": "boolean"},
        "outcomes": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["id", "description", "nipun_mapped"],
                "properties": {
                    "id": {"type": "string"},
                    "description": {"type": "string"},
                    "nipun_mapped": {"type": "boolean"},
                },
            },
        },
    },
}
