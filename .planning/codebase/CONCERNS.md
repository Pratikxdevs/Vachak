# Concerns

Audit scope: open-source release of Vachak (offline Android Kotlin/Compose/Room, minSdk 28).
Method: manifest read + `rg` sweeps over `android/**` + targeted file reads. Every finding cites a
file:line actually read. No network stack exists in `android/` (verified by grep), so most
network/crypto classes are N/A — noted where checked.

## Critical (ship-blockers for open-source)

### Unsigned packs: sha256 is self-attestation, no asymmetric signature — Severity: Critical
- **File & line:** `android/sync/src/main/java/com/vachak/sync/PackInstaller.kt:78-117`
- **Snippet:**
  ```kotlin
  val expectedPackSha = manifestJson.optString("packSha256", "")
  if (expectedPackSha.isNotBlank()) {
      val actualPackSha = sha256File(file)
      if (actualPackSha != expectedPackSha) {
          Log.w(tag, "packSha256 mismatch ... — continuing (recomputed is source of truth)")
          // Not fatal: packSha in manifest was before final copy; we log but don't fail
      }
  }
  ```
  plus `android/sync/src/main/java/com/vachak/sync/PackInstaller.kt:278-285`:
  ```kotlin
  if (path.isBlank() || expected.isBlank()) return true
  val entry = zip.getEntry(path) ?: return true
  ```
- **Threat:** Anyone can build a `.vachakpack` (zip + self-written `manifest.json` with matching
  sha256 values) and get it installed via SAF sideload or USB. Per-file sha256 only proves the zip
  is internally consistent, not that it came from the curriculum authority. A malicious pack can
  ship poisoned lessons, a trojaned `model.onnx`, or tampered `lexicon.txt`. `packSha256` mismatch
  only warns and continues. Blank sha / missing entry returns `true` (skip), so unsigned files pass
  silently. `.planning/STATE.md` claims "signed packs" / "manifest sha256" — the sha256 half is
  true, the signature half does not exist in code.
- **Remediation:** Add Ed25519 pack signing; ship the public key in the APK and fail closed:
  ```kotlin
  // manifest.json gains "signature" (Ed25519 over canonical manifest bytes minus signature field)
  val sigOk = PackSignature.verify(manifestBytesWithoutSig, manifestJson.optString("signature", ""))
  if (!sigOk) { zip.close(); return EngineResult.Err(EngineError.INVALID_INPUT, "pack signature invalid") }
  ```
  Concrete steps: (1) generate Ed25519 keypair offline, embed pubkey in `res/values` or `BuildConfig`
  field; (2) sign in `scripts/pack.py` at build time; (3) `PackInstaller` verifies before extraction;
  (4) make `packSha256` mismatch fatal, not `Log.w`; (5) treat blank-sha / missing-entry as failure
  for files under `models/` and `curriculum/`, skip-only for explicitly optional assets.

### User speech transcripts logged verbatim to logcat — Severity: High
- **File & line:** `android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt:174`
- **Snippet:**
  ```kotlin
  Log.d(tag, "transcribed ${samples.size} samples @ $sampleRate Hz -> \"$text\" (${latencyMs}ms, tokens=${result.tokens.size})")
  ```
  Same pattern in `android/app/src/main/java/com/vachak/ml/adapter/SherpaAsrAdapter.kt:87`,
  `android/ml/src/main/java/com/vachak/ml/StreamingAsrSession.kt:486`
  (`ASR_FINAL ... text="$txt"`), and MT input logging in
  `android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt:229`
  (`translate [$src->$tgt] "${text.take(60)}"`) / `:246`.
- **Threat:** Children's classroom speech (names, family details, location) lands in logcat in
  cleartext. Logcat is readable via `adb`, bug reports, and the in-app `VachakLogger` ring buffer
  (`android/app/src/main/java/com/vachak/ui/debug/VachakLogger.kt:17-29`, 200-line buffer shown by
  `DebugOverlay`). Any screenshot/bug-report export leaks PII. Also enables log injection (below).
- **Remediation:** Never log transcript content; log shape only:
  ```kotlin
  Log.d(tag, "transcribed ${samples.size} samples @ $sampleRate Hz (chars=${text.length}, latency=${latencyMs}ms)")
  ```
  Gate the two content-logging lines behind `BuildConfig.DEBUG`, and exclude `Vachak-ASR`/`Vachak-MT`
  content tags from `VachakLogger.mirror()`.

