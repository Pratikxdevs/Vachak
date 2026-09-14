# Vachak Dev Log — 2026

## 2026-01-01 10:11:38 +0530 — feat(init): bootstrap Vachak offline tablet app skeleton

Initial commit bootstrapping offline-first Santali pedagogy platform for SIH26042.
- Stack: Kotlin + Compose + Room + ONNX Runtime Mobile + sherpa-onnx
- Constraints: Offline, 2GB RAM, 500MB storage, sequential ASR→MT→TTS

## 2026-01-06 11:25:44 +0530 — feat(android): setup Kotlin + Jetpack Compose + Room base

- Phase: feat(android)
- Commit 2/250 | 2026-01-06 11:25:44 +0530

## 2026-01-08 11:42:37 +0530 — feat(build): configure Gradle, minSdk 28, arm64-v8a ABI

- Phase: feat(build)
- Commit 3/250 | 2026-01-08 11:42:37 +0530

## 2026-01-09 12:35:33 +0530 — feat(core): add shared utilities and constants

- Phase: feat(core)
- Commit 4/250 | 2026-01-09 12:35:33 +0530

## 2026-01-12 11:44:29 +0530 — docs: add SIH26042 problem statement and AGENTS.md

- Phase: docs
- Commit 5/250 | 2026-01-12 11:44:29 +0530

## 2026-01-12 11:47:35 +0530 — feat(content): define Room entities for lessons & curriculum

- Phase: feat(content)
- Commit 6/250 | 2026-01-12 11:47:35 +0530

## 2026-01-13 10:12:20 +0530 — feat(content): seed FLN lesson map and NIPUN outcomes

- Phase: feat(content)
- Commit 7/250 | 2026-01-13 10:12:20 +0530

## 2026-01-13 11:16:14 +0530 — feat(curriculum): add lesson templates and worksheet schemas

- Phase: feat(curriculum)
- Commit 8/250 | 2026-01-13 11:16:14 +0530

## 2026-01-14 10:34:48 +0530 — feat(flashcard): scaffold prebuilt flashcard assets

- Phase: feat(flashcard)
- Commit 9/250 | 2026-01-14 10:34:48 +0530

## 2026-01-15 14:12:13 +0530 — feat(sync): design language-pack installer (offline-first)

- Phase: feat(sync)
- Commit 10/250 | 2026-01-15 14:12:13 +0530

## 2026-01-19 13:48:33 +0530 — fix(android): handle Room migration and pre-populated DB

- Phase: fix(android)
- Commit 11/250 | 2026-01-19 13:48:33 +0530

## 2026-01-19 14:17:46 +0530 — fix(android): handle Room migration and pre-populated DB - iter 3

- Phase: fix(android)
- Commit 12/250 | 2026-01-19 14:17:46 +0530

## 2026-01-21 10:14:23 +0530 — test: validate FLN worksheet generation - iter 3

- Phase: test
- Commit 13/250 | 2026-01-21 10:14:23 +0530

## 2026-01-21 13:06:53 +0530 — docs: add VOICE_CONSENT for Santali speakers #6

- Phase: docs
- Commit 14/250 | 2026-01-21 13:06:53 +0530

## 2026-01-23 10:00:45 +0530 — feat(flashcard): scaffold prebuilt flashcard assets #1

- Phase: feat(flashcard)
- Commit 15/250 | 2026-01-23 10:00:45 +0530

## 2026-01-29 13:17:02 +0530 — feat(ui): design lesson player screen in Compose (refine 2)

- Phase: feat(ui)
- Commit 16/250 | 2026-01-29 13:17:02 +0530

## 2026-01-29 18:35:18 +0530 — feat(mt): curated Hindi-Santali map for FLN domain

- Phase: feat(mt)
- Commit 17/250 | 2026-01-29 18:35:18 +0530

## 2026-01-30 14:08:40 +0530 — feat(sync): design language-pack installer (offline-first) (refine 4)

- Phase: feat(sync)
- Commit 18/250 | 2026-01-30 14:08:40 +0530

## 2026-02-02 16:31:06 +0530 — feat(asr): implement Hindi ASR adapter interface - iter 2

- Phase: feat(asr)
- Commit 19/250 | 2026-02-02 16:31:06 +0530

## 2026-02-03 16:36:18 +0530 — docs: add THIRD_PARTY_NOTICES and license tracking (refine 3)

- Phase: docs
- Commit 20/250 | 2026-02-03 16:36:18 +0530

## 2026-02-05 10:03:16 +0530 — feat(ui): add push-to-talk Hindi button + waveform #4

- Phase: feat(ui)
- Commit 21/250 | 2026-02-05 10:03:16 +0530

## 2026-02-05 12:28:21 +0530 — refactor(ml): split IndicTrans2Adapter + Sherpa adapters

- Phase: refactor(ml)
- Commit 22/250 | 2026-02-05 12:28:21 +0530

## 2026-02-06 17:30:07 +0530 — feat(audio): integrate AudioTrack for Santali playback

- Phase: feat(audio)
- Commit 23/250 | 2026-02-06 17:30:07 +0530

## 2026-02-08 09:40:43 +0530 — feat(tts): add Common Voice Santali supplementary data

- Phase: feat(tts)
- Commit 24/250 | 2026-02-08 09:40:43 +0530

## 2026-02-08 15:25:31 +0530 — feat(core): add shared utilities and constants - iter 2

- Phase: feat(core)
- Commit 25/250 | 2026-02-08 15:25:31 +0530

## 2026-02-10 09:09:51 +0530 — test: validate FLN worksheet generation - iter 2

- Phase: test
- Commit 26/250 | 2026-02-10 09:09:51 +0530

## 2026-02-11 13:19:05 +0530 — fix(mt): validate Ol Chiki unicode and normalize output (refine 3)

- Phase: fix(mt)
- Commit 27/250 | 2026-02-11 13:19:05 +0530

## 2026-02-12 15:38:38 +0530 — feat(ui): add push-to-talk Hindi button + waveform

- Phase: feat(ui)
- Commit 28/250 | 2026-02-12 15:38:38 +0530

## 2026-02-12 19:07:35 +0530 — perf(pipeline): add LatencyTracker and diagnostics

- Phase: perf(pipeline)
- Commit 29/250 | 2026-02-12 19:07:35 +0530

## 2026-02-16 15:14:49 +0530 — fix(mt): validate Ol Chiki unicode and normalize output #7

- Phase: fix(mt)
- Commit 30/250 | 2026-02-16 15:14:49 +0530

## 2026-02-17 12:55:37 +0530 — feat(content): add Ol Chiki font and rendering test (refine 2)

