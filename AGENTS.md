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

After every stage: build must pass, all unit tests green, check on the emulator, copy the APK to `apk/ErgoTube-stageN-debug.apk` for the user, update `README.md` status and `docs/DECISIONS.md`.

## Rules
- Code and comments in English; UI strings in `res/values` and `res/values-ru`.
- Account cookies go only to youtube.com / googlevideo.com; never log cookies or tokens.
- InnerTube parsers live in `innertube/parse`, tested on saved responses in `app/src/test/resources/innertube`.
- Account actions go through `ActionGuard` (careful mode for the user's main account).
- Verify library APIs and YouTube formats against sources (NewPipeExtractor, yt-dlp, YouTube.js, Metrolist) instead of guessing.

## Status (3 Nov 2026)
Done: stages 1–5 (skeleton, Google login, account feeds/video page/comments/Shorts/DeArrow/RYD, player with background/PiP/mini-player/SponsorBlock, radial tree navigation with calibration and handedness). The 4 stage-4 backlog fixes are implemented. 92 tests green. Stage 5.1 implements the five user UX fixes: one-level radial navigation, independent elliptical calibration, revised portrait/fullscreen controls and pinning, persistent Shorts actions and anonymous next-Shorts preparation. APK: `apk/ErgoTube-stage5.1-debug.apk`. The finish verdict scored all eight material fixes resolved; report: `.impeccable/review/stage5.1-review.md`. Stage 5.2 supersedes the action-column trial: full-width pinned video/title/summary, floating Video/Channel radial groups, hand-side fullscreen Back/Next, radial player settings and an elliptical sleep dial, compact Shorts summary/actions, no empty Home footer, fixed unpin scroll and TTML subtitle source. APK: `apk/ErgoTube-stage5.2-debug.apk`. Review: `.impeccable/review/stage5.2-review.md`. Hardware comfort remains a user trial. Account Home is covered by structural fixtures; emulator checks remain anonymous.

Latest trial: stage 5.7 adds recommendation-centered action fans, permanent portrait playback controls, gear-anchored settings, stable close/arc origins, retained video surface on pin changes and RD mix loading. APK `apk/ErgoTube-stage5.7-debug.apk`; 104 tests pass. See latest DECISIONS/BACKLOG and review for validation limits.

## Next, in order
1. Stage 6 — local subscriptions without an account + new-video notifications (WorkManager, anonymous RSS). Takeout/NewPipe import is optional, later.
2. Stage 7 — settings screen for all toggles in `AppSettings`, errors, offline, expired sign-in.
3. Stage 8 — downloads and offline playback.
