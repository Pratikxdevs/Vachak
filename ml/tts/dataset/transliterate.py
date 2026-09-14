#!/usr/bin/env python3
"""
transliterate.py — Santali Devanagari (Jharkhand school orthography) -> Ol Chiki.

Why: TTS training audio (raw/santali_male_native_web, ~10h single male speaker,
48kHz) is transcribed in Devanagari Santali, but MT outputs Ol Chiki and the
sherpa-onnx TTS tokens are Ol Chiki (U+1C50-U+1C7F). The model must train on
Ol Chiki so train/inference graphemes match.

Mapping decisions (empirically grounded against datasets/hin_sat MT corpus):
  - o-vowels (ओ/ो/ो+nukta/ऑ) -> U+1C5A (ᱚ). MT corpus has ZERO U+1C73 (ᱳ),
    and anchors match exactly: दो़->ᱫᱚ, को़->ᱠᱚ, होड़/हो़ड़->ᱦᱚᱲ.
  - VOWEL+nukta on a-vowels (ा़/आ़) -> ᱟ+ᱹ (U+1C79 gahlah, length mark):
    आ़डी->ᱟᱹᱰᱤ (MT 95x), का़मी->ᱠᱟᱹᱢᱤ, पा़रसी->ᱯᱟᱹᱨᱥᱤ (MT 20x).
  - ड़/ढ़ (U+095C/U+095D or ड/ढ+़) -> U+1C72 (ᱲ).
  - anusvara/chandrabindu -> U+1C78 (ᱸ): ताँहें->ᱛᱟᱸᱦᱮᱸ, हो़ं->ᱦᱚᱸ.
  - visarga -> U+1C77 (ᱷ, glottal): आबोवाः->ᱟᱵᱚᱣᱟᱷ. MT uses ᱷ 4358x.
  - word-final halant STOP -> voiced counterpart + U+1C7C + ᱟ (MT convention
    for checked consonants): मेनाक्->ᱢᱮᱱᱟᱜᱼᱟ (MT 2955x).
  - word-final halant non-stop -> bare consonant (coda).
  - medial conjunct (C+halant+C) -> bare C + continue (Ol Chiki: no conjuncts).
  - schwa handling for bare-medial C: DELETE iff next akshara carries an
    explicit LONG vowel (ा ी ू े ै ो ौ ॉ); else keep as C+ᱚ; word-final
    bare C always deletes. Verified: सानताड़->ᱥᱟᱱᱛᱟᱲ, काना->ᱠᱟᱱᱟ,
    होड़को->ᱦᱚᱲᱠᱚ, कोवा->ᱠᱚᱣᱟ, बुनियद keeps य.
  - aspirates -> base + ᱦ (ख->ᱠᱷ); DP: खो़न->ᱠᱷᱚᱱ.
  - digits (Devanagari + ASCII) -> Ol Chiki digits U+1C50-59.
  - punctuation passthrough: । . , ? ! : ; ' " - ( ) — requires tokens.txt
    extension (see export step); ZWJ/ZWNJ dropped.

Also provides normalize_mt(): runtime normalizer bringing MT Ol Chiki output
into the training convention (whitespace, digits, vocab filter). v1 is
deliberately minimal; WORD_FIXES holds hand-verified mismatches.

Usage:
  python ml/tts/dataset/transliterate.py --report      # coverage + MT overlap
  python ml/tts/dataset/transliterate.py --write-tsv   # dataset/santali_olchiki.tsv
"""

from __future__ import annotations

import argparse
import collections
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
TTS_TXT = ROOT / "raw" / "santali_male_native_web" / "santali_male_text.txt"
MT_FILES = [ROOT / "datasets" / "hin_sat" / "corpus.tsv",
            ROOT / "datasets" / "hin_sat" / "corpus.verified.tsv"]
OUT_TSV = ROOT / "ml" / "tts" / "dataset" / "santali_olchiki.tsv"

# ---------------------------------------------------------------- Ol Chiki ---
O = lambda cp: chr(cp)  # noqa: E731
OL_DIGITS = {str(d): O(0x1C50 + d) for d in range(10)}
OL_DIGITS.update({"०": O(0x1C50), "१": O(0x1C51), "२": O(0x1C52),
                  "३": O(0x1C53), "४": O(0x1C54), "५": O(0x1C55),
                  "६": O(0x1C56), "७": O(0x1C57), "८": O(0x1C58),
                  "९": O(0x1C59)})
