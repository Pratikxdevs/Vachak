"""HTTP API for the translation service (6A).

Serves POST /translate. Uses FastAPI when available; otherwise falls back to a stdlib
http.server so the service runs locally with zero extra dependencies. Backend is chosen by env TRANSLATION_BACKEND (mock | baseline | satfinal | final).
FLAGSHIP: satfinal (fine-tuned Hindi<->Santali) is the default. Swap with no UI change.

Response contract: {sourceText, targetText, confidence, terminologyWarnings[], backend, isFixture, note}
"""

from __future__ import annotations

import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Optional

from .translation_service import TranslationRequest, TranslationService

_HERE = os.path.dirname(os.path.abspath(__file__))
_DEFAULT_VOCAB = os.path.join(_HERE, "terminology.csv")


def build_service() -> TranslationService:
    vocab = _DEFAULT_VOCAB if os.path.isfile(_DEFAULT_VOCAB) else None
    return TranslationService.from_name(
        os.environ.get("TRANSLATION_BACKEND", "satfinal"),
        vocabulary_csv=vocab,
        model_dir=os.environ.get("INDICTRANS2_MODEL_DIR", ""),
    )


def handle_translate(payload: dict) -> dict:
    req = TranslationRequest(
        text=payload.get("text", ""),
        source=payload.get("source", "hin"),
        target=payload.get("target", "sat"),
        context=payload.get("context", {}) or {},
    )
    if not req.text:
        return {"error": "field 'text' is required"}
    resp = build_service().translate(req)
    return resp.as_dict()


def create_fastapi_app():
    try:
        from fastapi import FastAPI
        from fastapi.responses import JSONResponse
    except ImportError as e:  # pragma: no cover
        raise RuntimeError("fastapi not installed; use serve_stdlib() instead") from e
    app = FastAPI(title="Vachak Translation Service")

    @app.post("/translate")
    def translate(payload: dict):
        out = handle_translate(payload)
        if "error" in out:
            return JSONResponse(out, status_code=400)
        return out

    @app.get("/health")
    def health():
        return {"status": "ok", "backend": os.environ.get("TRANSLATION_BACKEND", "satfinal")}

    return app


class _Handler(BaseHTTPRequestHandler):
    def do_POST(self):  # noqa: N802
        if self.path.rstrip("/") != "/translate":
            self._send(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            self._send(400, {"error": "invalid JSON"})
            return
        self._send(200, handle_translate(payload))

    def do_GET(self):  # noqa: N802
        if self.path.rstrip("/") in ("/health", "/"):
            self._send(200, {"status": "ok", "backend": os.environ.get("TRANSLATION_BACKEND", "satfinal")})
        else:
            self._send(404, {"error": "not found"})

    def _send(self, code: int, obj: dict) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):  # silence default stderr logging
        return


def serve_stdlib(host: str = "127.0.0.1", port: int = 8080) -> None:
    srv = HTTPServer((host, port), _Handler)
    print(f"[translate] stdlib server on http://{host}:{port}/translate  (backend={os.environ.get('TRANSLATION_BACKEND','satfinal')})")
    srv.serve_forever()


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    try:
        create_fastapi_app()
        print("FastAPI available: run `uvicorn backend.api.app:create_fastapi_app` or use stdlib.")
    except RuntimeError:
        pass
    serve_stdlib(port=port)
