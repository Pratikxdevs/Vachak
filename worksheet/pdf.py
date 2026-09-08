"""
worksheet/pdf.py — minimal dependency-free PDF writer (text only).
---------------------------------------------------------------------------
Generates a valid single/multi-page PDF from a list of text lines. Intended for
simple worksheet/answer-key printouts on offline devices. No external libs
(reportlab/weasyprint are banned by the lean-deps rule). Glyph rendering depends
on the viewer's built-in fonts; Devanagari/Ol Chiki may require a font-embedding
step in production (see README "known limitations").
"""
import zlib

PAGE_W, PAGE_H = 595, 842  # A4 in points
MARGIN = 48
LINE_H = 14
FONT_SIZE = 10


def _esc(s):
    return s.replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")


def write_text_pdf(lines, path):
    """Write `lines` (list of str) to a PDF at `path`."""
    # paginate
    max_lines = max(1, (PAGE_H - 2 * MARGIN) // LINE_H)
    pages = [lines[i:i + max_lines] for i in range(0, len(lines), max_lines)] or [[""]]

    objects = []

    def add(obj):
        objects.append(obj)
        return len(objects)  # 1-based id

    # Reserve: 1=catalog, 2=pages, then per page: content + page, then font
    font_id = None
    content_ids = []
    page_ids = []

    # We'll assign ids sequentially as we build.
    # Build font object first.
    font_obj = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    # Build content + page objects
    page_objs = []
    content_objs = []
    for pg in pages:
        stream = ["BT", f"/F1 {FONT_SIZE} Tf", f"{MARGIN} {PAGE_H - MARGIN} Td", f"{LINE_H} TL"]
        for ln in pg:
            stream.append(f"({_esc(ln)}) Tj")
            stream.append("T*")
        stream.append("ET")
        content = "\n".join(stream)
        content_objs.append(content)
        page_objs.append(None)  # placeholder

    # Assemble object list with fixed ids:
    # 1 catalog, 2 pages, 3 font, then pairs (content, page) per page
    catalog_id = 1
    pages_id = 2
    font_id = 3
    body = []
    body.append((catalog_id, "<< /Type /Catalog /Pages %d 0 R >>" % pages_id))
    kids = []
    next_id = font_id + 1
    for content in content_objs:
        cid = next_id
        pid = next_id + 1
        kids.append(pid)
        compressed = zlib.compress(content.encode("latin-1", "replace"))
        body.append((cid,
            "<< /Length %d /Filter /FlateDecode >>\nstream\n%s\nendstream" % (len(compressed), compressed)))
        # page object references content + font
        page_dict = ("<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %d %d] "
                     "/Resources << /Font << /F1 %d 0 R >> >> /Contents %d 0 R >>"
                     % (pages_id, PAGE_W, PAGE_H, font_id, cid))
        body.append((pid, page_dict))
        next_id = pid + 1
    kids_str = " ".join("%d 0 R" % k for k in kids)
    body.append((pages_id, "<< /Type /Pages /Kids [%s] /Count %d >>" % (kids_str, len(kids))))
    body.append((font_id, font_obj))

    body.sort(key=lambda x: x[0])
    max_id = max(i for i, _ in body)

    out = b"%PDF-1.4\n"
    offsets = {}
    for oid, payload in body:
        offsets[oid] = len(out)
        out += ("%d 0 obj\n%s\nendobj\n" % (oid, payload)).encode("latin-1", "replace")
    xref_pos = len(out)
    out += ("xref\n0 %d\n" % (max_id + 1)).encode("latin-1")
    out += b"0000000000 65535 f \n"
    for oid in range(1, max_id + 1):
        off = offsets.get(oid, 0)
        out += ("%010d 00000 n \n" % off).encode("latin-1")
    out += ("trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n"
            % (max_id + 1, catalog_id, xref_pos)).encode("latin-1")

    with open(path, "wb") as f:
        f.write(out)
    return path


if __name__ == "__main__":
    write_text_pdf(["Hello Vachak", "Worksheet PDF (dependency-free).", "Line 3"], "/tmp/test.pdf")
    print("wrote /tmp/test.pdf")
