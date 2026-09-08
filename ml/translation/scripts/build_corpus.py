#!/usr/bin/env python3
"""Build a curated Hindi<->Santali (Ol Chiki) parallel corpus for IndicTrans2
fine-tuning when no public parallel data is available.

Two tiers:
  gold   : transcribed from a published Hindi->Santali reference phrase table
           (real human/MT reference). Included in BOTH directions.
  silver : generated with conservative, reusable Santali grammar templates over
           a curated lexicon + the regular Santali number system (1-1000).
           Included hi->sat only (the direction the app needs).

Run verify_corpus.py afterwards to cross-check silver pairs against the base
IndicTrans2 model (round-trip + agreement) and split into verified / review.

Output (datasets/hin_sat/):
  corpus.tsv / corpus.jsonl / manifest.json  (see verify_corpus.py for the
  verified/review split)
"""
from __future__ import annotations
import json, random, datetime
from pathlib import Path

OUT_DIR = Path("datasets/hin_sat")
SEED = 42
TARGET = 5000

GOLD = [
    ("नमस्ते", "ᱡᱚᱦᱟᱨ", "greetings"),
    ("सुप्रभात", "ᱥᱟᱹᱜᱩᱱ ᱥᱮᱛᱟᱜ", "greetings"),
    ("शुभ दोपहर", "ᱥᱟᱹᱜᱩᱱ ᱛᱟᱨᱟᱥᱤᱧ", "greetings"),
    ("शुभ संध्या", "ᱥᱟᱹᱜᱩᱱ ᱥᱤᱸᱜᱟᱹᱲ", "greetings"),
    ("शुभ रात्रि", "ᱥᱟᱹᱜᱩᱱ ᱧᱤᱫᱟᱹ", "greetings"),
    ("अलविदा", "ᱥᱟᱹᱜᱩᱱ ᱡᱚᱦᱟᱨ", "greetings"),
    ("बाद में मिलते हैं", "ᱛᱟᱭᱚᱢ ᱛᱮ ᱞᱟᱝ ᱧᱟᱯᱟᱢᱚᱜᱼᱟ", "greetings"),
    ("कल मिलते हैं", "ᱜᱟᱯᱟ ᱞᱟᱝ ᱧᱟᱯᱟᱢᱚᱜᱼᱟ", "greetings"),
    ("स्वागत है", "ᱥᱟᱹᱜᱩᱱ ᱫᱟᱨᱟᱢ", "greetings"),
    ("आप कैसे हैं?", "ᱟᱢ ᱪᱮᱫ ᱞᱮᱠᱟ ᱢᱮᱱᱟᱢᱟ?", "greetings"),
    ("मैं ठीक हूँ, धन्यवाद", "ᱤᱧ ᱴᱷᱤᱠ ᱜᱮᱭᱟᱹᱧ, ᱟᱭᱢᱟ ᱥᱟᱨᱦᱟᱣ", "greetings"),
    ("आपका दिन शुभ हो", "ᱟᱢᱟᱜ ᱫᱤᱱ ᱱᱟᱯᱟᱭ ᱛᱟᱦᱮᱱ ᱢᱟ", "greetings"),
    ("कृपया", "ᱫᱟᱭᱟ ᱠᱟᱛᱮ", "courtesy"),
    ("धन्यवाद", "ᱟᱭᱢᱟ ᱥᱟᱨᱦᱟᱣ", "courtesy"),
    ("बहुत बहुत धन्यवाद", "ᱟᱭᱢᱟ ᱥᱟᱨᱦᱟᱣ", "courtesy"),
    ("आपका स्वागत है", "ᱟᱢᱟᱜ ᱥᱟᱹᱜᱩᱱ ᱫᱟᱨᱟᱢ", "courtesy"),
    ("हाँ", "ᱦᱮᱸ", "courtesy"),
    ("नहीं", "ᱵᱟᱝ", "courtesy"),
    ("क्षमा करें", "ᱤᱠᱟᱹ ᱠᱟᱹᱧ ᱢᱮ", "courtesy"),
    ("मुझे खेद है", "ᱤᱧ ᱤᱠᱟᱹᱧ ᱠᱷᱚᱡᱚᱜ ᱠᱟᱱᱟ", "courtesy"),
    ("कोई समस्या नहीं", "ᱪᱮᱫ ᱦᱚᱸ ᱮᱴᱠᱮᱴᱚᱬᱮ ᱵᱟᱹᱱᱩᱜᱼᱟ", "courtesy"),
    ("अवश्य", "ᱥᱟᱨᱤ ᱜᱮ", "courtesy"),
    ("बधाई हो", "ᱥᱟᱨᱦᱟᱣ ᱦᱩᱭᱩᱜ ᱢᱟ", "courtesy"),
    ("शुभकामनाएँ", "ᱟᱭᱢᱟ ᱥᱟᱨᱦᱟᱣ", "courtesy"),
    ("आपका नाम क्या है?", "ᱟᱢᱟᱜ ᱧᱩᱛᱩᱢ ᱪᱮᱫ?", "introductions"),
    ("आपसे मिलकर अच्छा लगा", "ᱟᱢ ᱥᱟᱶ ᱧᱟᱯᱟᱢ ᱠᱟᱛᱮ ᱠᱩᱥᱤ ᱮᱱᱟᱹᱧ", "introductions"),
    ("आप कहाँ से हैं?", "ᱟᱢ ᱚᱠᱟ ᱨᱮᱱ ᱠᱟᱱᱟᱢ?", "introductions"),
    ("आपकी आयु कितनी है?", "ᱟᱢᱟᱜ ᱛᱤᱱᱮᱜ ᱵᱚᱭᱮᱥ ᱦᱩᱭᱩᱜ ᱠᱟᱱ ᱛᱟᱢᱟ?", "introductions"),
    ("मैं एक विद्यार्थी हूँ", "ᱤᱧ ᱫᱚ ᱢᱤᱫ ᱯᱟᱹᱴᱷᱩᱣᱟᱹ ᱠᱟᱱᱟᱹᱧ", "introductions"),
    ("मैं यहां छुट्टी पर हूँ", "ᱤᱧ ᱱᱚᱸᱰᱮ ᱪᱷᱩᱴᱤ ᱨᱮ ᱢᱤᱱᱟᱹᱧᱟ", "introductions"),
    ("मैं यहां काम के लिए हूँ", "ᱤᱧ ᱱᱚᱸᱰᱮ ᱠᱟᱹᱢᱤ ᱞᱟᱹᱜᱤᱫ ᱢᱤᱱᱟᱹᱧᱟ", "introductions"),
    ("यह मेरा दोस्त है", "ᱱᱩᱭ ᱫᱚ ᱤᱧ ᱨᱮᱱ ᱜᱟᱛᱮ ᱠᱟᱱᱟᱭ", "introductions"),
    ("एक", "ᱢᱤᱫ", "numbers"),
    ("दो", "ᱵᱟᱨ", "numbers"),
    ("तीन", "ᱯᱮ", "numbers"),
    ("चार", "ᱯᱩᱱ", "numbers"),
    ("पांच", "ᱢᱚᱬᱮ", "numbers"),
    ("छह", "ᱛᱩᱨᱩᱭ", "numbers"),
    ("सात", "ᱮᱭᱟᱭ", "numbers"),
    ("आठ", "ᱤᱨᱟᱹᱞ", "numbers"),
    ("नौ", "ᱟᱨᱮ", "numbers"),
    ("दस", "ᱜᱮᱞ", "numbers"),
    ("एक सौ", "ᱢᱤᱫ ᱥᱟᱭ", "numbers"),
    ("एक हजार", "ᱢᱤᱫ ᱦᱟᱡᱟᱨ", "numbers"),
    ("आज", "ᱛᱮᱦᱮᱧ", "time"),
    ("कल", "ᱜᱟᱯᱟ", "time"),
    ("कल", "ᱦᱚᱞᱟ", "time"),
    ("अभी", "ᱱᱤᱛᱚᱜ", "time"),
    ("बाद में", "ᱛᱟᱭᱚᱢ ᱛᱮ", "time"),
    ("क्या समय हो गया है?", "ᱱᱤᱛ ᱛᱤᱱᱟᱹᱜ ᱚᱠᱛᱚ ᱦᱩᱭ ᱟᱠᱟᱱᱟ?", "time"),
    ("सुबह में", "ᱥᱮᱛᱟᱜ ᱨᱮ", "time"),
    ("शाम को", "ᱥᱤᱸᱜᱟᱹᱲ ᱨᱮ", "time"),
    ("सोमवार", "ᱚᱛᱮ ᱢᱟᱦᱟᱸ", "time"),
    ("शुक्रवार", "ᱡᱟᱹᱨᱩᱢ ᱢᱟᱦᱟᱸ", "time"),
    ("अगले सप्ताह", "ᱫᱚᱥᱟᱨ ᱦᱟᱯᱛᱟ", "time"),
    ("एक घंटा", "ᱢᱤᱫ ᱴᱟᱲᱟᱝ", "time"),
    ("बाथरूम कहाँ है?", "ᱵᱟᱛᱷᱨᱩᱢ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?", "travel"),
    ("रेलवे स्टेशन कहां है?", "ᱴᱨᱮᱱ ᱥᱴᱮᱥᱚᱱ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?", "travel"),
    ("हवाई अड्डा कहाँ है?", "ᱮᱭᱟᱨᱯᱚᱨᱴ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?", "travel"),
    ("मैं वहां किस प्रकार पहुंचा?", "ᱤᱧ ᱚᱱᱰᱮ ᱪᱤᱠᱟᱹᱛᱮᱧ ᱥᱮᱴᱮᱨᱚᱜᱼᱟ?", "travel"),
    ("बाएँ मुड़ें", "ᱞᱮᱸᱜᱟ ᱥᱮᱫ ᱟᱹᱪᱩᱨᱚᱜ ᱢᱮ", "travel"),
    ("दाएं मुड़ें", "ᱡᱚᱡᱚᱢ ᱥᱮᱫ ᱟᱹᱪᱩᱨᱚᱜ ᱢᱮ", "travel"),
    ("सीधे आगे बढ़ें", "ᱥᱤᱫᱷᱟᱹ ᱞᱟᱦᱟ ᱥᱮᱱᱚᱜ ᱢᱮ", "travel"),
    ("यह निकट है", "ᱱᱚᱣᱟ ᱫᱚ ᱥᱩᱨ ᱨᱮ ᱜᱮᱭᱟ", "travel"),
    ("यह दूर है", "ᱱᱚᱶᱟ ᱫᱚ ᱥᱟᱝᱜᱤᱧ ᱜᱮᱭᱟ", "travel"),
    ("मैं खो गया हूँ", "ᱤᱧ ᱟᱫ ᱟᱠᱟᱱᱟᱹᱧ", "travel"),
    ("मुझे टैक्सी चाहिए", "ᱤᱧ ᱢᱤᱫ ᱴᱮᱠᱥᱤ ᱞᱟᱹᱠᱛᱤᱭᱟᱹᱧ ᱠᱟᱱᱟ", "travel"),
    ("मैं टिकट कहाँ से खरीद सकता हूँ?", "ᱤᱧ ᱚᱠᱟ ᱠᱷᱚᱱ ᱴᱤᱠᱤᱴ ᱠᱤᱨᱤᱧ ᱫᱟᱲᱮᱭᱟᱜᱼᱟᱹᱧ?", "travel"),
    ("टिकट कितनी है?", "ᱴᱤᱠᱤᱴ ᱫᱚ ᱛᱤᱱᱟᱹᱜ ᱜᱟᱱ?", "travel"),
    ("क्या पैदल चलना सुरक्षित है?", "ᱪᱮᱫ ᱱᱚᱣᱟ ᱛᱟᱲᱟᱢ ᱞᱟᱹᱜᱤᱫ ᱨᱚᱯᱟ ᱜᱮᱭᱟ?", "travel"),
    ("मुझे भूख लगी है", "ᱤᱧ ᱨᱮᱸᱜᱮᱡ ᱢᱤᱱᱟᱹᱧᱟ", "food"),
    ("मुझे प्यास लगी है", "ᱤᱧ ᱛᱮᱛᱟᱝ ᱟᱠᱟᱱᱟᱹᱧ", "food"),
    ("पानी, कृपया", "ᱫᱟᱜ, ᱫᱟᱭᱟ ᱠᱟᱛᱮ", "food"),
    ("कृपया दो लोगों के लिए एक टेबल", "ᱵᱟᱨ ᱦᱚᱲ ᱞᱟᱹᱜᱤᱫ ᱢᱤᱫ ᱴᱮᱵᱩᱞ, ᱫᱟᱭᱟ ᱠᱟᱛᱮ", "food"),
    ("कृपया मेनू", "ᱢᱮᱱᱭᱩ, ᱫᱟᱭᱟ ᱠᱟᱛᱮ", "food"),
    ("आप क्या सिफ़ारिश करते हैं?", "ᱟᱢ ᱪᱮᱫ ᱥᱚᱞᱦᱟᱢ ᱮᱢᱚᱜ ᱠᱟᱱᱟ?", "food"),
    ("मैं यह चाहूंगा", "ᱤᱧ ᱱᱚᱣᱟ ᱠᱩᱥᱤᱭᱟᱜ ᱠᱟᱱᱟᱹᱧ", "food"),
    ("यह स्वादिष्ट है", "ᱱᱚᱣᱟ ᱫᱚ ᱥᱤᱵᱤᱞ ᱜᱮᱭᱟ", "food"),
    ("बिल, कृपया", "ᱵᱤᱞ, ᱫᱟᱭᱟ ᱠᱟᱛᱮ", "food"),
    ("मैं मांस नहीं खाता", "ᱤᱧ ᱡᱤᱞ ᱵᱟᱹᱧ ᱡᱚᱢ ᱮᱫᱟ", "food"),
    ("मुझे खाने से एलर्जी है", "ᱤᱧ ᱫᱚ ᱡᱚᱢᱟᱜ ᱠᱷᱚᱱ ᱮᱞᱟᱨᱡᱤ ᱢᱮᱱᱟᱜ ᱛᱤᱧᱟ", "food"),
    ("कॉफ़ी", "ᱠᱚᱯᱷᱤ", "food"),
    ("चाय", "ᱪᱟ", "food"),
    ("रोटी", "ᱯᱤᱴᱷᱟᱹ", "food"),
    ("इसकी लागत कितनी है?", "ᱱᱚᱣᱟ ᱨᱮᱭᱟᱜ ᱜᱚᱱᱚᱝ ᱛᱤᱱᱟᱹᱜ ?", "shopping"),
    ("यह बहुत महंगा है", "ᱱᱚᱣᱟ ᱫᱚ ᱟᱹᱰᱤ ᱫᱟᱢ ᱜᱮᱭᱟ", "shopping"),
    ("क्या आप क्रेडिट कार्ड स्वीकार करते हैं?", "ᱪᱮᱫ ᱟᱢ ᱠᱨᱮᱰᱤᱴ ᱠᱟᱨᱰ ᱮᱢ ᱟᱝᱜᱚᱪ ᱮᱫᱟ?", "shopping"),
    ("मैं बस देख रहा हूँ", "ᱤᱧ ᱫᱚ ᱱᱤᱛ ᱜᱮ ᱠᱚᱭᱚᱜ ᱠᱟᱱᱟᱹᱧ", "shopping"),
    ("मैं इसे ले लूँगा", "ᱤᱧ ᱱᱚᱣᱟᱧ ᱦᱟᱛᱟᱣᱟ", "shopping"),
    ("खुला", "ᱡᱷᱤᱡᱽ ᱢᱮ", "shopping"),
    ("बंद", "ᱵᱚᱱᱫᱚ", "shopping"),
    ("प्रवेश", "ᱵᱚᱞᱚᱱ ᱰᱟᱦᱟᱨ", "shopping"),
    ("बाहर निकलें", "ᱚᱰᱚᱠᱚᱜ ᱢᱮ", "shopping"),
    ("बाज़ार कहाँ है?", "ᱵᱟᱡᱟᱨ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?", "shopping"),
    ("मदद", "ᱜᱚᱲᱚ", "health"),
    ("डॉक्टर को बुलाओ", "ᱢᱤᱫ ᱰᱟᱠᱴᱚᱨ ᱦᱚᱦᱚᱭᱮᱢ", "health"),
    ("पुलिस को बुलाओ", "ᱯᱩᱞᱤᱥ ᱦᱚᱦᱚ ᱠᱚᱢ", "health"),
    ("मुझे मदद चाहिए", "ᱤᱧ ᱜᱚᱲᱚ ᱞᱟᱹᱠᱛᱤᱭᱟᱹᱧ ᱠᱟᱱᱟ", "health"),
    ("मैं बीमार हूँ", "ᱤᱧ ᱨᱩᱣᱟᱹ ᱜᱮᱭᱟᱹᱧ", "health"),
    ("अस्पताल कहाँ है?", "ᱦᱚᱥᱯᱤᱴᱟᱞ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?", "health"),
    ("औषधालय कहां है?", "ᱯᱷᱟᱨᱢᱟᱥᱭ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?", "health"),
    ("यहाँ दर्द होता है", "ᱱᱚᱸᱰᱮ ᱦᱟᱹᱥᱩ ᱢᱮᱱᱟᱜᱼᱟ", "health"),
    ("मुझे एक डॉक्टर की जरूरत है", "ᱤᱧ ᱢᱤᱫ ᱰᱟᱠᱴᱚᱨ ᱞᱟᱹᱠᱛᱤᱭᱟᱹᱧ ᱠᱟᱱᱟ", "health"),
    ("क्या आस-पास कोई अस्पताल है?", "ᱪᱮᱫ ᱥᱩᱨ ᱨᱮ ᱢᱤᱫ ᱦᱚᱥᱯᱤᱴᱟᱞ ᱢᱮᱱᱟᱜᱼᱟ?", "health"),
    ("क्या आप अंग्रेज़ी बोलते हैं?", "ᱪᱮᱫ ᱟᱢ ᱤᱝᱨᱟᱡᱤ ᱨᱚᱲ ᱫᱟᱲᱮᱭᱟᱜ ᱠᱟᱱᱟᱢ?", "language"),
    ("मुझे समझ नहीं आता", "ᱤᱧ ᱵᱟᱹᱧ ᱵᱩᱡᱷᱟᱹᱣ ᱫᱟᱲᱮᱭᱟᱜ ᱠᱟᱱᱟ", "language"),
    ("मैं समझता हूँ", "ᱤᱧ ᱵᱩᱡᱷᱟᱹᱣ ᱮᱫᱟᱹᱧ", "language"),
    ("कृपया धीरे बोलें", "ᱫᱟᱭᱟ ᱠᱟᱛᱮ ᱵᱟᱹᱭ ᱵᱟᱹᱭ ᱛᱮ ᱨᱚᱲ ᱢᱮ", "language"),
    ("कृपया उसे दोहराएँ", "ᱫᱟᱭᱟ ᱠᱟᱛᱮ ᱚᱱᱟ ᱫᱚᱦᱲᱟᱭ ᱢᱮ", "language"),
    ("कृपया इसे लिख लें", "ᱫᱟᱭᱟ ᱠᱟᱛᱮ ᱱᱚᱣᱟ ᱚᱞ ᱢᱮ", "language"),
    ("इसका क्या मतलब है?", "ᱱᱚᱣᱟ ᱨᱮᱭᱟᱜ ᱢᱮᱱᱮᱛ ᱪᱮᱫ ᱠᱟᱱᱟ?", "language"),
    ("आप यह कैसे कहते हैं?", "ᱟᱢ ᱱᱚᱣᱟ ᱪᱤᱠᱟᱹᱛᱮᱢ ᱢᱮᱱ ᱮᱫᱟ?", "language"),
    ("मैं सीख रहा हूँ", "ᱤᱧ ᱪᱮᱫᱚᱜ ᱠᱟᱱᱟᱹᱧ", "language"),
    ("मैं थोड़ा बोलता हूँ", "ᱤᱧ ᱱᱟᱥᱮ ᱨᱚᱲ ᱮᱫᱟᱹᱧ", "language"),
    ("क्या आप मेरी मदद कर सकते हैं?", "ᱪᱮᱫ ᱟᱢ ᱤᱧᱮᱢ ᱜᱚᱲᱚ ᱟᱹᱧᱟ?", "language"),
    ("मुझे नहीं पता", "ᱮᱧ ᱫᱚ ᱵᱟᱝ ᱤᱧ ᱵᱟᱰᱟᱭᱟ", "language"),
]