## High

### Zip-bomb / OOM via whole-entry `readBytes()` — Severity: High
- **File & line:** `android/sync/src/main/java/com/vachak/sync/PackInstaller.kt:86`
- **Snippet:**
  ```kotlin
  val data = zip.getInputStream(entry).readBytes()
  val actual = sha256(data)
  ```
  (repeated `:100`, and `android/sync/src/main/java/com/vachak/sync/PackInstaller.kt:283`).
- **Threat:** `readBytes()` loads the entire entry into the Dalvik heap. The `entry.size > 600MB`
  guard (`PackInstaller.kt:137`) trusts the zip header, which an attacker controls (can be `-1` /
  spoofed, and deflated bombs expand far beyond declared size). On a 2GB tablet this is a trivial
  remote-DoS-by-SAF-file: open a crafted pack → OOM kill. Double hashing (verify pass + extract
  pass) also doubles peak I/O.
- **Remediation:** Stream-hash with a bounded counter and a decompression-ratio cap:
  ```kotlin
  private fun sha256Bounded(ins: InputStream, maxBytes: Long): String? {
      val md = MessageDigest.getInstance("SHA-256")
      val buf = ByteArray(8192); var total = 0L; var n: Int
      while (ins.read(buf).also { n = it } != -1) {
          total += n
          if (total > maxBytes) return null // reject: entry too large
          md.update(buf, 0, n)
      }
      return md.digest().joinToString("") { "%02x".format(it) }
  }
  ```

### `allowMainThreadQueries()` unconditional in production builds — Severity: High
- **File & line:** `android/content/src/main/java/com/vachak/content/db/AppDatabase.kt:54`
- **Snippet:**
  ```kotlin
  return Room.databaseBuilder(context, AppDatabase::class.java, "vachak_content.db")
      .addCallback(PrepopulateCallback(context))
      .fallbackToDestructiveMigration()
      // Kept for debug/demo & tests; prod must use suspend wrappers below.
      .allowMainThreadQueries()
      .build()
  ```
  Identical in `android/sync/src/main/java/com/vachak/sync/db/PackDatabase.kt:26`. The
  `isDebuggable` check at `AppDatabase.kt:44` only changes the log message, not the builder.
- **Threat:** Any current or future caller on the main thread performs synchronous SQLite I/O →
  ANR on low-end tablets; also silently legitimizes main-thread DB access, defeating the suspend
  wrappers. ANR during a classroom demo is a reliability ship-blocker, and hung UI invites
  force-stop data corruption mid-prepopulate.
- **Remediation:** Apply only for debuggable builds:
  ```kotlin
  val builder = Room.databaseBuilder(context, AppDatabase::class.java, "vachak_content.db")
      .addCallback(PrepopulateCallback(context))
      .fallbackToDestructiveMigration()
  if (isDebuggable) builder.allowMainThreadQueries()
  return builder.build()
  ```

### Lenient manifest validation accepts empty/missing file lists silently — Severity: High
- **File & line:** `android/sync/src/main/java/com/vachak/sync/PackInstaller.kt:70-76`
- **Snippet:**
  ```kotlin
  val models = manifestJson.optJSONArray("models")
  val currFiles = manifestJson.optJSONArray("curriculum_files")
  ...
  if (modelLen == 0 && currLen == 0) {
      return EngineResult.Err(EngineError.INVALID_INPUT, "manifest models empty")
  }
  ```
  Combined with the sha-skip (`:281-282`, `if blank return true`), a manifest with entries lacking
  `sha256` installs without any integrity check on those files.
- **Threat:** Downgrade/partial-pack attack: strip `sha256` fields (or entries) from a copied
  manifest; installer accepts and extracts unchecked files, e.g. replacing curriculum JSON while
  keeping a valid-looking pack id. No schema allowlist either — unknown top-level keys ignored.
- **Remediation:** Require `sha256` for every entry under `models/` and `curriculum/`; fail on
  blank/missing; validate `language` against an allowlist (`sat_Olck`) and `version` against a
  semver regex before `destDir` construction (path injection via crafted `language`/`version` is
  currently only saved by the canonical check).

