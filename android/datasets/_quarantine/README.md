# _quarantine — BY-NC-SA-FS quarantined datasets (NOT bundled)

This directory holds **copies/markers** for datasets that are **quarantined** and
**MUST NOT be bundled** into offline APKs or `.vachakpack` language packs.

## Why this exists

| Dataset | Original location | License | Status |
|---------|-------------------|---------|--------|
| `hin_mun` (Hindi–Mundari, ~10k synthetic + lexicon) | `datasets/hin_mun/` | **Karya BY-NC-SA-FS 1.0** (NonCommercial, ShareAlike, FreeSoftware) | **QUARANTINED — DO NOT BUNDLE** |

* `BY-NC-SA-FS 1.0` is **NonCommercial**. The SIH demo pack is offline and free, but
  any downstream commercial redistribution would violate NC. Until a commercial waiver
  or re-licensing is obtained, all Mundari synthetic/adapted material that **Incorporates**
  `hin_mun` remains research-only.
* `FreeSoftware` clause (§3(c)): if you **Incorporate** this data into an AI system,
  the **Incorporator's License must be GPL-3.0-or-later** — the adapter code path is
  GPL-compatible. This is noted but does not change the NC restriction.
* `ShareAlike` (§3(b)): any **Adapted Material** that is **Shared** must use
  `BY-NC-SA-FS 1.0` or a Compatible License.

Per `packages/build_pack.py:collect_files()` the allowlist **never** includes
`datasets/hin_mun` — the quarantine is enforced in code (`grep -r "hin_mun"` should
show only provenance/quarantine mentions, never a pack inclusion).

## What is quarantined vs. what is bundled

* **NOT bundled:** `datasets/hin_mun/corpus.tsv` + `LICENSE.txt` and any
  Mundari LoRA adapter (`it2_mundari_lora`) trained on it. The adapter entry in
  `THIRD_PARTY_NOTICES.md` and `packages/build_pack.py:build_licenses()` is
  explicitly marked `quarantined, not shipped` / `NOT bundled`.
* **Bundled (CC BY 4.0 only):** `COILD HIN-SAT 20,603`, `Education_v2`, `IndicVoices` etc.
  — see `THIRD_PARTY_NOTICES.md` + `docs/MODEL_AND_DATA_PROVENANCE.md`.
* **hin_sat synthetic 10k** (`datasets/hin_sat/classroom_10k.*`): **DRAFT / NOT approved
  pedagogy** — kept as stopgap, not shipped as verified curriculum. See
  `datasets/hin_sat/manifest.json` + provider notes. `hin_sat` pack-bundled corpus is
  the CC BY 4.0 `COILD` gold, not the 10k synthetic.

## This directory

* `hin_mun/LICENSE.txt` — **copy** of the original license for auditability.
  The **original** at `datasets/hin_mun/LICENSE.txt` is **kept untouched**
  (no delete) per the "do not delete anything, only add/clarify" rule.
* This `README.md` — quarantine marker explaining BY-NC-SA-FS not bundled.
* No corpus copies are stored here (avoids duplication); the corpus remains only at
  `datasets/hin_mun/corpus.tsv` with this marker pointing to it.

## Verification

```bash
# should be quarantined (not bundled)
grep -n "hin_mun" packages/build_pack.py  # -> allowlist comment: never bundled / quarantined
grep -n "quarantine\|BY-NC-SA-FS" THIRD_PARTY_NOTICES.md packages/build_pack.py docs/MODEL_AND_DATA_PROVENANCE.md

# pack must not contain hin_mun
unzip -l packages/packs/*.vachakpack | grep -i hin_mun && echo "ERROR: leaked" || echo "OK: no hin_mun in pack"

# license copies present
ls -l datasets/hin_mun/LICENSE.txt datasets/_quarantine/hin_mun/LICENSE.txt
diff -u datasets/hin_mun/LICENSE.txt datasets/_quarantine/hin_mun/LICENSE.txt && echo "LICENSE copies match"
```

## History

Created per `Fix curriculum/data` tasks: "Create datasets/_quarantine/hin_mun/ and move copy of hin_mun LICENSE there with note, but keep original for now per dont delete — create quarantine marker".

Do not remove this marker without resolving the BY-NC-SA-FS commercial-use question
and updating `THIRD_PARTY_NOTICES.md`, `VOICE_CONSENT.md` adapter notes, and
`packages/build_pack.py` license list together.
