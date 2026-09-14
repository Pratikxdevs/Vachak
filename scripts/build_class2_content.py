"""Build Class 2 content pack content (text + assignments + worksheets + flashcards).

Reads the 11 Class 2 Santali textbook PDFs (all Type-A Unicode), and emits
deterministic, review-gated content under curriculum/class/2/:

  chapters/<slug>/chapter.json      cleaned text, provenance, status
  chapters/<slug>/assignments.json  detected assignment candidates + confidence
  chapters/<slug>/worksheets/<id>.json (+ .md)  template-built worksheets
  chapters/<slug>/flashcards/deck.json          concept decks
  assets/                           deduped, downscaled chapter images
  manifest.json                     per-chapter + totals
  REVIEW.md                         speaker review sheet (gaps flagged)

Rules (per AGENTS.md):
  * No LLM, no network. Deterministic: same PDFs => byte-identical output.
  * Everything ships status=AUTO_EXTRACTED (never APPROVED); gaps flagged
    for the Santali speaker, never guessed.
  * Worksheet numeric variation only; sentence frames come from the books.
  * Ol Chiki filled for numerals only (safe 1:1 map); all other target
    strings are null + needs_review.

Usage:
    python scripts/build_class2_content.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys

try:
    import pymupdf
except ImportError:
    raise SystemExit("need pymupdf")

try:
    from PIL import Image
    import io as _io
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

sys.path.insert(0, os.path.join(os.path.dirname(__file__)))
from extract_book_text import cleanup  # noqa: E402 (reuse collapse rules)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# (pdf filename, chapter slug, subject)
CLASS2_BOOKS = [
    ("Class_2__Fun_with_Numbers__Santhali_.pdf", "fun-with-numbers", "Mathematics"),
    ("Class_2__Grouping_and_Sharing__Santhali_.pdf", "grouping-and-sharing", "Mathematics"),
    ("Class_2__Shapes_Around_Us__Santhali_.pdf", "shapes-around-us", "Mathematics"),
    ("Class_2__Data_Handling__Santhali_.pdf", "data-handling", "Mathematics"),
    ("Class_2__Playing_with_Lines__Santhali_.pdf", "playing-with-lines", "Mathematics"),
    ("Class_2__Decoration_for_Festival__Santhali_.pdf", "decoration-for-festival", "Mathematics"),
    ("Class_2__Fun_at_the_Fair__Santhali_.pdf", "fun-at-the-fair", "Mathematics"),
    ("Class_2__Rani_s_Gift__Santhali_.pdf", "ranis-gift", "Mathematics"),
    ("Class_2__Shadow_Story__Togalu___Santhali_.pdf", "shadow-story", "Mathematics"),
    ("Class_2__Which_Season_is_it___Santhali_.pdf", "which-season", "Mathematics"),
    ("Class_2__A_Day_at_the_Beach__Santhali_.pdf", "a-day-at-the-beach", "Language"),
]

DEVA_DIGITS = "०१२३४५६७८९"
OL_DIGITS = "᱐᱑᱒᱓᱔᱕᱖᱗᱘᱙"
DIGIT_MAP = {ord(a): b for a, b in zip(DEVA_DIGITS + "0123456789", OL_DIGITS * 2)}


def to_ol_numerals(s: str) -> str:
    return s.translate(DIGIT_MAP)


def stable_int(*parts) -> int:
    h = hashlib.sha256(("|".join(str(p) for p in parts)).encode()).hexdigest()
    return int(h, 16)


# --- assignment detection (rule-based, confidence-scored) --------------------
_RULES = [
    ("FILL_BLANK", re.compile(r"_{3,}|…{2,}|[.]{3,}"), 0.9),
    ("COUNT", re.compile(r"गिन|संख्या लिख|कितने|कितनी"), 0.8),
    ("COMPARE", re.compile(r"अधिक|कम\b|बड़ा|छोटा|कौन-सा|ज्यादा"), 0.8),
    ("MATCH", re.compile(r"मिलान|मिलाओ|जोड़ी|जोड़"), 0.8),
    ("QUESTION", re.compile(r"\?\s*$|क्या|कैसे|कहाँ|कब\b"), 0.6),
    ("ACTIVITY", re.compile(r"करो|बनाओ|लिखो|दिखाओ|बताओ|काटो|चिपकाओ|रंग"), 0.6),
]


def detect_assignments(page_no: int, lines: list[str]) -> list[dict]:
    out = []
    for ln in lines:
        s = ln.strip()
        if len(s) < 8:
            continue
        hits = [(t, c) for t, rx, c in _RULES if rx.search(s)]
        if not hits:
            continue
        typ, conf = max(hits, key=lambda h: h[1])
        out.append({
            "page": page_no, "type": typ, "confidence": conf,
            "text_sat_deva": s[:300], "text_hi": None, "text_target": None,
            "status": "AUTO_EXTRACTED", "needs_review": ["text_hi", "text_target"],
        })
    return out


def top_vocab(text: str, n: int = 24) -> list[str]:
    toks = re.findall(r"[ऀ-ॿ]{2,}", text)
    freq: dict[str, int] = {}
    for t in toks:
        t = t.strip("।?,!»:«()[]")
        if len(t) >= 2 and not t.isdigit():
            freq[t] = freq.get(t, 0) + 1
    stop = set("और की को से में है हैं का के यह वह जो तो भी न पर या ainsi".split())
    return [w for w, _ in sorted(freq.items(), key=lambda kv: -kv[1]) if w not in stop][:n]


def save_image(data: bytes, ext: str, seen: dict, assets_dir: str) -> tuple[str | None, bool]:
    digest = hashlib.sha256(data).hexdigest()[:16]
    if digest in seen:
        return seen[digest], False
    name = f"img_{digest}.{ext if ext in ('png', 'jpg', 'jpeg') else 'png'}"
    blob = data
    if HAS_PIL and len(data) >= 6 * 1024:
        try:
            im = Image.open(_io.BytesIO(data)).convert("RGB")
            if max(im.size) > 768:
                im.thumbnail((768, 768))
            buf = _io.BytesIO()
            im.save(buf, "JPEG", quality=70)
            blob, name = buf.getvalue(), f"img_{digest}.jpg"
        except Exception:
            pass
    if len(blob) < 6 * 1024:
        seen[digest] = None
        return None, True  # skipped decorative tile
    with open(os.path.join(assets_dir, name), "wb") as f:
        f.write(blob)
    seen[digest] = f"assets/{name}"
    return seen[digest], True


def build_book(pdf_path: str, slug: str, subject: str, out_ch: str,
               assets_dir: str, seen: dict, stats: dict) -> dict:
    doc = pymupdf.open(pdf_path)
    pages_clean, all_assign, img_refs = [], [], []
    skipped_tiles = 0
    for i, page in enumerate(doc):
        raw = page.get_text()
        clean, _ = cleanup(raw)
        lines = [l.strip() for l in clean.splitlines() if l.strip()]
        pages_clean.append({"page": i + 1, "lines": lines})
        all_assign.extend(detect_assignments(i + 1, lines))
        for xref, *_ in page.get_images(full=True):
            try:
                px = doc.extract_image(xref)
                ref, kept = save_image(px["image"], px["ext"], seen, assets_dir)
                if kept and ref:
                    img_refs.append({"page": i + 1, "asset": ref})
                elif not kept:
                    skipped_tiles += 1
            except Exception:
                continue
    full_text = "\n".join(l for p in pages_clean for l in p["lines"])
    vocab = top_vocab(full_text)

    chapter = {
        "chapter_id": f"CH-G2-{slug.upper()[:12]}",
        "grade": 2, "subject": subject, "slug": slug,
        "source_pdf": os.path.basename(pdf_path), "pages": len(doc),
        "chars": len(full_text),
        "text_sat_deva": full_text,
        "text_hi": None, "text_target": None,
        "status": "AUTO_EXTRACTED",
        "needs_review": ["text_hi", "text_target", "assignments", "vocabulary"],
        "vocabulary_sat_deva": vocab,
        "image_refs": img_refs,
    }
    with open(os.path.join(out_ch, "chapter.json"), "w", encoding="utf-8") as f:
        json.dump(chapter, f, ensure_ascii=False, indent=1)

    by_type: dict[str, int] = {}
    for a in all_assign:
        by_type[a["type"]] = by_type.get(a["type"], 0) + 1
    with open(os.path.join(out_ch, "assignments.json"), "w", encoding="utf-8") as f:
        json.dump({"chapter": chapter["chapter_id"], "count": len(all_assign),
                   "by_type": by_type, "items": all_assign},
                  f, ensure_ascii=False, indent=1)

    ws_dir = os.path.join(out_ch, "worksheets")
    fc_dir = os.path.join(out_ch, "flashcards")
    os.makedirs(ws_dir, exist_ok=True)
    os.makedirs(fc_dir, exist_ok=True)
    ws = build_worksheet(chapter, all_assign, vocab)
    with open(os.path.join(ws_dir, f"ws_{slug}_bilingual.json"), "w", encoding="utf-8") as f:
        json.dump(ws, f, ensure_ascii=False, indent=1)
    with open(os.path.join(ws_dir, f"ws_{slug}_bilingual.md"), "w", encoding="utf-8") as f:
        f.write(worksheet_markdown(ws))
    deck = build_deck(chapter, vocab, img_refs)
    with open(os.path.join(fc_dir, "deck_bilingual.json"), "w", encoding="utf-8") as f:
        json.dump(deck, f, ensure_ascii=False, indent=1)

    stats[slug] = {"pages": len(doc), "chars": len(full_text),
                   "assignments": len(all_assign), "by_type": by_type,
                   "vocab": len(vocab), "images": len(img_refs),
                   "skipped_tiles": skipped_tiles,
                   "worksheet_items": len(ws["questions"]),
                   "flashcards": len(deck["cards"])}
    return chapter


def build_worksheet(chapter: dict, assigns: list[dict], vocab: list[str]) -> dict:
    slug, cid = chapter["slug"], chapter["chapter_id"]
    questions, answers = [], []

    # 1) counting — deterministic numeric variation (translation-free, safe)
    for i in range(3):
        n = stable_int("cnt", cid, i) % 20 + 1
        qid = f"Q-{slug}-CNT-{i + 1}"
        questions.append({"id": qid, "type": "counting",
                          "prompt_sat_deva": f"जिनिस को लेखा मे ({n})",
                          "prompt_target": None, "needs_review": ["prompt_target"],
                          "render_count": n, "answer": n})
        answers.append({"id": qid, "answer": str(n)})

    # 2) fill-in-blank from detected book sentences (max 5)
    fibs = [a for a in assigns if a["type"] == "FILL_BLANK"][:5]
    for i, a in enumerate(fibs):
        qid = f"Q-{slug}-FIB-{i + 1}"
        questions.append({"id": qid, "type": "fill_in_blank",
                          "prompt_sat_deva": a["text_sat_deva"],
                          "prompt_target": None,
                          "needs_review": ["prompt_target", "answer"],
                          "answer": None, "source_page": a["page"]})
        answers.append({"id": qid, "answer": None})

    # 3) matching — Santali-Deva vocab ↔ Ol Chiki numerals where numeric
    pairs = []
    for w in vocab[:8]:
        tgt = to_ol_numerals(w) if re.fullmatch(r"[०-९0-9]+", w) else None
        pairs.append({"sat_deva": w, "target": tgt,
                      "needs_review": [] if tgt else ["target"]})
    questions.append({"id": f"Q-{slug}-MATCH-1", "type": "matching",
                      "pairs": pairs, "answer": "column A↔B pairs (review pending)"})
    answers.append({"id": f"Q-{slug}-MATCH-1", "answer": "see pairs"})
    return {
        "worksheet_id": f"WS-G2-{slug}-bilingual",
        "meta": {"grade": 2, "subject": chapter["subject"], "chapter": cid,
                 "language": "bilingual", "num_items": len(questions),
                 "generator": "scripts/build_class2_content.py (deterministic, offline)",
                 "status": "AUTO_EXTRACTED — not SME-verified"},
        "questions": questions, "answer_key": answers}


def worksheet_markdown(ws: dict) -> str:
    L = [f"# Worksheet — {ws['worksheet_id']}",
         f"- Grade 2 {ws['meta']['subject']} | items: {ws['meta']['num_items']}",
         f"- Status: {ws['meta']['status']}", ""]
    for qi, q in enumerate(ws["questions"], 1):
        L.append(f"## {qi}. [{q['type']}] {q.get('prompt_sat_deva') or q['id']}")
        if q["type"] == "counting":
            L.append(f"   count: {q['render_count']} objects — answer: {q['answer']}")
        if q["type"] == "matching":
            for p in q["pairs"]:
                L.append(f"   - {p['sat_deva']} ↔ {p['target'] or '⟦REVIEW⟧'}")
        if q.get("needs_review"):
            L.append(f"   ⟦needs review: {', '.join(q['needs_review'])}⟧")
        L.append("")
    L.append("## Teacher Answer Key (partial — review pending)")
    for ak in ws["answer_key"]:
        L.append(f"- {ak['id']}: {ak['answer'] if ak['answer'] is not None else '⟦REVIEW⟧'}")
    return "\n".join(L)


def build_deck(chapter: dict, vocab: list[str], img_refs: list[dict]) -> dict:
    cards = []
    for i in range(1, 11):  # numerals 1-10: safe Hi + Ol Chiki, prebuilt art
        cards.append({"card_id": f"FC-{chapter['slug']}-N{i}", "sequence": i,
                      "concept_hi": str(i), "concept_sat_deva": str(i),
                      "concept_target": to_ol_numerals(str(i)),
                      "image_ref": f"flashcard/assets/number_{i}.png",
                      "audio_ref": None, "needs_review": ["audio_ref"]})
    seq = 11
    for j, w in enumerate(vocab[:10]):
        tgt = to_ol_numerals(w) if re.fullmatch(r"[०-९0-9]+", w) else None
        cards.append({"card_id": f"FC-{chapter['slug']}-V{j + 1}", "sequence": seq,
                      "concept_hi": None, "concept_sat_deva": w,
                      "concept_target": tgt,
                      "image_ref": img_refs[j]["asset"] if j < len(img_refs) else None,
                      "audio_ref": None,
                      "needs_review": ["concept_hi"] + ([] if tgt else ["concept_target"]) + ["audio_ref"]})
        seq += 1
    return {"deck_id": f"DECK-G2-{chapter['slug']}-bilingual",
            "meta": {"grade": 2, "chapter": chapter["chapter_id"],
                     "card_count": len(cards), "language": "bilingual",
                     "generator": "scripts/build_class2_content.py (deterministic, offline)",
                     "status": "AUTO_EXTRACTED — not SME-verified"},
            "cards": cards}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pdfs", default=os.path.join(ROOT, "santali_pdfs"))
    ap.add_argument("--out", default=os.path.join(ROOT, "curriculum", "class", "2"))
    args = ap.parse_args()
    out, assets_dir = args.out, os.path.join(args.out, "assets")
    os.makedirs(assets_dir, exist_ok=True)

    seen: dict = {}
    stats: dict = {}
    review_lines = ["# Class 2 — Speaker Review Sheet", "",
                    "Status: AUTO_EXTRACTED. Approve or correct per chapter.", ""]
    for pdf_name, slug, subject in CLASS2_BOOKS:
        out_ch = os.path.join(out, "chapters", slug)
        os.makedirs(os.path.join(out_ch, "worksheets"), exist_ok=True)
        os.makedirs(os.path.join(out_ch, "flashcards"), exist_ok=True)
        ch = build_book(os.path.join(args.pdfs, pdf_name), slug, subject,
                        out_ch, assets_dir, seen, stats)
        s = stats[slug]
        print(f"[{slug}] {s['pages']}p {s['chars']}ch assign={s['assignments']} "
              f"{s['by_type']} vocab={s['vocab']} img={s['images']} "
              f"ws={s['worksheet_items']} fc={s['flashcards']}")
        first = ch["text_sat_deva"].splitlines()
        review_lines += [f"## {slug} ({subject}, {s['pages']}p, src: {pdf_name})",
                         f"- Assignments: {s['assignments']} {s['by_type']}",
                         f"- Worksheet items: {s['worksheet_items']}, Flashcards: {s['flashcards']}",
                         f"- Images: {s['images']} kept, {s['skipped_tiles']} tiles skipped",
                         "- Sample source lines:", "```"] + first[:6] + ["```", ""]

    manifest = {"grade": 2, "books": len(CLASS2_BOOKS),
                "generator": "scripts/build_class2_content.py (deterministic, offline)",
                "status": "AUTO_EXTRACTED — speaker review required before APPROVED",
                "unique_assets": sum(1 for v in seen.values() if v),
                "chapters": stats}
    with open(os.path.join(out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    with open(os.path.join(out, "REVIEW.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(review_lines))
    print(f"[done] unique assets: {manifest['unique_assets']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