## Medium

### `READ_EXTERNAL_STORAGE` declared without `maxSdkVersion` scoping — Severity: Medium
- **File & line:** `android/app/src/main/AndroidManifest.xml:7`
- **Snippet:** `<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />`
- **Threat:** Overbroad install-time permission signal; on API 33+ it is neutered for media and the
  app actually uses SAF (`ContentResolver.openInputStream`, `PackInstaller.kt:33`) which needs no
  storage permission at all. Keeping it invites Play/policy questions and user distrust for a
  children's classroom app.
- **Remediation:** Remove if SAF-only, or scope it:
  ```xml
  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
      android:maxSdkVersion="32" />
  ```

### `runBlocking` on the calling thread across `ContentEngine` — Severity: Medium
- **File & line:** `android/content/src/main/java/com/vachak/content/ContentEngine.kt:43`
- **Snippet:**
  ```kotlin
  runBlocking {
  ```
  (repeated `:56`, `:76`, `:88`, `:101`, `:118`, `:131`, `:138`, `:157`, `:177`, `:198`, `:213`, `:230`).
- **Threat:** Any Compose/UI caller invoking these helpers on the main thread blocks it on SQLite
  I/O (compounded by `allowMainThreadQueries`). Suspend wrappers exist (`AppDatabase.kt:158-166`)
  but the sync API makes the wrong call easy.
- **Remediation:** Migrate callers to the existing `getLessonsSuspend`/`getLessonSuspend` family and
  mark sync wrappers `@Deprecated("main-thread unsafe")`, or wrap bodies in
  `runBlocking(Dispatchers.IO)` as `PackManager` already does (`PackManager.kt:48`).

### `Room` `fallbackToDestructiveMigration()` wipes curriculum silently — Severity: Medium
- **File & line:** `android/content/src/main/java/com/vachak/content/db/AppDatabase.kt:52`
- **Snippet:** `.fallbackToDestructiveMigration()`
- **Threat:** Any schema version bump deletes all lessons/outcomes/worksheets/flashcards and
  re-seeds; teacher progress keys in `SharedPreferences` then point at stale ids. Data-loss bug,
  not exploitable, but unacceptable for classroom continuity.
- **Remediation:** Provide explicit `Migration(2, 3)` objects (even if no-op `ALTER`); keep
  destructive fallback debug-only.

### MT thread-count claim vs reality (`threads=1` log is false) — Severity: Medium
- **File & line:** `android/ml/src/main/java/com/vachak/ml/adapter/OnnxIndicTrans2Adapter.kt:106-110`
- **Snippet:**
  ```kotlin
  val mtThreads = minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
  val opts = OrtSession.SessionOptions().apply {
      setIntraOpNumThreads(mtThreads)
  ```
  vs the success log at `:131`: `"ONNX ready in ${ms}ms (enc+dec+past, threads=1)"`.
  ASR side: `android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt:139`
  (`minOf(2, ...)`).
- **Threat:** The AGENTS.md sequential/`numThreads=1` RAM discipline is the 2GB safety case, but MT
  actually runs up to 4 intra-op threads and ASR up to 2. Combined with sequential-but-overlapping
  session lifetimes this risks OOM on the target tablet; the false log line hides it from
  diagnostics.
- **Remediation:** Pin `setIntraOpNumThreads(1)` to match the documented budget (accept slower MT),
  or measure and document the real peak RSS per thread count in `docs/phases/P1-size-variance.md`
  and fix the log to print `mtThreads`.

### Log injection via unsanitized ASR/MT text in log lines — Severity: Medium
- **File & line:** `android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt:174`
- **Snippet:** `Log.d(tag, "transcribed ... -> \"$text\" ...")` (raw `text` interpolated).
- **Threat:** Recognized speech containing newlines forges additional logcat lines with a trusted
  `Vachak-ASR` tag, polluting `VachakLogger` ring buffer / `DebugOverlay` and any log-derived
  diagnostics. Low integrity impact, but it is the same line as the PII finding — fixing that fix
  removes this too.
- **Remediation:** Same as PII fix; if content logging is ever re-enabled for debug, sanitize:
  ```kotlin
  val safe = text.replace("\n", "\\n").replace("\r", "\\r").take(80)
  ```

