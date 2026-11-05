# ErgoTube

English · [Русский](README.ru.md)

ErgoTube is an Android YouTube client built for one-handed use. Everything you tap often sits in the lower part of the screen, next to your thumb, on the side of the hand you hold the phone with. You can sign in with your Google account without Google Play Services or microG, or use the app with no account at all. There are no ads, and playback continues in the background.

PiP hotfix: the system cross clears playback even when Android reports dismissal after `onStop`; headphones retain background audio. Validation is limited to the focused PiP action scenario and APK builds, as requested.

Current build: **1.2 "liquid 13"** (versionCode 16), branch `liquid-light-ui`.
- Liquid 14: one network request before playback (cached visitor id), lookup starts on card press, clock bottom-left on the video and always on the remote. APK `apk/ErgoTube-liquid-14-release.apk`.

> ErgoTube is an independent project. It is not affiliated with, endorsed by or connected to YouTube or Google. YouTube is a trademark of Google LLC.

## Features

**Made for one hand**
- A bottom dock ordered left to right: Mine, Subscriptions, Search, Shorts, Home. Mine contains Watch later, Downloads, History, Playlists and Settings. Its width is adjustable; unfolded screens use the full width. A separate setting swaps Mine and Home.
- Every fixed control sits below 55% of the screen height. A test (`ReachRuleTest`) checks this for both hands.
- No on-screen Back buttons. The system Back gesture and the dock are enough.
- Hold a video card and flick toward Watch later, Download, Save, Share, Not interested (with undo) or Channel. Releasing in the centre cancels.
- An optional classic layout that places buttons the way the official YouTube app does. Colours, effects and fonts stay configurable in both layouts.

**Watching**
- The portrait player keeps the video at the top and a control pad under your thumb: play/pause, ±10 seconds (taps add up), hold for 2×, and seeking with a frame preview that gets more precise as you move your finger up.
- Fullscreen opens when you turn the phone, swipe up on the pad or tap a button. The controls gather in a fan in the bottom corner.
- Background playback, picture-in-picture, and a floating mini-player you can drag to any corner or drop on a cross to close.
- Compact quality palettes anchored to HQ in portrait and fullscreen, with separate Wi-Fi and mobile quality limits and Auto. Speed, subtitles, loop and a sleep timer.
- Pinch to zoom and pan in portrait and fullscreen without changing playback. Single picture taps never pause; mini-player taps can open the portrait page or show controls.
- Time stays on the picture; while seeking it also appears on the remote. Returning from the Home mini-player retains the current buffer and position.
- A headphones action in system PiP keeps background audio; Close stops playback. Descriptions and comments support text selection, real links and timestamp seeking, including saved descriptions.
- Hollow like icons until a like is actually set, and a transparent navigation bar in the classic layout.
- Fast start: one short request to YouTube before playback, and the next Short starts loading as soon as you lift your finger.
- SponsorBlock with segments highlighted on the timeline, DeArrow titles and thumbnails, and Return YouTube Dislike. Each can be turned off.

**Your account, or none**
- With an account: recommendations, subscriptions, channels, history, playlists, comments, likes and saving to playlists.
- Without an account: local subscriptions on the device, RSS checks in the background and notifications about new videos from channels you choose.
- Careful mode protects your main account from accidental actions.

**Downloads and offline**
- Download any video or Short. A persistent queue with progress, pause, resume, retry and delete.
- Artwork, captions and metadata are stored on the device, so downloaded videos play with no network.

**Look**
- Light, dark, system and AMOLED themes, plus a classic YouTube dark theme.
- Foldables: a 2–3 column feed when unfolded, a two-column video page, and video on top with controls below in tabletop posture.

## Account warning

Signing in through an unofficial client **breaks YouTube's Terms of Service**. Google may show captchas, limit access for a while or suspend the account. The risk is small, but it is real. **Use a spare account, not your main one.** You can also use ErgoTube without signing in.

ErgoTube collects no data. Account cookies are stored on the phone encrypted with a key in the Android Keystore and are sent only to youtube.com and googlevideo.com. Cloud backup of app data is turned off.

## Install

1. Download the APK from the [Releases](../../releases) page of this repository.
2. Open the file on your phone. Android will ask you to allow installs from this source; allow it.
3. Android 8.0 (API 26) or newer is required.

Debug builds install as `app.freshtuber.debug` and can live next to a release build.

## Build from source

You need:

- JDK 21 (for example `brew install openjdk@21` on macOS);
- Android SDK with platform `android-37.0` and build-tools 37 (through Android Studio or `sdkmanager`).

Point Gradle at the SDK with `sdk.dir=...` in `local.properties` or the `ANDROID_HOME` variable. Then:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug
```

The APK appears at `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk` or copy it to the phone.

A minified release build: `./gradlew assembleRelease`. Without your own signing config it is signed with the debug key, which is fine for personal use but not for distribution.

### Tests

```bash
./gradlew testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest   # needs an emulator or a device
```

InnerTube parsers are tested against saved responses in `app/src/test/resources/innertube`. Playback, picture-in-picture and download instrumentation scenarios use real public YouTube videos and need a network. Account checks use structural fixtures; automated tests never sign in to a personal account.

## Project layout

- `app/` — the single Android module, package `app.freshtuber` (the project's working name before ErgoTube).
- `app/src/main/java/app/freshtuber/innertube/` — ErgoTube's own InnerTube layer; parsers live in `innertube/parse`.
- `docs/DECISIONS.md` — every product and technical decision and the stage plan.
- `docs/BACKLOG.md` — open issues.
- `PRODUCT.md` — who the app is for and its principles.

## Built with

- Kotlin, Jetpack Compose and Material 3
- Media3 (ExoPlayer), Room, DataStore, WorkManager, Hilt, OkHttp, Coil
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) for streams and anonymous data

Data formats were checked against open projects: [NewPipe](https://github.com/TeamNewPipe/NewPipe), [LibreTube](https://github.com/libre-tube/LibreTube), [Metrolist](https://github.com/mostafaalagamy/Metrolist), [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [YouTube.js](https://github.com/LuanRT/YouTube.js).

## License

[GNU GPL v3.0](LICENSE) with an author attribution term under section 7(b), described in [NOTICE](NOTICE).

You may use, change and share the code, but every copy and every modified version must keep the credit **"Based on ErgoTube by aeonist (https://github.com/aeonist/ergotube)"** in its source, its README and the About section of the app. Modified versions must say they were changed. ErgoTube uses NewPipeExtractor, which is GPLv3, so ErgoTube stays GPLv3.

Copyright (C) 2026 aeonist.
