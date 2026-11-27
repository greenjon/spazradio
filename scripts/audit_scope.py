#!/usr/bin/env python3
"""Decide which audit lanes are due (see docs/audit.md).

For each lane, the baseline is the newest of the last full-audit tag (audit-YYYY-MM-DD) and the last targeted tag
naming that lane (audit-YYYY-MM-DD-A4+A7). Files changed since the baseline (committed, staged, unstaged and
untracked) are mapped to lanes with LANES below. A full audit is required when a SHARED file changed, a changed
file maps to no lane, or the last full audit is too old.

Usage: scripts/audit_scope.py [--release] [--since REV] [--committed] [--table]
"""
import argparse
import datetime as dt
import fnmatch
import os
import re
import subprocess
import sys

P = "app/src/main/java/llm/slop/spazradio/"
UI = P + "ui/components/"
T = "app/src/test/java/llm/slop/spazradio/"
V = "lsd-visuals/src/main/java/lsd/liquid/visuals/"

LANES = {
    "A1": ("Playback, media session, notification", [
        P + "RadioService.kt", P + "RadioViewModel.kt", P + "LiveStream.kt", P + "PlaybackUiState.kt",
        P + "MainActivity.kt", T + "LiveStreamTest.kt"]),
    "A2": ("Now playing, metadata, schedule", [
        P + "MetadataViewModel.kt", P + "ScheduleViewModel.kt", P + "NowPlaying.kt", T + "NowPlayingTest.kt"]),
    "A3": ("Archive, jukebox, downloads", [
        P + "ArchiveViewModel.kt", P + "data/Archive*", UI + "ArchiveContent.kt", P + "utils/DownloadTracker.kt",
        UI + "DownloadPrompt.kt", "app/src/main/res/xml/file_paths.xml", T + "JukeboxShowTest.kt"]),
    "A4": ("Chat", [P + "ChatViewModel.kt", P + "data/Chat*", UI + "ChatContent.kt", T + "data/ChatMessagesTest.kt"]),
    "A5": ("Equalizer", [P + "Equalizer.kt", UI + "EqualizerSection.kt", T + "EqualizerTest.kt"]),
    "A6": ("Visuals and embedded web TV", [
        "lsd-visuals/*", UI + "Oscilloscope.kt", UI + "VisualsSettingsSection.kt", UI + "PocketSensor.kt",
        UI + "ControlsAutoHide.kt", P + "MainActivity.kt", "scripts/sync_visuals.sh"]),
    "A7": ("UI shell, theme, text", [
        P + "MainActivity.kt", P + "UiState.kt", P + "InfoDisplay.kt", UI + "Header.kt", UI + "FooterToolbar.kt",
        UI + "InfoBox*", P + "ui/theme/*", "app/src/main/res/*", "app/src/res/*", "app/src/main/ic_launcher-*",
        T + "ExampleUnitTest.kt", "app/src/androidTest/*"]),
    "A8": ("Privacy, permissions, network security", [
        "PRIVACY.md", "*AndroidManifest.xml", P + "utils/SpazNetwork.kt", P + "utils/NetworkMonitor.kt",
        P + "utils/LogCollector.kt", "app/src/main/res/xml/backup_rules.xml",
        "app/src/main/res/xml/data_extraction_rules.xml", V + "Content*.kt"]),
    "A9": ("Build and release", [
        "*build.gradle.kts", "settings.gradle.kts", "gradle/*", "gradlew*", "*.lockfile", "*proguard-rules.pro",
        "*consumer-rules.pro", "gradle.properties", ".github/*", "fastlane/Appfile", "fastlane/Fastfile", "Gemfile",
        "key.properties.example", "app/release/*", ".gitignore", "*/.gitignore"]),
    "A10": ("Docs, store text, tracking", [
        "README.md", "CONTRIBUTING.md", "LICENSE", "fastlane/metadata/*", "docs/*",
        "scripts/audit_scope.py"]),
}

# Touching any of these needs a full audit: they can break every area.
SHARED = [
    "*AndroidManifest.xml", "*build.gradle.kts", "settings.gradle.kts", "gradle/libs.versions.toml",
    "gradle/wrapper/*", "*.lockfile", "*proguard-rules.pro", "*consumer-rules.pro", "gradle.properties",
    P + "utils/SpazNetwork.kt", P + "utils/NetworkMonitor.kt",
]

# Never audited: editor and build noise, local instructions.
IGNORE = [".idea/*", ".kotlin/*", ".claude/*", "CLAUDE.local.md", ".planning/*"]

ALWAYS = ["A0"]
RELEASE = ["A9", "A10"]
MAX_FULL_AGE_DAYS = 60
MAX_RELEASES_SINCE_FULL = 3
LIST_MAX = 12

