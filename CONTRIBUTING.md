# Contributing

Fixes and ideas are welcome. Open an [issue](https://github.com/greenjon/spazradio/issues) first for anything big, or just send a pull request for small things.

- Build and test with `./gradlew assembleDebug testDebugUnitTest lintDebug` (JDK 17). CI runs the tests and lint before it publishes anything.
- Do not edit `lsd-visuals/` here. It is a copy of the liquid-lsd engine; change it in liquid-lsd and run `scripts/sync_visuals.sh`.
- New user-visible text goes in `strings.xml` and all of `values-de`, `values-es` and `values-fr`.
- If a change adds a server or a kind of data the app sends, update `PRIVACY.md`.
- `docs/audit.md` describes the review checklist used before releases. Skim the lane that matches what you touched.