NOUNS = [
    ("सेब", "ᱥᱮᱯ", "fruit"), ("केला", "ᱠᱮᱞᱟ", "fruit"), ("आम", "ᱟᱢ", "fruit"),
    ("अमरूद", "ᱟᱢᱨᱩᱫ", "fruit"), ("नारियल", "ᱱᱟᱨᱤᱭᱟᱞ", "fruit"),
    ("पानी", "ᱫᱟᱜ", "food"), ("दूध", "ᱫᱩᱫᱷ", "food"), ("चावल", "ᱪᱟᱣᱞ", "food"),
    ("चीनी", "ᱪᱤᱱᱤ", "food"), ("नमक", "ᱱᱩᱱ", "food"), ("तेल", "ᱛᱮᱞ", "food"),
    ("सब्ज़ी", "ᱥᱚᱵᱽᱡᱤ", "food"), ("अंडा", "ᱟᱱᱫᱟ", "food"), ("दाल", "ᱫᱟᱞ", "food"),
    ("चाय", "ᱪᱟ", "food"), ("कॉफ़ी", "ᱠᱚᱯᱷᱤ", "food"),
    ("किताब", "ᱯᱚᱛᱚᱢ", "school"), ("पेन", "ᱯᱮᱱ", "school"), ("पेंसिल", "ᱯᱮᱱᱥᱤᱞ", "school"),
    ("काग़ज़", "ᱠᱟᱜᱚᱡᱽ", "school"), ("ब्लैकबोर्ड", "ᱵᱞᱟᱠᱵᱚᱨᱰ", "school"),
    ("कुर्सी", "ᱠᱩᱨᱥᱤ", "school"), ("मेज़", "ᱴᱮᱵᱩᱞ", "school"), ("स्कूल", "ᱥᱠᱩᱞ", "school"),
    ("शिक्षक", "ᱥᱟᱦᱟᱸ", "school"), ("छात्र", "ᱯᱟᱹᱴᱷᱩᱣᱟᱹ", "school"),
    ("अक्षर", "ᱚᱠᱷᱚᱨ", "school"), ("शब्द", "ᱥᱚᱵᱚᱫ", "school"),
    ("लड़का", "ᱠᱚᱲᱟ ᱜᱤᱫᱽᱨᱟᱹ", "person"), ("लड़की", "ᱠᱩᱲᱤ ᱜᱤᱫᱽᱨᱟᱹ", "person"),
    ("आदमी", "ᱦᱚᱲ", "person"), ("दोस्त", "ᱜᱟᱛᱮ", "person"),
    ("माँ", "ᱟᱫᱟᱢ", "family"), ("पिता", "ᱟᱯᱟᱛ", "family"),
    ("भाई", "ᱵᱟᱭ", "family"), ("बहन", "ᱵᱚᱦᱮᱱ", "family"),
    ("गाय", "ᱜᱟᱭ", "animal"), ("कुत्ता", "ᱦᱚᱨᱚᱜ", "animal"), ("बिल्ली", "ᱵᱤᱲᱤ", "animal"),
    ("हाथी", "ᱦᱟᱛᱤ", "animal"), ("घोड़ा", "ᱜᱷᱚᱲᱟ", "animal"), ("बंदर", "ᱵᱟᱸᱫᱚᱨ", "animal"),
    ("बकरी", "ᱵᱚᱠᱨᱟ", "animal"), ("भेड़", "ᱵᱷᱮᱲ", "animal"), ("मुर्गी", "ᱢᱩᱠᱨᱤ", "animal"),
    ("मछली", "ᱢᱟᱪᱷᱤ", "animal"), ("साँप", "ᱥᱟᱸᱯ", "animal"), ("चिड़िया", "ᱪᱤᱲᱤᱡ", "animal"),
    ("सूरज", "ᱥᱩᱯ", "nature"), ("चाँद", "ᱪᱟᱸᱫᱚ", "nature"), ("पेड़", "ᱫᱷᱟᱨ", "nature"),
    ("फूल", "ᱯᱩᱥ", "nature"), ("आकाश", "ᱟᱠᱟᱥ", "nature"), ("नदी", "ᱱᱟᱹᱭ", "nature"),
    ("पहाड़", "ᱯᱟᱦᱟᱲ", "nature"), ("जंगल", "ᱡᱚᱸᱜᱮᱞ", "nature"), ("बारिश", "ᱵᱟᱨᱤᱥ", "nature"),
    ("हवा", "ᱦᱟᱣᱟ", "nature"), ("आग", "ᱟᱜᱽ", "nature"), ("मिट्टी", "ᱢᱟᱴᱤ", "nature"),
    ("पत्थर", "ᱯᱟᱛᱷᱚᱨ", "nature"),
    ("घर", "ᱚᱲᱚᱜ", "object"), ("दरवाज़ा", "ᱫᱟᱹᱨᱣᱟᱡᱽ", "object"), ("खिड़की", "ᱠᱤᱲᱠᱤ", "object"),
    ("चाबी", "ᱪᱟᱹᱵᱤ", "object"), ("कपड़ा", "ᱠᱟᱯᱲᱟ", "object"), ("जूता", "ᱡᱩᱛᱟ", "object"),
    ("टोपी", "ᱴᱚᱯᱤ", "object"), ("बस", "ᱵᱟᱥ", "object"), ("गाड़ी", "ᱜᱟᱹᱰᱤ", "object"),
    ("सड़क", "ᱥᱚᱲᱚᱠ", "object"), ("पुल", "ᱯᱩᱞ", "object"),
    ("गाँव", "ᱟᱹᱛᱩ", "place"), ("शहर", "ᱥᱚᱦᱚᱨ", "place"), ("बाज़ार", "ᱵᱟᱡᱟᱨ", "place"),
    ("हाथ", "ᱛᱤ", "body"), ("पैर", "ᱠᱟᱸᱪ", "body"), ("सिर", "ᱯᱟᱹᱛᱩ", "body"),
    ("आँख", "ᱧᱤᱞ", "body"), ("कान", "ᱪᱩᱯ", "body"), ("मुँह", "ᱢᱩᱦᱟᱹ", "body"), ("नाक", "ᱱᱟᱠ", "body"),
    ("नाम", "ᱧᱩᱛᱩᱢ", "abstract"), ("भाषा", "ᱯᱟᱹᱨᱥᱤ", "abstract"),
    ("समय", "ᱚᱠᱛᱚ", "abstract"), ("दिन", "ᱫᱤᱱ", "abstract"), ("रात", "ᱧᱤᱫᱟᱹ", "abstract"),
    ("सप्ताह", "ᱦᱟᱯᱛᱟ", "abstract"), ("साल", "ᱥᱟᱞ", "abstract"),
    ("रंग", "ᱨᱚᱝ", "abstract"), ("खेल", "ᱠᱷᱮᱞ", "abstract"),
    ("दवा", "ᱫᱟᱹᱣᱟ", "health"), ("डॉक्टर", "ᱡᱟᱹᱠᱴᱚᱨ", "health"), ("नर्स", "ᱱᱟᱨᱥ", "health"),
]

