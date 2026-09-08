"""
worksheet/engine.py — Phase 4A
====================================================================
Worksheet generator. Deterministic, offline, NO LLM.

Input  (WorksheetRequest): grade, subject, learning_outcome (code),
        difficulty, language ('hi'|'target'|'bilingual'), num_items (optional)
Output (WorksheetArtifact): JSON structure with questions + teacher answer key,
        plus renderers to Markdown and a minimal hand-rolled PDF.

Question types produced:
  * counting       — objects to count (deterministic N)
  * multiple_choice— vocab term -> target, 3 deterministic distractors
  * matching       — Hindi <-> target pairs
  * fill_in_blank  — from authored assessments
  * image_based    — flashcard image -> label

Determinism: all "randomness" is a stable hash of (seed_string, index).
Same request => identical artifact on every machine, forever.
"""
import hashlib
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from localization.layer import localize, normalize_language, language_label  # noqa: E402

try:
    from curriculum.data import load as load_curriculum
except ImportError:  # standalone
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "curriculum"))
    from data import load as load_curriculum  # type: ignore

SEED_CONST = 20240828  # fixed; never changes -> stable output


def _stable_int(*parts):
    """Deterministic non-negative int from string parts (hash-based)."""
    h = hashlib.sha256(("|".join(str(p) for p in parts)).encode("utf-8")).hexdigest()
    return int(h, 16)


def _pick_distractors(correct, pool, n, seed_parts):
    """Deterministically pick `n` distractors != correct from pool."""
    out = []
    i = 0
    while len(out) < n and i < len(pool) * 3:
        cand = pool[(_stable_int(*seed_parts, i)) % max(1, len(pool))]
        if cand != correct and cand not in out:
            out.append(cand)
        i += 1
    return out


# Object pool for counting questions (emoji are offline-safe glyphs).
_COUNT_OBJECTS = [
    ("सेब", "ᱥᱟᱯ", "🍎"), ("किताब", "ᱯᱚᱛᱚ", "📕"),
    ("पेंसिल", "ᱯᱮᱱᱥᱤᱞ", "✏️"), ("गेंद", "ᱜᱮᱱᱫᱚ", "⚽"),
    ("फूल", "ᱯᱩᱥᱯᱟ", "🌸"), ("तारा", "ᱛᱟᱨᱟ", "⭐"),
    ("पत्ता", "ᱥᱟᱠᱟᱢ", "🍃"), ("मेज़", "ᱴᱮᱵᱚᱞ", "🪑"),
]

_Q_TYPES = ["counting", "multiple_choice", "matching", "fill_in_blank", "image_based"]


class WorksheetRequest:
    def __init__(self, grade, subject, learning_outcome, difficulty="easy",
                 language="bilingual", num_items=8):
        self.grade = grade
        self.subject = subject
        self.learning_outcome = learning_outcome
        self.difficulty = difficulty
        self.language = normalize_language(language)
        self.num_items = int(num_items)

    def to_dict(self):
        return {
            "grade": self.grade, "subject": self.subject,
            "learning_outcome": self.learning_outcome, "difficulty": self.difficulty,
            "language": self.language, "num_items": self.num_items,
        }


