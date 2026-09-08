"""PHASE 2B tests — ModelRegistry install/uninstall/validate/rollback/storage."""
from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path

from offline.model_registry.manifest_schema import ModelEntry, PackManifest, compute_pack_checksum, sha256_file
from offline.model_registry.registry import ModelRegistry, ModelRegistryError


def make_pack(root: Path, language: str, version: str, with_files: bool = True) -> Path:
    pack = root / f"pack-{language}-{version}"
    pack.mkdir(parents=True, exist_ok=True)
    models = []
    for kind, fname, content in [
        ("asr", "asr/model.onnx", b"asr-bytes"),
        ("mt", "mt/model.onnx", b"mt-bytes"),
        ("tts", "tts/model.onnx", b"tts-bytes"),
    ]:
        fp = pack / fname
        fp.parent.mkdir(parents=True, exist_ok=True)
        fp.write_bytes(content)
        models.append(ModelEntry(
            name=f"{kind}_{language}", file=fname,
            sizeBytes=len(content), checksum=sha256_file(fp), kind=kind))
    manifest = PackManifest(
        language=language, version=version, minAndroid=28,
        checksum=compute_pack_checksum(models), models=models)
    manifest.save(pack / "manifest.json")
    return pack


class ModelRegistryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.reg = ModelRegistry(self.tmp / "registry")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_install_and_validate(self):
        pack = make_pack(self.tmp, "mund", "0.1.0")
        m = self.reg.install(pack)
        self.assertEqual(m.language, "mund")
        self.assertIn("mund", self.reg.installed())
        self.assertTrue(self.reg.validate("mund"))

    def test_checksum_mismatch_rejected(self):
        pack = make_pack(self.tmp, "mund", "0.1.0")
        # corrupt a model file after manifest written
        (pack / "asr/model.onnx").write_bytes(b"tampered")
        with self.assertRaises(ModelRegistryError):
            self.reg.install(pack)

    def test_version_check(self):
        pack = make_pack(self.tmp, "mund", "0.1.0")
        self.reg.install(pack)
        self.assertEqual(self.reg.version_check("mund", "0.1.0"), "same")
        self.assertEqual(self.reg.version_check("mund", "0.2.0"), "new")
        self.assertEqual(self.reg.version_check("mund", "0.0.9"), "older")

    def test_rollback(self):
        p1 = make_pack(self.tmp, "mund", "0.1.0")
        p2 = make_pack(self.tmp, "mund", "0.2.0")
        self.reg.install(p1)
        self.reg.install(p2)
        self.assertEqual(self.reg._installed_version("mund"), "0.2.0")
        m = self.reg.rollback("mund")
        self.assertEqual(m.version, "0.1.0")  # restored older backup
        self.assertEqual(self.reg._installed_version("mund"), "0.1.0")

    def test_uninstall_and_storage(self):
        pack = make_pack(self.tmp, "mund", "0.1.0")
        self.reg.install(pack)
        used = self.reg.storage_used_bytes()
        self.assertGreater(used, 0)
        self.reg.uninstall("mund")
        self.assertNotIn("mund", self.reg.installed())
        self.assertEqual(self.reg.storage_used_bytes(), 0)

    def test_dev_fixture_manifest_loads(self):
        base = Path(__file__).resolve().parents[3]
        m = PackManifest.load(base / "modelpacks" / "mundari" / "manifest.json")
        self.assertEqual(m.language, "mund")
        self.assertEqual(len(m.models), 3)


if __name__ == "__main__":
    unittest.main()
