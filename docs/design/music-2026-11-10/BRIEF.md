# ErgoTube Music v2: implementation brief

Source of truth for look and behaviour: `index.html` in this folder (open in a browser; the concept text is below the six phones). The old music player on branch `codex/music-player` was rejected by the user ("дерьмовый"). This is a test build for friends, so pick sensible defaults and move on; do not stop to ask.

## Decisions (made on the user's behalf, 2026-11-11)
1. Setting "Музыка вместо Shorts" in the main dock. Default off. Music is always reachable from Моё → Музыка, the card fan "Слушать", the "Как музыку" button on the video page and the track line in the dock.
2. One library: phone files and YouTube songs together, in lists, playlists and the queue. Filters Всё / Скачанное / С телефона. Source mark per row (download_done, phone_android, cloud).
3. Lyrics from LRCLIB (https://lrclib.net/api/get, synced LRC when available; only artist, title, album, duration are sent). Toggle in settings, default on. "Текст не нашёлся" state.
4. Heart = local "Любимое" only. YouTube like is a separate item in the track menu, explicit tap only.

## Must have in this build
- Music mode uses the same liquid light (LiquidBackground/LiquidLight shaders), with the light source and accent hue taken from the cover instead of a video frame. Same drop-shaped dock and pad as video. No Material NavigationBar, no plain Material screens.
- Music dock (right hand, mirrored for left hand and by the existing Mine/Home swap setting): Видео · Поиск · Библиотека · Слушать. While music plays or is paused, the track line (cover 40dp, title, artist, play/pause, thin progress on the drop's top edge) lives inside the dock drop, both in music mode and in the main video dock. Gestures on the line: up = open player, sideways = next/previous, down = stop with "Вернуть" snackbar.
- Screens: Слушать (Микс дня hero with 2x2 cover collage, Продолжить, Джемы с Главной, Недавно; traditional scroll like Home), Сейчас играет (cover on top, title + heart, pad at the bottom: collapse / lyrics / sleep / more; shuffle / prev / play / next / repeat(off-all-one); time + "Дальше: ..." + queue; scrub bar with the video pad's fine scrubbing), Очередь (sheet growing from the pad; current track at the bottom near the thumb; layers: Ты добавил, подборка, бледное «Похожее» when Бесконечно is on; drag handle reorder, swipe to remove with undo; chips Перемешать / Повтор / Бесконечно; compact transport at the bottom), Библиотека (bottom-up list per the scroll-direction setting, filter chips and sort above the dock, shelf Любимое / Скачанное / С телефона / + Плейлист, fast-scroll thumb inset from the edge), Поиск (field at the bottom like the main search; results from phone first, then YouTube).
- Cover gestures: swipe sideways = next/prev with cover slide and light crossfade; tap = lyrics in place of the cover with current line highlighted; double tap left/right = -10/+10 s only for tracks longer than 20 min. Swipe down on the top area = collapse to the dock line. Swipe up on the pad = queue. Hold next = fast forward. Haptics on each.
- Queue: three layers, shuffle affects only the collection layer, no same track twice in a row, unavailable tracks auto-skipped with a short note, "Бесконечно" continues with radio from the last track. Persist across restarts.
- Only one thing plays: video start pauses music and keeps its line in the dock; play on the line resumes music and minimises/pauses video. "Как музыку" on the video page switches to audio-only at the same position with radio queue. "Смотреть клип" in the track menu opens the video at the same position.
- Background: MediaSession notification with prev / play / next + custom Любимое and Повтор, seek bar; pause on headphones unplugged (handleAudioBecomingNoisy); audio focus; sleep timer (reuse the elliptical dial) with "до конца песни" and 30 s fade-out.
- Energy: audio-only streams in music mode, prefetch the next track ~30 s before the end (gapless via the player playlist), whole-track ranges; light shader paused when the screen is not visible; music light at 30 fps; Energy saving section switches the light to static without refraction and grain.
- Offline: auto-download options for Микс дня and Любимое on Wi-Fi with a size cap (default 2 GB, old daily mixes removed first, Любимое never); without network the library switches to Скачанное and greys out the rest; audio quality settings for Wi-Fi / mobile / downloads.

## Nice to have if time allows
- Chapters from description timestamps for long mixes (next/prev jump by chapter, marks on the bar).

## Out of scope
Android Auto, Wear OS, light pulsing with the beat.