COLORS = [("लाल", "ᱞᱟᱞ"), ("नीला", "ᱱᱤᱞ"), ("हरा", "ᱦᱟᱨᱟ"),
          ("पीला", "ᱯᱤᱞ"), ("काला", "ᱠᱟᱞᱟ"), ("सफ़ेद", "ᱫᱟᱹᱲ")]

ADJ = [("बड़ा", "ᱟᱹᱰᱤ ᱢᱟᱨᱟᱝ"), ("छोटा", "ᱦᱩᱰᱤᱧ"),
       ("अच्छा", "ᱱᱟᱯᱟᱭ"), ("नया", "ᱱᱟᱣᱟ"), ("गर्म", "ᱨᱚᱜᱽ")]

VERBS = [("पढ़ना", "ᱯᱟᱲᱦᱟᱣ"), ("खेलना", "ᱠᱷᱮᱞ"), ("गाना", "ᱥᱮᱨᱮᱧ"),
         ("लिखना", "ᱚᱞ"), ("देखना", "ᱧᱮᱞ"), ("सुनना", "ᱟᱸᱡᱚᱢ"),
         ("खाना", "ᱡᱚᱢ"), ("पीना", "ᱧᱩ"), ("जाना", "ᱥᱮᱱ"), ("आना", "ᱦᱟᱹᱡᱤᱡ")]