### FileProvider share intent grants read to any chosen app — Severity: Medium
- **File & line:** `android/app/src/main/java/com/vachak/ui/pdf/WorksheetPdf.kt:84-91`
- **Snippet:**
  ```kotlin
  return Intent.createChooser(
      Intent(Intent.ACTION_SEND).apply {
          type = "application/pdf"
          putExtra(Intent.EXTRA_STREAM, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }, "Share worksheet"
  )
  ```
- **Threat:** `shareIntent` (distinct from read-only `viewIntent` at `:76`, which is correctly
  scoped) hands the worksheet PDF to an arbitrary user-chosen app with no audit trail. Worksheets
  embed answer keys (`WorksheetPdf.kt:40`, `worksheet.items.map { prompt to answerKey }`), so a
  share to a networked app exfiltrates assessment answers off the offline device. This is by design
  as a feature, but the answer-key inclusion makes it a data-loss footgun.
- **Remediation:** Generate student vs teacher variants (student PDF omits `answerKey`); add
  `ClipData` + grant flag on the chooser intent for reliability:
  ```kotlin
  val chooser = Intent.createChooser(send, "Share worksheet")
  chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  return chooser
  ```

## Low

### Unencrypted Room databases and `SharedPreferences` — Severity: Low
- **File & line:** `android/content/src/main/java/com/vachak/content/db/AppDatabase.kt:51-55`
- **Snippet:** plain `Room.databaseBuilder(...)` (no SQLCipher passphrase); prefs via
  `context.getSharedPreferences("vachak", Context.MODE_PRIVATE)` in
  `android/app/src/main/java/com/vachak/ui/prefs/VachakPrefs.kt:11` and
  `android/sync/src/main/java/com/vachak/sync/PackInstaller.kt:182`.
- **Threat:** `filesDir` databases (`vachak_content.db`, `vachak_packs.db`) and prefs XML are
  world-inaccessible (Linux UID sandbox) and `allowBackup=false`
  (`AndroidManifest.xml:16`) blocks adb-backup extraction, so practical exposure is limited to
  rooted/lost-unlocked devices. Stored data is low-sensitivity (curriculum, pack registry, teacher
  name, lesson progress) — no credentials. Not a blocker, but voice-audio retention policy should
  be explicit (see backlog).
- **Remediation:** Document as accepted risk; if threat model grows (student PII in prefs), migrate
  to `EncryptedSharedPreferences` + SQLCipher. No code change required for release.

### Predictable temp-pack filenames + non-atomic cleanup — Severity: Low
- **File & line:** `android/sync/src/main/java/com/vachak/sync/PackInstaller.kt:35`
- **Snippet:**
  ```kotlin
  val tempFile = File(context.cacheDir, "pack_tmp_${System.currentTimeMillis()}.vachakpack")
  input.use { ins -> tempFile.outputStream().use { out -> ins.copyTo(out) } }
  val result = installFromFile(tempFile)
  tempFile.delete()
  ```
- **Threat:** `cacheDir` is app-private so symlink/race risk is minimal; worst case is cache
  pollution if `installFromFile` throws an `Error` before `delete()` (exceptions are caught
  internally, but not guaranteed). Two concurrent installs could collide on identical millis names.
- **Remediation:**
  ```kotlin
  val tempFile = File.createTempFile("pack_", ".vachakpack", context.cacheDir)
  try { ... installFromFile(tempFile) } finally { tempFile.delete() }
  ```

### Unbounded in-memory conversation history — Severity: Low
- **File & line:** `android/app/src/main/java/com/vachak/ui/screens/LiveConversationStore.kt:31-33`
- **Snippet:**
  ```kotlin
  object LiveConversationStore {
      val items = mutableStateListOf<ConversationItem>()
  ```
- **Threat:** Positive: no transcript persistence (good for privacy). Negative: a long classroom
  session grows the list without bound on a 2GB device. Minor availability issue only.
- **Remediation:** Cap at e.g. 100 items with drop-oldest: `if (items.size > 100) items.removeAt(0)`.

### `exportSchema = false` on both Room databases — Severity: Low
- **File & line:** `android/content/src/main/java/com/vachak/content/db/AppDatabase.kt:25`
- **Snippet:** `@Database(..., version = 2, exportSchema = false)` (same at
  `android/sync/src/main/java/com/vachak/sync/db/PackDatabase.kt:8`).
