<div align="center">

<img src="docs/media/hero.jpg" alt="ErgoTube: YouTube that lives under your thumb" width="100%">

<h3>An Android YouTube client built around one thumb.</h3>

<p>
<a href="https://github.com/aeonist/ergotube/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/aeonist/ergotube?include_prereleases&style=for-the-badge&label=download&color=F3C39E&labelColor=2a1a1d"></a>
<a href="LICENSE"><img alt="GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-F3C39E?style=for-the-badge&labelColor=2a1a1d"></a>
<img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-F3C39E?style=for-the-badge&logo=android&logoColor=white&labelColor=2a1a1d">
</p>
<p>
<img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
<img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white">
<img alt="Media3" src="https://img.shields.io/badge/player-Media3%20ExoPlayer-34A853?style=flat-square">
<img alt="Tests" src="https://img.shields.io/badge/tests-230%2B-2ea44f?style=flat-square">
<img alt="No Google Play Services" src="https://img.shields.io/badge/Google%20Play%20Services-not%20needed-555?style=flat-square">
</p>

**English** · [Русский](README.ru.md)

<a href="https://github.com/aeonist/ergotube/releases/latest"><b>⬇ Download the latest APK</b></a> · <a href="#features">Features</a> · <a href="#why-its-good">Why</a> · <a href="#how-it-compares">Compare</a> · <a href="#install">Install</a> · <a href="#build-from-source">Build</a> · <a href="#support-the-project">Donate</a> · <a href="CHANGELOG.md">Changelog</a>

</div>

---

YouTube apps keep search, tabs and menus at the top of the screen, where your thumb can't reach. ErgoTube moves them to the bottom.

No ads. Background play. Downloads. Music mode. Sign in with Google (no microG needed) or use it without an account.

<img src="docs/media/carousel.jpg" alt="Five ErgoTube screens side by side: sign-in, Home, the action fan, the player and Search" width="100%">

<table>
<tr>
<td valign="top" width="33%">

### The action fan

Hold a video and six actions open around your finger. Slide to one and let go.

</td>
<td valign="top" width="33%">

### The player

All controls are under your thumb. Turn the phone sideways and they move into the corner.

</td>
<td valign="top" width="33%">

### Motion

Menus appear through a soft blur. The background glows in the colors of the video.

</td>
</tr>
</table>

<img src="docs/media/reach.jpg" alt="Every fixed control sits in the bottom half of the screen" width="100%">

<p align="center"><img src="docs/media/fullscreen.jpg" alt="Fullscreen with the corner fan and the arc timeline" width="90%"><br><sub>In fullscreen the controls sit in a fan in the corner, and you seek by sweeping your thumb along the arc.</sub></p>

## Why it's good

**Extremely flexible.** Left hand, right hand, five themes, and the classic YouTube layout for people who enjoy suffering.

**Fast.** One request to YouTube instead of seven. The video is ready before you've decided to watch it.

**The best design known to mankind.** Obviously.

**Private.** No servers, no analytics. We couldn't sell your data even if we wanted to. We don't.

**Free.** No ads, no subscription, open source. You're welcome.

**Saves data.** Uses VP9 when your phone supports it: about a third less traffic for the same picture. Sorry, carriers.

**Tested.** Over 230 automated tests. We sleep fine.

## Features

<table>
<tr>
<td valign="top" width="50%">

**One hand**
- Buttons on the side of your hand, mirrored for the left hand
- Everything you tap is in the bottom half of the screen
- Optional classic YouTube layout

**Watching**
- Background play, picture-in-picture, a mini-player you can drag
- Precise seeking in fullscreen
- Quality, speed, captions, sleep timer
- SponsorBlock, DeArrow, Return YouTube Dislike

</td>
<td valign="top" width="50%">

**Account**
- Sign in with Google to get your feed, subscriptions, history and playlists
- Or no account: subscriptions are kept on the phone and you still get new-video notifications

**Music**
- Mixes, synced lyrics, a queue
- YouTube songs and the music files on your phone in one library

**Downloads**
- Videos, Shorts and songs, playable offline

</td>
</tr>
</table>

## How it compares

NewPipe and LibreTube are good YouTube clients. ErgoTube differs mainly in layout: everything is reachable with one thumb.

| | **ErgoTube** | NewPipe | LibreTube |
|---|:---:|:---:|:---:|
| Every control in the thumb zone | ✅ | — | — |
| Hold-and-flick card actions | ✅ | — | — |
| Sign in with a Google account, no microG | ✅ | — | — |
| Use without any account | ✅ | ✅ | ✅ |
| Background play and picture-in-picture | ✅ | ✅ | ✅ |
| Downloads and offline playback | ✅ | ✅ | ✅ |
| SponsorBlock | ✅ | — | ✅ |
| Music mode with lyrics | ✅ | — | — |