FULL_RE = re.compile(r"^audit-\d{4}-\d{2}-\d{2}$")
LANE_RE = re.compile(r"^audit-\d{4}-\d{2}-\d{2}-(A\d+(?:\+A\d+)*)$")


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def match(path, globs):
    return any(fnmatch.fnmatch(path, g) for g in globs)


def tags_newest_first():
    out = git("for-each-ref", "--merged", "HEAD", "--sort=-creatordate",
              "--format=%(refname:short) %(creatordate:unix)", "refs/tags/audit-*")
    return [(n, int(t)) for n, t in (line.split() for line in out.splitlines())]


def changed_since(rev, committed_only):
    files = set(git("diff", "--name-only", f"{rev}..HEAD").split())
    if not committed_only:
        files |= set(git("diff", "--name-only", "HEAD").split())
        files |= set(git("ls-files", "--others", "--exclude-standard").split())
    return sorted(f for f in files if not match(f, IGNORE))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--release", action="store_true", help="pre-release audit: A9 and A10 always due")
    ap.add_argument("--since", help="scope every lane from this revision instead of the audit tags")
    ap.add_argument("--committed", action="store_true", help="ignore uncommitted and untracked files")
    ap.add_argument("--table", action="store_true", help="print the lane/file table and exit")
    a = ap.parse_args()

    if a.table:
        print("A0  Smoke: always runs\n")
        for lane, (title, globs) in LANES.items():
            print(f"{lane:<4}{title}")
            for g in globs:
                print(f"      {g}")
        print("\nSHARED (forces a full audit):")
        for g in SHARED:
            print(f"      {g}")
        return 0

    tags = tags_newest_first()
    full = next(((n, t) for n, t in tags if FULL_RE.match(n)), None)
    reasons = []

    if a.since:
        base = {lane: a.since for lane in LANES}
    else:
        base = {}
        for lane in LANES:
            for n, _ in tags:  # newest first, so the first hit is the lane's baseline
                m = LANE_RE.match(n)
                if FULL_RE.match(n) or (m and lane in m.group(1).split("+")):
                    base[lane] = n
                    break
        if full is None:
            reasons.append("no full audit yet")
        else:
            age = (dt.datetime.now().timestamp() - full[1]) / 86400
            if age > MAX_FULL_AGE_DAYS:
                reasons.append(f"last full audit {full[0]} is {age:.0f} days old")
            releases = [r for r in git("tag", "--merged", "HEAD", "--no-merged", full[0], "v*").split()]
            if len(releases) >= MAX_RELEASES_SINCE_FULL:
                reasons.append(f"{len(releases)} releases since {full[0]}")

    due, shared_hits, unmapped = {}, set(), set()
    for lane, (_, globs) in LANES.items():
        rev = base.get(lane)
        if rev is None:
            continue
        for f in changed_since(rev, a.committed):
            if match(f, globs):
                due.setdefault(lane, []).append(f)
    everything = changed_since(a.since or (full[0] if full else git("rev-list", "--max-parents=0", "HEAD").split()[0]),
                               a.committed)
    for f in everything:
        if match(f, SHARED):
            shared_hits.add(f)
        if os.path.exists(f) and not any(match(f, g) for _, g in LANES.values()):
            unmapped.add(f)  # a deleted file needs no lane
    if shared_hits:
        reasons.append("shared files changed: " + ", ".join(sorted(shared_hits)))
    if unmapped:
        reasons.append("files in no lane (add them to LANES in this script): " + ", ".join(sorted(unmapped)))

    if reasons and not a.since:
        print("FULL AUDIT required:")
        for r in reasons:
            print(f"  - {r}")
        print("\nRun every lane: A0 " + " ".join(LANES))
        return 0
    if reasons:
        print("Note (--since given, so this is informational only):")
        for r in reasons:
            print(f"  - {r}")
        print()

    lanes = list(ALWAYS) + [l for l in LANES if l in due or (a.release and l in RELEASE)]
    print("TARGETED AUDIT. Lanes due: " + " ".join(lanes) + "\n")
    print("A0   Smoke (always)")
    for lane in LANES:
        title = LANES[lane][0]
        files = due.get(lane, [])
        if files:
            print(f"{lane:<5}{title}  since {base[lane]}  ({len(files)} files)")
            for f in files[:LIST_MAX]:
                print(f"       {f}")
            if len(files) > LIST_MAX:
                print(f"       ... and {len(files) - LIST_MAX} more")
        elif a.release and lane in RELEASE:
            print(f"{lane:<5}{title}  (due for release)")
        else:
            print(f"{lane:<5}{title}  clean since {base.get(lane, '-')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
