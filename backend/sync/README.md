# backend/sync — Teacher Correction + Sync (PHASE 10)

Offline-first correction capture with a hard human-review gate. Corrections are
**NEVER auto-trained**.

## Data model (`correction.py`)
`TeacherCorrection { source, target, language, grade, subject, lesson,
teacher_correction, timestamp, status }` — serialized to JSON.

## Queue (`queue.py`)
`SyncQueue` — durable on-device JSONL store. No network calls. `enqueue`,
`pending`, `mark_status`, `by_status`, `all`.

## Backend (`backend.py`)
- `SyncBackend` — `sync_when_online(queue)` uploads **only when connectivity
  returns**; otherwise corrections stay on device. Uploaded items enter
  `PENDING_HUMAN_REVIEW`.
- `HumanReviewer` — external review surface (approve/reject).
- `TrainingGate` — `AUTO_TRAIN_ENABLED = False`. `can_release_corpus` refuses
  unless every correction is `VERIFIED` AND a human explicitly approved.
  `request_training_manifest` describes what *could* train but does NOT train.

## Safety property
```
device queue →(internet returns)→ backend → human review → verified corpus →(explicit) training
```
Unverified corrections can never reach a training corpus.

## Tests
`tests/test_sync.py` — offline guard, verify→export, training-gate blocks.
```bash
python backend/sync/tests/test_sync.py
```

## Sample data
`sample_data/sample_corrections.jsonl` — 3 example corrections (gold-style
classroom sentences). Sample only; not training data until verified.

## Known limitations
- Transport is delegated to the offline package installer (`EngineContracts.SyncManager`);
  this module persists + gates, it does not open sockets.
- `HumanReviewer` is a placeholder; the real review console is out of scope here.
