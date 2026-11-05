# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

- **The author**: Russian-speaking, not technical, watches YouTube on an Android phone daily. Confirmed scenes: one-handed on the move (transport, queues, street); lying in bed, often at night in the dark; as background audio while eating or doing chores, barely looking at the screen.
- **Friends and family**: the author plans to publish the project on GitHub and recommend it to friends, who install the APK themselves. They must understand the app without the author explaining it.

## Product Purpose

A personal YouTube client for Android that gives YouTube Premium-level features for free with the user's own Google account: no ads, background playback with the screen off, picture-in-picture on minimize, downloads for offline viewing. It adds ReVanced-style extras: SponsorBlock, Return YouTube Dislike counts, DeArrow titles and thumbnails, brightness and volume swipes, repeat and sleep timer. Success: the author prefers it to the official app every day, and friends who install it from GitHub get it working on their own.

## Positioning

Every frequent action sits on an arc under the thumb: a radial menu anchored in the bottom corner, calibrated to the user's own thumb sweep, with left- and right-hand modes. NewPipe, LibreTube and ReVanced keep YouTube's layout; this app is built around one-handed reach.

## Operating Context

- Phone held in one hand, often while moving; the thumb rests near the bottom corner (right hand by default).
- Dark bedroom at night: glare matters, eyes are adjusted to the dark.
- Background listening with the screen off; controls live in the notification and on the lock screen.
- Android gesture navigation owns the screen edges (back from the sides, home from the bottom).
- Distribution is APK sideloading from GitHub; no Google Play, no Google Play Services.

## Capabilities and Constraints

- Google sign-in through an in-app WebView; anonymous mode (skip sign-in) works with local subscriptions.
- Feeds: recommendations, subscriptions, history, playlists, Watch later, liked, search, comments; like, dislike, subscribe, add to playlist.
- Shorts never appear on Home. A separate Shorts tab with vertical swipe exists and can be turned off.
- Notifications about new videos from subscribed channels, configurable.
- Every extra feature can be switched on or off in settings.
- No Google Play Services, microG, Firebase, analytics or tracking. Account cookies go only to youtube.com and googlevideo.com.
- Signing in with an unofficial client breaks YouTube's terms and can trigger captchas or an account block. The author signs in with their main account, so the app must act on the account only when the user does something.
- UI in Russian and English. License GPLv3.
- Name: ErgoTube (chosen 2026-11-02). App icon: approved cloud-sofa silhouette (5 November 2026).

## Brand Commitments

- Name: ErgoTube (renamed from FreshTuber on 2026-11-02).
- The radial thumb menu is the signature interaction.
- Plain, friendly copy without technical jargon: the primary user is not technical.
- Visual standing preference (chosen 2026-11-01 over four directions): the category standard, a YouTube-like look executed at full craft. The bar is the official YouTube app, Telegram and Spotify. Convention is the commitment: no smuggled quirks. The app icon and logo must not copy YouTube's trademarked logo.
- The stage-5.2 phone-trial revision uses full-width pinned video/title/summary, two approved radial Video/Channel action groups, radial playback settings and an elliptical sleep dial; Home has no reserved empty footer. Navigation remains a radial tree, showing one level at a time with clear labels: sections first, then the chosen section's functions. Home and Library use this model as a trial; Search and Shorts open directly. The previous simultaneous inner/outer arcs belong to the stage-5 implementation and are superseded as the target. See `docs/UX-REVISION.md` for agreed player, Shorts and elliptical calibration behavior. Implementation was explicitly authorized on 2026-11-02 ("да, пиши код").

## Evidence on Hand

Four user-supplied phone screenshots from 2026-11-02 show ambiguous radial selection and the portrait player's misplaced red button. A comparison mockup led to the agreed “Свободный видеокадр” trial; these are design evidence, not proof of implementation. No user counts, ratings or testimonials exist. Do not invent stars, download numbers, reviews or claims about friends using it.

## Product Principles

1. Reach first: anything done often is reachable by the thumb without changing grip.
2. Nothing between the user and the video: no ads, no clutter, no dark patterns.
3. Careful with the account: act on the user's account only on an explicit action, and be honest about the risks.
4. Every extra is a choice: sensible defaults, everything can be switched off.
5. Explains itself: a friend installing from GitHub understands it without help.

## Accessibility & Inclusion

- One-handed use with left- and right-hand modes; touch targets at least 48dp; nothing interactive inside the system gesture zones.
- Text in sp, following the system font size.
- Comfortable in a dark room at night: no glaring full-white surfaces in the dark theme.
- Respect the system "Remove animations" setting.


Stage 5.3 revision (2026-11-03): Home opens All directly; Settings is a root action. Compact 64dp radial slots fill consecutively bottom to top, with 300ms branch dwell. Mini-player is below the anchor; portrait Video/Channel launchers form a vertical hand-side column and preserve drag selection. Shorts horizontal inset uses safeDrawing plus 4dp. See docs/DECISIONS.md for scope and validation limits.

Stage 5.7 (2026-11-03): user-approved portrait trial replaces the red navigation launcher with fullscreen and a persistent five-button playback quarter arc (back 10s, play/pause, forward 10s, next, settings). Settings open around the pressed gear, including fullscreen. Video/Channel action slots use one fixed layout origin; only Close replaces the specific launcher, with no intermediate wrong-origin frame. Portrait player content uses safeDrawing horizontal insets rather than broad gesture padding. The video surface stays in one composition location while pinning changes its scroll offset and a matching lazy-list placeholder. Mini-player background extends through navigation-bar padding; there is no exposed feed strip below it.

Home and related recommendation cards have a three-dot contextual fan: Not interested, Save, Share, disabled Download. Action centers are equally far from the pressed dots. Full action labels fit the 72dp preferred targets; layout adapts inward near edges, retaining 48dp minimum if needed. Local hiding persists on the device and applies only to recommendations, not saved lists/search. Signed-in Not interested replays the response's matching feedback command through ActionGuard when available; otherwise hiding is local. Save reuses the guarded playlist picker; Share uses Android's chooser. Download intentionally has no action.

Mixes (RD playlists) load through watch-next playlist panels with seed video and playlistIndex continuation, rather than ordinary VL browse requests. Only the playlist panel is parsed, so related recommendations cannot leak into the mix list.


Brand revision (5 November 2026): the user selected cloud-sofa, without lettering, as the application mark and launcher icon. This supersedes the historical fan-mark commitment. Apricot on wine for the launcher; apricot on dark and wine on light surfaces inside the app.
