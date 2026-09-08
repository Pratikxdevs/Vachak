# Phase 5: Language-Pack + Offline Installer — Context

**Gathered:** 2026-08-29
**Status:** Ready for planning
**Source:** AGENTS.md sync/ is installer not client, docs/PHASES.md Phase 5

<domain>
## Phase Boundary

Signed offline packs (MT+TTS+ASR models + curriculum) built by packages/ and installed by android/sync/ without network. Covers pack builder, manifest (hash+licenses), installer, Manage Packs UI, pack-path config for adapters (enables P2 swap). Does not include model training (P1/P2) or benchmarking (P6).
</domain>

<decisions>
## Implementation Decisions

- sync/ is pack installer (reads from storage / USB / sideload), never does network I/O, no INTERNET permission
- packages/ emits versioned .vachakpack (zip) with manifest.json {version, language:sat_Olck, models[], curriculum hash, licenses[], sha256}
- Installer verifies sha256, copies to app filesDir/packs/<version>/, registers in Room, engines reload from active pack path
- Adapters (MT/TTS/ASR) must read active pack path, not hardcoded asset path

### Claude's Discretion
- Zip vs tar, signing key placement (debug vs release), Manage Packs UI location

</decisions>

<canonical_refs>
- AGENTS.md — sync/ is installer
- packages/ — pack builder
- android/sync/ — installer module
- android/ml/ adapters — must support pack path
</canonical_refs>