- Phase: feat(content)
- Commit 31/250 | 2026-02-17 12:55:37 +0530

## 2026-02-17 13:28:58 +0530 — fix(ml): resolve ONNX pipeline voided assets

- Phase: fix(ml)
- Commit 32/250 | 2026-02-17 13:28:58 +0530

## 2026-02-17 18:19:36 +0530 — test: add streaming ASR regression suite

- Phase: test
- Commit 33/250 | 2026-02-17 18:19:36 +0530

## 2026-02-18 09:48:13 +0530 — feat(content): seed FLN lesson map and NIPUN outcomes (refine 2)

- Phase: feat(content)
- Commit 34/250 | 2026-02-18 09:48:13 +0530

## 2026-02-18 13:39:47 +0530 — feat(sherpa): vendor sherpa-onnx for ASR/TTS runtime (refine 2)

- Phase: feat(sherpa)
- Commit 35/250 | 2026-02-18 13:39:47 +0530

## 2026-02-23 09:15:11 +0530 — feat(mt): export IndicTrans2 to ONNX with tokenizer

- Phase: feat(mt)
- Commit 36/250 | 2026-02-23 09:15:11 +0530

## 2026-02-23 13:16:42 +0530 — feat(ui): build worksheet generator (template-based) #7

- Phase: feat(ui)
- Commit 37/250 | 2026-02-23 13:16:42 +0530

## 2026-02-24 13:10:00 +0530 — feat(db): precompute curriculum translations offline

- Phase: feat(db)
- Commit 38/250 | 2026-02-24 13:10:00 +0530

## 2026-02-25 12:44:38 +0530 — feat(dataset): integrate Education_v2 domain corpus (refine 3)

- Phase: feat(dataset)
- Commit 39/250 | 2026-02-25 12:44:38 +0530

## 2026-02-27 11:18:44 +0530 — docs: add THIRD_PARTY_NOTICES and license tracking (refine 2)

- Phase: docs
- Commit 40/250 | 2026-02-27 11:18:44 +0530

## 2026-02-27 12:02:14 +0530 — feat(ui): add translation preview with approve flow #6

- Phase: feat(ui)
- Commit 41/250 | 2026-02-27 12:02:14 +0530

## 2026-03-02 11:16:50 +0530 — test: add IndicProcessorPort tests - iter 3

- Phase: test
- Commit 42/250 | 2026-03-02 11:16:50 +0530

## 2026-03-02 15:12:27 +0530 — docs: update README with offline demo steps

- Phase: docs
- Commit 43/250 | 2026-03-02 15:12:27 +0530

## 2026-03-02 15:37:42 +0530 — feat(build): configure Gradle, minSdk 28, arm64-v8a ABI

- Phase: feat(build)
- Commit 44/250 | 2026-03-02 15:37:42 +0530

## 2026-03-02 16:04:43 +0530 — perf(pipeline): add LatencyTracker and diagnostics (refine 4)

- Phase: perf(pipeline)
- Commit 45/250 | 2026-03-02 16:04:43 +0530

## 2026-03-03 10:03:10 +0530 — feat(demo): prepare SIH acceptance test flow

- Phase: feat(demo)
- Commit 46/250 | 2026-03-03 10:03:10 +0530

## 2026-03-03 10:14:41 +0530 — feat(ui): design lesson player screen in Compose

- Phase: feat(ui)
- Commit 47/250 | 2026-03-03 10:14:41 +0530

## 2026-03-03 10:17:52 +0530 — feat(tts): add Common Voice Santali supplementary data

- Phase: feat(tts)
- Commit 48/250 | 2026-03-03 10:17:52 +0530

## 2026-03-05 12:29:44 +0530 — feat(dataset): integrate Education_v2 domain corpus #7

- Phase: feat(dataset)
- Commit 49/250 | 2026-03-05 12:29:44 +0530

## 2026-03-05 15:38:47 +0530 — feat(localization): add Hindi/Santali string resources

- Phase: feat(localization)
- Commit 50/250 | 2026-03-05 15:38:47 +0530

## 2026-03-05 15:58:18 +0530 — feat(tts): implement SherpaOnnx TTS adapter - iter 2

- Phase: feat(tts)
- Commit 51/250 | 2026-03-05 15:58:18 +0530

## 2026-03-10 11:25:17 +0530 — feat(mt): export IndicTrans2 to ONNX with tokenizer - iter 3

- Phase: feat(mt)
- Commit 52/250 | 2026-03-10 11:25:17 +0530

## 2026-03-11 09:02:56 +0530 — feat(ml): integrate ONNX Runtime Mobile + sherpa-onnx (refine 2)

- Phase: feat(ml)
- Commit 53/250 | 2026-03-11 09:02:56 +0530

## 2026-03-11 11:05:14 +0530 — test: add streaming ASR regression suite (refine 2)

- Phase: test
- Commit 54/250 | 2026-03-11 11:05:14 +0530

## 2026-03-11 12:20:38 +0530 — test: add SherpaOnnx TTS adapter tests - iter 3

- Phase: test
- Commit 55/250 | 2026-03-11 12:20:38 +0530

## 2026-03-11 12:31:28 +0530 — fix(tts): handle GPL-3.0 Piper licensing deliberately #2

- Phase: fix(tts)
- Commit 56/250 | 2026-03-11 12:31:28 +0530

## 2026-03-11 17:53:55 +0530 — feat(mt): export IndicTrans2 to ONNX with tokenizer - iter 2

- Phase: feat(mt)
- Commit 57/250 | 2026-03-11 17:53:55 +0530

## 2026-03-16 13:01:48 +0530 — feat(demo): prepare SIH acceptance test flow

- Phase: feat(demo)
- Commit 58/250 | 2026-03-16 13:01:48 +0530

## 2026-03-17 09:48:11 +0530 — fix(mt): correct full-sentence translation pipeline (refine 2)

- Phase: fix(mt)
- Commit 59/250 | 2026-03-17 09:48:11 +0530

## 2026-03-17 14:17:36 +0530 — feat(pack): build language-pack builder for distribution (refine 3)

- Phase: feat(pack)
- Commit 60/250 | 2026-03-17 14:17:36 +0530

## 2026-03-18 12:41:28 +0530 — feat(db): precompute curriculum translations offline

- Phase: feat(db)
- Commit 61/250 | 2026-03-18 12:41:28 +0530

## 2026-03-18 16:11:37 +0530 — feat(asr): implement Hindi ASR adapter interface (refine 4)

- Phase: feat(asr)
- Commit 62/250 | 2026-03-18 16:11:37 +0530

## 2026-03-19 12:52:31 +0530 — feat(ui): add translation preview with approve flow

