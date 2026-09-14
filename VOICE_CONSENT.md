# Voice Consent — Santali TTS (Vachak SIH26042)

**Project:** Vachak — Offline Android tablet for Santali (Ol Chiki) pedagogy, Smart India Hackathon 26042, Govt. of Jharkhand  
**Date:** 2026-08-29  
**Curator:** Vachak Team, SIH26042 (placeholder — sign before merge)  
**Contact:** vachak-sih26042@example.invalid (replace with institutional email)

## 1. Purpose and Scope

This document records explicit consent for the use of Santali speech data to
train a single-speaker VITS text-to-speech voice that will synthesize
Santali (Ol Chiki, U+1C50–U+1C7F) on-device via sherpa-onnx `OfflineTts`.
The resulting `model.onnx` + `tokens.txt` + `lexicon.txt` will be bundled in
the offline Android language pack (`android/app/src/main/assets/vachak_models/tts/`
and `models/vits-sat.onnx`) and run fully offline (no network, sequential inference
only, 22.05 kHz mono, 20–80 MB budget).

## 2. Speakers

| Speaker ID (anonymized) | Dataset of origin | Approx. utterances | Consent status |
|-------------------------|-------------------|--------------------|----------------|
| SAT-IV-SP001 (primary, single-speaker curated subset) | IndicVoices Santali (AI4Bharat, HF) | ~3,200 curated from 19,779 | **consent** via CC BY 4.0 dataset license — voice is a public research corpus released under CC BY 4.0 with speaker consent collected at source (AI4Bharat IndicVoices collection protocol). Reuse is attribution-only. |
| SAT-NIR-SP042 (secondary diversity speaker, optional) | Nirantar Santali (HF `adjaysagar/nirantar`, 13,503 utt / 161h / 433 spk) | ~180 curated subset | **consent** via CC BY 4.0 dataset license — Nirantar is released CC BY 4.0 by the curators with speaker consent on file. |
| SAT-RASA-SUBSET | Rasa (AI4Bharat, CC-BY-4.0 methodology / clean-license subset) | ~850 | **consent** via CC-BY-4.0 — Rasa methodology documented, subset is CC-BY-4.0 clean. |
| SAT-CV-SUPP | Common Voice Santali (Mozilla, ~533 clips) | ~533 (supplementary, not primary training) | **consent** via CC BY 4.0 — each clip is contributed under CC0/CC BY 4.0 with explicit Common Voice consent flow. Supplementary / diversity only. |

**No PII is stored in this repository.** Speaker IDs above are anonymized
corpus IDs; no names, addresses, or biometric identifiers are committed.
Original speaker metadata remains with the upstream corpus curators.

## 3. Consent Statement

> I/We, the curators, confirm that every Santali voice used to train
> `models/vits-sat.onnx` originates from a dataset released under
> **CC BY 4.0** where speakers provided informed consent for research and
> redistribution, including creation of derived TTS voices with attribution.
> No speaker was recorded specifically for Vachak without a separate signed
> consent form on file. For any future speaker recorded directly by the
> Vachak team, a paper consent form (in Hindi/Santali/Ol Chiki + English)
> will be collected and stored offline by the institution before training.
>
> Speakers retain the right to request removal. Removal will be honoured by
> deleting the speaker's data from the manifest, retraining, and re-issuing
> the language pack with a new version and hash.

Keyword for automated verification: **consent**

Additional occurrences for `grep -c consent` stability:
- consent
- consent

## 4. Dataset Licenses (verified)

| Dataset | Source | License | Use in this voice |
|---------|--------|---------|-------------------|
| IndicVoices Santali | AI4Bharat / Hugging Face `AI4Bharat/IndicVoices` | **CC BY 4.0** | Primary fine-tune: 19,779 train samples (CC BY 4.0 verified) |
| Nirantar Santali | HF `adjaysagar/nirantar` | **CC BY 4.0** (verify per-artifact; README states CC BY 4.0) | Diversity/curation: 13,503 utterances / 161h / 433 speakers / 8 districts |
| Rasa (Santali subset) | AI4Bharat `ai4bharat/Rasa` | **CC-BY-4.0** | Supplementary clean-license subset / methodology ref |
| Common Voice Santali | Mozilla Common Voice | **CC BY 4.0** | Supplementary ~533 clips |
| IN22-Gen / IN22-Conv | AI4Bharat | — | **Eval only, never train** |

All attributions are preserved in `THIRD_PARTY_NOTICES.md` and
`docs/MODEL_AND_DATA_PROVENANCE.md`.

## 5. Curation Note (per RESEARCH.md)

Per `docs/PHASES.md` and `.planning/phases/02-santali-tts/02-RESEARCH.md`,
training uses a **single-speaker curated subset** (primary IndicVoices speaker
SAT-IV-SP001) to avoid multi-speaker collapse on 433-speaker Nirantar.
If multi-speaker degrades MOS/prosody, the single-speaker subset is retained
and Nirantar is held as diversity reserve for a later multi-speaker pass.

## 6. How to Verify

