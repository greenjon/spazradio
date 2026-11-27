# Open items

Items left open by audits, plus owner decisions. Strike or delete them as they are done. Do not append dated logs.

## Before the 2.0.4 release

- **Real-phone checks** (A1-10, A5-3, A6-4, A7-7, A7-9):
  - unplugging headphones pauses playback
  - lock-screen and Bluetooth controls
  - EQ by ear: built-in and system
  - release-build visuals
  - light theme over the visuals
  - 200% font
  - the pocket sensor
  - landscape (branch `landscape-layouts`): a phone on its side (cinema strip, panel on the right, chat with the
    keyboard up) and a tablet or foldable (two panels, Settings in Chat's slot, rotating both ways)
- **Resync liquid-lsd-android** to liquid-lsd e6901b3 (merged to liquid-lsd main 2026-11-26).
- **Play Console Data safety (A8-11).** Re-check it against the rewritten `PRIVACY.md`.

- **Landscape and tablet screenshots** for the Play listing, once landscape is checked on real devices.

## After the 2.0.4 release

- **A6-1.** Set `CONTENT_ENGINE = 4` in liquid-lsd `scripts/make_content_manifest.py` before the next content
  publish. Do it only once a release with ENGINE 4 is out. Then run web TV lane W7 against the v2.0.0 engine.

## Owner decisions

- **A9-9.** The `app-release.apk` asset name is a convention, enforced only by docs.
- **A10-6.** MIT repo vs the licensing intent for liquid-lsd content. Licensing is on hold.

## Polish and leftovers from audit 2026-11-26

- **A1-3 tail.** After the 20th failed retry (about 9 minutes), the header still says "Reconnecting…". Show a plain
  error there instead.
- **A1-11.** No artwork in the media session. The launcher icon is adaptive XML, so a PNG is needed.
- **A3-3 (uncertain).** After a reinstall on API 33+, scoped storage may hide old downloads. Needs a MediaStore
  query or a design decision.
- **A3-7.** A failed HEAD hides the header download until the show changes.
- **A3-9.** The FileProvider serves the whole cache dir. Move the log file into `cache/logs/` and narrow
  `file_paths.xml`.
- **A3-10.** Tell the user downloads go to `Music/SPAZRadio`.
- **A3-11.** Download tracking is lost on process death.
- **Landscape: hardware Enter on the chat name field.** Joining with a physical keyboard's Enter (adb keyevent 66
  on the emulator) lands the panel pager on Settings. The on-screen keyboard's ✓ and the Chat button are fine.
  Matters for Chromebooks and keyboard cases.
- **A7-1 remainder.** Hard-coded English left in `ArchiveContent`, `DownloadTracker` and the view models.
- **A8-3 remainder.** The nickname is redacted from shared logs, but `ChatRepository` still logs presence lines.
- **A9-3, A9-4.** ProGuard rules are broad and partly dead.
- **A9-7.** One library is pinned outside the version catalog.
- **A9-10, A9-11.** Dependencies are 12 to 15 months old. Material icons-extended is on the classpath.
- **A6-5.** `sync_visuals.sh`:
  - The default liquid-lsd path is odd.
  - A worktree without `web/gallery/` wipes the vendored gallery, as happened during this audit. The sync should
    refuse when `web/gallery.json` or `web/gallery/` is missing, or reuse the checkout's existing copy.
- **Proposed automatic checks** (section F of the report):
  - no trust-all TLS
  - strings parity
  - changelog for versionCode
  - privacy hosts
  - visuals vendored
