---
version: 1
slug: "app-src-main-java-app-freshtuber-ui"
primary_target: "app/src/main/java/app/freshtuber/ui"
related_targets: []
---

## Scope

App shell and every working screen of ErgoTube on Android phones (Home and other feeds, Search, Library, Account, Settings, the player overlay, the radial thumb menu). Visitor mode: Operate. Built code-led, in Jetpack Compose with Material 3.

## Audience and job

The author and friends who install the APK from GitHub. One-handed on the move, in bed in the dark, or listening in the background. Job: find something to watch fast, keep up with subscriptions, control playback, all without changing grip.

## Direction contract

Stage 5.2 revision (2026-11-03) follows the user’s phone trial: full-width pinned video/title/summary, two approved radial groups Video/Channel at the working-hand corner, radial player settings with nested quality/speed choices and an elliptical sleep dial. Fullscreen Back/Next move to the working hand; title and seek controls have independent insets. Home has no reserved bottom strip; Shorts summary scrims fit a channel line and a single title line with tap-to-expand. See `docs/DECISIONS.md` for exact behavior. These changes supersede the earlier action-column trial.

Revision agreed 2026-11-02: `docs/UX-REVISION.md` supersedes the interaction target described below. Navigation shows one labeled level at a time; reach calibration is elliptical with independent axes. The portrait player uses the “Свободный видеокадр” trial, bottom navigation/pin controls and a sliding action column; Shorts actions stay visible. The simultaneous arcs below describe stage 5 only. Implementation was explicitly authorized on 2026-11-02 ("да, пиши код").

THESIS: The YouTube app people already know, executed at full craft and driven entirely from an arc under the thumb. It refuses top tab strips, chip rows and a bottom bar as the way to move around: navigation lives on the radial menu.

OWN-WORLD: Near-black ground (#0F0F0F) with stepped neutral surfaces (#181818, #212121, #272727, #3F3F3F), primary text #F1F1F1, secondary #AAAAAA. Brand red (#FF0033) only for watch progress, live badges, the logo and the menu anchor. Interactive primary is white with black text, links blue (#3EA6FF). Light theme mirrors it on white. Roboto on the Material type scale. Thumbnails 16:9 with 12dp corners and a duration badge. Rounded 48dp+ radial buttons on neutral discs.

STORY: The visitor opens the app, sees a clean feed of real thumbnails, taps the anchor under their thumb, chooses a labeled section and then a function on the same arc, one level at a time. They understand it is YouTube without the ads, made for one hand.

FIRST VIEWPORT: Top: small ErgoTube wordmark with its own fan-shaped red mark, account avatar on the right. Below: a full-width feed of 16:9 thumbnail cards (12dp side margins), each with channel avatar, two-line title and meta line. Current filter shown as a quiet label under the wordmark, not as chips. Bottom corner on the dominant-hand side: the red anchor button, inset from the gesture zones; opened, it shows one labeled elliptical arc, replacing sections with the chosen section's functions. On the watch page the video stays full width, actions sit below beside the content and slide away on scroll; the bottom dock keeps navigation, pin and fullscreen access. Fullscreen tap unfolds playback controls immediately; Shorts reactions remain visible.

FORM: Category standard (the canon), chosen by the user from four directions; reference bar YouTube, Telegram, Spotify. seed:a0645404eb40c7b1

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Open decisions

- Stage 5 finish review completed on 2026-11-02: the reviewer scored its three material fixes resolved (cross-screen filters, portrait player spacing, outer labels). Evidence and verdict: `.impeccable/review/stage5-review.md`; system tokens: `DESIGN.md` and `.impeccable/design.json`.
- App name is settled: ErgoTube. The final icon remains open.


Stage 5.3 revision (2026-11-03): Home opens All directly; Settings is a root action. Compact 64dp radial slots fill consecutively bottom to top, with 300ms branch dwell. Mini-player is below the anchor; portrait Video/Channel launchers form a vertical hand-side column and preserve drag selection. Shorts horizontal inset uses safeDrawing plus 4dp. See docs/DECISIONS.md for scope and validation limits.

Stage 5.4 (2026-11-03): six stable page slots, accessible target fitting down to 48dp with paginated list fallback, 80dp minimum reach axes, portrait native video aspect, launcher-centered Video/Channel close control, aligned Shorts navigation anchor and explicit double-tap like. Pause feedback deferred. See docs/DECISIONS.md and .impeccable/review/stage5.4-review.md for evidence and phone-trial limits.

Stage 5.5 (2026-11-03): the automatic list replacement is removed. Six page slots stay on an ellipse for all supported phone reach settings. Fit targets down to 48dp first; if necessary, extend the ellipse vertically, preserving horizontal reach, then horizontally within safe bounds. Very short windows can use the full quarter ellipse. Anchor and page membership remain fixed. Compact labels fit whole words. Layout fitting is remembered across pointer updates.

Stage 5.6 (2026-11-03): root navigation uses five fixed page slots: Home, Shorts, Subscriptions, Search, Next. Media/Library, Account and Settings are on page two (with Previous). Other action menus retain their six-slot capacity. Fitting now tries full 64dp buttons (scaled for system font size), extending available ellipse length before reducing size. Root horizontal insets use safeDrawing, vertical insets retain safeGestures protection, and the anchor uses a 36dp inset; the targets retain 8dp edge clearance within that safe area. This supersedes stage 5.5's shrink-first policy.

Stage 5.7 (2026-11-03): user-approved portrait trial replaces the red navigation launcher with fullscreen and a persistent five-button playback quarter arc (back 10s, play/pause, forward 10s, next, settings). Settings open around the pressed gear, including fullscreen. Video/Channel action slots use one fixed layout origin; only Close replaces the specific launcher, with no intermediate wrong-origin frame. Portrait player content uses safeDrawing horizontal insets rather than broad gesture padding. The video surface stays in one composition location while pinning changes its scroll offset and a matching lazy-list placeholder. Mini-player background extends through navigation-bar padding; there is no exposed feed strip below it.

Home and related recommendation cards have a three-dot contextual fan: Not interested, Save, Share, disabled Download. Action centers are equally far from the pressed dots. Full action labels fit the 72dp preferred targets; layout adapts inward near edges, retaining 48dp minimum if needed. Local hiding persists on the device and applies only to recommendations, not saved lists/search. Signed-in Not interested replays the response's matching feedback command through ActionGuard when available; otherwise hiding is local. Save reuses the guarded playlist picker; Share uses Android's chooser. Download intentionally has no action.

Mixes (RD playlists) load through watch-next playlist panels with seed video and playlistIndex continuation, rather than ordinary VL browse requests. Only the playlist panel is parsed, so related recommendations cannot leak into the mix list.
