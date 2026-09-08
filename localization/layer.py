"""
Vachak localization layer (Phase 4C)
=====================================================================
Single source of truth for multilingual rendering of generated artifacts.
Offline-first: language config is a static JSON file, no network, no LLM.

Supported modes
---------------
  "hi"        -> Hindi only (Devanagari)
  "target"    -> Target language only (default Santali Ol Chiki; configurable)
  "bilingual" -> Hindi + target side by side

A *Triple* is any dict/mapping with keys: hi, target, en
(e.g. {"hi": "सेब", "target": "ᱥᱟᱯ", "en": "apple"}).

The target language is determined by localization/languages.json so the same
code works for Mundari (task brief) or Santali (rest of the Vachak repo) by
changing one config value — no code edits required.
"""

import json
import os

_CONFIG_PATH = os.path.join(os.path.dirname(__file__), "languages.json")

# Frozen fallback so the module still works if the JSON is missing.
_DEFAULT_CONFIG = {
    "target_language_code": "sat",
    "target_language_name": "Santali (Ol Chiki)",
    "target_script_note": "Ol Chiki script. Swap target_language_code to 'unr' (Mundari) and re-seed to target Mundari.",
    "languages": {
        "hi": {"label": "Hindi", "script": "Devanagari"},
        "target": {"label": "Santali (Ol Chiki)", "script": "Ol Chiki"},
        "bilingual": {"label": "Hindi + Target", "script": "Dual"}
    }
}


def load_config(path=None):
    cfg_path = path or _CONFIG_PATH
    try:
        with open(cfg_path, "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, OSError):
        return _DEFAULT_CONFIG


CONFIG = load_config()
TARGET_CODE = CONFIG.get("target_language_code", "sat")
LANGUAGES = CONFIG.get("languages", _DEFAULT_CONFIG["languages"])


def is_valid_language(lang):
    return lang in LANGUAGES


def normalize_language(lang):
    """Coerce arbitrary input to a known mode; default to bilingual."""
    if lang in LANGUAGES:
        return lang
    # common aliases
    alias = {
        "hindi": "hi", "hi": "hi", "devanagari": "hi",
        "sat": "target", "santali": "target", "target": "target",
        "unr": "target", "mundari": "target",
        "both": "bilingual", "bi": "bilingual", "bilingual": "bilingual",
    }
    return alias.get(str(lang).lower(), "bilingual")


def localize(triple, lang):
    """
    Render a Triple for the requested language mode.

    :param triple: dict with 'hi', 'target', 'en' keys (en optional)
    :param lang:   'hi' | 'target' | 'bilingual' (auto-normalized)
    :returns:      string
    """
    if not isinstance(triple, dict):
        return str(triple)
    lang = normalize_language(lang)
    hi = triple.get("hi", "")
    target = triple.get("target", "")
    if lang == "hi":
        return hi
    if lang == "target":
        return target
    # bilingual
    if hi and target:
        return f"{hi}  —  {target}"
    return hi or target


def localize_field(triple, lang, sep=" — "):
    """Like localize but with custom bilingual separator."""
    if not isinstance(triple, dict):
        return str(triple)
    lang = normalize_language(lang)
    hi = triple.get("hi", "")
    target = triple.get("target", "")
    if lang == "hi":
        return hi
    if lang == "target":
        return target
    if hi and target:
        return f"{hi}{sep}{target}"
    return hi or target


def language_label(lang):
    return LANGUAGES.get(normalize_language(lang), {}).get("label", lang)


# Convenience: build a Triple from flat strings.
def triple(hi, target, en=""):
    return {"hi": hi, "target": target, "en": en}