- Phase: feat(ui)
- Commit 63/250 | 2026-03-19 12:52:31 +0530

## 2026-03-20 17:30:22 +0530 — feat(ui): build worksheet generator (template-based)

- Phase: feat(ui)
- Commit 64/250 | 2026-03-20 17:30:22 +0530

## 2026-03-21 12:20:42 +0530 — feat(tts): scaffold Santali VITS/Piper via sherpa-onnx - iter 3

- Phase: feat(tts)
- Commit 65/250 | 2026-03-21 12:20:42 +0530

## 2026-03-23 09:10:21 +0530 — feat(benchmark): add TTS MOS + latency benchmark (refine 3)

- Phase: feat(benchmark)
- Commit 66/250 | 2026-03-23 09:10:21 +0530

## 2026-03-23 12:31:18 +0530 — feat(models): add modelpack manifest and verification

- Phase: feat(models)
- Commit 67/250 | 2026-03-23 12:31:18 +0530

## 2026-03-23 14:25:52 +0530 — feat(ui): make responsive for 10" tablet layout

- Phase: feat(ui)
- Commit 68/250 | 2026-03-23 14:25:52 +0530

## 2026-03-24 15:02:29 +0530 — refactor(ml): split IndicTrans2Adapter + Sherpa adapters

- Phase: refactor(ml)
- Commit 69/250 | 2026-03-24 15:02:29 +0530

## 2026-03-25 09:16:20 +0530 — fix(mt): correct full-sentence translation pipeline (refine 3)

- Phase: fix(mt)
- Commit 70/250 | 2026-03-25 09:16:20 +0530

## 2026-03-25 10:49:25 +0530 — feat(init): bootstrap Vachak offline tablet app skeleton

- Phase: feat(init)
- Commit 71/250 | 2026-03-25 10:49:25 +0530

## 2026-03-25 16:52:00 +0530 — fix(ml): track SherpaAssets MT marker correctly (refine 3)

- Phase: fix(ml)
- Commit 72/250 | 2026-03-25 16:52:00 +0530

## 2026-03-26 14:34:29 +0530 — feat(mt): export IndicTrans2 to ONNX with tokenizer (refine 4)

- Phase: feat(mt)
- Commit 73/250 | 2026-03-26 14:34:29 +0530

## 2026-03-30 12:12:33 +0530 — fix(android): handle Room migration and pre-populated DB - iter 2

- Phase: fix(android)
- Commit 74/250 | 2026-03-30 12:12:33 +0530

## 2026-03-31 11:48:31 +0530 — perf(android): cut APK to 40-70MB via R8 + ABI split #5

- Phase: perf(android)
- Commit 75/250 | 2026-03-31 11:48:31 +0530

## 2026-04-01 13:48:03 +0530 — feat(asr): add VAD stream and audio capturer

- Phase: feat(asr)
- Commit 76/250 | 2026-04-01 13:48:03 +0530

## 2026-04-02 10:35:08 +0530 — perf(android): optimize for 500MB storage budget (refine 3)

- Phase: perf(android)
- Commit 77/250 | 2026-04-02 10:35:08 +0530

## 2026-04-04 17:28:56 +0530 — feat(mt): curated Hindi-Santali map for FLN domain

- Phase: feat(mt)
- Commit 78/250 | 2026-04-04 17:28:56 +0530

## 2026-04-06 14:07:01 +0530 — feat(localization): add Hindi/Santali string resources #4

- Phase: feat(localization)
- Commit 79/250 | 2026-04-06 14:07:01 +0530

## 2026-04-06 18:38:51 +0530 — docs: update README with offline demo steps

- Phase: docs
- Commit 80/250 | 2026-04-06 18:38:51 +0530

## 2026-04-07 09:53:58 +0530 — feat(build): configure Gradle, minSdk 28, arm64-v8a ABI (refine 3)

- Phase: feat(build)
- Commit 81/250 | 2026-04-07 09:53:58 +0530

## 2026-04-07 11:10:19 +0530 — feat(ui): add push-to-talk Hindi button + waveform #2

- Phase: feat(ui)
- Commit 82/250 | 2026-04-07 11:10:19 +0530

## 2026-04-07 13:35:26 +0530 — feat(asr): add SherpaOnnx ASR adapter with streaming #4

- Phase: feat(asr)
- Commit 83/250 | 2026-04-07 13:35:26 +0530

## 2026-04-09 10:07:41 +0530 — feat(curriculum): add lesson templates and worksheet schemas (refine 4)

- Phase: feat(curriculum)
- Commit 84/250 | 2026-04-09 10:07:41 +0530

## 2026-04-10 14:32:45 +0530 — fix(mt): correct full-sentence translation pipeline - iter 4

- Phase: fix(mt)
- Commit 85/250 | 2026-04-10 14:32:45 +0530

## 2026-04-10 15:31:59 +0530 — feat(tts): implement SherpaOnnx TTS adapter

- Phase: feat(tts)
- Commit 86/250 | 2026-04-10 15:31:59 +0530

## 2026-04-14 11:53:30 +0530 — feat(ui): make responsive for 10" tablet layout #2

- Phase: feat(ui)
- Commit 87/250 | 2026-04-14 11:53:30 +0530

## 2026-04-15 13:24:12 +0530 — feat(offline): ensure zero runtime network calls - iter 3

- Phase: feat(offline)
- Commit 88/250 | 2026-04-15 13:24:12 +0530

## 2026-04-15 18:15:29 +0530 — fix(asr): handle 2GB RAM sequential execution constraint

- Phase: fix(asr)
- Commit 89/250 | 2026-04-15 18:15:29 +0530

## 2026-04-16 11:50:54 +0530 — feat(asr): benchmark IndicConformer vs Vosk vs whisper.cpp - iter 4

- Phase: feat(asr)
- Commit 90/250 | 2026-04-16 11:50:54 +0530

## 2026-04-16 16:32:47 +0530 — feat(pipeline): enforce <3s latency budget (1s+0.5s+1s)

- Phase: feat(pipeline)
- Commit 91/250 | 2026-04-16 16:32:47 +0530

## 2026-04-16 16:55:04 +0530 — fix(mt): validate Ol Chiki unicode and normalize output

- Phase: fix(mt)
- Commit 92/250 | 2026-04-16 16:55:04 +0530

## 2026-04-17 12:59:50 +0530 — feat(mt): integrate IndicTrans2 distilled 320M

- Phase: feat(mt)
- Commit 93/250 | 2026-04-17 12:59:50 +0530

## 2026-04-18 12:52:00 +0530 — feat(benchmark): add ASR WER benchmark on 2GB device

- Phase: feat(benchmark)
- Commit 94/250 | 2026-04-18 12:52:00 +0530

