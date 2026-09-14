"""Build Class 3 content pack (text + assignments + worksheets + flashcards).

Class-3 SPECIAL script (not generalized). Reads the 31 PDFs under
santali_organized/Class 3 (EVS 11 + Math 13 + PE 7, of which 4 are
Class-4 chapters misfiled in the PE folder) and emits deterministic,
review-gated content under curriculum/class/3/:

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
  * The 4 misfiled Class-4 PE chapters are still packed (source folder is
    the authority) but flagged grade=4 + grade_mismatch for the reviewer.

Usage:
    python scripts/build_class3_content.py
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

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_book_text import cleanup  # noqa: E402 (reuse collapse rules)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
C3 = os.path.join(ROOT, "santali_organized", "Class 3")

# (relative path under santali_organized/Class 3, chapter slug, subject, grade)
CLASS3_BOOKS = [
    # --- Environmental Studies (11) ---
    ("Environmental Studies/assignments/Class_3__Celebrating_Festivals__Santhali_.pdf", "celebrating-festivals", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__Family_and_Friends__Santhali_.pdf", "family-and-friends", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__Food_We_Eat__Santhali_.pdf", "food-we-eat", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__Getting_to_Know_Plants__Santhali_.pdf", "getting-to-know-plants", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__Going_to_the_Mela__Santhali_.pdf", "going-to-the-mela", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__Living_in_Harmony__Santhali_.pdf", "living-in-harmony", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__Making_Things__Santhali_.pdf", "making-things", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__Plants_and_Animals_Live_Together__Santhali_.pdf", "plants-and-animals-together", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__Taking_Charge_of_Waste__Santhali_.pdf", "taking-charge-of-waste", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__This_World_of_Things__Santhali_.pdf", "this-world-of-things", "Environmental Studies", 3),
    ("Environmental Studies/assignments/Class_3__Water__A_Precious_Gift__Santhali_.pdf", "water-a-precious-gift", "Environmental Studies", 3),
    # --- Mathematics (13) ---
    ("Mathematics/assignments/Class_3__Double_Century__Santali_.pdf", "double-century", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__Fair_Share__Santali_.pdf", "fair-share", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__Filling_and_Lifting__Santali_.pdf", "filling-and-lifting", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__Fun_at_Class_Party___Santali_.pdf", "fun-at-class-party", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__Fun_with_Shapes__Santali_.pdf", "fun-with-shapes", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__Give_and_Take__Santali_.pdf", "give-and-take", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__House_of_Hundreds_-_I__Santali_.pdf", "house-of-hundreds-1", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__Raksha_Bandhan__Santali_.pdf", "raksha-bandhan", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__The_Surajkund_Fair__Santali_.pdf", "surajkund-fair", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__Time_Goes_On__Santali_.pdf", "time-goes-on", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__Toy_Joy__Santali_.pdf", "toy-joy", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__Vacation_with_My_Nani_Maa__Santali_.pdf", "vacation-with-nani-maa", "Mathematics", 3),
    ("Mathematics/assignments/Class_3__What_s_in_a_Name___Santali_.pdf", "whats-in-a-name", "Mathematics", 3),
    # --- Physical Education & Well Being (3 x Class 3) ---
    ("Physical Education  & Well Being/assignments/Class_3__Little_Steps__Santali_.pdf", "little-steps", "Physical Education & Well Being", 3),
    ("Physical Education  & Well Being/assignments/Class_3__Strike_the_Ball__Santali_.pdf", "strike-the-ball", "Physical Education & Well Being", 3),
    ("Physical Education  & Well Being/assignments/Class_3__Yogic_Practices__Yoga_Sadhana___Santali_.pdf", "yogic-practices", "Physical Education & Well Being", 3),
    # --- Misfiled Class-4 chapters inside the Class-3 PE folder (flagged) ---
    ("Physical Education  & Well Being/assignments/Class_4__Chapter_-_1_Throwing_and_Catching__Santhali_.pdf", "throwing-and-catching", "Physical Education & Well Being", 4),
    ("Physical Education  & Well Being/assignments/Class_4__Chapter_-_2_Kicking_and__Receiving__Santhali_.pdf", "kicking-and-receiving", "Physical Education & Well Being", 4),
    ("Physical Education  & Well Being/assignments/Class_4__Chapter_-_5_Local_and_Traditional_Games__Santhali_.pdf", "local-and-traditional-games", "Physical Education & Well Being", 4),
    ("Physical Education  & Well Being/assignments/Class_4__Chapter_-_6_Yoga_for_Daily__Life__Santhali_.pdf", "yoga-for-daily-life", "Physical Education & Well Being", 4),
]

DEVA_DIGITS = "०१२३४५६७८९"
OL_DIGITS = "᱐᱑᱒᱓᱔᱕᱖᱗᱘᱙"
DIGIT_MAP = {ord(a): b for a, b in zip(DEVA_DIGITS + "0123456789", OL_DIGITS * 2)}


def to_ol_numerals(s: str) -> str:
    return s.translate(DIGIT_MAP)


def stable_int(*parts) -> int:
    h = hashlib.sha256(("|".join(str(p) for p in parts)).encode()).hexdigest()
    return int(h, 16)


# --- assignment detection (rule-based, confidence-scored, same as Class 2) ---
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
    stop = set("और की को से में है हैं का के यह वह जो तो भी न पर या".split())
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


def build_book(pdf_path: str, slug: str, subject: str, grade: int, out_ch: str,
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

    # Class-3 SPECIAL: honest encoding flag — most books are Krishna legacy
    # (deva_ratio ~0), 3 are scanned images (0 chars). Never present Krishna
    # codepoints as cleaned Devanagari.
    deva_n = sum(1 for c in full_text if "\u0900" <= c <= "\u097f")
    deva_ratio = deva_n / max(1, len(full_text))
    if len(full_text) == 0:
        encoding, note = "SCANNED_IMAGE_ONLY", "No embedded text — scanned pages, OCR required."
    elif deva_ratio < 0.05:
        encoding, note = "KRISHNA_LEGACY", "Raw Krishna codepoints — NOT cleaned Devanagari. Requires Krishna mapping table."
    else:
        encoding, note = "UNICODE_DEVANAGARI", "Unicode Devanagari with shaping-collapse applied."

    needs = ["text_hi", "text_target", "assignments", "vocabulary"]
    if encoding == "KRISHNA_LEGACY":
        needs = ["krishna_decode"] + needs
    if encoding == "SCANNED_IMAGE_ONLY":
        needs = ["ocr_required"] + needs
    if grade != 3:
        needs = needs + ["grade_mismatch"]
    chapter = {
        "chapter_id": f"CH-G{grade}-{slug.upper()[:12]}",
        "grade": grade, "subject": subject, "slug": slug,
        "source_pdf": os.path.relpath(pdf_path, C3), "pages": len(doc),
        "chars": len(full_text),
        "text_sat_deva": full_text,
        "text_hi": None, "text_target": None,
        "status": "AUTO_EXTRACTED",
        "needs_review": needs,
        "text_encoding": encoding,
        "text_note": note,
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
                   "grade": grade, "subject": subject,
                   "assignments": len(all_assign), "by_type": by_type,
                   "vocab": len(vocab), "images": len(img_refs),
                   "skipped_tiles": skipped_tiles,
                   "worksheet_items": len(ws["questions"]),
                   "flashcards": len(deck["cards"])}
    return chapter


def build_worksheet(chapter: dict, assigns: list[dict], vocab: list[str]) -> dict:
    slug, cid = chapter["slug"], chapter["chapter_id"]
    grade = chapter["grade"]
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
        "worksheet_id": f"WS-G{grade}-{slug}-bilingual",
        "meta": {"grade": grade, "subject": chapter["subject"], "chapter": cid,
                 "language": "bilingual", "num_items": len(questions),
                 "generator": "scripts/build_class3_content.py (deterministic, offline)",
                 "status": "AUTO_EXTRACTED — not SME-verified"},
        "questions": questions, "answer_key": answers}


def worksheet_markdown(ws: dict) -> str:
    L = [f"# Worksheet — {ws['worksheet_id']}",
         f"- Grade {ws['meta']['grade']} {ws['meta']['subject']} | items: {ws['meta']['num_items']}",
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
    return {"deck_id": f"DECK-G{chapter['grade']}-{chapter['slug']}-bilingual",
            "meta": {"grade": chapter["grade"], "chapter": chapter["chapter_id"],
                     "card_count": len(cards), "language": "bilingual",
                     "generator": "scripts/build_class3_content.py (deterministic, offline)",
                     "status": "AUTO_EXTRACTED — not SME-verified"},
            "cards": cards}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=C3)
    ap.add_argument("--out", default=os.path.join(ROOT, "curriculum", "class", "3"))
    args = ap.parse_args()
    out, assets_dir = args.out, os.path.join(args.out, "assets")
    os.makedirs(assets_dir, exist_ok=True)

    seen: dict = {}
    stats: dict = {}
    review_lines = ["# Class 3 — Speaker Review Sheet", "",
                    "Status: AUTO_EXTRACTED. Approve or correct per chapter.",
                    "Note: 4 Class-4 PE chapters were misfiled inside the Class-3 PE folder;",
                    "they are packed with grade=4 + grade_mismatch for the reviewer.", ""]
    for rel, slug, subject, grade in CLASS3_BOOKS:
        pdf_path = os.path.join(args.src, rel)
        if not os.path.exists(pdf_path):
            print(f"[SKIP missing] {rel}")
            continue
        out_ch = os.path.join(out, "chapters", slug)
        os.makedirs(os.path.join(out_ch, "worksheets"), exist_ok=True)
        os.makedirs(os.path.join(out_ch, "flashcards"), exist_ok=True)
        ch = build_book(pdf_path, slug, subject, grade,
                        out_ch, assets_dir, seen, stats)
        s = stats[slug]
        print(f"[{slug}] {s['pages']}p {s['chars']}ch assign={s['assignments']} "
              f"{s['by_type']} vocab={s['vocab']} img={s['images']} "
              f"ws={s['worksheet_items']} fc={s['flashcards']}", flush=True)
        first = ch["text_sat_deva"].splitlines()
        review_lines += [f"## {slug} (G{grade} {subject}, {s['pages']}p, src: {rel})",
                         f"- Assignments: {s['assignments']} {s['by_type']}",
                         f"- Worksheet items: {s['worksheet_items']}, Flashcards: {s['flashcards']}",
                         f"- Images: {s['images']} kept, {s['skipped_tiles']} tiles skipped",
                         "- Sample source lines:", "```"] + first[:6] + ["```", ""]

    manifest = {"grade": 3, "books": len(stats),
                "generator": "scripts/build_class3_content.py (deterministic, offline)",
                "status": "AUTO_EXTRACTED — speaker review required before APPROVED",
                "unique_assets": sum(1 for v in seen.values() if v),
                "chapters": stats}
    with open(os.path.join(out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    with open(os.path.join(out, "REVIEW.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(review_lines))
    print(f"[done] unique assets: {manifest['unique_assets']}")
    # Budget trim: single source of truth lives in trim_class3_assets.py
    from trim_class3_assets import finalize as _trim_finalize
    _trim_finalize(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
