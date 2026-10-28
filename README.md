# spazradio
Simple Android app to play the audio stream from spaz.radio.

Written in Kotlin/JetPack Compose on Android Studio.

Behind the panels it shows [Liquid LSD](https://liquid-lsd.greenjon.com) audio-reactive visuals (the same picture as spaz.org/tv), bundled in the app so they work offline. The simple lissajous scope is the fallback on devices without WebGL2. Tune them in Settings > Visuals. While visuals play with no panel open, the controls and system bars fade away after a few seconds and the screen stays on; the first tap (or Back) brings them back. Turn this off with "Auto-hide controls" in Settings > Visuals (it never hides under TalkBack).

The visuals live in `app/src/main/assets/visuals/`, a committed copy of the Liquid LSD web TV in its `?embed` mode. After changing the web TV, run `scripts/sync_visuals.sh [path/to/liquid-lsd]` and commit the result.

Looks can also improve without an app release: once a day (and on "Check now" in Settings > Visuals) the app reads the signed manifest at https://spaz.org/tv/update/, downloads changed data files (presets, playlists, shaders; never code) into app storage and uses them in place of the bundled copies. The signature is checked against the public key in `visuals/ContentUpdates.kt`; the manifest is made and signed by `scripts/publish_web_tv.sh` in the liquid-lsd repo. Downloaded content is dropped when the app itself updates, since the bundled files may be newer.

Translated for EN, ES, FR, DE.

We don't collect any info, serve any ads, want any money, etc.
