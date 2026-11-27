# Audit Protocol

How we audit the Spaz Radio Android app. There are two kinds of audit:

- A **full audit** runs every lane.
- A **targeted audit** runs only the lanes whose code changed since each lane was last audited.

`scripts/audit_scope.py` decides which kind you need and which lanes are due.

The protocol follows Liquid LSD's [audit blueprint](../../liquid-lsd/docs/developer/audit.md). It uses the same goal,
finding format and method, with lanes of its own.

## Goal

After an audit:

1. Code changed since the baseline has no known correctness bug. This covers service lifecycle, threading,
   persistence, network failure and process death.
2. `PRIVACY.md`, the store listing, the changelogs and the UI text match what the app does.
3. The release build (R8, signing, versionCode, changelog) is shippable.
4. The vendored `lsd-visuals` engine and the content it downloads still work together, including in older app
   versions that people still have installed.
5. Each audit costs less than the last. A finding type seen in two audits becomes an automatic check.

An audit does not cover new features, redesigns or taste. Those go to `.planning/open-items.md`.

## Full or targeted?

Run `python3 scripts/audit_scope.py` (add `--release` before a release). It prints the lanes that are due and
whether a full audit is required. The rules it applies:

1. **Lane A0 (smoke) always runs.** Every audit runs it, even a one-lane audit.
2. **A lane is due** when a file mapped to it changed since that lane's baseline. The baseline is the newest of the
   last full-audit tag and the last targeted-audit tag that names the lane. A file can map to several lanes.
   `MainActivity.kt`, for example, maps to A1, A6 and A7.
3. **A full audit is required** when any of these is true:
   - A *shared* file changed. These are the manifest, Gradle files, lockfiles, R8/ProGuard rules,
     `gradle.properties`, `SpazNetwork.kt` and `NetworkMonitor.kt`. A dependency bump or a networking change can
     break any area, so a path-limited review is not enough.
   - A changed file matches no lane. Fix the table in the script first, then rerun. Unknown files are never
     waved through.
   - There has been no full audit yet, the last one is more than 60 days old, or three releases have shipped
     since it.
4. **Before a release** (`--release`), lanes A9 (build and release) and A10 (docs and store text) are due
   whatever changed.
5. When the diff is small, one reviewer may take several due lanes together.

Examples:

- A chat-only change runs A0 and A4.
- A theme-only change runs A0 and A7.
- An `lsd-visuals` sync runs A0 and A6. It also runs A8 when the sync touched `ContentUpdater`/`ContentStore`, and
  a full audit when it touched the module's Gradle file or manifest.
- Any dependency bump runs a full audit.

To see the whole file-to-lane table, run `python3 scripts/audit_scope.py --table`. The table lives in the script
only, so the doc and the script cannot drift apart. If you add a source file, add it to the table in the same
commit.

## Baseline and tags

| Kind | Tag | Example |
|---|---|---|
| Full audit | `audit-YYYY-MM-DD` | `audit-2026-12-01` |
| Targeted audit | `audit-YYYY-MM-DD-<lanes>` | `audit-2026-12-08-A4+A7` |

Tag the commit the audit closed on. A targeted tag lists exactly the lanes that were run. A0 is implied, so leave
it out. Use `--since <rev>` to scope from somewhere else, such as a release tag.

| Audit | Tag | Kind | Notes |
|---|---|---|---|
| (none yet) | | | first run will be a full audit |

## Lanes

Each lane has a fixed checklist. The reviewer works through the whole checklist and records the clean items too,
so "checked and clean" can be told apart from "not looked at". Grow these checklists as findings come in.

### A0. Smoke (always)

- `./gradlew testDebugUnitTest assembleRelease` passes. Record test counts and any new compiler warnings in
  changed files.
- On the emulator (see the Android emulator memory note), run the **release** APK: launch, play live, switch to
  low bitrate, open chat, open archives, background the app, use the notification controls, rotate, then
  force-stop and relaunch.
- `adb logcat` for the app process shows no crash, ANR or `StrictMode` violation.

### A1. Playback, media session, notification

Files: `RadioService`, `RadioViewModel`, `LiveStream`, `PlaybackUiState`, plus the wiring in `MainActivity`.

- Foreground service: it starts with the `mediaPlayback` type, the notification appears within the deadline, and
  it stops (no zombie notification) on pause timeout, swipe-away and stop.
- Audio focus: a call or another app ducks or pauses playback, and focus is regained correctly. Unplugging
  headphones (becoming noisy) pauses.
- Media session: metadata and artwork update, and lock screen, Bluetooth and headset buttons work.
- Network: there is no reconnect storm after a network loss, the user can see errors, and a Wi-Fi to mobile
  handover recovers.
