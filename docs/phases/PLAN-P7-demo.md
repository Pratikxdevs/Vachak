# PLAN — P7: Full Demo Acceptance Run (Offline)

**Goal:** Execute the SIH demo acceptance test end-to-end with WiFi OFF and zero hidden network.

**Context:**
- AGENTS.md demo script: app opens → lesson loads → WiFi OFF → translate → play Santali audio
  → worksheet → flashcards → push-to-talk Hindi → show recognized text → show Santari → play
  audio → show <3 s latency → diagnostics. No hidden internet.

**Tasks:**
1. Pre-condition: install language pack (P5), confirm no `INTERNET` permission in manifest.
2. Walk the full script on a real 2 GB device, WiFi + data OFF.
3. Capture screens + `LatencyTracker` + `logcat -s Vachak-*` as evidence.
4. Verify each step: translate (P1), audible Mundari (P2), ASR text (P3), curriculum (P4),
   <3 s (P6), diagnostics.
5. Write `docs/demo-acceptance.md` with evidence; confirm license/NOTICE completeness.

**Acceptance:** Every demo step passes offline; acceptance doc signed off.
