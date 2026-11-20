# Changelog

What changed in each ErgoTube build, newest first. Release APKs are on the [Releases](https://github.com/aeonist/ergotube/releases) page. The reasoning behind every change, and how it was checked, lives in [`docs/DECISIONS.md`](docs/DECISIONS.md).

Builds are numbered by `versionCode`. A release tag or APK name that contains `buildNN` is what the in-app update banner compares against.

## Build 39 · 18 Nov 2026

- Home and channel pages: the buttons at the bottom sit in **three evenly spaced rows**. On a channel page, **Subscribe and the bell** have a row of their own above the tabs.
- The player **follows your finger when you pull it down from anywhere** on its page: the video, the remote, the buttons, the description or the list (a list scrolls first and pulls from its top). Let go far enough, or flick down, and it collapses into the mini-player; otherwise it springs back. How far is enough depends on the room left below your finger, so a pull from the remote near the bottom works too.
- The **music player closes when you pull it down from anywhere**, like in Yandex Music, with the same rule for how far, on the lyrics and queue pages too. Lists scroll first and pull only from their top; the timeline and cover swipes work as before. The music keeps playing in the dock line.
- Pulling either player down now **goes out of focus instead of sliding**: the page follows your finger, shrinks a little and blurs as you pull; let go and it blurs away where it is while the mini-player or the song line comes into focus. If you don't pull far enough it sharpens back. The video moves together with the page and no longer jumps out of it into the mini-player.
- Home and channel pages: the **dock sits lower**, much closer to the bottom edge, and the gaps between the rows above it are a little wider and even.
- **«As music» under a video works again**: the song now really takes over from the same second, the video stops exactly when the song starts, and Play while it is still loading no longer restarts the song from the beginning. If the song can't be found, the video simply keeps playing.

## Build 38 · 16 Nov 2026

- Full screen: dragging along the timeline always moves the marker at **one third of your finger's speed**, slow or fast, so you can land on the exact moment. The marker starts where it is.
- Full screen: the elapsed / total time sits at the start of the timeline, on the bottom edge, like YouTube's.
- Channel pages show the **@handle, subscriber count and number of videos** under the name.
- A **bell** next to Subscribe on the channel page turns on notifications about the channel's new videos.

## Build 37 · 14 Nov 2026

- The interface defaults to English. Hand & look offers English, Russian, and the phone language.
- **Account** is immediately left of **Settings** in **Mine**.
- **Support the author** is immediately left of **Music** in Settings.

## Build 36 · 14 Nov 2026

- Channel pages switch between **Home, Videos, Shorts, Live, Playlists** (and Podcasts, Releases, Courses where a channel has them). The switch sits at the bottom, under the thumb.
- English first: «Support the author» lists card and PayPal first and the Russian payment page last; a Russian interface keeps the Russian page on top.

## Build 35 · 14 Nov 2026

- The app is now **`app.ergotube`** and is signed with ErgoTube's own release key.
- ⚠️ Android treats it as a new app: it installs next to the old ErgoTube (`app.freshtuber`) instead of over it. Sign in again and uninstall the old one by hand. Every later build updates over this one.

## Build 34 · 14 Nov 2026

- **Support the author** card in Settings: CloudTips (Mir, SBP) from Russia, lava.top (card, PayPal) from abroad, and USDT on TON or TRC20 with a network warning. Tap to copy; on TON a wallet app opens.

## Build 33 · 13 Nov 2026

- In the music player the light around the cover **breathes with the song**: brighter on loud passages, a short flash on a bass kick. It listens inside the app's own player, so no microphone permission. Switch: Settings → Energy → «Background to the music».

## Build 32 · 13 Nov 2026

- Fullscreen: no timeline while the video simply plays; seeking glides slower than the finger from where the handle is, and a quick sweep still crosses the whole video.
- Lists read top to bottom by default, the music queue included (one switch in Hand & look).
- Mini-player play/pause moved to its top left corner.
- Music: delete a song from the phone, downloads and phone files alike.
- New «YouTube-style controls» for the portrait player and a switch that hides the Music ear on Home.
- The update banner reads the build number from a release's tag, title or APK name and checks on every start.
- Search chips fold away softly with the finger.

## Build 31 · 12 Nov 2026

- Background light without edge stripes: pictures are mirrored at their edge and blur smoothly down to their mean colour.
- The feed light follows the row you last swiped sideways and waits for pictures to load.
- Music: previous/play/next at the thumb edge of the remote, a taller big view with shuffle and three-state repeat, lyrics and queue as whole pages.

## Build 30 · 12 Nov 2026

- **Precise fullscreen seek**: the timeline zooms to 12% of the video (45 s to 5 min), so the handle runs two to three times slower; lifting the finger above the bar makes it five times finer.
- The fullscreen corner fan is about 10% smaller.

## Build 29 · 12 Nov 2026

- **Every appear and hide goes through a blur**: sheets, dock, mini-player, tiles, banners, chips, the pad, fan items and the music cover come into focus and blur away. Nothing slides.

## Build 28 · 12 Nov 2026

- One **Music** page with labelled jam rows and a single «Favourites» library.
- Heart instead of «Add to library», a seven-button music remote with cover, lyrics and queue in one place.
- Music / Video switch in Search, and a «Music in the app» switch that removes music entirely.

## Build 27 · 12 Nov 2026

- Two-row portrait remote that fits folded phones; Back always leaves the player.
- Quiet reconnect after network drops, faster Shorts start, a springier card fan.
- Music downloads saved as tagged files in `Music/ErgoTube`; «My jam» first; duplicate cleanup.
- Update banner for new GitHub releases.

## Build 26 · 11 Nov 2026

- Fullscreen fan buttons no longer overlap; elapsed time hides with the other controls.

## Build 25 · 11 Nov 2026 — ErgoTube Music v2

- A music mode in the same liquid light: «Listen», a player lit by the cover, lyrics, a three-layer queue, one library for phone files and YouTube songs, and the song line inside the dock.

## Build 24 · 9–10 Nov 2026

- Pull to refresh, bottom-up lists with a traditional option, a mirrored dock.
- Audio-only background button, finer timeline scrubbing, Previous in Android media controls, Subscribe next to the channel name.
- English, Russian or system language on Android 13+. Faster Shorts start; Home scrolls to the top on tap.

## 1.2 «liquid» test builds · 5 Nov 2026

- **Liquid light** design: the background takes soft light from the picture on screen.
- Cloud-sofa logo across the app, launcher and notifications.
- One network request before playback (about 0.3 s instead of 2–3 s), quality chosen by connection speed.
- Foldable layouts, the classic YouTube look as an option, SponsorBlock segments on the timeline.
- Pinch to zoom, compact quality palettes, selectable descriptions with working links and timestamps, PiP headphones action.

## 1.1 · 4 Nov 2026 — the dock

- The radial menu is replaced by a **hand-side bottom dock** and bottom bars on every screen. Every fixed control sits below 55% of the height (`ReachRuleTest`).
- Hold-and-flick card fan, portrait player with the pad under the thumb, fullscreen corner fan.
- 1.1.1: no on-screen Back buttons, compact headers, chips that hide on scroll, a draggable floating mini-player, Shorts timeline.

## 1.0.0 · 3 Nov 2026 — stages 6 to 8

- Local subscriptions with background RSS checks and notifications, bottom search.
- Complete settings, themes and recoverable errors.
- Persistent downloads with pause, resume, retry and delete; artwork, captions and metadata on the phone; offline playback.

## Stages 1 to 5 · 1–3 Nov 2026

- Project skeleton, Google sign-in without Play Services, account feeds, the video page, comments, Shorts, DeArrow and Return YouTube Dislike.
- Player with background playback, picture-in-picture, mini-player and SponsorBlock.
- The first one-handed navigation: a radial menu calibrated to the user's thumb (later replaced by the dock).