- The `POST_NOTIFICATIONS` request on API 33+ is handled when the user denies it.
- Prefs in `LiveStream.PREFS` are shared by `LiveStream`, `RadioViewModel` and `RadioService`. Check keys,
  defaults and migration. A rename loses the user's settings.
- Process death or restore leaves no stale "playing" state.

### A2. Now playing, metadata, schedule

Files: `MetadataViewModel`, `ScheduleViewModel`, `NowPlaying` (+ test).

- Polling stops when the app is in the background or the screen is hidden, and resumes cleanly.
- Missing, empty or odd fields in `/playing` and the schedule feed do not crash the app or show `null`.
- Times are in the device time zone, and DST and midnight wrap are handled.
- The jukebox/live source label is correct for both states.

### A3. Archive, jukebox, downloads

Files: `ArchiveViewModel`, `data/Archive*`, `ArchiveContent`, `DownloadTracker`, `DownloadPrompt`,
`res/xml/file_paths.xml` (+ `JukeboxShowTest`).

- RSS parse survives malformed items, missing enclosures and HTML in titles.
- `olde/` 404s: the web TV v60 fallback has been ported, or this is still listed as a known gap.
- The mobile-data prompt appears on metered networks and is remembered as documented.
- Downloads: they are saved where the UI says, cancel and failure are surfaced, there are no duplicates, and the
  FileProvider exposes only the download directory.
- Seeking and resuming archive playback still go through A1's service path.

### A4. Chat

Files: `ChatViewModel`, `data/Chat*`, `ChatContent` (+ `ChatMessagesTest`). Prefs file: `spaz_chat`.

- Bad timestamps, missing fields and huge or empty messages are handled. Web TV v71 hardened these paths; check
  that the app matches.
- Message text is shown as plain text, with no markup or link injection.
- Polling stops when chat is closed or the app is in the background.
- A failed send gives visible feedback and does not lose the user's draft.
- Nickname storage and its limits match the UI text.

### A5. Equalizer

Files: `Equalizer`, `EqualizerSection` (+ `EqualizerTest`).

- Off, Built-in and System modes each attach to the current audio session id, and re-attach after the player is
  recreated.
- Effects are released when the service stops, with no leaked `AudioEffect`.
- The System EQ intent is handled when no EQ app is installed.
- **Owner check:** the EQ has never run on a real device. Confirm by ear.

### A6. Visuals and the embedded web TV

Files: `lsd-visuals/**`, `Oscilloscope`, `VisualsSettingsSection`, `PocketSensor`, `ControlsAutoHide`,
`scripts/sync_visuals.sh`.

- `lsd-visuals/` changed only through `scripts/sync_visuals.sh`. `lsd-visuals/VERSION` names a liquid-lsd commit,
  and the tree matches that commit. Hand edits are a **bug**.
- The `window.LSD` calls the app makes (`feed`, `pause`, `resume`, `catalog`, `state`) and the `LSDHost`
  `@JavascriptInterface` methods exist in the vendored engine, with the same arguments and shapes.
- R8 keeps the `@JavascriptInterface` methods. Smoke-test the release build: a debug build hides this.
- Content compatibility: content published since the baseline loads in the engine shipped in the **oldest app
  version still in use**. This is the app side of web TV audit lane W7. Run both together.
- The WebView pauses when the app is backgrounded or visuals are off. There is no runaway battery or GPU drain;
  check `dumpsys gfxinfo` and battery stats on the emulator.
- The pocket sensor and auto-hide do not fight the user's touches.

### A7. UI shell, theme, text

Files: `MainActivity`, `UiState`, `InfoDisplay`, `Header`, `FooterToolbar`, `InfoBox*`, `ui/theme/*`, `res/**`.

- All four locales (`values`, `-de`, `-es`, `-fr`) have the same set of string keys. New English strings have
  translations, or are marked as pending.
- Composables contain no hard-coded user-visible strings.
- The Neon, Light, Dark and Auto themes all have readable contrast on every screen touched. Check at least the
  header, footer, chat, archives and settings.
- Font scale at 200%, a small screen (360 dp) and landscape: nothing clips or overlaps.
- Icon-only buttons have a `contentDescription`.
- `app/src/res/values-es/strings.xml` sits outside `main/` and is ignored by the build. Delete it, or merge it if
  its wording is the better one.

### A8. Privacy, permissions, network security

Files: manifest (shared), `PRIVACY.md`, `SpazNetwork`/`NetworkMonitor` (shared), `LogCollector`,
`res/xml/{backup,data_extraction}_rules.xml`, `lsd-visuals/.../Content{Updater,Updates,Store}.kt`.

- Every host the app contacts is named in `PRIVACY.md`. That includes `radio.spaz.org`, `spaz.org`, GitHub and
  the content manifest host. List them with
  `grep -rhoE 'https?://[^"/]+' app/src/main/java lsd-visuals/src/main/java | sort -u`.