LA, LAA = O(0x1C5A), O(0x1C5F)          # ᱚ ᱟ
MU_TTUDDAG = O(0x1C78)                  # ᱸ nasalization
GAHLAH = O(0x1C79)                      # ᱹ length mark
TUDAG_GLOTTAL = O(0x1C7C)               # ᱼ (MT checked-final convention)
AHAD = O(0x1C77)                        # ᱷ (visarga/glottal)

ASP = O(0x1C77)  # ᱷ aspiration Johns (MT: ᱠᱷᱚᱱ, ᱢᱟᱪᱷᱤ, ᱫᱩᱫᱷ)
CONS = {  # Devanagari base -> Ol Chiki letter
    "क": O(0x1C60), "ख": O(0x1C60) + ASP, "ग": O(0x1C5C),
    "घ": O(0x1C5C) + ASP, "ङ": O(0x1C5D),
    "च": O(0x1C6A), "छ": O(0x1C6A) + ASP, "ज": O(0x1C61),
    "झ": O(0x1C61) + ASP, "ञ": O(0x1C67),
    "ट": O(0x1C74), "ठ": O(0x1C74) + ASP, "ड": O(0x1C70),
    "ढ": O(0x1C70) + ASP, "ण": O(0x1C6C),
    "त": O(0x1C5B), "थ": O(0x1C5B) + ASP, "द": O(0x1C6B),
    "ध": O(0x1C6B) + ASP, "न": O(0x1C71),
    "प": O(0x1C6F), "फ": O(0x1C6F) + ASP, "ब": O(0x1C75),
    "भ": O(0x1C75) + ASP, "म": O(0x1C62),
    "य": O(0x1C6D), "र": O(0x1C68), "ल": O(0x1C5E), "व": O(0x1C63),
    "श": O(0x1C65), "ष": O(0x1C65), "स": O(0x1C65), "ह": O(0x1C66),
    "ळ": O(0x1C5E), "क़": O(0x1C60), "ख़": O(0x1C60) + ASP,
    "ग़": O(0x1C5C), "ज़": O(0x1C61), "झ़": O(0x1C61), "फ़": O(0x1C6F),
    "ड़": O(0x1C72), "ढ़": O(0x1C72),
    "\u095c": O(0x1C72), "\u095d": O(0x1C72),  # precomposed ड़/ढ़
    "\u0958": O(0x1C60), "\u0959": O(0x1C60) + ASP,
    "\u095a": O(0x1C5C), "\u095b": O(0x1C61), "\u095e": O(0x1C6F),
}
VOICED_FINAL = {  # checked-final stops -> MT voiced + ᱼ + ᱟ convention
    "क": O(0x1C5C), "ख": O(0x1C5C), "ग": O(0x1C5C), "घ": O(0x1C5C),
    "क़": O(0x1C5C), "ख़": O(0x1C5C), "ग़": O(0x1C5C),
    "च": O(0x1C61), "छ": O(0x1C61), "ज": O(0x1C61), "झ": O(0x1C61),
    "ज़": O(0x1C61), "झ़": O(0x1C61),
    "ट": O(0x1C70), "ठ": O(0x1C70), "ड": O(0x1C70), "ढ": O(0x1C70),
    "त": O(0x1C6B), "थ": O(0x1C6B), "द": O(0x1C6B), "ध": O(0x1C6B),
    "प": O(0x1C75), "फ": O(0x1C75), "ब": O(0x1C75), "भ": O(0x1C75),
    "फ़": O(0x1C75),
}
VOWEL_SIGN = {  # explicit matra -> Ol Chiki vowel
    "ा": LAA, "ि": O(0x1C64), "ी": O(0x1C64), "ु": O(0x1C69),
    "ू": O(0x1C69), "ृ": O(0x1C68) + O(0x1C64), "े": O(0x1C6E),
    "ै": O(0x1C6E) + O(0x1C6D), "ो": LA, "ौ": LAA + O(0x1C63),
    "ॉ": LA, "ॆ": O(0x1C6E), "ॊ": LA, "ॎ": LA,
}
INDEP_VOWEL = {
    "अ": LA, "आ": LAA, "इ": O(0x1C64), "ई": O(0x1C64),
    "उ": O(0x1C69), "ऊ": O(0x1C69), "ऋ": O(0x1C68) + O(0x1C64),
    "ए": O(0x1C6E), "ऐ": O(0x1C6E) + O(0x1C6D), "ओ": LA,
    "औ": LAA + O(0x1C63), "ऑ": LA,
}
LONG_SIGNS = set("ाीूेैोौॉॆॊॎ")
NUKTA = "़"
HALANT = "्"
ANUSVARA = {"ं": MU_TTUDDAG, "ँ": MU_TTUDDAG}
VISARGA = "ः"
CONSONANTS = set(CONS) | {"\u095c", "\u095d", "\u0958", "\u0959",
                           "\u095a", "\u095b", "\u095e"}
