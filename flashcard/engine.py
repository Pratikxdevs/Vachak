"""
flashcard/engine.py — Phase 4B
====================================================================
Flashcard deck generator. Deterministic, offline, NO LLM.

Input  (FlashcardSpec): concept, image_ref, hindi_label, target_label,
        audio_ref, (optional) english_label
Output (FlashcardDeck): JSON-serializable deck with per-card facing sides
        rendered for a chosen language mode ('hi'|'target'|'bilingual').

Also supports building a full deck from a curriculum lesson's
flashcard_concepts (build_from_lesson).
"""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from localization.layer import localize, normalize_language, language_label  # noqa: E402

try:
    from curriculum.data import load as load_curriculum
except ImportError:
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "curriculum"))
    from data import load as load_curriculum  # type: ignore


class FlashcardSpec:
    def __init__(self, concept, image_ref, hindi_label, target_label,
                 audio_ref=None, english_label=None, card_id=None, sequence=0):
        self.concept = concept
        self.image_ref = image_ref
        self.hindi_label = hindi_label
        self.target_label = target_label
        self.audio_ref = audio_ref
        self.english_label = english_label
        self.card_id = card_id
        self.sequence = sequence

    def to_dict(self):
        return {
            "concept": self.concept, "image_ref": self.image_ref,
            "hindi_label": self.hindi_label, "target_label": self.target_label,
            "audio_ref": self.audio_ref, "english_label": self.english_label,
            "card_id": self.card_id, "sequence": self.sequence,
        }


class FlashcardEngine:
    def __init__(self, db=None):
        self.db = db or load_curriculum()

    def build_card(self, spec: FlashcardSpec, language="bilingual"):
        language = normalize_language(language)
        front = localize({"hi": spec.hindi_label, "target": spec.target_label,
                          "en": spec.english_label or ""}, language)
        back = localize({"hi": spec.target_label, "target": spec.hindi_label,
                         "en": spec.english_label or ""}, language)
        return {
            "card_id": spec.card_id or f"FC-{spec.sequence}",
            "sequence": spec.sequence,
            "front": front,
            "back": back,
            "image_ref": spec.image_ref,
            "audio_ref": spec.audio_ref,
            "language": language,
            "raw": spec.to_dict(),
        }

    def build_deck(self, specs, language="bilingual", deck_id="DECK"):
        cards = [self.build_card(s, language) for s in specs]
        deck = {
            "deck_id": deck_id,
            "meta": {
                "language": language,
                "language_label": language_label(language),
                "card_count": len(cards),
                "generator": "flashcard/engine.py v1.0 (deterministic, offline)",
                "fixture_note": "DEV FIXTURE — generated deck, not SME-verified.",
            },
            "cards": cards,
        }
        return deck

    def build_from_lesson(self, lesson_id, language="bilingual"):
        cards_src = self.db.flashcards(lesson_id)
        specs = [
            FlashcardSpec(
                concept=fc["concept_en"], image_ref=fc.get("image_ref"),
                hindi_label=fc["concept_hi"], target_label=fc["concept_target"],
                audio_ref=fc.get("audio_ref"), english_label=fc["concept_en"],
                card_id=fc["card_id"], sequence=fc["sequence"])
            for fc in cards_src
        ]
        return self.build_deck(specs, language, deck_id=f"DECK-{lesson_id}")


if __name__ == "__main__":
    eng = FlashcardEngine()
    deck = eng.build_from_lesson("L-COUNT-G2", "bilingual")
    print(json.dumps(deck["meta"], ensure_ascii=False))
    for c in deck["cards"][:3]:
        print(c["card_id"], "|", c["front"], "||", c["back"])
    with open("/tmp/deck_sample.json", "w", encoding="utf-8") as f:
        json.dump(deck, f, ensure_ascii=False, indent=2)
    print("Wrote /tmp/deck_sample.json")