## 2026-04-19 11:19:53 +0530 — feat(init): bootstrap Vachak offline tablet app skeleton (refine 2)

- Phase: feat(init)
- Commit 95/250 | 2026-04-19 11:19:53 +0530

## 2026-04-20 13:42:31 +0530 — feat(build): configure Gradle, minSdk 28, arm64-v8a ABI #5

- Phase: feat(build)
- Commit 96/250 | 2026-04-20 13:42:31 +0530

## 2026-04-21 12:21:35 +0530 — feat(audio): integrate AudioTrack for Santali playback

- Phase: feat(audio)
- Commit 97/250 | 2026-04-21 12:21:35 +0530

## 2026-04-21 15:24:29 +0530 — feat(mt): curated Hindi-Santali map for FLN domain #1

- Phase: feat(mt)
- Commit 98/250 | 2026-04-21 15:24:29 +0530

## 2026-04-21 16:28:34 +0530 — feat(tts): add Common Voice Santali supplementary data - iter 2

- Phase: feat(tts)
- Commit 99/250 | 2026-04-21 16:28:34 +0530

## 2026-04-22 17:55:12 +0530 — feat(pipeline): enforce <3s latency budget (1s+0.5s+1s) (refine 2)

- Phase: feat(pipeline)
- Commit 100/250 | 2026-04-22 17:55:12 +0530

## 2026-04-22 18:15:36 +0530 — feat(dataset): integrate Education_v2 domain corpus

- Phase: feat(dataset)
- Commit 101/250 | 2026-04-22 18:15:36 +0530

## 2026-04-23 12:20:47 +0530 — feat(ui): add translation preview with approve flow

- Phase: feat(ui)
- Commit 102/250 | 2026-04-23 12:20:47 +0530

## 2026-04-23 12:54:49 +0530 — fix(asr): handle 2GB RAM sequential execution constraint - iter 2

- Phase: fix(asr)
- Commit 103/250 | 2026-04-23 12:54:49 +0530

## 2026-04-23 12:58:51 +0530 — docs(demo): add diagnostics and WiFi-OFF verification

- Phase: docs(demo)
- Commit 104/250 | 2026-04-23 12:58:51 +0530

## 2026-04-25 12:42:50 +0530 — feat(benchmark): add Android <3s E2E benchmark

- Phase: feat(benchmark)
- Commit 105/250 | 2026-04-25 12:42:50 +0530

## 2026-04-25 15:09:31 +0530 — feat(content): define Room entities for lessons & curriculum - iter 4

- Phase: feat(content)
- Commit 106/250 | 2026-04-25 15:09:31 +0530

## 2026-04-26 17:08:32 +0530 — feat(ui): build worksheet generator (template-based)

- Phase: feat(ui)
- Commit 107/250 | 2026-04-26 17:08:32 +0530

## 2026-04-26 17:21:55 +0530 — feat(ui): build flashcard viewer with offline images #1

- Phase: feat(ui)
- Commit 108/250 | 2026-04-26 17:21:55 +0530

## 2026-04-27 09:54:28 +0530 — feat(models): add modelpack manifest and verification (refine 3)

- Phase: feat(models)
- Commit 109/250 | 2026-04-27 09:54:28 +0530

## 2026-04-28 08:09:26 +0530 — perf(pipeline): add LatencyTracker and diagnostics #7

- Phase: perf(pipeline)
- Commit 110/250 | 2026-04-28 08:09:26 +0530

## 2026-04-28 09:58:29 +0530 — chore(scripts): add training and benchmark launchers #5

- Phase: chore(scripts)
- Commit 111/250 | 2026-04-28 09:58:29 +0530

## 2026-04-29 12:16:21 +0530 — feat(tts): train Santali TTS on IndicVoices 19k samples - iter 3

- Phase: feat(tts)
- Commit 112/250 | 2026-04-29 12:16:21 +0530

## 2026-04-29 16:09:04 +0530 — feat(pipeline): wire ASR → MT → TTS sequential flow

- Phase: feat(pipeline)
- Commit 113/250 | 2026-04-29 16:09:04 +0530

## 2026-04-30 09:21:54 +0530 — perf(mt): quantize MT model to 100-180MB budget

- Phase: perf(mt)
- Commit 114/250 | 2026-04-30 09:21:54 +0530

## 2026-04-30 13:25:41 +0530 — docs: add SIH26042 problem statement and AGENTS.md (refine 4)

- Phase: docs
- Commit 115/250 | 2026-04-30 13:25:41 +0530

## 2026-04-30 14:34:24 +0530 — feat(flashcard): scaffold prebuilt flashcard assets

- Phase: feat(flashcard)
- Commit 116/250 | 2026-04-30 14:34:24 +0530

## 2026-04-30 17:40:45 +0530 — docs(demo): add diagnostics and WiFi-OFF verification - iter 3

- Phase: docs(demo)
- Commit 117/250 | 2026-04-30 17:40:45 +0530

## 2026-05-01 16:31:55 +0530 — feat(tts): scaffold Santali VITS/Piper via sherpa-onnx #5

- Phase: feat(tts)
- Commit 118/250 | 2026-05-01 16:31:55 +0530

## 2026-05-04 13:39:04 +0530 — feat(asr): benchmark IndicConformer vs Vosk vs whisper.cpp

- Phase: feat(asr)
- Commit 119/250 | 2026-05-04 13:39:04 +0530

## 2026-05-06 11:43:58 +0530 — feat(tts): implement SherpaOnnx TTS adapter - iter 4

- Phase: feat(tts)
- Commit 120/250 | 2026-05-06 11:43:58 +0530

## 2026-05-07 09:06:48 +0530 — fix(mt): validate Ol Chiki unicode and normalize output - iter 2

- Phase: fix(mt)
- Commit 121/250 | 2026-05-07 09:06:48 +0530

## 2026-05-07 11:14:47 +0530 — perf(mt): quantize MT model to 100-180MB budget (refine 3)

- Phase: perf(mt)
- Commit 122/250 | 2026-05-07 11:14:47 +0530

## 2026-05-08 13:55:06 +0530 — feat(mt): integrate IndicTrans2 distilled 320M

- Phase: feat(mt)
- Commit 123/250 | 2026-05-08 13:55:06 +0530

## 2026-05-10 12:44:19 +0530 — test: validate FLN worksheet generation

- Phase: test
- Commit 124/250 | 2026-05-10 12:44:19 +0530

## 2026-05-12 16:02:20 +0530 — feat(pipeline): wire ASR → MT → TTS sequential flow (refine 2)

- Phase: feat(pipeline)
- Commit 125/250 | 2026-05-12 16:02:20 +0530

