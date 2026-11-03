# InnerTube fixtures

Saved responses for parser tests. Account endpoints need a signed-in session, so
`account_menu.json` and `accounts_list.json` are reconstructed from the shapes that
Metrolist (innertubex) and yt-dlp parse, with fake names, ids and URLs. When real
captures become available, replace them with personal data removed (names, emails,
ids, photo URLs, visitorData).

`*.json.gz` are real anonymous WEB-client responses captured on 2026-11-01 (hl=ru, gl=RU):
search ("рецепт борща"), playlist (VLPLBCF2DAC6FFB574DE), channel_videos (MrBeast videos tab),
next (dQw4w9WgXcQ), comments (first comments page of that video), home_anon (logged-out home,
only a nudge), reel_seq (Shorts sequence). They hold public data only.


`home_grid.json` (2026-11-02) is a synthetic, deidentified structural regression fixture,
modeled after YouTube.js RichGrid/HomeFeed: main recommendation, regional news shelf,
Shorts shelf, ad slot, filters and distinct continuation tokens. It is not a captured
signed-in response and contains no account data. It tests Home's container boundaries.