class WorksheetEngine:
    def __init__(self, db=None):
        self.db = db or load_curriculum()

    def _find_outcome(self, code):
        for o in self.db.by_table["learning_outcomes"]:
            if o["code"] == code or o["outcome_id"] == code:
                return o
        return None

    def generate(self, req: WorksheetRequest):
        outcome = self._find_outcome(req.learning_outcome)
        if outcome is None:
            raise KeyError(f"Unknown learning_outcome: {req.learning_outcome}")
        lesson_id = outcome["lesson_id"]
        vocab = self.db.vocabulary(lesson_id)
        flashcards = self.db.flashcards(lesson_id)
        assessments = self.db.assessments(outcome["outcome_id"])

        questions = []
        answer_key = []

        # 1) counting questions
        n_count = max(1, req.num_items // 4)
        for i in range(n_count):
            obj = _COUNT_OBJECTS[_stable_int("count", lesson_id, i) % len(_COUNT_OBJECTS)]
            max_n = {"easy": 10, "medium": 20, "hard": 50}.get(req.difficulty, 10)
            n = (_stable_int("countn", lesson_id, i, SEED_CONST) % max_n) + 1
            qid = f"Q-CNT-{i+1}"
            prompt = {
                "hi": f"इन वस्तुओं को गिनें ({obj[0]}):",
                "target": f"ᱱᱚᱶᱟ ᱡᱤᱱᱤᱥ ᱠᱚ ᱞᱮᱠᱷᱟ ᱢᱟ ({obj[1]}):",
                "en": f"Count the objects ({obj[2]}):",
            }
            questions.append({
                "id": qid, "type": "counting",
                "prompt": localize(prompt, req.language),
                "render": f"{obj[2]} " * n,
                "answer": n,
            })
            answer_key.append({"id": qid, "answer": str(n)})

        # 2) multiple-choice from vocabulary
        n_mc = max(1, req.num_items // 4)
        pool_target = [v["term_target"] for v in vocab] or [outcome["description_target"]]
        for i, v in enumerate(vocab[:n_mc]):
            qid = f"Q-MC-{i+1}"
            correct = v["term_target"]
            distract = _pick_distractors(correct, pool_target, 3, ["mc", lesson_id, i])
            opts = list(distract) + [correct]
            # stable shuffle
            opts.sort(key=lambda x: _stable_int("opt", lesson_id, i, x))
            prompt = {
                "hi": f"{v['term_hi']} का अर्थ क्या है?",
                "target": f"{v['term_target']} ᱨᱮᱭᱟᱜ ᱢᱮᱱᱮᱛ ᱪᱮ?",
                "en": f"What does '{v['term_en']}' mean?",
            }
            questions.append({
                "id": qid, "type": "multiple_choice",
                "prompt": localize(prompt, req.language),
                "options": opts,
                "answer": correct,
            })
            answer_key.append({"id": qid, "answer": correct})

        # 3) fill-in-blank from authored assessments
        n_fib = 0
        for a in assessments:
            if a["type"] in ("written", "oral") and "____" in a["prompt_hi"]:
                n_fib += 1
                qid = f"Q-FIB-{n_fib}"
                prompt = {
                    "hi": a["prompt_hi"], "target": a["prompt_target"], "en": a["prompt_en"],
                }
                questions.append({
                    "id": qid, "type": "fill_in_blank",
                    "prompt": localize(prompt, req.language),
                    "answer": a["answer_key"],
                })
                answer_key.append({"id": qid, "answer": a["answer_key"]})

        # 4) image-based from flashcards
        for i, fc in enumerate(flashcards[:max(1, req.num_items // 6)]):
            qid = f"Q-IMG-{i+1}"
            prompt = {
                "hi": f"इस चित्र का नाम बताएं:",
                "target": f"ᱱᱚᱶᱟ ᱪᱤᱛᱟᱹ ᱨᱮᱭᱟᱜ ᱧᱩᱛᱩᱢ ᱞᱟᱹᱭ ᱢᱟ:",
                "en": f"Name this picture:",
            }
            questions.append({
                "id": qid, "type": "image_based",
                "prompt": localize(prompt, req.language),
                "image_ref": fc.get("image_ref"),
                "answer": localize({"hi": fc["concept_hi"], "target": fc["concept_target"], "en": fc["concept_en"]}, req.language),
            })
            answer_key.append({"id": qid, "answer": fc["concept_target"]})

        # 5) matching block (Hindi <-> target)
        if vocab:
            qid = "Q-MATCH-1"
            pairs = [{"hi": v["term_hi"], "target": v["term_target"]} for v in vocab]
            questions.append({
                "id": qid, "type": "matching",
                "prompt": localize({"hi": "मिलान करें (Hindi ↔ Target)",
                                     "target": "ᱢᱤᱞᱟᱹᱣ ᱢᱟ (Hindi ↔ Target)",
                                     "en": "Match (Hindi ↔ Target)"}, req.language),
                "pairs": pairs,
                "answer": "ਬ" if False else "See answer key: column A↔B pairs",
            })
            answer_key.append({
                "id": qid,
                "answer": " | ".join(f"{p['hi']}={p['target']}" for p in pairs),
            })

        # Trim to requested num_items (counting/mc/fib/image are small; keep matching)
        # We always include the matching block; trim the rest if over budget.
        core = [q for q in questions if q["type"] != "matching"]
        match_block = [q for q in questions if q["type"] == "matching"]
        if len(core) > req.num_items:
            core = core[:req.num_items]
        questions = core + match_block

        artifact = {
            "worksheet_id": f"WS-{outcome['outcome_id']}-{req.language}-{req.difficulty}",
            "meta": {
                "grade": req.grade, "subject": req.subject,
                "learning_outcome": outcome["code"],
                "difficulty": req.difficulty, "language": req.language,
                "language_label": language_label(req.language),
                "num_items": len(questions),
                "generator": "worksheet/engine.py v1.0 (deterministic, offline)",
                "fixture_note": "DEV FIXTURE — generated content, not SME-verified.",
            },
            "questions": questions,
            "answer_key": answer_key,
        }
        return artifact

    def to_markdown(self, artifact):
        return to_markdown(artifact)


def to_markdown(artifact):
    lines = []
    m = artifact["meta"]
    lines.append(f"# Worksheet — {m['learning_outcome']}")
    lines.append(f"- Grade: {m['grade']}  Subject: {m['subject']}")
    lines.append(f"- Difficulty: {m['difficulty']}  Language: {m['language_label']}")
    lines.append(f"- Items: {m['num_items']}")
    lines.append("")
    for qi, q in enumerate(artifact["questions"], 1):
        lines.append(f"## {qi}. [{q['type']}] {q['prompt']}")
        if q["type"] == "counting":
            lines.append("")
            lines.append(q.get("render", ""))
        if q["type"] == "multiple_choice":
            for oi, opt in enumerate(q["options"]):
                lines.append(f"   ({chr(65+oi)}) {opt}")
        if q["type"] == "image_based" and q.get("image_ref"):
            lines.append(f"   [image: {q['image_ref']}]")
        if q["type"] == "matching":
            for p in q["pairs"]:
                lines.append(f"   - {p['hi']}  ↔  {p['target']}")
        lines.append("")
    lines.append("---")
    lines.append("## Teacher Answer Key")
    for ak in artifact["answer_key"]:
        lines.append(f"- **{ak['id']}**: {ak['answer']}")
    lines.append("")
    lines.append(f"_{m['fixture_note']}_")
    return "\n".join(lines)


def render_pdf(artifact, path):
    """Hand-rolled minimal PDF (text only, no external deps)."""
    try:
        from worksheet.pdf import write_text_pdf
    except ImportError:
        from pdf import write_text_pdf  # type: ignore
    md = to_markdown(artifact)
    write_text_pdf(md.split("\n"), path)
    return path


if __name__ == "__main__":
    eng = WorksheetEngine()
    req = WorksheetRequest("G2", "SUBJ-MATH-G2", "G2-M-CNT-01", "easy", "bilingual", 8)
    art = eng.generate(req)
    print(eng.to_markdown(art))
    with open("/tmp/ws_sample.json", "w", encoding="utf-8") as f:
        json.dump(art, f, ensure_ascii=False, indent=2)
    print("Wrote /tmp/ws_sample.json")
