# GammaOS Nano - Boxart / Cover Scraper (es-de style)

Goal (user directive): add boxart/cover scraping to the Nano PS3 XMB, modeled on
EmulationStation-DE. Replace a game's XMB icon with its scraped cover, show
background fanart on hover over a ROM, let the user pick their scraper, scrape per
system, and allow per-system scraper/credential overrides. Options live in the
Settings category; per-system overrides live in Game Systems -> <system>.

Hard constraint (project-wide): zero extra CPU/RAM when the feature is idle.
Everything lazy-inits on use and fully tears down on leave (mirror NanoAudio /
ps3canyon / photo cover lifecycle). The manifest (small JSON metadata) may stay
resident; GL textures must be freed when leaving the Game category and on
sleep/occlusion.

Scraping is NOT in the web XMB (it is a device addition like Settings / Game
Systems). Visual presentation follows the XMB language: covers render in the icon
slot, fanart uses the existing cinfo hover-background mechanism.

## Scrapers

Both require user-supplied credentials (verified 2026-06: ScreenScraper rejects
without a registered developer account; TheGamesDB rejects without an API key). So
credentials are first-class settings, default empty; the feature is fully built and
activates once the user fills them. We do NOT ship anyone else's keys.

- ScreenScraper.fr (primary): `api2/jeuInfos.php` with devid+devpassword (+ optional
  user account ssid/sspassword for higher limits), matched by CRC32 (libz) + rom
  filename + systemeid. Rich media incl box-2D cover + fanart.
- TheGamesDB (secondary): `v1/Games/ByGameName` (apikey + filter[platform]) then
  `v1/Games/Images` (boxart + fanart).

Network = `/system/bin/curl` via popen (no libcurl). JSON responses are downloaded
to a temp file (`curl -s -o`) then read (avoids the popen 16KiB cap), parsed with
njson. All shell args single-quote-escaped (ROM filenames are arbitrary). Media
downloaded with `curl -s -o <tmp>` then atomic rename.

## Modules

- `NanoScraper.{h,cpp}` (new, GL-free): CRC32 hashing, platform-id map
  (romDir/shortname -> {ss systemeid, tgdb platform}), ScreenScraper + TheGamesDB
  clients, `scrapeRom(engine, creds, romPath, name, plat, wantBox, wantFan,
  cacheDir) -> ScrapeOutcome{ok,title,boxFile,fanFile,error}`.
- `NanoMenuScraper.cpp` (new, NanoMenu methods): manifest index.json (lazy load),
  the scrape worker thread (mirror `musicScanThreadFunc`: snapshot inputs, no-mutex
  work, publish under mutex, drain on render thread), per-system engine/cred
  resolution, the lazy boxart GL texture LRU + the focused-ROM fanart texture, and
  teardown.

## Config

Global props (defaults in code):
- `persist.gammaos.scraper.engine` = screenscraper | thegamesdb
- `persist.gammaos.scraper.region` = us | eu | jp | wor
- `persist.gammaos.scraper.boxart` = true   (replace icon with cover)
- `persist.gammaos.scraper.fanart` = true   (hover background)
- `persist.gammaos.scraper.overwrite` = false (re-scrape already-cached)
- `persist.gammaos.scraper.ssdevid`, `.ssdevpw`, `.ssuser`, `.sspass`
- `persist.gammaos.scraper.tgdbkey`

Per-system (XmbSystem fields + nano_systems.json `scraper{}`):
- `override` (""=use global | screenscraper | thegamesdb | off)
- `user`, `pass` (per-system ScreenScraper account override; empty=inherit)
- `key` (per-system TheGamesDB key override; empty=inherit)
- `platform` (manual systemeid override; empty=auto-map)

## Cache layout (`/data/system/nano_scrape/`)

- `<fnv>.box.png` cover, `<fnv>.fan.jpg` fanart  (fnv = FNV-1a of romPath)
- `index.json` manifest: romPath -> {box, fan, title, scraper, time}
- `.resp.json` scratch (API response), `.tmp` download scratch

## UI

Settings category: new "Boxart Scraper" item in `kSettingsItems` with bound leaves
(Scraper list, Region list, Replace Icons toggle, Hover Background toggle, Overwrite
toggle, ScreenScraper Username/Password/Dev ID/Dev Password, TheGamesDB API Key) +
"Scrape All Systems" action. New `@password` OSK option type (masked) for the
secret fields; `@text` for the visible ones.

Game System editor (buildGameSystemEditor): new GSF_SCRAPER (Default/ScreenScraper/
TheGamesDB/Off list), GSF_SCRAPE_USER, GSF_SCRAPE_PASS (OSK), GSF_SCRAPE_NOW (scrape
this system action).

## Render

- Icon: in the PS3_ROM draw branch, if boxart enabled and `romBoxartTex(romPath)`
  loaded, draw the cover aspect-fit in the icon box instead of the generic glass
  cartridge. LRU cache, freed on leave Game.
- Hover fanart: in the cinfo call site, when a PS3_ROM is focused and a fanart file
  exists, load it into the focused-ROM fanart texture and pass to a generalized
  drawPs3CinfoBg (fanart mode = bg only, no description). Same dwell/fade timing.

## Phases

1. Data model + config + Settings UI + per-system editor UI (no engine).
2. NanoScraper engine + platform map + cache/manifest + worker + scrape actions.
3. ROM icon replacement (lazy boxart textures, free on leave).
4. Hover fanart background (generalize cinfo bg).

Each phase: build (`m gammaos-nano` first to catch -Werror), EROFS stage, flash,
verify on the Brick, commit as TheGammaSqueeze, post to Discord #claude.