ONES_H = ["शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छः", "सात", "आठ", "नौ"]
TENS_H = {10: "दस", 20: "बीस", 30: "तीस", 40: "चालीस", 50: "पचास",
          60: "साठ", 70: "सत्तर", 80: "अस्सी", 90: "नब्बे"}
TEEN_H = ["ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस"]
ONES_S = ["ᱥᱩᱱᱩᱢ", "ᱢᱤᱫ", "ᱵᱟᱨ", "ᱯᱮ", "ᱯᱩᱱ", "ᱢᱚᱬᱮ", "ᱛᱩᱨᱩᱭ", "ᱮᱭᱟᱭ", "ᱤᱨᱟᱹᱞ", "ᱟᱨᱮ"]


def hindi_number(n: int) -> str:
    if n == 0:
        return ONES_H[0]
    if n < 10:
        return ONES_H[n]
    if n == 10:
        return "दस"
    if 11 <= n <= 19:
        return TEEN_H[n - 11]
    if n < 100:
        t = (n // 10) * 10
        o = n % 10
        return TENS_H[t] if o == 0 else TENS_H[t] + " " + ONES_H[o]
    if n == 100:
        return "सौ"
    if n < 1000:
        h = n // 100
        r = n % 100
        base = "सौ" if h == 1 else ONES_H[h] + " सौ"
        return base if r == 0 else base + " " + hindi_number(r)
    if n == 1000:
        return "हज़ार"
    return str(n)


def santali_number(n: int) -> str:
    if n == 0:
        return ONES_S[0]
    if n < 10:
        return ONES_S[n]
    if n == 10:
        return "ᱜᱮᱞ"
    if 11 <= n <= 19:
        return "ᱜᱮᱞ " + ONES_S[n - 10]
    if 20 <= n < 30:
        o = n % 10
        return "ᱠᱩᱲᱤ" if o == 0 else "ᱠᱩᱲᱤ " + ONES_S[o]
    if n < 100:
        t = n // 10
        o = n % 10
        base = ONES_S[t] + " ᱜᱮᱞ"
        return base if o == 0 else base + " " + ONES_S[o]
    if n == 100:
        return "ᱢᱤᱫ ᱥᱟᱭ"
    if n < 1000:
        h = n // 100
        r = n % 100
        base = "ᱢᱤᱫ ᱥᱟᱭ" if h == 1 else ONES_S[h] + " ᱥᱟᱭ"
        return base if r == 0 else base + " " + santali_number(r)
    if n == 1000:
        return "ᱢᱤᱫ ᱦᱟᱡᱟᱨ"
    return str(n)


def build_silver():
    pairs = []

    for n in list(range(1, 101)) + [200, 300, 500, 1000]:
        pairs.append((hindi_number(n), santali_number(n), "numbers"))

    for h, s, cat in NOUNS:
        pairs.append((f"यह एक {h} है।", f"ᱱᱚᱣᱟ ᱫᱚ ᱢᱤᱫ {s} ᱠᱟᱱᱟ।", cat))
        pairs.append((f"वह एक {h} है।", f"ᱱᱚᱶᱟ ᱫᱚ ᱢᱤᱫ {s} ᱠᱟᱱᱟ।", cat))
        pairs.append((f"यह मेरा {h} है।", f"ᱱᱚᱣᱟ ᱫᱚ ᱤᱧ ᱨᱮᱱ {s} ᱠᱟᱱᱟ।", cat))
        pairs.append((f"मेरा {h}।", f"ᱤᱧ ᱨᱮᱱ {s}।", cat))
        pairs.append((f"मेरे पास {h} है।", f"ᱤᱧ ᱨᱮᱱ {s} ᱢᱮᱱᱟᱜᱼᱟ।", cat))
        pairs.append((f"{h} कहाँ है?", f"{s} ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?", cat))
        pairs.append((f"यह {h} अच्छा है।", f"ᱱᱚᱣᱟ ᱫᱚ {s} ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ।", cat))
        pairs.append((f"यह {h} नहीं है।", f"ᱱᱚᱣᱟ ᱫᱚ {s} ᱵᱟᱝ ᱠᱟᱱᱟ।", cat))
        pairs.append((f"क्या यह {h} अच्छा है?", f"ᱪᱮᱫ ᱱᱚᱣᱟ {s} ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?", cat))
        for ah, asat in ADJ:
            pairs.append((f"यह {ah} {h} है।", f"ᱱᱚᱣᱟ ᱫᱚ {asat} {s} ᱠᱟᱱᱟ।", cat))

    for ch, cs in COLORS:
        for h, s, cat in NOUNS:
            pairs.append((f"यह {ch} {h} है।", f"ᱱᱚᱣᱟ ᱫᱚ {cs} {s} ᱠᱟᱱᱟ।", cat))

    for hv, sv in VERBS:
        pairs.append((f"मैं {hv} पसंद करता हूँ।", f"ᱤᱧ {sv} ᱠᱩᱥᱤᱭᱟᱜ ᱠᱟᱱᱟᱹᱧ।", "verbs"))
        pairs.append((f"बच्चे {hv} हैं।", f"ᱜᱤᱫᱽᱨᱟᱹ ᱠᱚ {sv} ᱠᱟᱱᱟ ᱠᱚ।", "verbs"))

    q_nouns = NOUNS[:50]
    q_nums = list(range(1, 51)) + list(range(60, 101, 10))
    for n in q_nums:
        sn = santali_number(n)
        hn = hindi_number(n)
        for h, s, cat in q_nouns:
            pairs.append((f"मेज़ पर {hn} {h} हैं।", f"ᱴᱮᱵᱩᱞ ᱨᱮ {sn} ᱜᱚᱴᱟᱝ {s} ᱢᱮᱱᱟᱜ ᱠᱚᱣᱟ।", cat))
            pairs.append((f"विद्यालय में {hn} {h} हैं।", f"ᱵᱤᱨᱫᱟᱹᱜᱲ ᱨᱮ {sn} ᱜᱚᱴᱟᱝ {s} ᱢᱮᱱᱟᱜ ᱠᱚᱣᱟ।", cat))
            pairs.append((f"मेरे पास {hn} {h} हैं।", f"ᱤᱧ ᱴᱷᱮᱱ {sn} ᱜᱚᱴᱟᱝ {s} ᱢᱮᱱᱟᱜᱼᱟ ᱠᱚᱣᱟ।", cat))

    return pairs


def main():
    random.seed(SEED)
    out = []

    for hi, sat, cat in GOLD:
        out.append({"hi": hi, "sat": sat, "tier": "gold", "category": cat})
        out.append({"hi": sat, "sat": hi, "tier": "gold_rev", "category": cat})

    for hi, sat, cat in build_silver():
        out.append({"hi": hi, "sat": sat, "tier": "silver", "category": cat})

    seen = set()
    uniq = []
    for r in out:
        k = (r["hi"].strip(), r["sat"].strip())
        if k in seen:
            continue
        seen.add(k)
        uniq.append(r)

    if len(uniq) < TARGET:
        for n in range(1, 101):
            sn = santali_number(n)
            hn = hindi_number(n)
            for h, s, cat in NOUNS:
                if len(uniq) >= TARGET:
                    break
                r = {"hi": f"विद्यालय में {hn} {h} हैं।",
                     "sat": f"ᱵᱤᱨᱫᱟᱹᱜᱲ ᱨᱮ {sn} ᱜᱚᱴᱟᱝ {s} ᱢᱮᱱᱟᱜ ᱠᱚᱣᱟ।",
                     "tier": "silver", "category": cat}
                k = (r["hi"], r["sat"])
                if k in seen:
                    continue
                seen.add(k)
                uniq.append(r)
            if len(uniq) >= TARGET:
                break

    random.shuffle(uniq)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with open(OUT_DIR / "corpus.tsv", "w", encoding="utf-8") as f:
        for r in uniq:
            f.write(f"{r['hi']}\t{r['sat']}\n")
    with open(OUT_DIR / "corpus.jsonl", "w", encoding="utf-8") as f:
        for r in uniq:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    from collections import Counter
    tiers = Counter(r["tier"] for r in uniq)
    cats = Counter(r["category"] for r in uniq)
    manifest = {
        "generated_at": datetime.date.today().isoformat(),
        "total_pairs": len(uniq),
        "tiers": dict(tiers),
        "categories": dict(cats),
        "source": {
            "gold": "Transcribed from a published Hindi->Santali (Ol Chiki) reference phrase table.",
            "silver": "Conservative Santali grammar templates over a curated lexicon + the regular Santali number system (1-1000).",
        },
        "license_note": "Gold = machine/MT-derived; verify before approved pedagogy. Silver = machine-generated; MUST be validated by a native Santali speaker. Stopgap until COILD-MT-Corpus HIN-SAT / Education_v2 are un-gated.",
        "verification": "Run verify_corpus.py to cross-check silver pairs against the base IndicTrans2 model and split into verified / review sets.",
        "format": "corpus.tsv = headerless hi<tab>sat; compatible with prep_finetune_data.py",
    }
    with open(OUT_DIR / "manifest.json", "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    print(f"[build] total pairs : {len(uniq)}")
    print(f"[build] tiers       : {dict(tiers)}")
    print(f"[build] written to  : {OUT_DIR}/corpus.tsv, corpus.jsonl, manifest.json")


if __name__ == "__main__":
    main()