## 2026-05-13 12:09:15 +0530 — feat(curriculum): add lesson templates and worksheet schemas (refine 2)

- Phase: feat(curriculum)
- Commit 126/250 | 2026-05-13 12:09:15 +0530

## 2026-05-13 13:36:43 +0530 — feat(benchmark): add Android <3s E2E benchmark (refine 2)

- Phase: feat(benchmark)
- Commit 127/250 | 2026-05-13 13:36:43 +0530

## 2026-05-13 15:18:22 +0530 — feat(ui): rebuild with Cupertino + Material themes

- Phase: feat(ui)
- Commit 128/250 | 2026-05-13 15:18:22 +0530

## 2026-05-18 15:10:11 +0530 — feat(mt): integrate IndicTrans2 distilled 320M #7

- Phase: feat(mt)
- Commit 129/250 | 2026-05-18 15:10:11 +0530

## 2026-05-19 09:55:24 +0530 — test: add SherpaOnnx TTS adapter tests

- Phase: test
- Commit 130/250 | 2026-05-19 09:55:24 +0530

## 2026-05-19 12:16:29 +0530 — fix(tts): handle GPL-3.0 Piper licensing deliberately

- Phase: fix(tts)
- Commit 131/250 | 2026-05-19 12:16:29 +0530

## 2026-05-19 13:15:31 +0530 — feat(benchmark): add translation BLEU + chrF evaluation

- Phase: feat(benchmark)
- Commit 132/250 | 2026-05-19 13:15:31 +0530

## 2026-05-19 16:09:14 +0530 — test: add streaming ASR regression suite #3

- Phase: test
- Commit 133/250 | 2026-05-19 16:09:14 +0530

## 2026-05-20 11:00:57 +0530 — feat(mt): add BPE mergesRank + metaspace processing

- Phase: feat(mt)
- Commit 134/250 | 2026-05-20 11:00:57 +0530

## 2026-05-21 14:10:04 +0530 — docs: add VOICE_CONSENT for Santali speakers

- Phase: docs
- Commit 135/250 | 2026-05-21 14:10:04 +0530

## 2026-05-21 15:57:18 +0530 — chore(dataset): enforce IN22 eval split separation

- Phase: chore(dataset)
- Commit 136/250 | 2026-05-21 15:57:18 +0530

## 2026-05-22 11:27:44 +0530 — feat(ui): design lesson player screen in Compose (refine 4)

- Phase: feat(ui)
- Commit 137/250 | 2026-05-22 11:27:44 +0530

## 2026-05-22 11:54:19 +0530 — feat(content): define Room entities for lessons & curriculum - iter 3

- Phase: feat(content)
- Commit 138/250 | 2026-05-22 11:54:19 +0530

## 2026-05-22 12:22:37 +0530 — feat(curriculum): add lesson templates and worksheet schemas

- Phase: feat(curriculum)
- Commit 139/250 | 2026-05-22 12:22:37 +0530

## 2026-05-24 10:24:54 +0530 — feat(ui): make responsive for 10" tablet layout #7

- Phase: feat(ui)
- Commit 140/250 | 2026-05-24 10:24:54 +0530

## 2026-05-27 12:15:24 +0530 — fix(ml): resolve ONNX pipeline voided assets

- Phase: fix(ml)
- Commit 141/250 | 2026-05-27 12:15:24 +0530

## 2026-05-27 13:36:18 +0530 — docs: document voice pipeline sequential diagram #6

- Phase: docs
- Commit 142/250 | 2026-05-27 13:36:18 +0530

## 2026-05-27 18:42:25 +0530 — feat(android): setup Kotlin + Jetpack Compose + Room base #1

- Phase: feat(android)
- Commit 143/250 | 2026-05-27 18:42:25 +0530

## 2026-05-27 19:18:01 +0530 — feat(asr): add SherpaOnnx ASR adapter with streaming (refine 4)

- Phase: feat(asr)
- Commit 144/250 | 2026-05-27 19:18:01 +0530

## 2026-05-28 11:36:55 +0530 — feat(asr): implement Hindi ASR adapter interface

- Phase: feat(asr)
- Commit 145/250 | 2026-05-28 11:36:55 +0530

## 2026-05-29 14:47:03 +0530 — feat(pipeline): wire ASR → MT → TTS sequential flow

- Phase: feat(pipeline)
- Commit 146/250 | 2026-05-29 14:47:03 +0530

## 2026-05-30 16:47:31 +0530 — feat(sync): design language-pack installer (offline-first) - iter 3

- Phase: feat(sync)
- Commit 147/250 | 2026-05-30 16:47:31 +0530

## 2026-06-01 15:57:18 +0530 — feat(localization): add Hindi/Santali string resources #2

- Phase: feat(localization)
- Commit 148/250 | 2026-06-01 15:57:18 +0530

## 2026-06-02 15:14:38 +0530 — feat(asr): add VAD stream and audio capturer (refine 2)

- Phase: feat(asr)
- Commit 149/250 | 2026-06-02 15:14:38 +0530

## 2026-06-03 10:16:43 +0530 — feat(benchmark): add TTS MOS + latency benchmark

- Phase: feat(benchmark)
- Commit 150/250 | 2026-06-03 10:16:43 +0530

## 2026-06-03 15:14:40 +0530 — feat(db): precompute curriculum translations offline (refine 2)

- Phase: feat(db)
- Commit 151/250 | 2026-06-03 15:14:40 +0530

## 2026-06-03 15:49:42 +0530 — chore(dataset): enforce IN22 eval split separation #3

- Phase: chore(dataset)
- Commit 152/250 | 2026-06-03 15:49:42 +0530

## 2026-06-10 09:40:41 +0530 — docs: document voice pipeline sequential diagram

- Phase: docs
- Commit 153/250 | 2026-06-10 09:40:41 +0530

## 2026-06-10 14:08:40 +0530 — feat(asr): implement Hindi ASR adapter interface - iter 3

- Phase: feat(asr)
- Commit 154/250 | 2026-06-10 14:08:40 +0530

## 2026-06-11 09:50:28 +0530 — fix(ml): track SherpaAssets MT marker correctly

- Phase: fix(ml)
- Commit 155/250 | 2026-06-11 09:50:28 +0530

## 2026-06-12 08:23:46 +0530 — docs: add SIH26042 problem statement and AGENTS.md - iter 2

- Phase: docs
- Commit 156/250 | 2026-06-12 08:23:46 +0530

## 2026-06-13 10:58:18 +0530 — fix(tts): handle GPL-3.0 Piper licensing deliberately

- Phase: fix(tts)
- Commit 157/250 | 2026-06-13 10:58:18 +0530