PUNCT_KEEP = set("।.,?!:;'-()\"/—–")
ZW = {"\u200c", "\u200d", "\u200b", "\ufeff", "‘", "’", "\u0952", "\"", "\""}

unmapped: collections.Counter = collections.Counter()


def _ol_consonant(base: str, nukta: bool) -> str:
    if nukta and base in ("ड", "ढ"):
        return O(0x1C72)  # ड़/ढ़ -> ᱲ (plain ड/ढ stay ᱰ)
    out = CONS.get(base, "")
    if not out:
        unmapped["cons:" + base] += 1
    return out


def _ol_vowel(v: str, nukta: bool) -> str:
    if v in ("ा", "आ"):
        return (LAA + GAHLAH) if nukta else LAA
    if v in ("ो", "ओ", "ॉ", "ऑ", "ॊ"):
        return LA  # nukta dropped: दो़->ᱫᱚ (MT 1514x)
    if v == "अ":
        return LA
    base = VOWEL_SIGN.get(v, INDEP_VOWEL.get(v, ""))
    if not base:
        unmapped["vow:" + v] += 1
        return ""
    if nukta and v in ("ि", "ई", "इ", "ु", "ू", "उ", "ऊ", "े", "ए"):
        return base + GAHLAH
    return base


def transliterate_word(word: str) -> str:
    """Transliterate one Devanagari token -> Ol Chiki (punct passed through)."""
    # Parse into aksharas: dicts {kind: cons|indep|other, base, vowel, nukta,
    #                             halant_final, mods:[], lit}
    aksharas = []
    i, n = 0, len(word)
    while i < n:
        ch = word[i]
        if ch in ZW:
            i += 1
            continue
        if ch in OL_DIGITS:
            aksharas.append({"kind": "other", "lit": OL_DIGITS[ch]})
            i += 1
            continue
        if ch in PUNCT_KEEP:
            aksharas.append({"kind": "other", "lit": ch})
            i += 1
            continue
        if ch in INDEP_VOWEL or ch == "अ":
            nukta = (i + 1 < n and word[i + 1] == NUKTA)
            mods = []
            j = i + 1 + (1 if nukta else 0)
            while j < n and (word[j] in ANUSVARA or word[j] == VISARGA):
                mods.append(word[j])
                j += 1
            aksharas.append({"kind": "indep", "base": ch, "vowel": ch,
                             "nukta": nukta, "mods": mods})
            i = j
            continue
        if ch in CONSONANTS:
            base = ch
            j = i + 1
            nukta = False
            if j < n and word[j] == NUKTA:
                nukta = True
                j += 1
            vowel = None
            if j < n and word[j] in VOWEL_SIGN:
                vowel = word[j]
                j += 1
                if j < n and word[j] == NUKTA:  # ो + nukta order
                    nukta = True
                    j += 1
            mods = []
            while j < n and (word[j] in ANUSVARA or word[j] == VISARGA):
                mods.append(word[j])
                j += 1
            halant_final = False
            if j < n and word[j] == HALANT:
                if j + 1 < n and word[j + 1] in CONSONANTS:
                    # conjunct: coda, next consonant starts new akshara
                    aksharas.append({"kind": "cons", "base": base,
                                     "vowel": None, "nukta": nukta,
                                     "coda": True, "mods": mods})
                    i = j + 1
                    continue
                halant_final = True
                j += 1
            aksharas.append({"kind": "cons", "base": base, "vowel": vowel,
                             "nukta": nukta, "halant_final": halant_final,
                             "mods": mods})
            i = j
            continue
        if ch == NUKTA:  # stray nukta: attach to previous akshara
            if aksharas and aksharas[-1].get("kind") in ("cons", "indep"):
                aksharas[-1]["nukta"] = True
            i += 1
            continue
        if ch in ANUSVARA:
            if aksharas and aksharas[-1].get("kind") in ("cons", "indep"):
                aksharas[-1].setdefault("mods", []).append(ch)
            else:
                aksharas.append({"kind": "other", "lit": MU_TTUDDAG})
            i += 1
            continue
        if ch == VISARGA:
            if aksharas and aksharas[-1].get("kind") in ("cons", "indep"):
                aksharas[-1].setdefault("mods", []).append(ch)
            else:
                aksharas.append({"kind": "other", "lit": AHAD})
            i += 1
            continue
        if ch == HALANT:
            if aksharas and aksharas[-1].get("kind") == "cons":
                aksharas[-1]["halant_final"] = True
            i += 1
            continue
        if ch.isspace():
            aksharas.append({"kind": "other", "lit": " "})
            i += 1
            continue
        unmapped["other:" + ch] += 1
        i += 1

    out = []
    last = len(aksharas) - 1
    # index of last *phonetic* akshara (skip trailing punct)
    last_phon = last
    while last_phon >= 0 and aksharas[last_phon]["kind"] == "other":
        last_phon -= 1
    for idx, a in enumerate(aksharas):
        if a["kind"] == "other":
            out.append(a["lit"])
            continue
        mods = "".join(MU_TTUDDAG if m in ANUSVARA else AHAD
                       for m in a.get("mods", []))
        if a["kind"] == "indep":
            out.append(_ol_vowel(a["vowel"], a["nukta"]) + mods)
            continue
        # consonant akshara
        c_ol = _ol_consonant(a["base"], a["nukta"])
        if a.get("coda"):
            out.append(c_ol + mods)
            continue
        if a.get("halant_final"):
            if a["base"] in VOICED_FINAL:
                out.append(VOICED_FINAL[a["base"]] + TUDAG_GLOTTAL + LAA + mods)
            else:
                out.append(c_ol + mods)  # non-stop coda
            continue
        if a["vowel"] is not None:
            out.append(c_ol + _ol_vowel(a["vowel"], a["nukta"]) + mods)
            continue
        # bare consonant: schwa rule
        if idx == last_phon:
            out.append(c_ol + mods)  # word-final coda
            continue
        nxt = aksharas[idx + 1]
        if nxt.get("kind") == "cons" and nxt.get("vowel") in LONG_SIGNS:
            out.append(c_ol + mods)  # delete before explicit long vowel
        else:
            out.append(c_ol + LA + mods)  # keep inherent as ᱚ
    return "".join(out)


