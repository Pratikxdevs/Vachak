"""Content pack builder — sat_Olck v0.2.0 (curriculum/class packs).

Bundles the deterministic curriculum/class/{1..5} output (chapters,
assignments, worksheets, flashcard decks, deduped images) + pack_summary.json
into a side-loadable .vachakpack for the offline SAF installer
(PackInstaller/PackManager — no network, ever).

Content-only pack: models array is empty; file integrity rides on
curriculum_files[] sha256 entries (PackInstaller accepts either).

Status honesty: every chapter ships AUTO_EXTRACTED; the manifest records
content_status accordingly. Never APPROVED.

Usage:
    python scripts/build_content_pack.py --version 0.2.0
    # output: packages/packs/sat_Olck-v0.2.0.vachakpack

Prereq: run scripts/build_pack_summary.py first so the asset copy is fresh.
"""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLASS_DIR = os.path.join(ROOT, "curriculum", "class")
SUMMARY_ASSET = os.path.join(
    ROOT, "android", "app", "src", "main", "assets", "curriculum", "pack_summary.json")
OUT_DIR = os.path.join(ROOT, "packages", "packs")


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def collect() -> list[tuple[str, str]]:
    """Returns [(arcname, realpath)] for everything shipped in the pack."""
    items: list[tuple[str, str]] = []
    for g in ("1", "2", "3", "4", "5"):
        gdir = os.path.join(CLASS_DIR, g)
        for dp, _, fns in os.walk(gdir):
            for fn in sorted(fns):
                real = os.path.join(dp, fn)
                arc = os.path.join("curriculum", "class", g, os.path.relpath(real, gdir))
                items.append((arc, real))
    # Phase 12: shared smallest-PDF pool (referenced by pool slots).
    pool_dir = os.path.join(CLASS_DIR, "pdf_pool")
    if os.path.isdir(pool_dir):
        for fn in sorted(os.listdir(pool_dir)):
            real = os.path.join(pool_dir, fn)
            if os.path.isfile(real):
                items.append((os.path.join("curriculum", "class", "pdf_pool", fn), real))
    if not os.path.exists(SUMMARY_ASSET):
        raise SystemExit("missing pack_summary.json — run scripts/build_pack_summary.py first")
    items.append(("curriculum/pack_summary.json", SUMMARY_ASSET))
    return items


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default="0.2.0")
    ap.add_argument("--lang", default="sat_Olck")
    args = ap.parse_args()

    items = collect()
    entries = []
    total = 0
    for arc, real in items:
        size = os.path.getsize(real)
        total += size
        entries.append({"name": arc, "path": arc,
                        "sha256": sha256_file(real), "size": size})

    # Per-grade rollup from manifests (chapter counts only — no translations).
    # Phase 12 fallback: converted tree without builder manifests — derive
    # from demo_set.json (the wired listing) + chapter dir count.
    grades = []
    for g in ("1", "2", "3", "4", "5"):
        m_path = os.path.join(CLASS_DIR, g, "manifest.json")
        if os.path.isfile(m_path):
            m = json.load(open(m_path, encoding="utf-8"))
            grades.append({"grade": int(g), "books": m.get("books", 0),
                           "chapters": len(m.get("chapters", {})),
                           "status": m.get("status", "AUTO_EXTRACTED")})
            continue
        demo_path = os.path.join(CLASS_DIR, g, "demo_set.json")
        demo = json.load(open(demo_path, encoding="utf-8")) if os.path.isfile(demo_path) else []
        ch_root = os.path.join(CLASS_DIR, g, "chapters")
        n_dirs = len([d for d in os.listdir(ch_root)
                      if os.path.isdir(os.path.join(ch_root, d))]) if os.path.isdir(ch_root) else 0
        statuses = set()
        for dp, _, fns in os.walk(os.path.join(CLASS_DIR, g)):
            if "chapter.json" in fns and os.path.basename(dp) != g:
                try:
                    statuses.add(json.load(
                        open(os.path.join(dp, "chapter.json"), encoding="utf-8")
                    ).get("status", "?"))
                except (OSError, ValueError):
                    statuses.add("UNREADABLE")
        grades.append({"grade": int(g), "books": len(demo) or n_dirs,
                       "chapters": len(demo) or n_dirs,
                       "status": "+".join(sorted(statuses)) or "UNKNOWN"})

    manifest = {
        "packName": f"{args.lang}-v{args.version}.vachakpack",
        "language": args.lang,
        "version": args.version,
        "created": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d"),
        "kind": "curriculum-content",
        "content_status": "AUTO_EXTRACTED — speaker review required before APPROVED",
        "models": [],
        "curriculum_files": entries,
        "grades": grades,
        "licenses": [
            {"name": "Vachak curriculum/class packs (G1-G5)",
             "license": "CC BY 4.0 (textbook-derived, AUTO_EXTRACTED)",
             "source": "santali_organized textbooks"},
        ],
        "totalBytes": total,
    }

    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, manifest["packName"])
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for arc, real in items:
            z.write(real, arc)
        z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=1))

    print(f"[pack] {out} ({os.path.getsize(out)/1048576:.1f} MB zip, "
          f"{total/1048576:.1f} MB content, {len(entries)} files)")

    # Phase 15: mirror the pool PDFs + slot mapping into APK assets so the
    # reader has a direct-asset fallback independent of the zip-pack path.
    export_pdf_assets()
    return 0


def export_pdf_assets() -> None:
    """Copy curriculum/class/pdf_pool/*.pdf + slot mapping to APK assets."""
    import shutil
    pool_dir = os.path.join(CLASS_DIR, "pdf_pool")
    out_dir = os.path.join(
        ROOT, "android", "app", "src", "main", "assets", "pdf_pool")
    os.makedirs(out_dir, exist_ok=True)
    n = 0
    if os.path.isdir(pool_dir):
        for fn in sorted(os.listdir(pool_dir)):
            real = os.path.join(pool_dir, fn)
            if os.path.isfile(real):
                shutil.copyfile(real, os.path.join(out_dir, fn))
                n += 1
    map_src = os.path.join(
        ROOT, ".planning", "phases", "12-pdf-worksheets", "pdf_slot_mapping.json")
    if os.path.isfile(map_src):
        doc = json.load(open(map_src, encoding="utf-8"))
        mapping = {f"{s['grade']}/{s['slug']}":
                   {"file": s["pool_file"], "sha256": s["sha256"]}
                   for s in doc.get("slots", [])}
        with open(os.path.join(out_dir, "mapping.json"), "w", encoding="utf-8") as f:
            json.dump(mapping, f, ensure_ascii=False, indent=1, sort_keys=True)
            f.write("\n")
    print(f"[pack] assets/pdf_pool/ refreshed ({n} PDFs)")


if __name__ == "__main__":
    raise SystemExit(main())