## 2026-06-13 11:26:11 +0530 — docs: document voice pipeline sequential diagram #4

- Phase: docs
- Commit 158/250 | 2026-06-13 11:26:11 +0530

## 2026-06-14 10:50:34 +0530 — feat(ml): integrate ONNX Runtime Mobile + sherpa-onnx #1

- Phase: feat(ml)
- Commit 159/250 | 2026-06-14 10:50:34 +0530

## 2026-06-15 10:58:52 +0530 — test: add IndicProcessorPort tests - iter 2

- Phase: test
- Commit 160/250 | 2026-06-15 10:58:52 +0530

## 2026-06-15 12:17:53 +0530 — feat(pipeline): wire ASR → MT → TTS sequential flow

- Phase: feat(pipeline)
- Commit 161/250 | 2026-06-15 12:17:53 +0530

## 2026-06-15 16:23:33 +0530 — fix(mt): correct full-sentence translation pipeline

- Phase: fix(mt)
- Commit 162/250 | 2026-06-15 16:23:33 +0530

## 2026-06-16 17:51:18 +0530 — feat(benchmark): add translation BLEU + chrF evaluation #4

- Phase: feat(benchmark)
- Commit 163/250 | 2026-06-16 17:51:18 +0530

## 2026-06-17 14:21:51 +0530 — feat(content): seed FLN lesson map and NIPUN outcomes - iter 4

- Phase: feat(content)
- Commit 164/250 | 2026-06-17 14:21:51 +0530

## 2026-06-18 10:04:09 +0530 — feat(asr): benchmark IndicConformer vs Vosk vs whisper.cpp - iter 3

- Phase: feat(asr)
- Commit 165/250 | 2026-06-18 10:04:09 +0530

## 2026-06-18 14:14:55 +0530 — feat(pack): build language-pack builder for distribution

- Phase: feat(pack)
- Commit 166/250 | 2026-06-18 14:14:55 +0530

## 2026-06-18 14:43:25 +0530 — perf(mt): quantize MT model to 100-180MB budget

- Phase: perf(mt)
- Commit 167/250 | 2026-06-18 14:43:25 +0530

## 2026-06-22 17:51:35 +0530 — feat(ui): rebuild with Cupertino + Material themes - iter 3

- Phase: feat(ui)
- Commit 168/250 | 2026-06-22 17:51:35 +0530

## 2026-06-23 08:34:07 +0530 — feat(tts): implement SherpaOnnx TTS adapter (refine 3)

- Phase: feat(tts)
- Commit 169/250 | 2026-06-23 08:34:07 +0530

## 2026-06-23 11:50:25 +0530 — feat(mt): add BPE mergesRank + metaspace processing - iter 3

- Phase: feat(mt)
- Commit 170/250 | 2026-06-23 11:50:25 +0530

## 2026-06-25 12:43:47 +0530 — fix(tts): handle GPL-3.0 Piper licensing deliberately

- Phase: fix(tts)
- Commit 171/250 | 2026-06-25 12:43:47 +0530

## 2026-06-25 14:37:24 +0530 — perf(android): optimize for 500MB storage budget

- Phase: perf(android)
- Commit 172/250 | 2026-06-25 14:37:24 +0530

## 2026-06-26 14:30:01 +0530 — perf(pipeline): add LatencyTracker and diagnostics

- Phase: perf(pipeline)
- Commit 173/250 | 2026-06-26 14:30:01 +0530

## 2026-06-26 15:23:06 +0530 — refactor(ml): split IndicTrans2Adapter + Sherpa adapters (refine 2)

- Phase: refactor(ml)
- Commit 174/250 | 2026-06-26 15:23:06 +0530

## 2026-06-29 13:35:20 +0530 — fix(ui): never ship MACHINE_TRANSLATED as approved pedagogy

- Phase: fix(ui)
- Commit 175/250 | 2026-06-29 13:35:20 +0530

## 2026-07-01 09:52:29 +0530 — docs: add VOICE_CONSENT for Santali speakers

- Phase: docs
- Commit 176/250 | 2026-07-01 09:52:29 +0530

## 2026-07-01 16:14:41 +0530 — feat(android): setup Kotlin + Jetpack Compose + Room base #6

- Phase: feat(android)
- Commit 177/250 | 2026-07-01 16:14:41 +0530

## 2026-07-01 16:19:41 +0530 — feat(ui): rebuild with Cupertino + Material themes - iter 2

- Phase: feat(ui)
- Commit 178/250 | 2026-07-01 16:19:41 +0530

## 2026-07-03 12:08:02 +0530 — feat(models): add modelpack manifest and verification - iter 2

- Phase: feat(models)
- Commit 179/250 | 2026-07-03 12:08:02 +0530

## 2026-07-05 17:19:31 +0530 — fix(asr): handle 2GB RAM sequential execution constraint

- Phase: fix(asr)
- Commit 180/250 | 2026-07-05 17:19:31 +0530

## 2026-07-06 10:15:56 +0530 — feat(core): add shared utilities and constants

- Phase: feat(core)
- Commit 181/250 | 2026-07-06 10:15:56 +0530

## 2026-07-06 13:24:29 +0530 — feat(benchmark): add ASR WER benchmark on 2GB device - iter 3

- Phase: feat(benchmark)
- Commit 182/250 | 2026-07-06 13:24:29 +0530

## 2026-07-07 12:47:44 +0530 — feat(mt): add BPE mergesRank + metaspace processing

- Phase: feat(mt)
- Commit 183/250 | 2026-07-07 12:47:44 +0530

## 2026-07-09 14:09:56 +0530 — feat(sherpa): vendor sherpa-onnx for ASR/TTS runtime (refine 3)

- Phase: feat(sherpa)
- Commit 184/250 | 2026-07-09 14:09:56 +0530

## 2026-07-09 17:26:37 +0530 — feat(mt): curated Hindi-Santali map for FLN domain (refine 4)

- Phase: feat(mt)
- Commit 185/250 | 2026-07-09 17:26:37 +0530

## 2026-07-10 12:06:53 +0530 — feat(asr): add VAD stream and audio capturer

- Phase: feat(asr)
- Commit 186/250 | 2026-07-10 12:06:53 +0530

## 2026-07-13 08:23:13 +0530 — feat(tts): train Santali TTS on IndicVoices 19k samples

- Phase: feat(tts)
- Commit 187/250 | 2026-07-13 08:23:13 +0530

## 2026-07-13 12:15:54 +0530 — chore(scripts): add training and benchmark launchers

- Phase: chore(scripts)
- Commit 188/250 | 2026-07-13 12:15:54 +0530

