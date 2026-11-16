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

After every stage: build must pass, all unit tests green, check on the emulator, copy the APK to `apk/ErgoTube-stageN-debug.apk` for the user, add the build to `CHANGELOG.md` and `docs/DECISIONS.md`. `README.md` (English) and `README.ru.md` (Russian) are the project's showcase, not a build log: touch them only when a feature list, install step or image needs to change. README images live in `docs/media/` (see `docs/README.md`).

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

## Build 27 (12 Nov 2026)
Tester polish: two-row portrait remote, Back always leaves the player, quiet playback recovery, Music/ErgoTube export with tags and marker, labelled jam shelves, update banner from GitHub releases (needs a public repo; `UPDATE_REPO` in app/build.gradle.kts). Release tags must name `buildN`. 181 JVM tests. The user asked to stop emulator testing after it crashed; do not run long emulator sessions unasked. APKs `apk/ErgoTube-build27-*.apk`.

## Build 28 (12 Nov 2026)
One «Музыка» page, ear above the shared dock on Home, single «Любимое» with offline hiding, heart instead of «В библиотеку», 7-button player remote with a three-face scene, queue «Режим», Музыка/Видео switch in Search, «Музыка в приложении» switch. Built and unit-tested only, not run on a device. See docs/DECISIONS.md «Build 28». APK `apk/ErgoTube-build28-music-debug.apk`.

## Build 29 (12 Nov 2026)
Every appear and hide goes through a blur: sheets (`BlurSheet` replaces `ModalBottomSheet`), dock, ear, mini-player, «Моё» tiles, update banner, chips/search bars hiding on scroll, pad, fan items, Shorts caption and the music-player cover swap come into focus out of a blur with a slight scale and blur away; nothing slides. Helpers in `ui/motion/Motion.kt` (`blurFade`, `blurIn`, `blurOut`, `BlurVisibility`, `blurVisible`, `blurSwap`). AlertDialogs and dropdown menus stay the system ones. 186 JVM tests, not run on a device. APK `apk/ErgoTube-build29-blur-debug.apk`.

## Build 30 (12 Nov 2026)
Precise fullscreen seek: the fullscreen timeline (classic bar and the fan's scrub arc/drag) zooms to a fixed stretch of time, 12% of the video clamped to 45 s–5 min (`TimelineWindow.zoomAtPrecise`, `Scrub.preciseSeconds`), so the handle runs two to three times slower; lifting the finger 48 dp above the classic bar makes it five times finer. The fullscreen corner fan is about 10% smaller. 188 JVM tests, not run on a device. APK `apk/ErgoTube-build30-precise-seek-debug.apk`.

## Build 31 (12 Nov 2026)
Background light: pictures are mirrored across their nearest edge and blur smoothly with distance down to the mean colour (`glow` in `LiquidShaders`), no edge stripes. Feed focus measures across as well, holds until another card is clearly nearer, waits for pictures, and follows the row last swiped sideways (middle card). «Музыка» ear sits on top of Home's chip row (in Home's BottomBar). Music: previous/play/next at the thumb edge of the remote, full-screen queue with «Режим» at the bottom, no «Лайк на YouTube» in the song sheet. Not run on a device. APK `apk/ErgoTube-build31-soft-light-debug.apk`. Second pass: music big view with a taller remote (shuffle, 3-state repeat, prev/play/next at the thumb edge, labelled Скачать/Текст/Очередь/Ещё, heart by the title); lyrics and queue are whole pages with the music line at the bottom; queue handle away from the thumb; ear hidden while music plays.

## Build 32 (13 Nov 2026)
Fullscreen: no bottom timeline while the fan is hidden; scrubbing (fan and classic bar) starts in place and runs slower than a slow finger, fully on a quick sweep (`GlideScrub`). «Ленты сверху вниз» (`TraditionalScroll`, key `scroll_top_down`) on by default, the music queue follows it. Mini-player play/pause top left. Music «Удалить с устройства» for downloads and phone files (MediaStore consent). «Управление как в YouTube» setting (portrait tap overlay, off by default). `MusicEar` switch hides the Home music ear. Update banner reads the build from tag/title/APK name, falls back to the publish date, checks on every start. Search chips fold softly with the finger (`ScrollFold`). Not run on a device. APKs `apk/ErgoTube-build32-*.apk`.

## Build 33 (13 Nov 2026)
Build 32 plus the music pulse: the music player's background light follows the song (`AudioPulse` tap in the music ExoPlayer's audio chain, no microphone permission; `LiquidLight.stepPulse`, shader uniforms `uPulse`/`uBeat`; switch `AppFeature.LiquidPulse`). Two instrumentation tests (`LiquidShaderCompileTest`, `AudioPulseIntegrationTest`) ran on the emulator; not looked at on a phone. APK `apk/ErgoTube-build33-release.apk`.

## Build 34 (14 Nov 2026)
Build 33 plus «Поддержать автора»: a card above the account row in Settings (hidden when nothing is configured) opens a `BlurSheet` (`ui/screens/settings/SupportSheet.kt`). All values live in `data/support/DonateLinks.kt`: CloudTips (Russia), lava.top (abroad), USDT wallets as a list of network + address (TON first with a `ton://transfer` jetton link, TRC20 copy only), each with a network warning. Not run on a device. APK `apk/ErgoTube-build34-donate-release.apk`.

## Build 35 (14 Nov 2026)
Build 34 with applicationId `app.ergotube` (Kotlin namespace and packages stay `app.freshtuber`; debug keeps the `.debug` suffix) and its own release key. `signingConfigs.release` reads the gitignored `keystore.properties`; the keystore lives in the gitignored `signing/` folder of this checkout (never commit it, never print the password). Without that file release falls back to the debug key, which cannot update a phone that has Build 35. New app for Android: the old `app.freshtuber` install stays and must be removed by hand. APK `apk/ErgoTube-build35-release.apk`.

## Build 38 (16 Nov 2026)
Fullscreen scrubbing is a fixed 1/3 of the finger (`SlowScrub`, replaces `GlideScrub` and the fullscreen fine mode). Fullscreen time sits at the start of the timeline arc. Channel header shows @handle, subscribers and videos (`ChannelHeaderParser`) and a bell for the local new-video notifications. Released from `main` as build38.
