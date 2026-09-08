# shared/orchestrator — Voice Pipeline Orchestrator (PHASE 9)

Language-agnostic reference implementation of the full request lifecycle:

```
audio → VAD → ASR → Context → NMT → TerminologyValidator → TTS → Speaker
```

## What it owns
- **9A Orchestrator** (`orchestrator.py`): request-lifecycle state machine. Sequential
  only (RAM budget). Times every stage with `perf_counter`.
- **9B ClassroomState** (`classroom_state.py`): live session state
  (lesson/activity/language/transcript/translation/audio/student response).
- **9C Error policy** (`errors.py`): ASR fail→retry; translate fail→show source;
  TTS fail→show translation; model unavailable→clear error + language-pack action.

## Interface contract (`interfaces.py`)
Components are abstract: `VAD, ASR, ContextEngine, NMT, TerminologyValidator,
TTS, Speaker`. Swap mock ↔ real (sherpa-onnx / IndicTrans2) without touching the
state machine. `ModelUnavailableError` is the signal that triggers the
language-pack action.

## Usage
```python
from shared.orchestrator import Orchestrator, Components, MockVAD, MockASR, MockNMT, ...
orch = Orchestrator(Components(vad=MockVAD(), asr=MockASR(), nmt=MockNMT(), ...))
r = orch.process_utterance(audio)
# r.status, r.transcript, r.translation, r.present_text/audio, r.prompt_language_pack
```

## Tests
`tests/test_orchestrator.py` — unit tests for the state machine + error policy.
Run offline, no models:
```bash
python shared/orchestrator/tests/test_orchestrator.py
```

## Kotlin counterpart
`android/ml/orchestrator` mirrors this over the existing `EngineContracts`
types (ASREngine / TranslationEngine / TTSEngine / LanguagePackManager).

## Known limitations
- Mocks (`mocks.py`) are DEV FIXTURES — clearly-fake, not inference.
- No parallelism by design (2GB RAM); real engine wiring is other phases.
