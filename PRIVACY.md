# Privacy Policy for SPAZ Radio

**Effective Date:** November 26, 2026

This describes what the SPAZ Radio Android app (source: [github.com/greenjon/spazradio](https://github.com/greenjon/spazradio)) sends over the network and stores on your device.

In short: no accounts, no ads, no analytics or tracking libraries, and nothing is sold or shared. The one thing you make public is what you type in chat.

## 1. What the app sends, and when

All servers are run by Spaz Radio: `radio.spaz.org` and `spaz.org`. Like any web request, each one sees your IP address and basic request details (such as the app's user-agent) while it serves you.

| What | Where | When |
| --- | --- | --- |
| Live audio stream | `radio.spaz.org` | While you play the radio |
| "Now playing" and listener count (`/playing`) | `radio.spaz.org` | About every 10 seconds while the radio plays or the app is open |
| Show schedule | `radio.spaz.org` | When you open the schedule, then hourly while it stays open |
| Archive list (RSS) and archive playback | `spaz.org` | When you browse or play archives |
| Archive downloads | `spaz.org` | Only when you tap Download. Files are saved to the shared `Music/SPAZRadio` folder, which other apps can see |
| Chat | `radio.spaz.org` (public MQTT broker, port 1885, and the chat log) | Only while the app is open in the foreground. See section 2 |
| Visuals content check | `spaz.org/tv/` | About once a day in the background, on any network, even when the app is closed. Downloads small scene and playlist updates. Turn it off with Settings > Visuals > "Update scenes over the network" |
| Live visuals relay | `wss://spaz.org/lsd-relay` | While the visuals are showing and "Follow live broadcasts" is on, to show a Liquid LSD artist's live picture when one is on air. Turn it off in Settings > Visuals |

## 2. Chat is public

Chat uses a public chat server with no login.

- The nickname you choose and every message you send are visible to anyone connected to that server, and anyone can read them without using this app.
- Your nickname is also published as an "online" presence while you are connected, and may be kept by the server as the last known state.
- The server keeps a public chat log of recent messages (about the last 100), readable by anyone without a login.
- Nicknames are not verified. Do not put personal information in a nickname or message. You cannot delete messages once sent.
- Chat connects only while the app is in the foreground.

## 3. What is stored on your device

Your settings (theme, equalizer, stream quality, autoplay, visuals options) and your chat nickname are stored in the app's private storage so you do not have to re-enter them. Cached schedule data and downloaded visuals content are kept there too. Android's Auto Backup may back up the small settings (including the nickname); downloaded visuals content and caches are excluded. Clearing the app's data in Android settings removes all of this. Downloaded archive shows live in `Music/SPAZRadio` and are yours to delete.

## 4. Debug logs

Settings > Debug & Support > "Send Debug Logs" is optional and only runs when you tap it. It collects the app's recent system log (about the last 2000 lines), plus your device make, model and Android version, into a file and opens Android's share sheet. Nothing is sent unless you pick an app to send it with; it then goes to whoever you choose. Logs contain technical messages such as stream and network errors and archive file URLs. The chat nickname is removed from the log lines the app writes.

## 5. Permissions

- **INTERNET**: to stream, fetch schedules and archives, chat, and update visuals.
- **ACCESS_NETWORK_STATE**: to tell whether you are online or on a metered (mobile data) connection, so the app can pick a stream quality, ask before big downloads, and reconnect chat.
- **FOREGROUND_SERVICE and FOREGROUND_SERVICE_MEDIA_PLAYBACK**: to keep the radio playing with the screen off.
- **POST_NOTIFICATIONS** (Android 13+): to show the playback notification and download progress.
- **WAKE_LOCK**: to keep the network stream running while the screen is off, and to turn the screen off while the phone is in your pocket or face down with visuals playing (uses the proximity sensor; the sensor reading never leaves the device).
- **WRITE_EXTERNAL_STORAGE** (Android 9 and older only): to save archive downloads to `Music/SPAZRadio`.

The app does not ask for your location, contacts, microphone or camera.

## 6. Children

SPAZ Radio does not knowingly collect personal information from children under 13. Chat is unmoderated and public, so it is not intended for young children.

## 7. Changes

If this policy changes, the effective date above and the file in the [project repository](https://github.com/greenjon/spazradio) are updated.

## 8. Contact

Questions: [open an issue on GitHub](https://github.com/greenjon/spazradio/issues).
