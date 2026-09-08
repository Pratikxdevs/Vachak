#!/usr/bin/env python3
"""Deterministic Hindi<->Santali NUMERAL dataset (1-100 + possessive + counts).

Not machine-translated prose: a verifiable numeral fact table. Santali Ol Chiki
composition rules (validated against the project's gold pairs):
  units 1-9: ᱢᱤᱫ ᱵᱟᱨ ᱯᱮ ᱯᱳᱱ ᱢᱚᱬᱮ ᱛᱩᱨᱩᱭ ᱮᱭᱟᱭ ᱟᱨ ᱱᱟᱭ
  10 ᱜᱮᱞ  20 ᱠᱩᱲᱤ  100 ᱥᱟᱭ
  11-19 ᱜᱮᱞ+unit ; 21-29 ᱠᱩᱲᱤ+unit ; 30 ᱯᱮ ᱜᱮᱞ ; 40 ᱯᱳᱱ ᱜᱮᱞ ; ...
Possessive:  "मेरे पास X <obj> हैं।" -> "ᱤᱧ ᱴᱷᱮᱱ <sat> ᱜᱚᱴᱟᱝ <satobj> ᱢᱮᱱᱟᱜᱼᱟ ᱠᱚᱣᱟ।"
"""
from __future__ import annotations
import argparse

HIN_UNIT = ["शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छः", "सात", "आठ", "नौ"]
HIN_TEEN = ["", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अट्ठारह", "उन्नीस"]
HIN_TEN = ["", "", "बीस", "तीस", "चालीस", "पचास", "साठ", "सत्तर", "अस्सी", "नब्बे"]
HIN_20S = ["", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस"]
HIN_30S = ["", "इकतीस", "बत्तीस", "तेतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस"]
HIN_40S = ["", "इकतालीस", "बयालीस", "तेंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "संतालीस", "अड़तालीस", "नवालीस"]
HIN_50S = ["", "इक्यावन", "बावन", "तिरेपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ"]
HIN_60S = ["", "इकसठ", "बासठ", "तिरेसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनसठ"]
HIN_70S = ["", "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छियत्तर", "सतहत्तर", "अठहत्तर", "उनासी"]
HIN_80S = ["", "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सतासी", "अट्ठासी", "नवासी"]
HIN_90S = ["", "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे"]

SAT_UNIT = ["", "ᱢᱤᱫ", "ᱵᱟᱨ", "ᱯᱮ", "ᱯᱳᱱ", "ᱢᱚᱬᱮ", "ᱛᱩᱨᱩᱭ", "ᱮᱭᱟᱭ", "ᱟᱨ", "ᱱᱟᱭ"]
SAT_TEN = ["", "", "ᱠᱩᱲᱤ", "ᱯᱮ ᱜᱮᱞ", "ᱯᱳᱱ ᱜᱮᱞ", "ᱢᱚᱬᱮ ᱜᱮᱞ",
           "ᱛᱩᱨᱩᱭ ᱜᱮᱞ", "ᱮᱭᱟᱭ ᱜᱮᱞ", "ᱟᱨ ᱜᱮᱞ", "ᱱᱟᱭ ᱜᱮᱞ"]
SAT_HUNDRED = "ᱥᱟᱭ"


def hin_word(n: int) -> str:
    if n == 0:
        return "शून्य"
    if n == 100:
        return "सौ"
    if n > 100:
        hd, rem = divmod(n, 100)
        return HIN_UNIT[hd] + " सौ" + ((" " + hin_word(rem)) if rem else "")
    t, u = divmod(n, 10)
    if t == 0:
        return HIN_UNIT[u]
    if t == 1:
        return HIN_TEEN[u] if u else "दस"
    if u == 0:
        return HIN_TEN[t]
    return {"2": HIN_20S, "3": HIN_30S, "4": HIN_40S, "5": HIN_50S, "6": HIN_60S,
            "7": HIN_70S, "8": HIN_80S, "9": HIN_90S}[str(t)][u]


def sat_word(n: int) -> str:
    if n == 0:
        return ""
    if n == 100:
        return SAT_HUNDRED
    if n > 100:
        hd, rem = divmod(n, 100)
        return SAT_UNIT[hd] + " " + SAT_HUNDRED + ((" " + sat_word(rem)) if rem else "")
    t, u = divmod(n, 10)
    if t == 0:
        return SAT_UNIT[u]
    if t == 1:
        return "ᱜᱮᱞ" + ("" if u == 0 else " " + SAT_UNIT[u])
    if u == 0:
        return SAT_TEN[t]
    return SAT_TEN[t] + " " + SAT_UNIT[u]


# object: hindi -> santali
OBJECTS = [
    ("किताब", "ᱠᱤᱛᱟᱯ"), ("पेंसिल", "ᱯᱮᱱᱥᱤᱞ"), ("सेब", "ᱟᱢᱚᱞ"), ("केला", "ᱠᱮᱞᱟ"),
    ("आम", "ᱟᱢ"), ("बच्चा", "ᱜᱤᱫᱽᱨᱟᱹ"), ("कुर्सी", "ᱠᱩᱨᱥᱤ"), ("रुपया", "ᱯᱟᱹᱲᱯᱟ"),
    ("पानी", "ᱫᱟᱜ"), ("पक्षी", "ᱪᱤᱲᱤᱡ"), ("मुर्गी", "ᱢᱩᱠᱨᱤ"), ("हाथी", "ᱦᱟᱛᱤ"),
    ("घोड़ा", "ᱜᱷᱚᱲᱟ"), ("चावल", "ᱪᱟᱣᱞ"), ("शब्द", "ᱥᱚᱵᱚᱫ"), ("फल", "ᱯᱟᱹᱨᱟᱹ"),
]

PAIRS: list[tuple[str, str]] = []

# bare numbers 1-100
for n in range(1, 101):
    PAIRS.append((hin_word(n), sat_word(n)))

# possessive for 1-100 (rotate objects)
for i, n in enumerate(range(1, 101)):
    obj_hi, obj_sat = OBJECTS[i % len(OBJECTS)]
    hi = f"मेरे पास {hin_word(n)} {obj_hi} हैं।"
    sat = f"ᱤᱧ ᱴᱷᱮᱱ {sat_word(n)} ᱜᱚᱴᱟᱝ {obj_sat} ᱢᱮᱱᱟᱜᱼᱟ ᱠᱚᱣᱟ।"
    PAIRS.append((hi, sat))

# counting lists 1-10, 11-20
for lo in (1, 11):
    h = ", ".join(hin_word(k) for k in range(lo, lo + 10))
    s = ", ".join(sat_word(k) for k in range(lo, lo + 10))
    PAIRS.append((h + "।", s + "।"))

# "गिनती: X।"
for n in list(range(1, 21)) + [30, 50, 100]:
    PAIRS.append((f"गिनती: {hin_word(n)}।", f"ᱞᱮᱠᱷᱟ: {sat_word(n)}।"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="ml/finetune/data/numbers_hi_sat.tsv")
    a = ap.parse_args()
    with open(a.out, "w", encoding="utf-8") as f:
        for h, s in PAIRS:
            f.write(f"{h}\t{s}\n")
    print(f"wrote {len(PAIRS)} numeral pairs -> {a.out}")


if __name__ == "__main__":
    main()
