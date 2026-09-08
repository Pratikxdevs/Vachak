# PLAN — P5: Language-Pack System + Offline Package Builder/Installer

**Goal:** Distribute models + curriculum as signed offline packages installed by a
non-network "sync" module (USB/SD/sideload), never over the internet.

**Context:**
- AGENTS.md: `sync/` is a package installer, **not** a network client. `packages/` builds
  distribution packs. Models are large → must be swappable without APK rebuild (ties to P2).
- Hard rule: no runtime network calls, ever.

**Tasks:**
1. `packages/` builder: bundle MT+TTS+ASR models + curriculum into a versioned pack (manifest
   with hashes + license list).
2. `sync/` installer: read pack from storage, verify integrity (hash), copy into app data,
   register in Room, no network permission.
3. Adapter config reads active pack paths (enables P2 model swap + future model updates).
4. UI: "Manage packs" screen (installed packs, free space, install from file).
5. Verify build has **no** `INTERNET` permission; install a pack and confirm engines reload.

**Acceptance:** App runs fully after installing a pack with WiFi OFF; manifest/licenses intact.
