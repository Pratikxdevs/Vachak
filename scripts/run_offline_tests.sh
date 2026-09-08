#!/usr/bin/env bash
# Run the offline Python test harness + interface contract tests.
# Mirrors the on-device gradle connectedCheck (android/app/src/androidTest).
set -e
cd "$(dirname "$0")/.."
echo "== interface contract (shared) =="
python3 -m unittest shared.schemas.tests.test_engines -v
echo "== model registry (2B) =="
python3 -m unittest offline.model_registry.tests.test_registry -v
echo "== offline harness (2C) =="
python3 -m unittest offline.sync_engine.offline_harness -v
echo "ALL OFFLINE TESTS PASSED"
