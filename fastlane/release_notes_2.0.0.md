**SPAZ Radio 2.0 puts a live, audio-reactive picture behind the radio.** These are the changes since 1.6.3.

## Liquid LSD visuals
- **The simple scope is replaced by the full picture from [spaz.org/tv](https://spaz.org/tv/),** made with [Liquid LSD](https://liquid-lsd.greenjon.com). It follows whatever you're hearing, radio or archive shows. It's built into the app, so it works offline. Devices without WebGL2 still get the scope.
- **Scenes that move with the music,** each with a fresh variation every time it comes on. Quiet and loud shows move the picture equally, breakdowns stay calm, and the picture cuts in on the drop.
- **New scenes that draw the sound itself:** Spectrogram scrolls the music across the screen, and Scope is a glowing oscilloscope in a feedback loop. KIFS Storm is a storm of nested cubes, and only plays on phones fast enough for it.
- **The SPAZ listener photo gallery is the background:** 129 photos from spaz.org/radio come in on the beat and drift across the screen. They're built in too.
- **Live broadcasts:** when a Liquid LSD artist is on air, you see their live visuals. This uses data, and you can turn it off in Settings > Visuals.
- **New scenes without an app update:** about once a day the app downloads new scenes and playlists from spaz.org and checks their signature. These downloads are data only, never code. Turn it off or tap *Check now* in Settings > Visuals.

## While you watch
- **Controls fade away** after a few seconds and the screen stays on. Tap or press Back to bring them back; that first tap won't pause the music. Turn this off with *Auto-hide controls*.
- **In a pocket or face down,** the screen goes dark after about 3 seconds, the picture stops drawing and the radio keeps playing. Phones without a proximity sensor just use their normal screen timeout when lying face down.
- **Saving battery:** the picture stops drawing when the app is in the background or the radio is paused, and drops to Low quality under Android's battery saver.

## Settings
- **Audio quality** (Settings > Playback): High, Low or Auto. High is the default. Low uses about half the data, and Auto switches to Low on mobile data and other metered connections.
- **Settings > Visuals:** picture quality, reaction to the music, reduced motion, picture delay (raise it if the picture moves before the beat; Bluetooth speakers need more), auto-hide controls, live broadcasts and scene updates.

## Chat
- Messages that arrive while the app is open now show their time.
- Messages sent while your connection was down are filled in when it comes back.

## Updating
- **From Google Play:** update as usual.
- **APK:** download `app-release.apk` below. It's signed with the same key as the Play Store version, so it installs over a Play install or a 2.0.0 beta.
- **If you installed the 1.6.3 APK from GitHub or a 1.6.4 beta:** those used a different signing key, so uninstall first. This clears the app's settings.
- The app is larger now, about 17 MB instead of 6 MB, because the visuals and photos are built in. It also adds the `WAKE_LOCK` permission, which turns the screen off in a pocket.

Report problems on the [issue tracker](https://github.com/greenjon/spazradio/issues).
