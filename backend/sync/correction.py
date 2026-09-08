"""Teacher correction data model (PHASE 10).

A correction captures one teacher-submitted fix to a translation. It is queued
locally and only ever leaves the device when connectivity returns; it then goes
to HUMAN REVIEW and, if approved, joins the verified corpus. Corrections are
NEVER used to auto-train a model (see backend.py / TrainingGate).
"""
from __future__ import annotations

import time
import uuid
from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Optional


class CorrectionStatus(str, Enum):
    QUEUED = "queued"                 # on device, not yet uploaded
    UPLOADED = "uploaded"             # sent to backend, awaiting review
    PENDING_HUMAN_REVIEW = "pending_human_review"
    VERIFIED = "verified"             # approved by a human reviewer
    REJECTED = "rejected"


@dataclass
class TeacherCorrection:
    source: str                       # original source text (Hindi)
    target: str                       # original model output (Mundari)
    teacher_correction: str           # teacher's improved target text
    language: str = "hi-mund"         # language pair id
    grade: Optional[int] = None
    subject: Optional[str] = None
    lesson: Optional[str] = None
    id: str = field(default_factory=lambda: uuid.uuid4().hex)
    timestamp: float = field(default_factory=time.time)
    status: CorrectionStatus = CorrectionStatus.QUEUED

    def to_dict(self) -> dict:
        d = asdict(self)
        d["status"] = self.status.value
        return d

    @classmethod
    def from_dict(cls, d: dict) -> "TeacherCorrection":
        d = dict(d)
        d["status"] = CorrectionStatus(d.get("status", "queued"))
        return cls(**d)


__all__ = ["TeacherCorrection", "CorrectionStatus"]
