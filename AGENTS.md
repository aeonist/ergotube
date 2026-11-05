# ErgoTube — notes for coding agents

Android YouTube client (Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, Media3, NewPipeExtractor, own InnerTube layer). Package `app.freshtuber`, single module `app`. License GPLv3.

## Read first
- `PRODUCT.md` — who it is for and product principles.
- `docs/DECISIONS.md` — every product and technical decision so far, and the stage plan.
- `docs/BACKLOG.md` — open issues found by the user. **Do these first.**
- `.impeccable/surfaces/app-src-main-java-app-freshtuber-ui.md` — visual direction contract (YouTube-like canon, radial menu as signature).

## The user
Not technical, writes in Russian. Decide technical matters yourself; ask only product/UX questions, in plain Russian, with a recommended option. Explain results briefly and simply. Never push to GitHub or publish unless asked. From 3 Nov 2026, the user explicitly requests commits for completed changes; commit after validation.

## Build and test
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug testDebugUnitTest
```
Android SDK: `/opt/homebrew/share/android-commandlinetools` (see `local.properties`). Emulator AVD `FreshTuber_API37`:
```bash
/opt/homebrew/share/android-commandlinetools/emulator/emulator -avd FreshTuber_API37 -no-window -no-audio -gpu swiftshader_indirect -no-snapshot-save &
```
The emulator's hardware video decoders always fail; the player skips them and uses software ones (expected).

After every stage: build must pass, all unit tests green, check on the emulator, copy the APK to `apk/ErgoTube-stageN-debug.apk` for the user, update the status in `README.md` (English) and `README.ru.md` (Russian) and `docs/DECISIONS.md`.

## Rules
- Code and comments in English; UI strings in `res/values` and `res/values-ru`.
- Account cookies go only to youtube.com / googlevideo.com; never log cookies or tokens.
- InnerTube parsers live in `innertube/parse`, tested on saved responses in `app/src/test/resources/innertube`.
- Account actions go through `ActionGuard` (careful mode for the user's main account).
- Verify library APIs and YouTube formats against sources (NewPipeExtractor, yt-dlp, YouTube.js, Metrolist) instead of guessing.

## Status (3 Nov 2026)
Done: stages 1–5 (skeleton, Google login, account feeds/video page/comments/Shorts/DeArrow/RYD, player with background/PiP/mini-player/SponsorBlock, radial tree navigation with calibration and handedness). The 4 stage-4 backlog fixes are implemented. 92 tests green. Stage 5.1 implements the five user UX fixes: one-level radial navigation, independent elliptical calibration, revised portrait/fullscreen controls and pinning, persistent Shorts actions and anonymous next-Shorts preparation. APK: `apk/ErgoTube-stage5.1-debug.apk`. The finish verdict scored all eight material fixes resolved; report: `.impeccable/review/stage5.1-review.md`. Stage 5.2 supersedes the action-column trial: full-width pinned video/title/summary, floating Video/Channel radial groups, hand-side fullscreen Back/Next, radial player settings and an elliptical sleep dial, compact Shorts summary/actions, no empty Home footer, fixed unpin scroll and TTML subtitle source. APK: `apk/ErgoTube-stage5.2-debug.apk`. Review: `.impeccable/review/stage5.2-review.md`. Hardware comfort remains a user trial. Account Home is covered by structural fixtures; emulator checks remain anonymous.

Latest trial: stage 5.7 adds recommendation-centered action fans, permanent portrait playback controls, gear-anchored settings, stable close/arc origins, retained video surface on pin changes and RD mix loading. APK `apk/ErgoTube-stage5.7-debug.apk`; 104 tests pass. See latest DECISIONS/BACKLOG and review for validation limits.

## Current release
Stages 6–8 complete as ErgoTube 1.0.0 (versionCode 2). Local RSS subscriptions and notifications, bottom search, complete settings/theme/recovery, persistent WorkManager downloads with pause/resume/retry/delete, local artwork/captions/metadata and offline playback. Downloads use separate progressive video/audio files merged by Media3, no muxing dependency. Room schema 4 migrates every released schema 1–3. 113 JVM tests and 10 API37 instrumentation tests pass; lint has zero errors, Debug and minified Release build successfully. The final phone/emulator validation and limitations live in docs/DECISIONS.md and .impeccable/review/stage8-review.md. APK: apk/ErgoTube-stage8-debug.apk. User revoked the original five-hour limit and requests autonomous testing; do not ask them to carry out QA.

## Version 1.1 — variant A (4 Nov 2026)
Branch `variant-a-ui`: the radial navigation is replaced by the mockup's variant A (`.impeccable/mocks/ergotube-variant-a-final.html`): hand-side bottom dock with «Моё», bottom bars on every screen (reach rule: fixed controls below 55% of the height, enforced by `ReachRuleTest`), hold-and-flick card fan, portrait player with the pad under the thumb and a fullscreen corner fan. The radial menu, calibration and sleep dial code are deleted. 114 JVM and 12 API37 instrumentation tests pass, lint zero errors. APK `apk/ErgoTube-1.1-variantA-debug.apk`. See docs/DECISIONS.md "1.1".

## Version 1.1.1 (4 Nov 2026)
User fixes on variant A: no on-screen Back buttons anywhere, Subscriptions source follows the account (no switch), compact 26sp headers, Home chips hide on scroll, draggable floating mini-player (TextureView), compact pad that hides while reading the list, pin/unpin in the watch actions row, Shorts timeline. 117 JVM and 12 instrumentation tests pass. APK `apk/ErgoTube-1.1.1-variantA-debug.apk`. Shut the emulator down after testing (user request).

## Optional future work
Takeout/NewPipe subscription import is outside the completed eight-stage plan. Do not start it without a new request. Continue committing validated changes; never publish or push without authorization.