- **Threat:** No compile-time schema history; future migrations cannot be auto-verified, raising the
  odds of a destructive-migration data loss (see Medium finding). No runtime exploit.
- **Remediation:** Set `exportSchema = true` with `room.schemaLocation` in the content/sync modules'
  `build.gradle`, commit the schema JSON.

## Strong patterns observed (offline no-INTERNET, sequential guards, sha256+zip-slip, Ol Chiki validation, consent/provenance — only list what you VERIFIED in code)

- No `INTERNET` permission: manifest declares only `RECORD_AUDIO`, `READ_EXTERNAL_STORAGE`,
  `MODIFY_AUDIO_SETTINGS` (`android/app/src/main/AndroidManifest.xml:6-10`); `rg` for
  `HttpURLConnection|OkHttp|Retrofit|Volley|WebView|DownloadManager|Firebase` across `android/`
  returns only comments/docs and placeholder strings in tests — no runtime network code.
- Only exported component is the launcher `MainActivity` (`android:exported="true"` with a
  MAIN/LAUNCHER filter, `android/app/src/main/AndroidManifest.xml:20-31`); `FileProvider` is
  `exported="false"` with `grantUriPermissions` and a least-privilege `filepaths.xml` exposing only
  `files-path/worksheets/`; `allowBackup="false"` (`:16`).
- Sequential pipeline enforced in two layers: `ReentrantLock` around MT load/translate
  (`android/ml/src/main/java/com/vachak/ml/adapter/OnnxIndicTrans2Adapter.kt:40,88,140,156,261`)
  and `Dispatchers.IO.limitedParallelism(1)` + `Mutex.withLock` around ASR→MT→TTS in
  `android/app/src/main/java/com/vachak/ui/screens/LiveViewModel.kt:93-95,133-134,348-349`, with
  mic capture gated on `isTranslating` (`LiveScreen.kt:285-287`).
- Pack installer verifies per-file sha256 (`PackInstaller.kt:80-107`), rejects `..`/absolute paths
  (`:131`), enforces a canonical-path containment check (`:144`), applies size guards (`:53`,
  `:137`) and a free-space check (`:55`).
- Ol Chiki validation at two layers: warn-on-insert in
  `android/content/src/main/java/com/vachak/content/db/AppDatabase.kt:147-154` and
  `isOlChikiValid` in `android/content/src/main/java/com/vachak/content/ContentEngine.kt:265-267`;
  curriculum ships precomputed `textSatOlChiki` (no on-device MT for lessons,
  `ContentEngine.kt:29`).
- No injection surface found: all Room access is `@Query` with bound `:params`
  (`LessonDao.kt`, `PackDao.kt`) — no `@RawQuery`/`rawQuery`; no `Runtime.exec`/`ProcessBuilder`
  (only `availableProcessors()`); no `WebView`; no hardcoded secrets (`HF_TOKEN` read from env in
  `colab/santali_tts_colab.py:115`, placeholder `hf_...` at `:22`); SHA-256 used for integrity
  (`PackInstaller.kt:288,293`), no MD5/SHA-1/AES-ECB/`java.util.Random` in `android/`.
- Conversation transcripts are memory-only (`LiveConversationStore.kt:31-33`, explicitly
  non-persistent per the file header); mic audio is streamed PCM buffers, never written to files
  (no audio-file writes found in `android/` greps).
- Provenance hygiene: `THIRD_PARTY_NOTICES.md` (218 lines) records repo/model/dataset licenses
  including Piper GPL-3.0 handling and the `hin_mun` BY-NC-SA-FS quarantine;
  `VOICE_CONSENT.md` documents CC BY 4.0 corpus consent with right-to-removal (§3) and flags the
  native-corpus gap (§9); `IN22` eval-only separation is stated in both files and enforced by
  keeping eval out of training manifests.

## Open-source readiness checklist (secrets, binaries/gitignore, licenses, consent, eval separation, debug flags, log hygiene — each as [x] verified / [ ] gap with file ref)

- [x] No `INTERNET` permission; offline claim verified in manifest + code grep
  (`android/app/src/main/AndroidManifest.xml:4-10`).
