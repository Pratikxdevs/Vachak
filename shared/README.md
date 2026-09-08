# shared/ — Interface Contracts (PHASE 1)

This directory holds the **shared vocabulary** between the Android app and any
Python backend/orchestration tool. The Kotlin interfaces
(`android/app/.../engine/EngineContracts.kt`) and the Python `Protocol`s
(`shared/schemas/engines.py`) are kept in lockstep.

## Contents

- `schemas/engines.py` — Python `Protocol` interfaces for all 9 engines.
- `schemas/mock_engines.py` — DEV FIXTURE mock implementations.
- `schemas/tests/test_engines.py` — swapability proof (mock ↔ real).
- `schemas/samples/sample_io.json` — sample request/response per engine.
- `schemas/README.md` — full API/interface contract doc.

## Why this matters

Swapping a mock for a production adapter must not change caller code. Both the
Kotlin (`EngineProvider`) and Python (constructor injection) sides depend on the
interface type only. `tests/test_engines.py` asserts a `FakeRealTranslationEngine`
satisfies the same contract as the mock.

## Run the contract tests

```bash
python3 -m unittest shared.schemas.tests.test_engines -v
```

> All sample I/O is DEV FIXTURE data — not real model output.
