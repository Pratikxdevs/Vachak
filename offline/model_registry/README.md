# offline/model_registry — Language-Pack Manager (PHASE 2B)

Offline authority for installing, validating, and rolling back language packs.
Mirrors the Android `LanguagePackManager` interface. **No network** — packs are
side-loaded from local directories.

## Manifest schema

`modelpacks/mundari/manifest.json` is the reference example:

```json
{
  "language": "mund",
  "version": "0.1.0",
  "minAndroid": 28,
  "checksum": "<sha256 of concatenated model checksums>",
  "models": [
    { "name": "asr_hi", "file": "asr/model.onnx", "sizeBytes": 0,
      "checksum": "<sha256>", "kind": "asr" },
    { "name": "mt_hi_mund", "file": "mt/model.onnx", "sizeBytes": 0,
      "checksum": "<sha256>", "kind": "mt" },
    { "name": "tts_mund", "file": "tts/model.onnx", "sizeBytes": 0,
      "checksum": "<sha256>", "kind": "tts" }
  ]
}
```

## API

`ModelRegistry(root)` persists state to `registry.json` under `root`.

| Method | Purpose |
|--------|---------|
| `install(pack_dir)` | Validate schema + per-model checksums, backup prev version, copy pack, record state |
| `uninstall(language)` | Remove pack + state |
| `version_check(language, candidate)` | `"new"` / `"same"` / `"older"` |
| `validate(language)` | Re-check pack + model checksums against recorded state |
| `rollback(language)` | Reinstall most recent `.rollback` backup |
| `storage_used_bytes()` | Sum on-disk size of installed packs |

## Run tests

```bash
python3 -m unittest offline.model_registry.tests.test_registry -v
```

## Build a pack

```bash
python3 scripts/build_modelpack.py --lang mund --version 0.1.0 \
    --out modelpacks/mundari --asr asr/model.onnx --mt mt/model.onnx --tts tts/model.onnx
```

## Known limitations

- Checksums in the example manifest / DEV packs are placeholders (`DEV-FIXTURE-*`).
  Real packs must carry true SHA-256 values or `install` rejects tampered files.
- Rollback keeps one backup per language under `<root>/.rollback/`.
