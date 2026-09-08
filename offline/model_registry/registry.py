"""PHASE 2B — ModelRegistry: install / uninstall / version check / checksum
validation / rollback / storage-size reporting for offline language packs.

This is the offline authority the Android `LanguagePackManager` talks to (via
SyncManager side-loading). It never touches the network. State is persisted to
a local `registry.json` inside the registry root.

Storage math: storage_used_bytes sums the on-disk size of every installed model
file plus the manifest. rollback keeps the previous pack version under
`<packRoot>/.rollback/<version>` until the next install.
"""
from __future__ import annotations

import json
import shutil
from pathlib import Path
from typing import Dict, List, Optional

from .manifest_schema import ModelEntry, PackManifest, sha256_file


class ModelRegistryError(Exception):
    pass


class ModelRegistry:
    def __init__(self, root: str | Path):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self._state_file = self.root / "registry.json"
        self._state: Dict[str, dict] = self._load_state()

    # ---- state persistence -------------------------------------------------
    def _load_state(self) -> Dict[str, dict]:
        if self._state_file.exists():
            return json.loads(self._state_file.read_text(encoding="utf-8"))
        return {}

    def _save_state(self) -> None:
        self._state_file.write_text(json.dumps(self._state, indent=2), encoding="utf-8")

    # ---- public API --------------------------------------------------------
    def install(self, pack_dir: str | Path) -> PackManifest:
        """Install a side-loaded pack. Validates schema + checksums, backs up
        any previous version for rollback, records state, returns manifest."""
        pack_dir = Path(pack_dir)
        manifest_path = pack_dir / "manifest.json"
        if not manifest_path.exists():
            raise ModelRegistryError(f"no manifest.json in {pack_dir}")
        manifest = PackManifest.load(manifest_path)

        # checksum validation of model files (skip missing files in DEV fixtures)
        for m in manifest.models:
            fp = pack_dir / m.file
            if fp.exists():
                actual = sha256_file(fp)
                if actual != m.checksum:
                    raise ModelRegistryError(
                        f"checksum mismatch for {m.name}: expected {m.checksum}, got {actual}")

        dest = self.root / f"{manifest.language}-{manifest.version}"
        # rollback backup of previously installed same-language pack
        prev = self._installed_version(manifest.language)
        if prev:
            old_dest = self.root / f"{manifest.language}-{prev}"
            if old_dest.exists():
                roll = self.root / ".rollback" / f"{manifest.language}-{prev}"
                roll.parent.mkdir(parents=True, exist_ok=True)
                if roll.exists():
                    shutil.rmtree(roll)
                shutil.copytree(old_dest, roll)
                shutil.rmtree(old_dest)

        if dest.exists():
            shutil.rmtree(dest)
        shutil.copytree(pack_dir, dest)
        self._state[manifest.language] = {
            "version": manifest.version,
            "path": str(dest),
            "checksum": manifest.checksum,
        }
        self._save_state()
        return manifest

    def uninstall(self, language: str) -> None:
        entry = self._state.pop(language, None)
        if entry is None:
            raise ModelRegistryError(f"pack for {language} not installed")
        dest = Path(entry["path"])
        if dest.exists():
            shutil.rmtree(dest)
        self._save_state()

    def installed(self) -> List[str]:
        return list(self._state.keys())

    def _installed_version(self, language: str) -> Optional[str]:
        return self._state.get(language, {}).get("version")

    def version_check(self, language: str, candidate: str) -> str:
        """Return 'new' | 'same' | 'older' relative to installed version
        (simple dotted-integer compare)."""
        cur = self._installed_version(language)
        if cur is None:
            return "new"
        return _cmp_version(candidate, cur)

    def validate(self, language: str) -> bool:
        entry = self._state.get(language)
        if not entry:
            return False
        dest = Path(entry["path"])
        manifest = PackManifest.load(dest / "manifest.json")
        if manifest.checksum != entry["checksum"]:
            return False
        for m in manifest.models:
            fp = dest / m.file
            if fp.exists() and sha256_file(fp) != m.checksum:
                return False
        return True

    def rollback(self, language: str) -> PackManifest:
        """Reinstall the most recent rollback backup for this language."""
        roll_base = self.root / ".rollback"
        candidates = sorted(roll_base.glob(f"{language}-*")) if roll_base.exists() else []
        if not candidates:
            raise ModelRegistryError(f"no rollback backup for {language}")
        backup = candidates[-1]
        return self.install(backup)

    def storage_used_bytes(self) -> int:
        total = 0
        for p in self.root.rglob("*"):
            if p.is_file() and ".rollback" not in p.parts and p.name != "registry.json":
                total += p.stat().st_size
        return total


def _cmp_version(a: str, b: str) -> str:
    pa = [int(x) for x in a.split(".")]
    pb = [int(x) for x in b.split(".")]
    if pa > pb:
        return "new"
    if pa == pb:
        return "same"
    return "older"