- All traffic is HTTPS, with no cleartext.
- Each permission in the manifest is used and explained in `PRIVACY.md`. The Play Data safety answers still match.
- The content updater verifies signatures before using content, fails closed, and does not download on metered
  networks against the user's setting.
- `LogCollector` output contains no chat nickname, tokens or full URLs with query strings, unless the policy says
  so.
- `allowBackup="true"`: the backup rules exclude anything that should not move to a new device. Confirm this was
  intended.
- The WebView does not have file access or universal access from file URLs beyond what the engine needs.

### A9. Build and release

Files: Gradle files and lockfiles (shared), `proguard-rules.pro`, `.github/workflows/*`, `fastlane/{Appfile,Fastfile}`,
`Gemfile`, `fdroid.yml`, `metadata/*`, `key.properties.example`, `app/release/*`.

- `versionCode` is greater than the last released one, on GitHub, F-Droid and Play.
- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` exists and is at most 500 characters.
- The R8 release build runs (A0). The keep rules cover reflection, serialization and the JS bridge.
- Lockfiles are updated with any dependency change, and there are no unreviewed version jumps.
- Signing comes from `key.properties`. No secrets are committed (check `git ls-files` for `*.jks` and
  `key.properties`).
- The GitHub release asset is still named `app-release.apk`, because the sticker QR page at spaz.org/go/ depends on
  it.
- The F-Droid metadata (`fdroid.yml`, `metadata/llm.slop.spazradio.yml`) builds the same version.
- No junk is tracked. Candidates seen so far are `exit`, `.kotlin/errors/*.log` and `app/release/output-metadata.json`.

### A10. Docs, store text, tracking

Files: `README.md`, `CONTRIBUTING.md`, `LICENSE`, `fastlane/metadata/**` text, `fastlane/release_notes_*.md`,
`docs/*`.

- Every user-visible commit since the baseline is in the next changelog.
- `full_description.txt`, `short_description.txt` and the screenshots match the current UI and features.
- `README.md` build and install steps still work.
- Memory notes and the next-release to-do list agree with the code. Strike items that are done, and flag notes that
  say "uncommitted" or "unseen" when that is no longer true.

## Checks only the owner can run

These are listed in the report as **decision** items and are never marked clean by an agent:

- Real phone: EQ by ear, Bluetooth and car head unit, lock-screen controls, and a long background session
  (battery).
- The Play Console Data safety form and the pre-launch report.
- Whether a finding is worth delaying a release for.

## Method

This is the same as Liquid LSD's method, shortened:

1. **Scope.** Run `scripts/audit_scope.py`, then `git log --oneline <baseline>..HEAD` for each due lane.
2. **Review (read-only).** Use one reviewer per due lane, or group the lanes when the diff is small. Findings go to
   `.planning/audit-YYYY-MM-DD/<lane>.md`.
3. **Verify.** A second reader confirms every **bug** and **wrong** at the cited line before it is fixed.
4. **Triage.** Merge into `.planning/audit-YYYY-MM-DD.md` under A (bugs), B (docs/policy false), C (stale),
   D (polish), E (owner decisions) and F (new checks).
5. **Fix**, with a test where practical. User-visible fixes get a changelog line.
6. **Docs pass** last. Covers `PRIVACY.md`, the changelog, the store text and this page.
7. **New checks.** Turn any finding type seen twice into an automatic check.
8. **Close.** Tag the commit (see above), add a row to the audit table, and move open items to
   `.planning/open-items.md`.

## Who runs what

| Work | Agent | Model |
|---|---|---|
| A1, A3, A6, A8 (lifecycle, network, security) | `general-purpose` | Opus |
| A2, A4, A5 | `general-purpose` | Sonnet, or Opus if the diff touches threading |
| A7, A9, A10 and the docs pass | `general-purpose` | Sonnet |
| A0 | run directly | none |
| Fixes | `general-purpose` | Sonnet |

## Finding format

```
### A<lane>-<n> [bug|wrong|stale|polish|decision] Short title
- Where: path/File.kt:123
- Evidence: what the code does vs. what the doc, policy or user expects
- Impact: who notices and when
- Fix: the smallest change that resolves it
- Verified: yes / no, false positive because ...
```

The severities mean the same as in the Liquid LSD blueprint.

## Automatic checks

| Check | Catches | Status |
|---|---|---|
| `audit_scope.py` | which lanes are due; shared and unmapped files | exists |
| `strings_parity` | locale key sets differ (A7) | proposed |
| `changelog_for_version` | missing or oversize `changelogs/<versionCode>.txt` (A9) | proposed |
| `privacy_hosts` | a contacted host that is not named in `PRIVACY.md` (A8) | proposed |
| `visuals_vendored` | `lsd-visuals/` differs from the liquid-lsd commit in `VERSION` (A6) | proposed |
| CI runs unit tests | the workflows build but never run tests | proposed |
