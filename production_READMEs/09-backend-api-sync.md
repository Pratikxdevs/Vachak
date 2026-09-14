# 09 — Backend API & Sync

`backend/api/` — `POST /translate`, stdlib `http.server`, zero deps.
`TRANSLATION_BACKEND=satfinal` default (flagship hin↔sat bidi).
Run: `cd backend/api && /usr/bin/python3 app.py` (PORT, default 8080).

## Endpoints (`backend/api/app.py`)

- `POST /translate` (`Content-Type: application/json`)
  `{text, source, target, context{grade,subject,learningOutcome}}` →
  `{sourceText, targetText, confidence, terminologyWarnings[], backend, isFixture, note}`;
  `400 {error}` on missing text/invalid JSON; `404` otherwise.
- `GET /health | /` → `{status: ok, backend: <name>}`.

## Backends (`backend/api/translation_service.py` — swap, no UI change)

| Name | Class | Behavior |
|---|---|---|
| `mock` | `MockTranslationBackend` | `[mun] <input>`, confidence 0.0, DEV FIXTURE |
| `baseline` | `BaselineIndicTrans2Backend` | Real IndicTrans2 CT2 INT8 `hin→sat_Olck` (lazy ctranslate2/transformers/Toolkit); NOT Mundari |
| `satfinal` (default) | `FinetunedSantaliBackend` | hin↔sat LoRA-r64-merged dist-320M CT2 INT8 at `modelpacks/sat_bidi_ct2_int8`, trained 9,423 verified pairs, chrF 40.4 hi→sat / 45.8 sat→hi; MACHINE-TRANSLATED draft, NOT Mundari |
| `final` | `FinalMundariTranslationBackend` | Stub `NotImplementedError` |

`terminology_validator.py` (deterministic, no LLM) appends
`alternative/unapproved/missing_key_term` warnings; `terminology.csv` is DEV FIXTURE
Hindi stand-in — replace with approved Mundari glossary.

## Sync — teacher corrections, never auto-train

- `backend/sync/correction.py` — `TeacherCorrection{…, status: QUEUED|UPLOADED|
  PENDING_HUMAN_REVIEW|VERIFIED|REJECTED}`.
- `backend/sync/queue.py` — file-backed JSONL `enqueue/pending/mark_status`.
- `backend/sync/backend.py` — `SyncBackend(internet_available=False)`;
  `approve/reject/export_verified_corpus(VERIFIED only)`; `HumanReviewer.decide()`;
  `TrainingGate.AUTO_TRAIN_ENABLED=False` — manifest only, never trains.
- `backend/sync/sync_service.py` — `assemble_pack()` → `manifest.json`,
  consumed by offline installer. Transport = side-loaded pack, never sockets.
