---
name: ErgoTube
description: Familiar Android video UI with a calibrated radial menu under the thumb.
colors:
  brand-red: "#FF0033"
  brand-on-red: "#FFFFFF"
  badge-background: "#0F0F0FCC"
  dark-primary: "#F1F1F1"
  dark-background: "#0F0F0F"
  dark-secondary: "#AAAAAA"
  dark-link: "#3EA6FF"
  dark-link-container: "#263850"
  dark-on-link-container: "#D6E9FF"
  dark-container-lowest: "#0A0A0A"
  dark-container-low: "#181818"
  dark-container: "#212121"
  dark-container-high: "#272727"
  dark-container-highest: "#3F3F3F"
  dark-outline: "#5A5A5A"
  dark-outline-variant: "#303030"
  dark-error: "#FF6B63"
  light-primary: "#0F0F0F"
  light-background: "#FFFFFF"
  light-secondary: "#606060"
  light-link: "#065FD4"
  light-link-container: "#DEF1FF"
  light-on-link-container: "#002A5C"
  light-container-low: "#F9F9F9"
  light-container: "#F2F2F2"
  light-container-high: "#E9E9E9"
  light-container-highest: "#E0E0E0"
  light-outline: "#909090"
  light-outline-variant: "#E5E5E5"
  light-error: "#CC0000"
  video-ground: "#000000"
  player-controls-scrim: "#00000066"
typography:
  headline-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "24px"
    fontWeight: 400
    lineHeight: "32px"
    letterSpacing: "0px"
  title-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "22px"
    fontWeight: 400
    lineHeight: "28px"
    letterSpacing: "0px"
  title-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 500
    lineHeight: "24px"
    letterSpacing: "0.2px"
  title-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: "20px"
    letterSpacing: "0.1px"
  body-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
    letterSpacing: "0.5px"
  body-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
    letterSpacing: "0.2px"
  body-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: "16px"
    letterSpacing: "0.4px"
  label-large:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: "20px"
    letterSpacing: "0.1px"
  label-medium:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: "16px"
    letterSpacing: "0.5px"
  label-small:
    fontFamily: "Roboto, sans-serif"
    fontSize: "11px"
    fontWeight: 500
    lineHeight: "16px"
    letterSpacing: "0.5px"
rounded:
  badge: "4px"
  mini-thumbnail: "6px"
  thumbnail: "12px"
  radial-leaf: "20px"
  search: "24px"
  circle: "50%"
spacing:
  compact: "4px"
  small: "8px"
  content: "12px"
  standard: "16px"
  section: "20px"
  generous: "24px"
components:
  button-primary:
    backgroundColor: "{colors.dark-primary}"
    textColor: "{colors.dark-background}"
    typography: "{typography.label-large}"
    height: "48px"
  button-tonal:
    backgroundColor: "{colors.dark-container-high}"
    textColor: "{colors.dark-primary}"
    typography: "{typography.label-large}"
    height: "48px"
  button-text:
    textColor: "{colors.dark-primary}"
    typography: "{typography.label-large}"
  search-field:
    backgroundColor: "{colors.dark-container-high}"
    textColor: "{colors.dark-primary}"
    rounded: "{rounded.search}"
  video-thumbnail:
    backgroundColor: "{colors.dark-container-high}"
    rounded: "{rounded.thumbnail}"
  radial-anchor:
    backgroundColor: "{colors.brand-red}"
    textColor: "{colors.brand-on-red}"
    rounded: "{rounded.circle}"
    size: "56px"
  radial-section:
    backgroundColor: "{colors.dark-container-high}"
    textColor: "{colors.dark-primary}"
    rounded: "{rounded.circle}"
    size: "48px"
  radial-section-selected:
    backgroundColor: "{colors.dark-primary}"
    textColor: "{colors.dark-background}"
    rounded: "{rounded.circle}"
    size: "48px"
  radial-leaf:
    backgroundColor: "{colors.dark-container-high}"
    textColor: "{colors.dark-primary}"
    typography: "{typography.label-small}"
    rounded: "{rounded.radial-leaf}"
    padding: "4px"
    size: "64px"
  mini-player:
    backgroundColor: "{colors.dark-container}"
    textColor: "{colors.dark-primary}"
    height: "64px"
---

# Design System: ErgoTube