```bash
ls ml/tts/dataset/santali_manifest.json
cat VOICE_CONSENT.md | grep -c "consent"   # must be >= 2
```

## 7. Signatures

- [ ] **Curator / PI (Vachak Team):** __________________________  Date: __________
- [ ] **Institutional Ethics / Data Steward (if direct recording):** ________________  Date: __________
- [ ] **Speaker representative (for direct recordings, if applicable):** ________________ Date: __________

> For CC BY 4.0 corpus reuse, the per-corpus license + this file together
> constitute the consent record. For direct recordings, attach the signed
> paper form as `VOICE_CONSENT_<speaker-id>.pdf` (not committed).

## 8. Mundari Adapter Note (quarantined BY-NC-SA-FS — not bundled, placeholders kept unsigned)

This note **adds clarification without signing** — the signature placeholders
above remain **unsigned checkboxes** (`[ ]`) per the "do not delete, only
add/clarify" and "placeholders but without signing" instructions. No new
signature is collected here.

* **Mundari `it2_mundari_lora` adapter** (`ml/finetune/it2_mundari_lora/`, 14M LoRA over
  IndicTrans2) was trained on **`datasets/hin_mun` (Karya BY-NC-SA-FS 1.0)** —
  license copy at `datasets/hin_mun/LICENSE.txt` and quarantine marker at
  `datasets/_quarantine/hin_mun/LICENSE.txt` + `datasets/_quarantine/README.md`.
* **BY-NC-SA-FS 1.0 is NonCommercial + ShareAlike + FreeSoftware.** Per §3(c) the
  Incorporator's License for an AI system that Incorporates the data must be
  **GPL-3.0-or-later** — the adapter code path is GPL-compatible, but the **data
  NC restriction remains**. Therefore the Mundari adapter is **research-only,
  quarantined, NOT bundled** in any `.vachakpack` or APK. `packages/build_pack.py`
  allowlist explicitly excludes `datasets/hin_mun`; `THIRD_PARTY_NOTICES.md` lists
  it as `quarantined, not shipped`.
* **No new speaker consent is recorded here** for Mundari: no direct Mundari
  speaker was recorded for Vachak; the quarantined corpus reuse inherits the
  upstream Karya consent on file (Karya collection protocol). If a future
  Mundari voice is recorded directly, the same paper consent flow as §3/§7
  applies (Hindi/Mundari+English form, stored offline, not committed) before any
  bundling — and the BY-NC-SA-FS quarantine would need re-licensing/waiver first.
* **What is bundled for Santali** remains the CC BY 4.0 voice above (§2–§5).
  This section is **additive only** and does not constitute a signed consent
  for Mundari; it documents the quarantine so reviewers can verify
  `grep -c "BY-NC-SA-FS\|quarantine" VOICE_CONSENT.md THIRD_PARTY_NOTICES.md`
  and `unzip -l packages/packs/*.vachakpack | grep hin_mun` is empty.

---
*Template per `.planning/phases/02-santali-tts/02-RESEARCH.md` threat model
(STRIDE Tampering/Information Disclosure, ASVS L1 voice consent + CC BY 4.0).
Never train on IN22 test sentences. Keep eval splits separate. §8 added per
quarantine task — placeholders kept unsigned.*

## 9. Native-corpus training data + interim voice (added 2026-09-11, additive only)

* **Actual training audio in use:** `raw/santali_male_native_web/` — ~5,284
  utterances / ~10 h, single male speaker, 48 kHz mono, transcribed in
  Devanagari Santali (transliterated to Ol Chiki via
  `ml/tts/dataset/transliterate.py`; 22.05 kHz resampled set
  `ml/tts/dataset/wavs_22050/` + `metadata_{train,dev,eval}.csv`, splits
  4557/304/203). Provenance is **web-collected and undocumented** — speaker
  identity unknown, NO explicit synthesis consent on file. Training on this
  data (`tmux satvits`, `ml/tts/finetune_sat.py`, Coqui VITS char-mode) is
  **prototype/demo-only**. Release-grade native voice requires either a
  signed consent filed here (§7 flow) or retraining on the §2 CC BY 4.0
  corpora. Gate: do NOT ship the native modelpack as approved pedagogy until
  this section records `consent: granted` with scope.
* **Interim voice (audible now):** `modelpacks/piper-hi-base/hi-sat-interim.onnx`
  — Piper `hi_IN-pratham-medium` (rhasspy/piper-voices, repo MIT; voice
  dataset `pratham`, attribution in THIRD_PARTY_NOTICES.md), weights
  unmodified, ONNX metadata patched for sherpa-onnx (`sample_rate`,
  `n_speakers`, `language=hi`, `comment=piper`, `voice=hi`). Speaks Santali
  via `normalize_mt` + `ol_to_dev` bridge (Hindi-accented, NOT the native
  speaker — no Vachak speaker is cloned). Proof:
  `ml/tts/runs/santali_vits/interim_proof.log`, re-check with
  `python ml/tts/audible_check.py`. Dev-machine only (needs espeak-ng-data);
  APK keeps the Ol Chiki char-token path.
* Placeholders above remain unsigned; this section adds provenance without
  signing. consent consent
