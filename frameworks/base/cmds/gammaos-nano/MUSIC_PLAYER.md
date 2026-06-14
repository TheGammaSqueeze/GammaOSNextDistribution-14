# GammaOS Nano - PS3 XMB Music Player

A 1:1 native port of the web XMB app's music player (`/work/ps3/xmb-app/`,
`index.html` + `canyon_port.js`) into the nano launcher. This document is the
reference for the music subsystem; keep it current as the code changes.

Source of truth: the web app. Implement exactly what it does.

## Scope
- Music library populated from user-imported folders (the "Search for Media Servers"
  item in the Music category), reusing the Settings > Game Systems native folder
  picker + scan + JSON-persist + cross-process reload pattern.
- Audio playback of mp3/flac/m4a/aac/ogg/opus/wav.
- A Now-Playing fullscreen screen: album-art jacket, marquee title, artist/album,
  progress bar + time, full-info toggle, the TRIANGLE control-panel options menu,
  repeat (off/all/one), shuffle, playback order, playlists.
- Two visualizers: XMB Waves (vis 0) and Canyon (vis 1). Globe (vis 2) is deferred.
- The XMB Waves visualizer drives the full enter/leave BACKGROUND transition: the
  wallpaper morphs (wave lift + gradient flip + magenta tint + doubled particles)
  in when you enter a playing track and out when you leave (and vice versa).
- Everything lazy-loaded: nothing music-related is touched at nano boot. The
  decoder, audio stream, library scan and visualizer GL resources initialize only
  on first entry into Music.

## Modules
| File | Role |
|------|------|
| `NanoAudio.{h,cpp}` | Standalone GL-free audio engine: decode (AMediaExtractor + AMediaCodec, libmediandk) -> int16 PCM ring on a worker thread -> AAudio output (cloned from the EQ-preview path) + a 512-pt FFT for the visualizer bands. |
| `NanoMenuAudioBands.{h,cpp}` | Tiny bridge: `nanoaudio::Bands` POD + `setBands`/`current` so visualizers read the FFT without coupling to NanoAudio. |
| `NanoMenuMusic.cpp` | NanoMenu-coupled glue: `nano_music.json` model/persist/reload, the scanner worker, the import flow, the Music content browser, the Now-Playing screen + control panel + playlists. (Phase 2-3) |
| `NanoMenuMusicCanyon.{h,cpp}` | `ps3canyon` namespace: the Canyon visualizer (port of `canyon_port.js`), modeled on `ps3globe`. (Phase 5) |
| `NanoMenuPS3Bg.{h,cpp}` (extended) | XMB Waves morph: `setMusicVisTarget`/`musicVisBlend` driving wave lift, gradient flip, tint, gain. (Phase 4) |
| `NanoMenuPS3Particles.{h,cpp}` (extended) | Doubled particle pool faded by the morph blend. (Phase 4) |

## Audio engine (NanoAudio) - DONE (Phase 1)
- `probe(path, Meta&)`: opens an extractor, reads container tags (TITLE/ARTIST/ALBUM)
  + track format (sample rate, channels, duration, mime->codec badge). Used by the
  library scanner and before opening a stream. Static, thread-safe.
- `open(path)`: probe -> (re)open the AAudio stream at the track's rate/channels ->
  size the ring (~3s) -> spawn the decoder thread (starts PAUSED).
- Decoder thread: AMediaExtractor + AMediaCodec decode loop -> writes int16 PCM into
  an SPSC ring (handles PCM_16BIT + PCM_FLOAT; raw/WAV falls back to direct copy).
  Self-throttles by polling the ring's free space (2ms sleep when full).
- AAudio callback (lock-free): drains the ring with the volume applied, zero-fills on
  underrun, advances the frame counter (position), and rolls the last 512 mono
  samples into a double-buffered window for the FFT.
- `getBands(Bands&)` (UI thread): Hann window -> 512-pt radix-2 FFT -> 256 magnitude
  bins -> Web-AnalyserNode dB->byte mapping (minDb -100, maxDb -30) with 0.82 temporal
  smoothing -> bass/mid/treble band means (splits at 0.10 / 0.45 of 256), matching
  `mpBands()`.
- `play/pause/togglePause/stop/seek` (seek respawns the decoder at the new position,
  race-free), `position/duration/ended/isPlaying/isPaused/isStopped`, `setVolume`,
  `meta`, `release`.
- Build: `libmediandk` + `libaaudio` linked in `Android.bp`; `NanoAudio.cpp` in srcs.

### 1:1 audio constants
- FFT size 512 -> 256 bins; smoothing 0.82; band splits 0.10 / 0.45 (bEnd 25, mEnd 115).
- dB->byte: `(20*log10(mag) + 100) / 70` clamped 0..1.

## Data model (Phase 2) - planned
`/data/system/nano_music.json` (atomic write + chown/chmod + mtime cross-process
reload, mirror `nano_systems.json`):
```
{ "version":1,
  "folders":[ "<abs path>" ],
  "tracks":[ {"file","n","a","st","dur","codec","album","mtime","track"} ],
  "playlists":[ {"name","files":[ "<file>" ]} ] }
```

## Lazy-load
- Nothing at boot. `musicEnsureLoaded()` (guarded) runs on first Music entry: parse
  JSON, `NanoAudioPlayer::init()` (no stream yet), kick a scan only if folders exist
  and tracks look stale (mtime-cached).
- Decoder + AAudio stream created in `open()` on first track play.
- On leaving Now-Playing: pause + free the decoder ring; keep the parsed library.
- Canyon GL resources init on first Canyon use, freed on leaving Now-Playing.

## Verification
- Build with `buildtv.sh`, EROFS, flash the Brick.
- Import a folder via Music > Search for Media Servers; confirm scan + `nano_music.json`.
- Play each format; check the Now-Playing layout, control panel, repeat/shuffle/auto-advance, playlists.
- Waves enter/leave morph (in and out); Canyon dissolve + flythrough + bass-reactive speed.
- Confirm lazy-load (no audio/decoder/scan/canyon at boot or on the home XMB).

## Status
- Audio engine (NanoAudio): DONE, verified on the Brick (AAudio stream opens at the
  track's native rate, decode + FFT active, correct durations extracted).
- Library + folder import: DONE, verified end to end on the Brick: Music > Search for
  Media Servers > Add Folder > pick a folder -> recursive scan (mtime-cached) ->
  nano_music.json persisted (with metadata) -> the album appears in the Music column
  -> album -> track list -> select a track -> audio plays. Folder picker is the Game
  Systems one, routed via mFolderPickTarget. Scan results drain + the column rebuilds
  at the settled XMB root (and on cross-process nano_music.json change).
  Known minor: the folders-screen per-folder track count shows the pre-scan value
  until you re-enter it (the async scan drains after the screen was built).
- Now-Playing screen + control panel + playlists: in progress (Phase 3). Selecting a
  track currently opens the player (audio + queue/repeat/shuffle/auto-advance) without
  the dedicated full-screen UI yet.
- XMB Waves morph + Canyon visualizer: pending (Phase 4-5; Canyon is a built stub).
