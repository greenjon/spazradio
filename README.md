# spazradio
Simple Android app to play the audio stream from spaz.radio.

Written in Kotlin/JetPack Compose on Android Studio.

Behind the panels it shows [Liquid LSD](https://liquid-lsd.greenjon.com) audio-reactive visuals (the same picture as spaz.org/tv), bundled in the app so they work offline. The simple lissajous scope is the fallback on devices without WebGL2. Tune them in Settings > Visuals.

The visuals live in `app/src/main/assets/visuals/`, a committed copy of the Liquid LSD web TV in its `?embed` mode. After changing the web TV, run `scripts/sync_visuals.sh [path/to/liquid-lsd]` and commit the result.

Translated for EN, ES, FR, DE.

We don't collect any info, serve any ads, want any money, etc.
