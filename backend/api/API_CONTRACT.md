# API Contract — POST /translate

Base URL: `http://<host>:<port>` (default `127.0.0.1:8080`).

## Endpoint
`POST /translate` — `Content-Type: application/json`

### Request body
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `text` | string | yes | Source utterance (Hindi). |
| `source` | string | no | Source lang code. Default `"hin"`. |
| `target` | string | no | Target lang code. Default `"mun"`. |
| `context` | object | no | `{ grade:int, subject:string, learningOutcome:string }` — drives terminology checks. |

### Response body (200)
| Field | Type | Notes |
|-------|------|-------|
| `sourceText` | string | Echo of input `text`. |
| `targetText` | string | Translated text (mock: `"[mun] <input>"`). |
| `confidence` | number | `0.0` for mocks/fixtures; see `note`. |
| `terminologyWarnings` | array | See warning schema below. Empty if clean. |
| `backend` | string | `"mock"` \| `"baseline-indictrans2-hin-sat"` \| `"final-mundari"`. |
| `isFixture` | bool | `true` if output is NOT from a production Mundari model. |
| `note` | string | Human-readable caveat (e.g., "DEV FIXTURE…", "terminology warnings present."). |

### terminologyWarnings[] item
| Field | Type | Notes |
|-------|------|-------|
| `token` | string | Offending / flagged token. |
| `kind` | string | `"alternative"` \| `"unapproved"` \| `"missing_key_term"`. |
| `suggestion` | string\|null | Canonical replacement, if any. |
| `message` | string | Explanation. |

### Errors
`400` with `{ "error": "..." }` when `text` missing or JSON invalid.

## Backend selection (no UI change)
Set env `TRANSLATION_BACKEND=mock|baseline|final`. The service reads it at request time.
`baseline` additionally reads `INDICTRANS2_MODEL_DIR`.

## Health
`GET /health` → `{ "status": "ok", "backend": "mock" }`.

## Notes
- Latency is NOT reported by this endpoint; it is measured by the speech pipeline harness (5D). Do not infer latency from this API.
- All non-`final` Mundari responses are flagged `isFixture=true`. The `final` Mundari model does not exist yet.