> Current interaction revision: stage 5.2 (2026-11-03), recorded in `docs/DECISIONS.md`. Portrait video and title/one-line summary share the full width; floating Video/Channel radial groups replace its reaction rail. Fullscreen transport/navigation controls share the working-hand corner, with a centered seek line/thumb. Player settings and their choices use one radial level at a time; sleep uses a calibrated-aspect elliptical dial. Home has a floating anchor and scroll-end clearance, without a permanent empty footer. Shorts retain their permanent action rail with a content-sized summary scrim. Older dimensional descriptions below remain explicitly stage-5 historical values.

> Revision agreed 2026-11-02: `docs/UX-REVISION.md` supersedes the target behavior for navigation, player controls, Shorts and elliptical calibration. Implementation was explicitly authorized and delivered as stage 5.1; see `.impeccable/review/stage5.1-review.md` for the review scope. The incumbent palette/type/shape tokens are retained; this is an interaction revision inside the established world. Dimensions, tokens and behavior below record the existing stage-5 build and do not certify the proposed revision.

## Overview

**Creative North Star: "The category standard, a YouTube-like look executed at full craft."**

The established identity is familiar Android video UI: real thumbnails, Roboto, restrained neutral Material surfaces and plain language. The category convention is a standing product commitment, with YouTube, Telegram and Spotify as the craft reference. ErgoTube's own fan mark and calibrated radial thumb menu provide its identity.

Keep frequent controls within one-handed reach, protect Android's gesture zones and retain a comfortable near-black ground at night. New screens extend this native system rather than introducing a separate visual theme. This document records the stage-5 implementation; it does not certify every state or replace the finish review.

**Key Characteristics:**

- Familiar video feed with real 16:9 thumbnails.
- Neutral surfaces, high-contrast primary actions and limited brand red.
- System-scaled Roboto through Compose Material 3 typography.
- Mirrored, calibrated radial navigation with a section-owned outer arc.
- Native Android controls, insets and media integration.

