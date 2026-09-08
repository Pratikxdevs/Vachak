"""6B — Deterministic terminology validator. No LLM.

Validates translated text against an approved vocabulary table (CSV). Produces
warnings and suggested replacements. Fully deterministic and offline.

The vocabulary table is curriculum-owned content. The sample bundled here is a DEV
FIXTURE (Hindi stand-in terms) — it is NOT the approved Mundari pedagogy glossary.
Replace terminology.csv with the real, human-approved glossary before any classroom use.
"""

from __future__ import annotations

import csv
import os
import re
from dataclasses import dataclass, field
from typing import Dict, List, Optional


_TOKEN_RE = re.compile(r"[A-Za-zऀ-ॿ]+", re.UNICODE)


@dataclass
class TerminologyWarning:
    token: str
    kind: str  # "unapproved" | "alternative" | "missing_key_term"
    suggestion: Optional[str]
    message: str


@dataclass
class ValidationReport:
    text: str
    warnings: List[TerminologyWarning] = field(default_factory=list)
    approved_terms_used: List[str] = field(default_factory=list)

    @property
    def has_warnings(self) -> bool:
        return bool(self.warnings)

    def as_dict(self) -> dict:
        return {
            "text": self.text,
            "approvedTermsUsed": self.approved_terms_used,
            "warnings": [
                {"token": w.token, "kind": w.kind,
                 "suggestion": w.suggestion, "message": w.message}
                for w in self.warnings
            ],
        }


class TerminologyValidator:
    def __init__(self, vocabulary_csv: str):
        self._csv = vocabulary_csv
        self._approved: Dict[str, dict] = {}
        self._alternatives: Dict[str, str] = {}
        self._required_by_context: Dict[str, List[str]] = {}
        self._load()

    def _load(self) -> None:
        if not os.path.isfile(self._csv):
            raise RuntimeError(f"Vocabulary file not found: {self._csv}")
        with open(self._csv, "r", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                term = (row.get("term") or "").strip()
                if not term:
                    continue
                status = (row.get("approved") or "yes").strip().lower()
                self._approved[term] = row
                alts = (row.get("alternatives") or "").strip()
                if alts:
                    for a in alts.split("|"):
                        a = a.strip()
                        if a:
                            self._alternatives[a] = term
                ctx_key = (row.get("grades") or "").strip()
                subj = (row.get("subjects") or "").strip()
                if ctx_key or subj:
                    key = f"{ctx_key}/{subj}"
                    self._required_by_context.setdefault(key, []).append(term)

    @staticmethod
    def _tokenize(text: str) -> List[str]:
        return _TOKEN_RE.findall(text)

    def validate(self, text: str, context: Optional[dict] = None) -> ValidationReport:
        report = ValidationReport(text=text)
        context = context or {}
        tokens = self._tokenize(text)
        for tok in tokens:
            if tok in self._alternatives:
                canon = self._alternatives[tok]
                report.warnings.append(TerminologyWarning(
                    token=tok, kind="alternative",
                    suggestion=canon,
                    message=f"'{tok}' is a non-standard spelling; use approved '{canon}'."))
            elif tok in self._banned_terms():
                report.warnings.append(TerminologyWarning(
                    token=tok, kind="unapproved", suggestion=None,
                    message=f"'{tok}' is not in the approved vocabulary."))
            elif tok in self._approved:
                report.approved_terms_used.append(tok)
        # Missing key term check (context-driven, deterministic).
        key = f"{context.get('grade', '')}/{context.get('subject', '')}"
        for req in self._required_by_context.get(key, []):
            if req not in tokens:
                report.warnings.append(TerminologyWarning(
                    token=req, kind="missing_key_term", suggestion=req,
                    message=f"Expected curriculum term '{req}' absent from translation."))
        return report

    def _banned_terms(self) -> Dict[str, dict]:
        # Terms explicitly marked approved=no in the CSV.
        return {t: r for t, r in self._approved.items() if (r.get("approved") or "yes").lower() == "no"}
