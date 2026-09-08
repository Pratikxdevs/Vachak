"""Classroom state model (PHASE 9B).

Holds the live teaching session state the orchestrator reads/writes as a
request flows through the pipeline. Kept as plain data so it can be snapshotted
for the UI, persisted to Room on Android, or serialized for tests.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Optional


@dataclass
class ClassroomState:
    # Session / lesson context
    current_lesson_id: Optional[str] = None
    lesson_title: Optional[str] = None
    activity: Optional[str] = None          # e.g. "translate", "worksheet", "flashcard"
    grade: Optional[int] = None
    subject: Optional[str] = None

    # Language pair for this session
    source_language: str = "hi"             # Hindi
    target_language: str = "mund"           # Mundari / Ol Chiki

    # Latest utterance results
    transcript: Optional[str] = None        # recognized Hindi
    translation: Optional[str] = None       # translated Mundari
    audio: Any | None = None                # synthesized PCM / path
    student_response: Optional[str] = None  # student's spoken/written reply

    # Audit trail of the most recent request lifecycle
    last_stage: Optional[str] = None
    last_status: Optional[str] = None
    last_error: Optional[str] = None
    last_action: Optional[str] = None       # degradation action taken, if any

    history: list[dict[str, Any]] = field(default_factory=list)

    def record(
        self,
        stage: str,
        *,
        status: str,
        error: Optional[str] = None,
        action: Optional[str] = None,
        transcript: Optional[str] = None,
        translation: Optional[str] = None,
        audio: Any | None = None,
        student_response: Optional[str] = None,
    ) -> None:
        if transcript is not None:
            self.transcript = transcript
        if translation is not None:
            self.translation = translation
        if audio is not None:
            self.audio = audio
        if student_response is not None:
            self.student_response = student_response
        self.last_stage = stage
        self.last_status = status
        self.last_error = error
        self.last_action = action
        self.history.append(
            {
                "stage": stage,
                "status": status,
                "error": error,
                "action": action,
                "transcript": self.transcript,
                "translation": self.translation,
            }
        )

    def snapshot(self) -> dict[str, Any]:
        return {
            "lesson": self.current_lesson_id,
            "lesson_title": self.lesson_title,
            "activity": self.activity,
            "grade": self.grade,
            "subject": self.subject,
            "source_language": self.source_language,
            "target_language": self.target_language,
            "transcript": self.transcript,
            "translation": self.translation,
            "student_response": self.student_response,
            "last_stage": self.last_stage,
            "last_status": self.last_status,
            "last_error": self.last_error,
            "last_action": self.last_action,
        }


__all__ = ["ClassroomState"]