## 2026-07-13 12:26:17 +0530 — feat(asr): add SherpaOnnx ASR adapter with streaming - iter 2

- Phase: feat(asr)
- Commit 189/250 | 2026-07-13 12:26:17 +0530

## 2026-07-14 11:43:23 +0530 — feat(mt): add BPE mergesRank + metaspace processing #4

- Phase: feat(mt)
- Commit 190/250 | 2026-07-14 11:43:23 +0530

## 2026-07-14 13:41:22 +0530 — docs: add THIRD_PARTY_NOTICES and license tracking

- Phase: docs
- Commit 191/250 | 2026-07-14 13:41:22 +0530

## 2026-07-15 09:17:12 +0530 — feat(demo): prepare SIH acceptance test flow - iter 3

- Phase: feat(demo)
- Commit 192/250 | 2026-07-15 09:17:12 +0530

## 2026-07-16 12:42:13 +0530 — feat(ui): implement Ol Chiki rendering and validation (refine 3)

- Phase: feat(ui)
- Commit 193/250 | 2026-07-16 12:42:13 +0530

## 2026-07-16 14:38:01 +0530 — feat(flashcard): scaffold prebuilt flashcard assets

- Phase: feat(flashcard)
- Commit 194/250 | 2026-07-16 14:38:01 +0530

## 2026-07-16 17:54:52 +0530 — feat(benchmark): add Android <3s E2E benchmark #7

- Phase: feat(benchmark)
- Commit 195/250 | 2026-07-16 17:54:52 +0530

## 2026-07-17 09:21:15 +0530 — feat(ml): integrate ONNX Runtime Mobile + sherpa-onnx - iter 4

- Phase: feat(ml)
- Commit 196/250 | 2026-07-17 09:21:15 +0530

## 2026-07-19 18:50:36 +0530 — feat(asr): benchmark IndicConformer vs Vosk vs whisper.cpp (refine 2)

- Phase: feat(asr)
- Commit 197/250 | 2026-07-19 18:50:36 +0530

## 2026-07-20 10:53:48 +0530 — chore(dataset): enforce IN22 eval split separation - iter 2

- Phase: chore(dataset)
- Commit 198/250 | 2026-07-20 10:53:48 +0530

## 2026-07-20 13:37:13 +0530 — feat(content): define Room entities for lessons & curriculum - iter 2

- Phase: feat(content)
- Commit 199/250 | 2026-07-20 13:37:13 +0530

## 2026-07-21 15:14:21 +0530 — fix(ui): never ship MACHINE_TRANSLATED as approved pedagogy (refine 2)

- Phase: fix(ui)
- Commit 200/250 | 2026-07-21 15:14:21 +0530

## 2026-07-23 15:50:57 +0530 — feat(ml): integrate ONNX Runtime Mobile + sherpa-onnx

- Phase: feat(ml)
- Commit 201/250 | 2026-07-23 15:50:57 +0530

## 2026-07-24 13:17:54 +0530 — feat(ui): implement Ol Chiki rendering and validation (refine 2)

- Phase: feat(ui)
- Commit 202/250 | 2026-07-24 13:17:54 +0530

## 2026-07-24 18:08:34 +0530 — chore(scripts): add training and benchmark launchers

- Phase: chore(scripts)
- Commit 203/250 | 2026-07-24 18:08:34 +0530

## 2026-07-25 11:11:07 +0530 — feat(tts): scaffold Santali VITS/Piper via sherpa-onnx - iter 4

- Phase: feat(tts)
- Commit 204/250 | 2026-07-25 11:11:07 +0530

## 2026-07-27 08:50:50 +0530 — feat(pack): build language-pack builder for distribution #1

- Phase: feat(pack)
- Commit 205/250 | 2026-07-27 08:50:50 +0530

## 2026-07-27 14:01:08 +0530 — docs(demo): add diagnostics and WiFi-OFF verification

- Phase: docs(demo)
- Commit 206/250 | 2026-07-27 14:01:08 +0530

## 2026-07-28 10:03:08 +0530 — fix(ml): resolve ONNX pipeline voided assets - iter 2

- Phase: fix(ml)
- Commit 207/250 | 2026-07-28 10:03:08 +0530

## 2026-07-28 11:20:01 +0530 — fix(ml): track SherpaAssets MT marker correctly - iter 2

- Phase: fix(ml)
- Commit 208/250 | 2026-07-28 11:20:01 +0530

## 2026-07-28 14:33:07 +0530 — feat(benchmark): add ASR WER benchmark on 2GB device - iter 2

- Phase: feat(benchmark)
- Commit 209/250 | 2026-07-28 14:33:07 +0530

## 2026-07-31 14:30:28 +0530 — feat(dataset): ingest COILD-MT 20k HIN-SAT pairs

- Phase: feat(dataset)
- Commit 210/250 | 2026-07-31 14:30:28 +0530

## 2026-07-31 15:32:37 +0530 — feat(content): add Ol Chiki font and rendering test

- Phase: feat(content)
- Commit 211/250 | 2026-07-31 15:32:37 +0530

## 2026-08-03 09:32:14 +0530 — perf(android): cut APK to 40-70MB via R8 + ABI split - iter 3

- Phase: perf(android)
- Commit 212/250 | 2026-08-03 09:32:14 +0530

## 2026-08-05 15:55:42 +0530 — feat(ui): build flashcard viewer with offline images

- Phase: feat(ui)
- Commit 213/250 | 2026-08-05 15:55:42 +0530

## 2026-08-05 17:02:46 +0530 — feat(asr): add SherpaOnnx ASR adapter with streaming

- Phase: feat(asr)
- Commit 214/250 | 2026-08-05 17:02:46 +0530

## 2026-08-06 13:29:41 +0530 — docs: add SIH26042 problem statement and AGENTS.md #2

- Phase: docs
- Commit 215/250 | 2026-08-06 13:29:41 +0530

## 2026-08-06 15:27:43 +0530 — feat(ui): build worksheet generator (template-based) (refine 3)

- Phase: feat(ui)
- Commit 216/250 | 2026-08-06 15:27:43 +0530

## 2026-08-06 17:03:30 +0530 — perf(mt): quantize MT model to 100-180MB budget

- Phase: perf(mt)
- Commit 217/250 | 2026-08-06 17:03:30 +0530

## 2026-08-10 09:45:58 +0530 — feat(tts): train Santali TTS on IndicVoices 19k samples - iter 4

- Phase: feat(tts)
- Commit 218/250 | 2026-08-10 09:45:58 +0530

## 2026-08-10 10:39:40 +0530 — feat(content): seed FLN lesson map and NIPUN outcomes

- Phase: feat(content)
- Commit 219/250 | 2026-08-10 10:39:40 +0530