def transliterate_text(text: str) -> str:
    return " ".join(transliterate_word(w) for w in text.split(" "))


# ------------------------------------------------- reverse (Ol Chiki -> Dev) ---
# Interim-only bridge: lets the Hindi-VITS interim voice speak MT Ol Chiki
# output via espeak-ng 'hi' phonemization. Deterministic char-level mapping;
# known accent notes: C+ᱚ always -> C+ो (explicit), so bare-kept schwas
# (e.g. उनकिन) gain a vowel (उनोकिन) — intelligible, fixed by native VITS.
OL_CONS_DEV = {
    O(0x1C5A): ("अ", None), O(0x1C5F): ("आ", "ा"), O(0x1C64): ("इ", "ि"),
    O(0x1C69): ("उ", "ु"), O(0x1C6E): ("ए", "े"), O(0x1C5C): ("ग", None),
    O(0x1C60): ("क", None), O(0x1C61): ("ज", None), O(0x1C62): ("म", None),
    O(0x1C63): ("व", None), O(0x1C65): ("स", None), O(0x1C66): ("ह", None),
    O(0x1C67): ("ञ", None), O(0x1C68): ("र", None), O(0x1C5B): ("त", None),
    O(0x1C5D): ("ङ", None), O(0x1C5E): ("ल", None), O(0x1C6A): ("च", None),
    O(0x1C6B): ("द", None), O(0x1C6C): ("ण", None), O(0x1C6D): ("य", None),
    O(0x1C6F): ("प", None), O(0x1C70): ("ड", None), O(0x1C71): ("न", None),
    O(0x1C72): ("ड़", None), O(0x1C74): ("ट", None), O(0x1C75): ("ब", None),
}
OL_VOWEL_AFTER_CONS = {O(0x1C5A): "ो"}  # C+ᱚ -> C+ो (explicit)
ASP_REV = {  # aspirate pairs -> Devanagari aspirate (must run pre-pass)
    O(0x1C60) + O(0x1C77): "ख", O(0x1C5C) + O(0x1C77): "घ",
    O(0x1C6A) + O(0x1C77): "छ", O(0x1C61) + O(0x1C77): "झ",
    O(0x1C74) + O(0x1C77): "ठ", O(0x1C70) + O(0x1C77): "ढ",
    O(0x1C5B) + O(0x1C77): "थ", O(0x1C6B) + O(0x1C77): "ध",
    O(0x1C6F) + O(0x1C77): "फ", O(0x1C75) + O(0x1C77): "भ",
}
CHECKED_REV = {  # MT checked-final convention -> Devanagari halant stops
    O(0x1C5C) + TUDAG_GLOTTAL + LAA: "क्", O(0x1C61) + TUDAG_GLOTTAL + LAA: "च्",
    O(0x1C70) + TUDAG_GLOTTAL + LAA: "ट्", O(0x1C6B) + TUDAG_GLOTTAL + LAA: "त्",
    O(0x1C75) + TUDAG_GLOTTAL + LAA: "प्",
}