Authority: `PRODUCT.md` and `.impeccable/surfaces/app-src-main-java-app-freshtuber-ui.md`. Exact implementation sources: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/components/VideoCard.kt`, `ui/screens/search/SearchScreen.kt`, `ui/AppRoot.kt`, `ui/radial/RadialGeometry.kt`, `ui/radial/RadialMenu.kt`, `ui/radial/CalibrationScreen.kt`, `ui/player/PlayerOverlay.kt` and `ui/player/MiniPlayer.kt`, all beneath `app/src/main/java/app/freshtuber/`. Material defaults were checked against the resolved Material 3 1.4.0 library. Visual corroboration: `.impeccable/review/phone-home.png`, `phone-menu.png`, `phone-calibration.png`, `phone-settings.png`, `phone-notification.png` and `phone-fullscreen-cutout.png`. Captures corroborate appearance; current Kotlin owns dimensions and behavior when an older capture differs.

## Colors

The palette is neutral, with brand red reserved for recognizable video and menu signals.

### Primary

- **Interactive foreground** (`dark-primary` / `light-primary`): filled primary actions and selected radial targets; use the contrasting background color for their content.
- **Brand red** (`brand-red`): watch progress, live badges, the fan mark and the radial anchor. Its white content is `brand-on-red`.

### Secondary

- **Quiet metadata** (`dark-secondary` / `light-secondary`): channel metadata, hints and secondary copy.

### Tertiary

- **Link blue** (`dark-link` / `light-link`): Material tertiary/link roles; use the corresponding link container and on-container tokens when a container is needed.

### Neutral

- **App ground** (`dark-background` / `light-background`): background and surface. Dark mode is near-black rather than full white or a decorative tinted ground.
- **Stepped containers** (`*-container-lowest`, `*-container-low`, `*-container`, `*-container-high`, `*-container-highest`): depth by tone. Light `surfaceContainerLowest` equals `light-background`; dark primary/secondary containers and surface variant use `dark-container-high`, and light equivalents use `light-container`.
- **Outlines** (`*-outline`, `*-outline-variant`): native control outlines and occasional dividers.
- **Thumbnail badge** (`badge-background`, white content): stays dark in either theme; a live badge uses brand red.
- **Video ground** (`video-ground`): black around fitted video, including fullscreen side areas. Controls use white over `player-controls-scrim`.
- **Errors** (`dark-error` / `light-error`): semantic errors, separate from brand red's role.

**The Brand Red Rule.** Keep brand red on progress, live badges, the fan mark and the menu anchor; primary app actions follow the neutral Material primary role.

Themes follow the system in the current app. Dynamic color exists as an opt-in parameter and is disabled by default; do not treat Android-generated dynamic values as the fixed brand palette. Additional theme settings remain future work.

## Typography

**Display and Body Font:** Android's default sans-serif, Roboto on the reviewed emulator, through `Typography()` rather than a bundled custom face. The platform can resolve the system font differently on another device.

**Character:** familiar, readable Material typography. There is no separate decorative display family or monospaced UI style.

### Hierarchy

The frontmatter lists the reused roles actually sampled. Role names map directly to `MaterialTheme.typography` camelCase names. Settings use `headlineSmall`; video details use `titleLarge`; the wordmark uses `titleLarge` with a bold override. Feed titles use `titleMedium` with a normal (400) weight override, at most two lines. Secondary metadata uses `bodySmall`; ordinary text uses `bodyMedium` or `bodyLarge`; playback times and thumbnail badges use `labelMedium`; outer-arc labels use `labelSmall` with at most two lines. Full accessible titles remain available even when visible radial labels are compact.

**The System Type Rule.** Set Android text in sp and retain the user's font scaling; solve fit with available space, compact localized labels and pagination rather than shrinking text below the established role.

**Native unit mapping:** this portable frontmatter uses CSS `px` strings for tooling. Every typography size, line height and tracking number represents the same numeric **sp** value in Compose; every layout, radius and component dimension represents the same numeric **dp** value. The CSS preview corresponds only to density 1 and fontScale 1. Android dp and sp are never physical screen pixels; use the native values and `LocalDensity` at runtime. A component `height` records its baseline/minimum, not a reason to clip scaled text. Native source and the sidecar's typography metadata retain this distinction.

## Layout

The current phone feed is a full-width vertical list. Thumbnail sides are inset by the content spacing token; metadata follows underneath with a channel avatar when available, a two-line title and secondary metadata. The screen has no decorative card grid. Standard content surfaces use the small/content/standard spacing steps, with section and generous steps for larger gaps. The shell reserves an 80dp bottom area for the thumb anchor and places the mini-player above it while browsing.

Radial coordinates are measured inside the full union of `WindowInsets.safeDrawing` and `WindowInsets.safeGestures`, with keyboard padding in the shell. The anchor center is 40dp from the safe bottom and dominant-hand side when there are no children. With children, that inset is `max(40dp, outerTarget / 2 + 8dp)`, so enlarged outer targets remain inside the safe area. Its disc is 56dp; section targets are 48dp with at least 8dp between adjacent section targets. The outer target begins at 64dp, scales with `fontScale`, and is clamped to 64–128dp. Outer centers are separated by at least the target size plus 16dp. Left-hand mode mirrors the geometry.

Calibration defaults to a 220dp reach and 90-degree sweep, normalized to a 160–260dp reach and 70–90-degree sweep. Fit is computed from the actual safe width and height, not a screen-size breakpoint. When items exceed an arc's capacity, previous/next targets paginate them; when the inner capacity falls below three, the menu becomes a vertically scrollable list with 48dp minimum rows. The heading/hint is omitted at safe heights of 320dp or less. These are implemented adaptations, not a tablet layout contract.

**The Safe Reach Rule.** Reuse the menu's safe coordinate space and calibrated mirrored geometry; never move interactive targets into system gesture zones to make an arc fit.

## Elevation & Depth

Content is flat by default. Stepped neutral surface roles, thumbnail clipping and scrims provide hierarchy; the sampled feed cards, radial discs and mini-player do not add custom drop shadows. Material dialogs, sheets, buttons and menus retain their library-owned elevation and interaction treatment. An opened app menu uses the current surface at 97% opacity; the player uses a transparent radial scrim over its own video-control scrim. Native media notifications belong to Android and may use artwork-derived colors; their screenshot does not define app palette tokens.

**The Neutral Layer Rule.** Use existing tonal surfaces and native Material elevation before introducing custom depth effects.

## Shapes

Media thumbnails have gently rounded corners using the thumbnail token; duration badges use the badge token. Circular avatars and radial section/anchor discs use `CircleShape`. Outer radial targets use the radial-leaf corner token; their larger rounded silhouette accommodates an icon and short label. Search uses the search corner token and no visible underline. The mini-player preview uses the smaller mini-thumbnail corner token. Standard buttons, sheets and native controls retain the Material 3 default shapes rather than a parallel custom shape system.

## Components

### Buttons

Familiar native actions with clear priority. Filled primary buttons use Material primary/onPrimary; tonal buttons use the neutral secondary container/on-container roles; text buttons leave the ground visible. Calibration and major settings actions have at least 48dp height. Selection, disabled treatment, ripple and focus remain Material-owned. Use actual disabled state for unavailable actions, as on calibration Save before a valid arc exists.

### Cards / Containers

The feed card is a clickable content column, without a separate raised plate. The thumbnail is 16:9, cropped to fill, with a duration/live badge inset 8dp from the bottom-right. A watched-video bar is 4dp high. A supplied channel avatar is 36dp visually; a visible avatar is not a replacement for the 48dp interaction floor when it is independently actionable. Titles and metadata follow the hierarchy above. Use actual video imagery; do not invent ratings or promotional social proof.

### Inputs / Fields

Search is a single-line native `TextField` on the high container tone with rounded search corners, a leading search icon and a clear action when nonempty. The field is inset by 12dp horizontally and 8dp vertically. Focused and unfocused indicator lines are transparent; native text editing, cursor, selection, keyboard Search action and accessibility semantics remain intact. Settings use native radio buttons and slider controls.

### Navigation

Navigation is a section-owned radial tree. Inner targets hold sections; the active section exposes its items on the outer arc. Home filters remain available when entering Home from another destination. Library sections belong to Library; profile/channel switching and Settings belong to the Account outer arc. Tap can unfold/select; a press-drag-release gesture can move anchor → section → item. The selected or hovered target takes primary/onPrimary; inactive targets use the high neutral container. Targets expose full semantic names, and moving between targets provides haptic feedback. Android Back closes an open menu.

### Radial thumb menu

The red anchor remains recognizable in either theme, with white Menu/Close icons. Inner targets use 24dp icons; outer targets can use a 20dp icon, compact localized `labelSmall` text and 4dp padding. Root/leaf capacity comes from geometry, with page controls rather than overlapping targets. Do not replace this signature navigation with top filter chips, tab strips or a bottom navigation bar.

### Player and mini-player

Video controls use white symbols on a darkened video, a red seek thumb and a 3dp seek track. The seek thumb is 12dp at rest and 16dp during drag. The player reuses the calibrated radial menu for ±10 seconds and play/pause/replay. Its radial area is separated from the bottom seek/fullscreen region by 76dp in portrait or 72dp in fullscreen; fullscreen also reserves 56dp at the top. The title/settings row is hidden while the radial menu is expanded. Controls fade with Compose animation; the player's menu uses a four-second idle timeout and remains open during a drag. These source values are implementation facts, not a claim that every orientation/font size has passed visual review.

The mini-player is a neutral container with a 2dp red progress line above a 64dp content row, a 48dp-high 16:9 preview, title/channel text and native playback/close icon buttons. Android's media session supplies the notification/lock-screen presentation, including artwork, metadata and transport actions.

## Do's and Don'ts

### Do:

- **Do** extend the pinned YouTube-like neutral Material world and its existing role names.
- **Do** use native dp for geometry and sp for text, with at least 48dp interactive targets.
- **Do** keep the radial tree, handedness, calibration and full safe drawing/gesture insets together.
- **Do** use real thumbnails and plain localized Russian and English copy.
- **Do** review normal and enlarged fonts, both themes and open/closed player controls on a phone.

### Don't:

- **Don't** replace radial section/filter navigation with top chip rows, tab strips or a bottom bar.
- **Don't** spread brand red to ordinary primary app actions or add an unrelated decorative identity.
- **Don't** copy YouTube's trademarked logo; preserve ErgoTube's own fan mark.
- **Don't** canonize collisions, broken words, undersized interaction targets or stale review captures as intended design.
- **Don't** infer app color tokens from Android's artwork-colored notification card or treat CSS previews as native runtime verification.

The stage 5 finish reviewer scored the three listed fixes resolved: cross-screen Home filters, portrait player target collisions and outer-label wrapping. The verdict covers those fixes. The desired system Remove animations behavior remains a product requirement rather than a verified custom motion implementation. The full settings screen and tablet-specific composition are not established at stage 5.


Stage 5.3 revision (2026-11-03): Home opens All directly; Settings is a root action. Compact 64dp radial slots fill consecutively bottom to top, with 300ms branch dwell. Mini-player is below the anchor; portrait Video/Channel launchers form a vertical hand-side column and preserve drag selection. Shorts horizontal inset uses safeDrawing plus 4dp. See docs/DECISIONS.md for scope and validation limits.

Stage 5.4 (2026-11-03): six stable page slots, accessible target fitting down to 48dp with paginated list fallback, 80dp minimum reach axes, portrait native video aspect, launcher-centered Video/Channel close control, aligned Shorts navigation anchor and explicit double-tap like. Pause feedback deferred. See docs/DECISIONS.md and .impeccable/review/stage5.4-review.md for evidence and phone-trial limits.

Stage 5.5 (2026-11-03): the automatic list replacement is removed. Six page slots stay on an ellipse for all supported phone reach settings. Fit targets down to 48dp first; if necessary, extend the ellipse vertically, preserving horizontal reach, then horizontally within safe bounds. Very short windows can use the full quarter ellipse. Anchor and page membership remain fixed. Compact labels fit whole words. Layout fitting is remembered across pointer updates.

Stage 5.6 (2026-11-03): root navigation uses five fixed page slots: Home, Shorts, Subscriptions, Search, Next. Media/Library, Account and Settings are on page two (with Previous). Other action menus retain their six-slot capacity. Fitting now tries full 64dp buttons (scaled for system font size), extending available ellipse length before reducing size. Root horizontal insets use safeDrawing, vertical insets retain safeGestures protection, and the anchor uses a 36dp inset; the targets retain 8dp edge clearance within that safe area. This supersedes stage 5.5's shrink-first policy.

Stage 5.7 (2026-11-03): user-approved portrait trial replaces the red navigation launcher with fullscreen and a persistent five-button playback quarter arc (back 10s, play/pause, forward 10s, next, settings). Settings open around the pressed gear, including fullscreen. Video/Channel action slots use one fixed layout origin; only Close replaces the specific launcher, with no intermediate wrong-origin frame. Portrait player content uses safeDrawing horizontal insets rather than broad gesture padding. The video surface stays in one composition location while pinning changes its scroll offset and a matching lazy-list placeholder. Mini-player background extends through navigation-bar padding; there is no exposed feed strip below it.

Home and related recommendation cards have a three-dot contextual fan: Not interested, Save, Share, disabled Download. Action centers are equally far from the pressed dots. Full action labels fit the 72dp preferred targets; layout adapts inward near edges, retaining 48dp minimum if needed. Local hiding persists on the device and applies only to recommendations, not saved lists/search. Signed-in Not interested replays the response's matching feedback command through ActionGuard when available; otherwise hiding is local. Save reuses the guarded playlist picker; Share uses Android's chooser. Download intentionally has no action.

Mixes (RD playlists) load through watch-next playlist panels with seed video and playlistIndex continuation, rather than ordinary VL browse requests. Only the playlist panel is parsed, so related recommendations cannot leak into the mix list.


## Liquid 11 refinement (5 Nov 2026)
The current liquid / classic implementation is the visual authority for this refinement; historical radial-navigation contracts above are superseded by the current bottom dock. User-confirmed navigation order is fixed left to right: Mine, Subscriptions, Search, Shorts, Home. Width is user-adjustable on phones, full-width on expanded screens, with unchanged icon sizes. Classic navigation has no painted panel or separator. Portrait video supports pinch and pan independently of playback; quality is a direct HQ control in portrait and fullscreen. Card hold has six evenly spaced choices, including Channel and Save, and the centre cancels. Preserve all unrelated liquid colours, typography and effects.


## Liquid 12 refinement (5 Nov 2026)
Quality is a contextual palette anchored to HQ, using rounded remote-style choice pills and a blur/scale reveal in portrait/fullscreen; no radio-list sheet. Ordinary video-picture single taps never pause. Both orientations support pinch and pan independently of playback; visible controls retain their gesture zones. Elapsed/total time stays over the picture, temporarily duplicated on the remote while seeking. The card fan emerges through blur over the blurred feed. An independent preference swaps only Mine and Home. Descriptions/comments retain native selectable text and annotated links/timestamps. All other liquid effects, typography and colour choices remain the incumbent implementation.


## Approved cloud-sofa mark (5 November 2026)
The user selected `docs/design/logo-2026-11-05/clouds/cloud-sofa.png`, without its wordmark. Its traced vector silhouette supersedes the historical fan mark and the classic YouTube badge. In-app marks are apricot on dark surfaces and wine on light surfaces; the launcher uses apricot (#F3C39E) on wine (#572A35), including themed-icon geometry. Other interface colors and typography retain their existing settings.