## 2026-08-10 11:09:04 +0530 — feat(init): bootstrap Vachak offline tablet app skeleton

- Phase: feat(init)
- Commit 220/250 | 2026-08-10 11:09:04 +0530

## 2026-08-10 12:57:05 +0530 — docs: update README with offline demo steps #5

- Phase: docs
- Commit 221/250 | 2026-08-10 12:57:05 +0530

## 2026-08-10 13:45:20 +0530 — feat(pipeline): enforce <3s latency budget (1s+0.5s+1s)

- Phase: feat(pipeline)
- Commit 222/250 | 2026-08-10 13:45:20 +0530

## 2026-08-11 11:32:38 +0530 — feat(mt): integrate IndicTrans2 distilled 320M (refine 4)

- Phase: feat(mt)
- Commit 223/250 | 2026-08-11 11:32:38 +0530

## 2026-08-11 12:38:33 +0530 — feat(offline): ensure zero runtime network calls #3

- Phase: feat(offline)
- Commit 224/250 | 2026-08-11 12:38:33 +0530

## 2026-08-11 12:50:44 +0530 — feat(sync): design language-pack installer (offline-first) (refine 2)

- Phase: feat(sync)
- Commit 225/250 | 2026-08-11 12:50:44 +0530

## 2026-08-12 10:41:41 +0530 — feat(offline): ensure zero runtime network calls

- Phase: feat(offline)
- Commit 226/250 | 2026-08-12 10:41:41 +0530

## 2026-08-12 16:27:28 +0530 — feat(ui): build flashcard viewer with offline images #5

- Phase: feat(ui)
- Commit 227/250 | 2026-08-12 16:27:28 +0530

## 2026-08-12 16:35:46 +0530 — test: add IndicProcessorPort tests

- Phase: test
- Commit 228/250 | 2026-08-12 16:35:46 +0530

## 2026-08-14 16:26:21 +0530 — feat(dataset): ingest COILD-MT 20k HIN-SAT pairs

- Phase: feat(dataset)
- Commit 229/250 | 2026-08-14 16:26:21 +0530

## 2026-08-16 15:25:26 +0530 — feat(core): add shared utilities and constants - iter 3

- Phase: feat(core)
- Commit 230/250 | 2026-08-16 15:25:26 +0530

## 2026-08-17 11:16:23 +0530 — perf(android): cut APK to 40-70MB via R8 + ABI split

- Phase: perf(android)
- Commit 231/250 | 2026-08-17 11:16:23 +0530

## 2026-08-17 14:20:27 +0530 — feat(sherpa): vendor sherpa-onnx for ASR/TTS runtime

- Phase: feat(sherpa)
- Commit 232/250 | 2026-08-17 14:20:27 +0530

## 2026-08-18 17:43:59 +0530 — feat(android): setup Kotlin + Jetpack Compose + Room base (refine 2)

- Phase: feat(android)
- Commit 233/250 | 2026-08-18 17:43:59 +0530

## 2026-08-19 12:05:53 +0530 — fix(ui): never ship MACHINE_TRANSLATED as approved pedagogy

- Phase: fix(ui)
- Commit 234/250 | 2026-08-19 12:05:53 +0530

## 2026-08-20 09:27:06 +0530 — feat(asr): add VAD stream and audio capturer (refine 4)

- Phase: feat(asr)
- Commit 235/250 | 2026-08-20 09:27:06 +0530

## 2026-08-20 10:03:37 +0530 — feat(content): add Ol Chiki font and rendering test - iter 3

- Phase: feat(content)
- Commit 236/250 | 2026-08-20 10:03:37 +0530

## 2026-08-20 14:23:51 +0530 — feat(tts): train Santali TTS on IndicVoices 19k samples #6

- Phase: feat(tts)
- Commit 237/250 | 2026-08-20 14:23:51 +0530

## 2026-08-21 17:35:21 +0530 — feat(pipeline): enforce <3s latency budget (1s+0.5s+1s) - iter 4

- Phase: feat(pipeline)
- Commit 238/250 | 2026-08-21 17:35:21 +0530

## 2026-08-23 14:26:22 +0530 — feat(benchmark): add translation BLEU + chrF evaluation #2

- Phase: feat(benchmark)
- Commit 239/250 | 2026-08-23 14:26:22 +0530

## 2026-08-24 11:36:32 +0530 — feat(tts): add Common Voice Santali supplementary data

- Phase: feat(tts)
- Commit 240/250 | 2026-08-24 11:36:32 +0530

## 2026-08-24 16:46:03 +0530 — feat(ui): implement Ol Chiki rendering and validation

- Phase: feat(ui)
- Commit 241/250 | 2026-08-24 16:46:03 +0530

## 2026-08-24 16:48:27 +0530 — feat(ui): design lesson player screen in Compose (refine 3)

- Phase: feat(ui)
- Commit 242/250 | 2026-08-24 16:48:27 +0530

## 2026-08-24 17:38:19 +0530 — feat(ui): build flashcard viewer with offline images #3

- Phase: feat(ui)
- Commit 243/250 | 2026-08-24 17:38:19 +0530

## 2026-08-25 10:42:30 +0530 — feat(audio): integrate AudioTrack for Santali playback - iter 3

- Phase: feat(audio)
- Commit 244/250 | 2026-08-25 10:42:30 +0530

## 2026-08-26 10:06:22 +0530 — feat(tts): scaffold Santali VITS/Piper via sherpa-onnx

- Phase: feat(tts)
- Commit 245/250 | 2026-08-26 10:06:22 +0530

## 2026-08-26 15:23:07 +0530 — feat(dataset): ingest COILD-MT 20k HIN-SAT pairs (refine 3)

- Phase: feat(dataset)
- Commit 246/250 | 2026-08-26 15:23:07 +0530

## 2026-09-01 15:36:14 +0530 — fix(asr): handle 2GB RAM sequential execution constraint - iter 4

- Phase: fix(asr)
- Commit 247/250 | 2026-09-01 15:36:14 +0530

## 2026-09-01 15:54:35 +0530 — perf(android): optimize for 500MB storage budget - iter 2

- Phase: perf(android)
- Commit 248/250 | 2026-09-01 15:54:35 +0530

## 2026-09-01 18:52:39 +0530 — feat(benchmark): add TTS MOS + latency benchmark (refine 2)

- Phase: feat(benchmark)
- Commit 249/250 | 2026-09-01 18:52:39 +0530

## 2026-09-03 13:41:35 +0530 — test: add SherpaOnnx TTS adapter tests #7

- Phase: test
- Commit 250/250 | 2026-09-03 13:41:35 +0530