def ol_to_dev(text: str) -> str:
    """Ol Chiki -> Devanagari (interim bridge for Hindi-VITS voice)."""
    for k, v in CHECKED_REV.items():
        text = text.replace(k, v)
    for k, v in ASP_REV.items():
        text = text.replace(k, v)
    out: list[str] = []
    prev_is_cons = False
    for ch in text:
        if ch in (" ", "\u200c", "\u200d"):
            out.append(" " if ch == " " else "")
            prev_is_cons = False
            continue
        if ch in OL_DIGITS.values():
            d = "0123456789०१२३४५६७८९"
            inv = {v: k for k, v in OL_DIGITS.items()}
            out.append(inv.get(ch, ch))
            prev_is_cons = False
            continue
        if ch == O(0x1C7D):  # pharkaa: drop (matches normalize_mt)
            continue
        if ch == MU_TTUDDAG:
            out.append("ं")
            continue
        if ch == GAHLAH:  # length mark: no Devanagari equivalent for espeak
            continue
        if ch == O(0x1C77):  # standalone ahad -> visarga
            out.append("ः")
            prev_is_cons = False
            continue
        if ch == TUDAG_GLOTTAL:  # stray tudag -> drop
            continue
        if ch in OL_VOWEL_AFTER_CONS and prev_is_cons:
            out.append(OL_VOWEL_AFTER_CONS[ch])
            prev_is_cons = False
            continue
        if ch in OL_CONS_DEV:
            indep, matra = OL_CONS_DEV[ch]
            if matra is not None and prev_is_cons:
                out.append(matra)
            else:
                out.append(indep)
            prev_is_cons = (matra is None)
            continue
        if ch in PUNCT_KEEP:
            out.append(ch)
            prev_is_cons = False
            continue
        if "\u0900" <= ch <= "\u097f":  # pre-pass output (ASP/CHECKED pairs)
            out.append(ch)              # passes through for espeak-ng 'hi'
            prev_is_cons = False
            continue
        unmapped["rev:" + ch] += 1
    return "".join(out)


# ------------------------------------------------------------------ runtime --
WORD_FIXES: dict[str, str] = {
    # hand-verified MT-output -> training-convention fixes (v1: empty core;
    # anchors already match; extend from eval mismatches)
}

OL_VOCAB_RE = re.compile(r"^[\u1c50-\u1c7f ।.,?!:;'\-()\"/—–0123456789 ]*$")


def normalize_mt(text: str) -> str:
    """Bring MT Ol Chiki output into TTS training convention."""
    text = " ".join(text.split())
    for k, v in WORD_FIXES.items():
        text = text.replace(k, v)
    # training never emits pharkaa ᱽ (U+1C7D); MT does (ᱜᱤᱫᱽᱨᱟᱹ, ᱥᱚᱵᱽᱡᱤ).
    # Dropping it maps to the closest in-vocab training spelling.
    text = text.replace(O(0x1C7D), "")
    text = "".join(OL_DIGITS.get(ch, ch) for ch in text)
    if not OL_VOCAB_RE.match(text):
        kept = "".join(ch for ch in text
                       if "\u1c50" <= ch <= "\u1c7f" or ch in " ।.,?!:;'-()\"/—– ")
        return " ".join(kept.split())
    return text