- [x] No hardcoded API keys/tokens in `android/`; `HF_TOKEN` env-only in colab tooling
  (`colab/santali_tts_colab.py:115-124`); no `local.properties`/`gradle.properties` secrets in git
  history (`git log --all -- local.properties` empty; root `local.properties` holds only `sdk.dir`).
- [ ] Gap: `*.vachakpack` (387MB × 2 in `packages/`) and `*.safetensors` are gitignored
  (`.gitignore:12,65`) and untracked — good — BUT 17 large `.onnx`/`.onnx.data` model blobs ARE
  git-tracked under `android/*/src/main/assets/` (via Git LFS per `.gitattributes`). Every clone
  pulls ~1GB+ of binaries; `git status` shows them modified. Move models out of git into the
  `.vachakpack` distribution path (or a release-asset download) before public push.
- [x] `THIRD_PARTY_NOTICES.md` complete for code/models/datasets including Piper GPL-3.0,
  Coqui MPL-2.0, espeak-ng GPL-3.0 (dev-only), and `hin_mun` quarantine.
- [ ] Gap: `Uktam` license cell is `—` (`THIRD_PARTY_NOTICES.md:22`); `VOICE_CONSENT.md`
  signatures unsigned (`§7` checkboxes, curator placeholder, `vachak-sih26042@example.invalid`
  contact at `:5-6`). Native-corpus training audio (`raw/santali_male_native_web/`) has NO consent
  on file — correctly flagged prototype-only (`VOICE_CONSENT.md §9`), but the derived model bytes
  must not be published until consent is filed or retrained on §2 CC BY 4.0 corpora.
- [x] Curriculum is `AUTHOR-DRAFT`, explicitly not approved pedagogy
  (`curriculum/lessons/sat_lessons.json:5`); `datasets/hin_sat` silver tier marked DRAFT 2.2%
  verified (`THIRD_PARTY_NOTICES.md:44`); IN22 eval-only separation documented.
- [ ] Gap: release build ships `allowMainThreadQueries` + `fallbackToDestructiveMigration` +
  verbose `Log.d` (incl. transcripts) with no `proguard`/log-stripping rule difference between
  debug/release beyond minify (`android/app/build.gradle.kts:31-41`); no `debuggable=false`
  override needed (default safe) but confirm signing config is not committed.
- [ ] Gap: log hygiene — transcript content in logcat (`IndicConformerAsrAdapter.kt:174`,
  `SherpaAsrAdapter.kt:87`, `StreamingAsrSession.kt:486`, `IndicTrans2Adapter.kt:229,246`) must be
  removed before any public beta (bug reports would carry children's speech).

## Improvement backlog (ranked top 10 by risk×effort, each one line with file ref)

1. Add Ed25519 pack signatures + fail-closed verify; fix sha-skip and warn-only packSha (`PackInstaller.kt:78-117,278-285`).
2. Strip transcript content from all `Log.d` lines; gate remaining diagnostics behind `BuildConfig.DEBUG` (`IndicConformerAsrAdapter.kt:174`, `SherpaAsrAdapter.kt:87`, `StreamingAsrSession.kt:486`).
3. Stream-hash zip entries with byte caps instead of `readBytes()` (`PackInstaller.kt:86,100,283`).
4. Make `allowMainThreadQueries()` debug-only in both Room builders (`AppDatabase.kt:54`, `PackDatabase.kt:26`).
5. Move LFS-tracked `.onnx` assets out of git into `.vachakpack`/release downloads (17 blobs under `android/*/src/main/assets/`).
6. Ship student PDF without `answerKey`; fix chooser grant flags (`WorksheetPdf.kt:40,84-91`).
7. Replace `fallbackToDestructiveMigration()` with explicit `Migration`s + `exportSchema=true` (`AppDatabase.kt:52`, `PackDatabase.kt:22`).
8. Pin MT `intraOpThreads=1` (or measure peak RSS) and fix false `threads=1` log (`OnnxIndicTrans2Adapter.kt:106-131`).
9. Remove/scope `READ_EXTERNAL_STORAGE` (`AndroidManifest.xml:7`); document mic-audio no-retention policy formally.
10. File voice consent (§7 signatures, institutional contact) and resolve native-corpus §9 gap before publishing TTS weights (`VOICE_CONSENT.md:80-88,126-151`).