<sub>Based on the standard builds of each app as of November 2026. Forks may add more.</sub>

## Install

1. Open [Releases](https://github.com/aeonist/ergotube/releases/latest) on your phone and download `ErgoTube-buildNN-release.apk`.
2. Open the file. Android asks to allow installs from this source; allow it.
3. Android 8.0 (API 26) or newer is required. No Google Play Services needed.

The app shows a banner when a new release is published here.

> [!WARNING]
> Signing in through any unofficial client breaks YouTube's Terms of Service. Google may show captchas, limit access for a while or suspend the account. The risk is small, but use a spare account rather than your main one, or use ErgoTube without signing in.

## Privacy

ErgoTube has no servers and collects no data. Account cookies are encrypted with a key in the Android Keystore and are sent only to `youtube.com` and `googlevideo.com`. Cloud backup of app data is turned off. The only other network calls are the optional SponsorBlock, DeArrow and Return YouTube Dislike lookups, song lyrics from LRCLIB, and the update check against this repository's releases.

## Build from source

You need JDK 21 and the Android SDK with platform `android-37.0` and build-tools 37 (through Android Studio or `sdkmanager`). Point Gradle at the SDK with `sdk.dir=…` in `local.properties` or `ANDROID_HOME`.

```bash
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest       # 200+ JVM tests
./gradlew lintDebug
./gradlew connectedDebugAndroidTest   # instrumentation tests, needs an emulator or a phone
```

Debug builds install as `app.ergotube.debug` next to a release build. `./gradlew assembleRelease` makes a minified release; without your own `keystore.properties` it is signed with the debug key, which is fine for personal use.

InnerTube parsers are tested against saved responses in `app/src/test/resources/innertube`. Automated tests never sign in to a personal account.

## Project structure

```
app/src/main/java/app/freshtuber/
├── innertube/    own InnerTube client: signed requests, cookie rotation, parsers
├── player/       Media3 playback service, stream resolver, PiP, music player, audio pulse
├── data/         account, feeds, downloads, music library, subscriptions, settings, updates
├── background/   WorkManager jobs: downloads, RSS checks, notifications
├── core/         Hilt modules, networking, NewPipe bridge, Keystore encryption
├── domain/       plain models shared by every layer
└── ui/           Compose screens, liquid-light shaders, blur motion, dock and fan
```

Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, WorkManager, Media3 (ExoPlayer), OkHttp, Coil and [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) for anonymous streams. The package is still `app.freshtuber`, the project's working name; the installed app id is `app.ergotube`.

Product and technical decisions are recorded in [`docs/DECISIONS.md`](docs/DECISIONS.md). Start at [`docs/README.md`](docs/README.md) for a map of the docs, and at [`CONTRIBUTING.md`](CONTRIBUTING.md) if you want to help.

## Support the project

ErgoTube is free. If you'd like to support it:

<p>
<a href="https://app.lava.top/2097792502?tabId=donate"><img alt="Donate by card or PayPal" src="https://img.shields.io/badge/Donate-card%20or%20PayPal-F3C39E?style=for-the-badge&labelColor=2a1a1d"></a>
</p>

USDT (send only USDT, and only on the network named):

- TON: `UQCE_gE_8LGNM9_8WztoWQcTSqRh3E2uY9CrTstU5IOIv_R2`
- TRON (TRC20): `TUzoKgYS74SmxA32a9hqkiPrTyfFKtRaxC`

The same options are in the app under Settings → Support the author.

## Thanks

Formats and ideas were checked against [NewPipe](https://github.com/TeamNewPipe/NewPipe), [LibreTube](https://github.com/libre-tube/LibreTube), [Metrolist](https://github.com/mostafaalagamy/Metrolist), [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [YouTube.js](https://github.com/LuanRT/YouTube.js). Segment data comes from [SponsorBlock](https://sponsor.ajay.app), titles from [DeArrow](https://dearrow.ajay.app), dislikes from [Return YouTube Dislike](https://returnyoutubedislike.com), lyrics from [LRCLIB](https://lrclib.net).

## License

[GNU GPL v3.0](LICENSE) with an author attribution term under section 7(b), described in [NOTICE](NOTICE). You may use, change and share the code, but every copy and every modified version must keep the credit **"Based on ErgoTube by aeonist (https://github.com/aeonist/ergotube)"** in its source, its README and the About section of the app, and modified versions must say they were changed.

Copyright © 2026 aeonist.

<sub>ErgoTube is an independent project and is not affiliated with YouTube or Google. YouTube is a trademark of Google LLC.</sub>