# -------------------------------------------------------------------- report --
def load_tts_rows():
    rows = []
    for line in TTS_TXT.read_text(encoding="utf-8").splitlines():
        if "\t" in line:
            uid, txt = line.split("\t", 1)
            rows.append((uid.strip(), txt.strip()))
    return rows


def load_mt_vocab():
    vocab: collections.Counter = collections.Counter()
    for p in MT_FILES:
        if not p.exists():
            continue
        for line in p.read_text(encoding="utf-8").splitlines():
            parts = line.split("\t")
            if len(parts) >= 2:
                for w in parts[1].split():
                    vocab[w] += 1
    return vocab


def report() -> int:
    global unmapped
    unmapped = collections.Counter()
    rows = load_tts_rows()
    tr = [(uid, transliterate_text(t)) for uid, t in rows]
    mt_vocab = load_mt_vocab()
    tr_types: collections.Counter = collections.Counter()
    for _, t in tr:
        for w in t.split():
            tr_types[w] += 1
    inter = set(tr_types) & set(mt_vocab)
    inter_tok = sum(mt_vocab[w] for w in inter)
    print(f"[report] tts utterances: {len(rows)}")
    print(f"[report] transliterated word types: {len(tr_types)}")
    print(f"[report] MT word types: {len(mt_vocab)}")
    print(f"[report] shared types: {len(inter)} "
          f"covering {inter_tok} MT tokens")
    top_mt = [w for w, _ in mt_vocab.most_common(100)]
    miss = [w for w in top_mt if w not in tr_types]
    print(f"[report] top-100 MT words missing from TTS vocab: {len(miss)}")
    for w in miss[:30]:
        print(f"   miss {w} x{mt_vocab[w]}")
    print("[report] unmapped input chars:")
    for k, c in unmapped.most_common(20):
        print(f"   {c:6d} {k!r}")
    anchors = {
        "दो़": "ᱫᱚ", "को़": "ᱠᱚ", "होड़": "ᱦᱚᱲ", "मेनाक्": "ᱢᱮᱱᱟᱜᱼᱟ",
        "काना": "ᱠᱟᱱᱟ", "आर": "ᱟᱨ", "सानताड़": "ᱥᱟᱱᱛᱟᱲ",
        "आ़डी": "ᱟᱹᱰᱤ", "पा़रसी": "ᱯᱟᱹᱨᱥᱤ", "का़मी": "ᱠᱟᱹᱢᱤ",
        "कोवा": "ᱠᱚᱣᱟ", "ओ़ना": "ᱚᱱᱟ", "खो़न": "ᱠᱷᱚᱱ",
        "हो़ं": "ᱦᱚᱸ", "सानताड़ी": "ᱥᱟᱱᱛᱟᱲᱤ", "कुड़ी": "ᱠᱩᱲᱤ",
    }
    bad = 0
    for dev, exp in anchors.items():
        got = transliterate_word(dev)
        ok = got == exp
        bad += (not ok)
        print(f"[anchor] {'OK ' if ok else 'FAIL'} {dev} -> {got} (exp {exp})")
    print(f"[report] anchors failed: {bad}/{len(anchors)}")
    return 1 if bad else 0


def write_tsv() -> None:
    rows = load_tts_rows()
    lines = ["utt_id\twav_path\ttext_olchiki"]
    for uid, txt in rows:
        ol = transliterate_text(txt)
        lines.append(f"{uid}\twavs/{uid}.wav\t{ol}")
    OUT_TSV.parent.mkdir(parents=True, exist_ok=True)
    OUT_TSV.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"[write] {OUT_TSV} ({len(rows)} rows)")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", action="store_true")
    ap.add_argument("--write-tsv", action="store_true")
    args = ap.parse_args()
    if not (args.report or args.write_tsv):
        args.report = True
    rc = 0
    if args.report:
        rc = report()
    if args.write_tsv:
        write_tsv()
    sys.exit(rc)


if __name__ == "__main__":
    main()
