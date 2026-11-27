# spazradio
Android app for SPAZ.Radio: the live stream, a searchable show archive with downloads, schedule, chat, an equalizer and audio-reactive visuals.

Written in Kotlin/JetPack Compose on Android Studio.

Behind the panels it shows [Liquid LSD](https://liquid-lsd.greenjon.com) audio-reactive visuals (the same picture as spaz.org/tv), bundled in the app so they work offline. The simple lissajous scope is the fallback on devices without WebGL2. Tune them in Settings > Visuals. While visuals play with no panel open, the controls and system bars fade away after a few seconds and the screen stays on (until the proximity sensor is covered for 3 s, in a pocket or face down: then the display goes dark the way it does at your ear in a call, the picture stops drawing and the system's screen timeout locks the phone as usual; devices without a proximity sensor only let the timeout run, and only when lying face down); the first tap (or Back) brings them back. Turn this off with "Auto-hide controls" in Settings > Visuals (it never hides under TalkBack).

The visuals come from the `lsd-visuals` module: a committed copy of liquid-lsd's `android/lsd-visuals/` (the WebView host, content updates, the audio feed) with the Liquid LSD web TV in its `?embed` mode as its assets (`lsd-visuals/src/main/assets/visuals/`; `lsd-visuals/VERSION` names the liquid-lsd commit). Don't edit it here: change it in liquid-lsd, then run `scripts/sync_visuals.sh [path/to/liquid-lsd]` (which runs liquid-lsd's `scripts/sync_lsd_visuals.sh`) and commit the result.

The visuals can also improve without an app release: once a day (and on "Check now" in Settings > Visuals) the app reads the signed manifest at https://spaz.org/tv/update/, downloads changed data files (presets, playlists, shaders; never code) into app storage and uses them in place of the bundled copies. The signature is checked against the public key in `lsd-visuals/src/main/java/lsd/liquid/visuals/ContentUpdates.kt`; the manifest is made and signed by `scripts/publish_web_tv.sh` in the liquid-lsd repo. Downloaded content is dropped when the app itself updates, since the bundled files may be newer.

## Install

Get the latest `app-release.apk` from the [releases page](https://github.com/greenjon/spazradio/releases/latest) and open it on your phone (you may need to allow installs from your browser).

## Build

You need JDK 17 and the Android SDK (Android Studio sets both up; `local.properties` must point at the SDK).

```
./gradlew assembleDebug            # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest lintDebug   # unit tests and lint (CI runs the same)
```

A release build is signed from environment variables (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) or from `key.properties`: copy `key.properties.example` and fill it in. Without a key, `./gradlew assembleRelease` fails on purpose; pass `-PallowDebugSigning` for a debug-signed test build. Add `-PuseMasterKey` for the standalone APK key. The GitHub release asset must stay named `app-release.apk`.

To update the visuals, run `scripts/sync_visuals.sh` (see above); never edit `lsd-visuals/` by hand.

Before a release, see [docs/audit.md](docs/audit.md).

Translated for EN, ES, FR, DE.

We don't collect any info, serve any ads, want any money, etc. See the [privacy policy](PRIVACY.md).
