"""Wave-0 tests for convert_authored_chapters (Phase 12, WS-01/WS-06).

Fixtures are the REAL user inputs (/1/so-many-toys, /3/fair-share), copied
to /tmp — the test NEVER writes under /1 or /3.
"""
from __future__ import annotations

import copy
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from convert_authored_chapters import convert_chapter  # noqa: E402

FIXTURES = [
    ("/cz/Vachak/santali_organized/1/so-many-toys", 1),
    ("/cz/Vachak/santali_organized/3/fair-share", 3),
]


class ConvertTest(unittest.TestCase):
    def setUp(self):
        for src, _ in FIXTURES:
            self.assertTrue(os.path.isdir(src), f"missing fixture {src}")
        self.tmp = tempfile.mkdtemp(prefix="vachak-conv-")
        self.out = os.path.join(self.tmp, "chapters")
        os.makedirs(self.out)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _copy_fixture(self, src):
        self._n = getattr(self, "_n", 0) + 1
        dst = os.path.join(self.tmp, f"{os.path.basename(src)}-{self._n}")
        shutil.copytree(src, dst)
        return dst

    def test_status_verbatim_draft(self):
        for src, grade in FIXTURES:
            row = convert_chapter(self._copy_fixture(src), grade, self.out)
            self.assertEqual(row["status"], "DRAFT")
            for rel in ("chapter.json",
                        f"worksheets/ws_{row['slug']}_bilingual.json",
                        "flashcards/deck_bilingual.json"):
                doc = json.load(open(os.path.join(self.out, row["slug"], rel),
                                     encoding="utf-8"))
                self.assertEqual(doc["status"], "DRAFT", rel)

    def test_prompt_mapping_exact(self):
        src = self._copy_fixture("/cz/Vachak/santali_organized/1/so-many-toys")
        row = convert_chapter(src, 1, self.out)
        orig = json.load(open(os.path.join(src, "source", "worksheets.json")
                              if os.path.exists(os.path.join(src, "source", "worksheets.json"))
                              else "/cz/Vachak/santali_organized/1/so-many-toys/worksheets.json",
                         encoding="utf-8"))
        conv = json.load(open(os.path.join(
            self.out, row["slug"], "worksheets", f"ws_{row['slug']}_bilingual.json"),
            encoding="utf-8"))
        self.assertEqual(len(conv["questions"]), len(orig["items"]))
        for o, q in zip(orig["items"], conv["questions"]):
            self.assertEqual(q["prompt_sat_deva"], (o["prompt"] or {}).get("sat_ol", ""))
            self.assertEqual(q["prompt_hi"], (o["prompt"] or {}).get("hi", ""))
            self.assertEqual(q["answer"], o.get("answer"))
            self.assertEqual(q["image_ref"], o.get("image_ref"))

    def test_ol_chiki_gate_fires(self):
        src = self._copy_fixture("/cz/Vachak/santali_organized/1/so-many-toys")
        fc_path = os.path.join(src, "flashcards.json")
        doc = json.load(open(fc_path, encoding="utf-8"))
        doc["cards"][0]["back_sat_ol"] = "plain ascii, no ol chiki"
        with open(fc_path, "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False)
        with self.assertRaises(SystemExit) as cm:
            convert_chapter(src, 1, self.out)
        self.assertEqual(cm.exception.code, 4)

    def test_missing_chapter_json_exit3(self):
        src = self._copy_fixture("/cz/Vachak/santali_organized/1/so-many-toys")
        os.remove(os.path.join(src, "chapter.json"))
        with self.assertRaises(SystemExit) as cm:
            convert_chapter(src, 1, self.out)
        self.assertEqual(cm.exception.code, 3)

    def test_image_refs_match_pages(self):
        for src, grade in FIXTURES:
            row = convert_chapter(self._copy_fixture(src), grade, self.out)
            ch = json.load(open(os.path.join(self.out, row["slug"], "chapter.json"),
                                encoding="utf-8"))
            disk = sorted(f for f in os.listdir(
                os.path.join(self.out, row["slug"], "pages")))
            self.assertEqual([r["asset"] for r in ch["image_refs"]],
                             ["pages/" + f for f in disk])

    def test_determinism(self):
        a = os.path.join(self.tmp, "a")
        b = os.path.join(self.tmp, "b")
        os.makedirs(a)
        os.makedirs(b)
        sa = os.path.join(a, "src")
        sb = os.path.join(b, "src")
        shutil.copytree("/cz/Vachak/santali_organized/3/fair-share", os.path.join(sa, "fair-share"))
        shutil.copytree("/cz/Vachak/santali_organized/3/fair-share", os.path.join(sb, "fair-share"))
        oa = os.path.join(a, "out")
        ob = os.path.join(b, "out")
        r1 = convert_chapter(os.path.join(sa, "fair-share"), 3, oa)
        r2 = convert_chapter(os.path.join(sb, "fair-share"), 3, ob)
        for rel in ("chapter.json", "assignments.json",
                    f"worksheets/ws_{r1['slug']}_bilingual.json",
                    "flashcards/deck_bilingual.json"):
            with open(os.path.join(oa, r1["slug"], rel), "rb") as f1, \
                    open(os.path.join(ob, r2["slug"], rel), "rb") as f2:
                d1 = f1.read().replace(a.encode(), b"<A>").replace(b"fair-share", b"S")
                d2 = f2.read().replace(b.encode(), b"<A>").replace(b"fair-share", b"S")
                self.assertEqual(d1, d2, rel)

    def test_inputs_untouched(self):
        before = {}
        for src, _ in FIXTURES:
            for dp, _, fns in os.walk(src):
                for fn in fns:
                    p = os.path.join(dp, fn)
                    with open(p, "rb") as f:
                        import hashlib
                        before[p] = hashlib.sha256(f.read()).hexdigest()
        convert_chapter(self._copy_fixture(FIXTURES[0][0]), 1, self.out)
        for p, h in before.items():
            with open(p, "rb") as f:
                import hashlib
                self.assertEqual(hashlib.sha256(f.read()).hexdigest(), h, p)


if __name__ == "__main__":
    unittest.main()
