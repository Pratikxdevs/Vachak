"""Evaluation harness for the Mundari NMT pipeline (scaffolding).

Implements automatic metrics (BLEU, chrF) via sacrebleu so a split can be scored
the moment predictions exist. Human evaluation, terminology accuracy, latency
and memory are scaffolded as explicit TODO stubs because they require either
gated data, a device, or human raters.

No metric in this file is ever fabricated. Where a value cannot yet be computed,
the function returns a TODO marker, never a number.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class EvalReport:
    metric: str
    value: object
    status: str  # "computed" | "TODO"
    detail: str = ""


@dataclass
class MundariEval:
    bleu: EvalReport = field(default_factory=lambda: EvalReport("BLEU", "TODO", "TODO"))
    chrf: EvalReport = field(default_factory=lambda: EvalReport("chrF", "TODO", "TODO"))
    human: EvalReport = field(default_factory=lambda: EvalReport("human", "TODO", "TODO"))
    terminology: EvalReport = field(default_factory=lambda: EvalReport("terminology", "TODO", "TODO"))
    latency_ms: EvalReport = field(default_factory=lambda: EvalReport("latency_ms", "TODO", "TODO"))
    memory_mb: EvalReport = field(default_factory=lambda: EvalReport("memory_mb", "TODO", "TODO"))

    def as_dict(self) -> Dict[str, object]:
        return {r.metric: {"value": r.value, "status": r.status, "detail": r.detail} for r in [
            self.bleu, self.chrf, self.human, self.terminology, self.latency_ms, self.memory_mb]}


def _automatic(refs: List[str], hyps: List[str]) -> MundariEval:
    import sacrebleu

    rep = MundariEval()
    if not hyps or len(hyps) != len(refs):
        rep.bleu = EvalReport("BLEU", "TODO", "TODO", "refs/hyps mismatch or empty")
        rep.chrf = EvalReport("chrF", "TODO", "TODO", "refs/hyps mismatch or empty")
        return rep
    rep.bleu = EvalReport("BLEU", sacrebleu.corpus_bleu(hyps, [refs]).score, "computed")
    rep.chrf = EvalReport("chrF", sacrebleu.corpus_chrf(hyps, [refs]).score, "computed")
    return rep


def evaluate(refs: List[str], hyps: List[str], measure_human: bool = False,
             measure_latency: bool = False, measure_memory: bool = False,
             terminology_terms: Optional[List[str]] = None) -> MundariEval:
    """Score predictions. Automatic metrics computed now; the rest are TODO.

    measure_* flags are provided so the SAME call site is used once the real
    Mundari model and a device are available; until then those branches stay TODO.
    """
    rep = _automatic(refs, hyps)
    if measure_human:
        rep.human = EvalReport("human", "TODO", "TODO",
                               "requires native Mundari-speaking raters; gated")
    else:
        rep.human = EvalReport("human", "TODO", "TODO", "set measure_human=True with raters")
    if terminology_terms:
        rep.terminology = EvalReport("terminology", "TODO", "TODO",
                                     "terminology accuracy needs gold term list + final model")
    if measure_latency:
        rep.latency_ms = EvalReport("latency_ms", "TODO", "TODO", "measure on target 2GB device")
    if measure_memory:
        rep.memory_mb = EvalReport("memory_mb", "TODO", "TODO", "measure RSS on target device")
    return rep
