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

## Screen-off playback (power button while music plays)
`enterDrmSleep()` (NanoMenuInput.cpp) keeps music going with the screen off, like a
normal phone music player:
- If a track is playing when the user presses power, `keepAudio` is set: nano blanks
  the panel + backlights, holds a kernel wakelock (`nano_music` -> `/sys/power/wake_lock`)
  AND drives proper Android standby (`sys.gammaos.nano.dosleep` -> the nano-dosleep init
  service injects KEYCODE_SLEEP -> PowerManager.goToSleep). The held wakelock blocks
  suspend-to-RAM, so the framework dozes (display off, low power the proper way - the
  lights HAL stops re-asserting the backlight) while the SoC stays up and the decode /
  AAudio threads keep running. It polls every 1s and auto-advances the queue (audio-only,
  no GL). On wake it injects KEYCODE_WAKEUP, recommits the panels, then releases the
  wakelock LAST (so the SoC cannot suspend in the gap before the wake lands).
- Holding the wakelock needs CAP_BLOCK_SUSPEND, granted in `gammaos-nano.rc` (both the
  home and overlay services). Without it the write returns EPERM and, with USB
  connected, the `usb_connecting` kernel wakeup source masks the failure - so it only
  manifested on UNPLUG, where the SoC then suspended and audio died. The write checks
  its result and logs on failure. `device/gammaos/sepolicy/bootanim.te` allows
  `block_suspend` + `sysfs_wake_lock` for an enforcing build (nano is the bootanim
  domain, permissive today).
- CPU clocks: on screen-off nano switches to the powersave governor
  (`/vendor/bin/setclock_powersave.sh`, ~408MHz) and re-applies the user's persisted
  performance mode on wake (`nanoApplyPerfClock`/`nanoRestorePerfClock`, NanoMenu.cpp;
  mode validated to stock/max/powersave). Audio decode + the 1Hz poll run fine at
  408MHz, so playback continues in a genuinely low-power state with no processes killed.
- When the queue ends (repeat off -> `mpStep` Pauses -> `isPaused()`), nano releases the
  wakelock and lets the device take a real sleep, so it is not left awake screen-off
  with nothing playing.

## Watchdog liveness (do not let sleep/park abort the process)
The render watchdog (`startRenderWatchdog`, NanoMenuRender.cpp) aborts the process if
`mRenderHeartbeat` does not advance for ~8s. EVERY intentional render-thread park (the
overlay screen-off idle poll, the hidden-overlay prop wait, the DRM occlusion guard,
frame pacing, idle fps) must keep the heartbeat alive or the watchdog kills the process
- in the overlay that is where the decode/AAudio threads live, so music + a foreground
app would die ~8s into sleep. The fix is structural: bump `mRenderHeartbeat` ONCE at
the top of the main render loop (NanoMenu.cpp threadLoop), since every park exits via
`continue` back to the top. A real hang inside `render()`/`pollInput()` never returns to
the top, so it is still caught. The ONE exception is `enterDrmSleep`'s nested poll,
which does not loop back and stays guarded by `mInDrmSleep`. The overlay also holds the
`nano_music` wakelock + auto-advances while playing screen-off (symmetric release), so
background audio survives a real suspend on battery too.

## Album art (jacket + folder cover)
- Jacket (`mpTrackArt`, NanoMenuPS3Icons.cpp, one cached slot): a per-track image named
  like the track file (`<dir>/<trackbase>.<img>`) overrides a per-folder cover named like
  the folder (`<dir>/<foldername>.<img>`); falls back to the note placeholder. JPEG / PNG
  / BMP via a vendored `stb_image.h` (STBI_ONLY_JPEG/PNG/BMP - no Skia or extra runtime
  lib, keeps the minimal-boot process lean, no Android.bp change), box-downscaled to 256.
- XMB Music column folder icons (`mpAlbumArt`, cached by album name, 128px): the album's
  folder cover is embedded in the icon (like the web photo folders), resolved from a
  representative track's folder, falling back to the generic glass folder icon.

## XMB Music column now-playing cues
- A soft pulsing cyan glow (concentric `ps3FillCircle`, drawList + drawParentLayer) marks
  the currently-playing album (`it.label == mMusicTracks[mMpQueue[mMpIdx]].album`) and
  track (`it.a == that index`), so you can see what is playing without opening it.
- Selecting the track that is already playing resumes the Now-Playing screen on the live
  session (`resumeMusicPlayer`) instead of restarting it from zero.

## Now-Playing bar sizing
The bar (jacket / title / artist / seek+time cluster) renders at 1.5x on small panels
(<=768) for readability; the control-panel icon grid stays 2x. The info cluster's left
edge (`clX`) widens at 1.5x so the larger elapsed/total time strings do not overlap.

## Phase 3 implementation recipe (Now-Playing screen + control panel)
Scaffolding already in place: state members + method decls in NanoMenu.h (mMpActive,
mMpFullInfo, mMpEnterT, mMpFullInfoT, the panel/transient/banner/msg fields,
renderMusicPlayer/openMpOpt/closeMpOpt/mpOptMove/mpOptActivate/mpOptBack/drawMpOpt/
drawMpStatusRow/mpShowMsg/mpCycleVis), plus mpIcon(n) (audioplayer texture loader)
and mpJacket() (jacket texture) defined in NanoMenuPS3Icons.cpp. openMusicPlayer
already builds the queue + plays; musicTick auto-advances.

COORDINATE MAPPING (web virtual VW=1920/VH=1080 == ps3::VW/VH; web canvas transform
== nano devX/devY/devS). For a web call at virtual (vx,vy):
- absolute X (web uses XCF(V.W*f)):  ps3::devX(ps3::XCF(VW*f))
- X delta/width (web XCF(V.W*f)):    ps3::devS(ps3::XCF(VW*f))
- absolute Y (web V.H*f):            ps3::devY(VH*f)
- size/height (web V.H*f):           ps3::devS(VH*f)
- font 'NNpx':                       scale = ps3::fontScale(NN); top y from a web
                                     baseline by = ps3::baselineToTopY(devY(VH*fy), scale)
- textAlign right at X: x = devX(XCF(VW*f)) - measureText(s, scale); center: minus half.
Primitives: drawText(s,px,py,scale,r,g,b,a), measureText(s,scale), drawQuad(x,y,w,h,
r,g,b,a), drawIconTex(tex,x,y,w,h,r,g,b,a). Background: render() already drew the wave
(renderEffect + ps3bg) before renderPs3Xmb, so renderMusicPlayer draws only the
bar/panel over it. Dispatch: in renderPs3Xmb add `if (mMpActive){ renderMusicPlayer(); return; }`
right after the mPs3TzActive branch (NanoMenuPS3Menu.cpp ~1403), before the menu chrome.

BAR (drawMusicPlayer, index.html:11893-12016) - all virtual: jacket x0.066 y0.827
sq0.085H; title baseline 0.866 32px, marquee bounce HOLD 1100ms SPEED 55px/s smoothstep,
clip to titleRight=fullInfo?0.715:0.955; artist/album baseline 0.900 19px alpha0.70
"(a)/(st)"; full-info cluster (fade mMpFullInfoT) rx0.738 rEnd0.940: counter (idx+1)/N
right@0.792 17px, time mpFmtTime(cur)/dur @0.866 22px (HH:MM:SS), codec badge mpIcon
CODEC_ICON{MP3:24,ATRAC:22,AAC:23,PCM:25,CD:26,WMA:27} @0.887,0.844 h0.024, seek bar
sx=rx sy0.892 h0.009 (track rgba70, edge rgba150 1px, fill rgba245 width sw*cur/dur).
Whole bar alpha = mMpEnterT. Status row (panelUp) drawMpStatusRow @y0.782 h0.030 uses
status icons play=0 pause=3 stop=4 prev=1 next=2 rew=5 ff=6 repeat-all=7 +one=8 shuf=9.
Banner mMpBanner 1500ms (150 in/300 out) @0.045,0.11 24px. msg chain drawMpMsg 0.45 dim
+ centred 28px (Deleting 800 / Delete completed 900 -> mpNext).

CONTROL PANEL (MP_CP[14], index.html:12089; openMpOpt/mpOptMove/mpOptActivate:12109;
drawMpOpt:12212; drawMpVolMeter:12191): grid ox=XCF(VW*0.273) oy=VH*0.441 cellX=VW*0.033
cellY=VH*0.061 ih=VH*0.046; cx=ox+gx*cellX cy=oy-gy*cellY; per btn shadow icon (s)
behind + normal (n), focused uses focus icon (f) + scale 1.18 + breathing halo + label
@0.568 + SELECT pill; open/close 200ms slide ox-=(1-t)*20; press flash 240ms; focus ease
140ms. Acts: vol(submenu -4..+4 -> setVolume((lvl+4)/8)), vis(mpCycleVis), addpl, del,
disp(toggle fullInfo), prev/next(transient glyph 900ms + mpPrev/mpNext), rew/ff(+-10s
seek + transient), play/pause/stop, repeat(0..2), shuffle(toggle+mpRebuildOrder).
INPUT (NanoMenuInput.cpp + ps3Xmb*): TRIANGLE toggles panel (openMpOpt/closeMpOpt);
when panel open: dpad->mpOptMove (Up=dy+1 Down=dy-1 Left=dx-1 Right=dx+1), X->mpOptActivate,
Circle->mpOptBack; when closed: Circle->closeMusicPlayer. SQUARE->mpCycleVis. Route the
mMpActive branches BEFORE the mPs3TzActive checks in each handler. mpFmtTime HH:MM:SS.

## Verification
- Build with `buildtv.sh`, EROFS, flash the Brick.
- Import a folder via Music > Search for Media Servers; confirm scan + `nano_music.json`.
- Play each format; check the Now-Playing layout, control panel, repeat/shuffle/auto-advance, playlists.
- Waves enter/leave morph (in and out); Canyon dissolve + flythrough + bass-reactive speed.
- Confirm lazy-load (no audio/decoder/scan/canyon at boot or on the home XMB).

## Status
- Audio engine (NanoAudio): DONE, verified on the Brick (AAudio stream opens at the
  track's native rate, decode + FFT active, correct durations extracted). All formats
  verified decoding on-device: MP3 (c2.android.mp3.decoder), FLAC (the platform FLAC
  extractor outputs PCM -> c2.android.raw.decoder), OGG/Vorbis (c2.android.vorbis.decoder),
  WAV/PCM (c2.android.raw.decoder), M4A/AAC (the AAC decoder). The Now-Playing UI renders
  for every format.
- Library + folder import: DONE, verified end to end on the Brick: Music > Search for
  Media Servers > Add Folder > pick a folder -> recursive scan (mtime-cached) ->
  nano_music.json persisted (with metadata) -> the album appears in the Music column
  -> album -> track list -> select a track -> audio plays. Folder picker is the Game
  Systems one, routed via mFolderPickTarget. Scan results drain + the column rebuilds
  at the settled XMB root (and on cross-process nano_music.json change).
  Known minor: the folders-screen per-folder track count shows the pre-scan value
  until you re-enter it (the async scan drains after the screen was built).
- Now-Playing screen + control panel + playlists: DONE, verified on the Brick.
  Selecting a track opens the full-screen player: jacket cover (real grey music-note
  art), marquee-bounce title, artist/album line, and the status row. TRIANGLE opens the
  control panel (the 14-button sparse MP_CP grid with nearest-neighbour nav, focus
  grow-in + breathing halo + press flash, focused-button label, Volume Control submeter)
  and every action works (play/pause/stop/prev/next/ff/rew/repeat/shuffle/visualizer/
  Display/add-to-playlist/delete). The Display toggle fades in the full-info cluster
  (track counter N/M, HH:MM:SS elapsed/total, MP3 codec plate, live seek bar). Circle
  exits to the XMB. Auto-advance, repeat/shuffle order and the enter/leave fade run from
  musicTick. Verified end to end: bar, panel, status row, Display cluster, Next transport
  (counter 1/2 -> 2/2, new decode), exit.
  Icon-rendering note: the audioplayer/codec/jacket PNGs load in FULL COLOUR
  (monoWhite=false) and the panel SKIPS the firmware "shadow" plate textures (b.s,
  near-opaque dark squares); it draws clean glyphs with a drop-shadow / halo, exactly
  like the web drawMpOpt. Glyphs use NATIVE aspect (repeat/shuffle = wide pills, codec
  badge = wide plate, repeat-one = a small "1"); mpIconAR caches each icon's w/h.
  Lazy-load fires on Music-category focus (musicOnCatFocus) so the column shows albums
  without anything touching music at boot.
  Test hooks: sys.gammaos.nano.nav "tri" = Triangle/panel, "sq" = Square/cycle-vis.
- XMB Waves morph (the enter/leave background transition): DONE. All of it lives in
  ps3bg, driven by the host calling ps3bg::setMusicVisTarget(mMpActive && vis==0 ? 1 : 0)
  every frame from musicTick (before its early-out, so the leave transition still plays
  after the player closes). ps3bg ramps a linear progress toward the target over ~1.0s
  and smoothsteps it into sMvBlend (index.html drawBG 7228-7231), which drives, 1:1 with
  the web:
  - the WAVE: lift uOffset.y += 0.30*blend, emission uFade *= 1+0.35*blend, and the
    ribbon tint cross-fades silver -> magenta [1.0,0.52,1.0] (index.html 5544/5572/5592);
  - the GRADIENT FLIP: FS_BG gains uMusicVis/uMusicTop; it cross-fades the menu gradient
    to the music field (uMusicTop=(0.82,0.30,1.00)*0.58, vert=pow(t,1.2), horiz=0.59+
    0.41*x, t=clamp((vUV.y-0.49)/0.51)), set in renderGradientCache and forced to rebuild
    every frame while the blend moves (sGradLastMv). nano's wave is a separate additive
    pass, so the music gradient here is wave-free (the raised ribbon is added on top);
  - the PARTICLES: ps3part gains setMusicVisBlend + a lazily-spawned second pool (same
    size, kept warm) whose brightness *= blend; the draw count doubles while blend>0
    (index.html activeParticlePool / buildParticleData _mvExtra).
  The morph runs through the normal home ps3bg path (no renderer switch); the Now-Playing
  bar draws over it.
  Perf (the enter/leave transition): the per-frame gradient re-bake during the morph is
  the only extra cost over the settled-in-player state, so the gradient cache is baked at
  HALF resolution (smooth gradient, the work-buffer blit upscales it) - the enter morph
  holds 60fps. The doubled particle pool got two look-preserving cuts: the iridescent
  colour uses one sincosf instead of three cosf (exact angle-addition), and the extra
  pool is only stepped while sMvBlend>0 (it used to keep animating ~1400 extra particles
  every frame on the home menu forever after the first track played).
- Canyon visualizer: DONE (NanoMenuMusicCanyon.cpp, ps3canyon). A 1:1 port of
  canyon_port.js: all 57 real presets (41 floats each), the terrain/feedback/tonemap
  shaders, the look-at + perspective camera, the 7s preset cross-fade cycle and the
  3-pass pipeline (terrain -> feedback motion-blur -> Reinhard tonemap) into half-res
  ping-pong FBOs. Audio reactive: bass surges the camera speed, mid lifts the exposure.
  GLES2 ADAPTATION: the web samples the heightfield with a VERTEX texture fetch, which
  nano's strict ES2 context does not guarantee, so the per-vertex height is recomputed
  on the CPU each frame with the EXACT sampleHeight (two-octave bilinear of the decoded
  normalmap) + valley formula and uploaded as an aHeight attribute; the fragment shader
  still samples the normalmap per-pixel for the sheen, so the look is unchanged. The
  height grid is computed once per cell (valley hoisted per column) then mapped into the
  triangle strip via a precomputed index. PERF: the normalmap is 128x128 (power of two)
  so the CPU sampler uses a bitmask wrap instead of integer modulo (A53 div is slow);
  and renderEffect + the offscreen wave work-texture are skipped while the Canyon fully
  covers (mMpCanyonAlpha >= 0.999), since the wave would just be overdrawn. The final
  tonemap pass composites to the panel with the DRM rotation + the crossfade alpha.
  SQUARE (nav hook "sq") cycles Waves <-> Canyon: musicTick ramps mMpCanyonAlpha 0<->1
  over ~0.5s while the wave morph ramps the opposite way, so they dissolve. Lazy init
  on first switch (ps3canyon::init/reset in mpCycleVis); freed on leaving Now-Playing
  (ps3canyon::shutdown in closeMusicPlayer). Lattice 160x192 (web is 200x256).
- Globe visualizer (vis 2): SQUARE cycles Waves -> Canyon -> Globe. A compact port of
  the REAL XMB web globe (globe_mp.js / MPGlobe) in NanoMenuMusicGlobe.cpp (ps3mpglobe).
  The firmware globe ships 2.1GB of captured DXT1 patch tiles + HDR LUTs + per-scene
  bloom harvests, which cannot fit on the device, so this reproduces the STYLE and
  EFFECTS compactly:
  - A free-camera RAY-MARCHED earth (single fullscreen pass): ray-sphere the unit earth,
    shade with the firmware surface model - day/night smoothstep(0.62,-0.30,N.sun), cloud
    whitening, earth_night city lights + cool ambient on the night side, the warm
    terminator twilight band (0.11,0.06,0.035), a rough ocean Blinn-Phong glint, the
    HDR *8 scale; the already-shipped earth_day/night/clouds equirect maps (nano_xmb_globe).
  - The analytic ATMOSPHERE: a Fresnel limb rim (cool blue 0.16,0.34,0.58, pow 12,
    night-biased) on the disc + a soft outer halo past the silhouette.
  - The SUN: a bright warm anisotropic disc/halo in the sun direction, occluded by the
    earth (only drawn on sky pixels), rendered hot so it blooms.
  - The signature GLOW/BLOOM: bright-pass (display luminance over a threshold) -> a
    2-level separable-Gaussian pyramid (half + quarter res) -> additive composite. This
    is the golden corona / eclipse-burst. Extended-Reinhard tonemap (exposure 0.0986,
    white 3.40918) per the firmware.
  - The CAMERA replays the REAL firmware scene-0..4 paths (NanoMenuMusicGlobeScenes.h,
    decoded from the captured MVP registers 260/261/263 + eye reg 460): eye/forward/up/
    fovy keyframes interpolated Catmull-Rom over each scene's duration, cycling 5 scenes
    with a dip-to-black fade. Real fovs 16.75/43.69/31.64/15.25/22.53 deg. Stars (sparse
    additive hash dots) on all scenes except the bright low-orbit scene 0.
  Composite to the panel with the DRM rotation + crossfade alpha. Lazy init in
  mpCycleVis(vis==2) + reset; freed on minimize/close (NOT shared with the timezone
  picker). The wave-gate covers Globe too (mMpGlobeAlpha >= 0.999). Tunables (prop
  overridable for on-device tuning): persist.gammaos.nano.globe.{sunbri,sunhalo,bloomthr,
  bloomgain,nightbri,rim,halo}.
- Background playback + minimize + resume: Circle/Back from Now-Playing now MINIMIZES
  (minimizeMusicPlayer) instead of stopping - the audio + queue persist and keep playing
  (the web paused on close; this is a deliberate enhancement so you can leave the player,
  and in the overlay resume your app, with music going). The decoder + AAudio threads run
  independent of the render loop, and the overlay process stays resident on dismiss, so
  playback continues. Auto-advance + the resume-item trigger were moved ahead of the
  !mMpActive early-out in musicTick so they run while minimized (note: while the overlay
  is fully dismissed the render thread parks, so the current track finishes and
  auto-advance resumes on the next overlay open). closeMusicPlayer (full release + clear
  queue) is now only for an explicit teardown.
- Screen-off keeps the music playing (power button): pressing power during playback blanks
  the panel but does NOT drive the full system suspend (which would freeze the decoder /
  AAudio threads). enterDrmSleep takes a keepAudio path (mMpQueue non-empty && isPlaying)
  that holds /sys/power/wake_lock, polls at 1s, and runs the auto-advance, so the album
  keeps playing with the screen off and relights on the next press. The render watchdog is
  suppressed (mInDrmSleep) while the render thread is parked in the sleep loop, so it does
  not abort the oneshot home process mid-sleep - that abort previously killed the panel and
  the music and left the power button looking dead until a restart. With no music it falls
  back to the normal PowerManager system suspend, now also watchdog-safe.
- Audio-playing indicator: drawPs3Clock draws a small procedural eighth-note at the far
  left of the status icons whenever mMusicPlayer is loaded (bright playing / dim paused),
  in both home and overlay.
- Quick Menu "Resume Audio Player": QA_RESUME_AUDIO, prepended to the Quick Menu (icon 3)
  only while mMusicResumeShown (musicTick rebuilds the cats when audio starts/stops);
  dispatch calls resumeMusicPlayer() which reopens Now-Playing on the live queue + the
  last-used visualizer.
- Overlay (in-app) visualizers: verified on the Brick over RetroArch in scrim mode
  (force with `setprop sys.gammaos.nano.app_launched 1` before raising, since the
  monkey-launch path does not set it). The Canyon and Globe composite straight to the
  panel, so they already showed over the live app. The XMB Waves visualizer normally
  renders OFFSCREEN in the overlay scrim path (it only feeds glass-icon refraction), so
  the Now-Playing screen showed the dimmed app instead of the morph. Fixed in
  NanoMenuRender.cpp: when mMpActive && mMpVis==0 the scrim branch composites the wave to
  the screen (compositeToScreen=mpWavesVis), bringing Waves in line with Canyon/Globe so
  the default visualizer is the morph over the app. Normal overlay (no player) is
  unchanged (wave stays offscreen, app shows scrimmed).

## Metadata, file order, scan robustness (2026-06-15)

- Real track tags: the Music library shows the actual ID3 / Vorbis / MP4 title, artist
  and album instead of the filename / parent-folder fallback. The root cause of the old
  fallback was a framework gap, not the scanner: the NDK `AMediaExtractor_getFileFormat`
  (via `NuMediaExtractor::getFileFormat`) only copied the mime type and dropped every
  container tag the extractor had already parsed. Fixed in libstagefright by calling
  `convertMetaDataToMessageFromMappings` on the file metadata (the same helper the track
  path uses), so title/artist/album/genre/year/cdtracknum and embedded album art come
  through. NanoAudio::probe / readMetaFromExtractor already read those keys.
- Re-probe on upgrade: nano_music.json carries a metadata-parser version
  (`kMusicMetaVersion`, currently 2). When the stored version is older the scanner drops
  its mtime cache for one pass and re-probes every file, so an existing library picks up
  the real tags after the fix instead of keeping the stale filename data. saveMusicConfig
  writes the current version.
- File order: tracks inside an album keep their on-disk file order (sorted by path, e.g.
  "01 - ...", "02 - ..."), not re-sorted by the parsed title. musicAlbumTrackIndices sorts
  by `MusicTrack::file`.
- Subtitle marquee: the Now-Playing artist/album line now marquee-bounces and clips to
  the same band as the title (shared `marqueeOff` + `clipBand` lambdas in
  renderMusicPlayer), so a long "artist / album" no longer runs across the seek bar and
  time readout.
- Add to Playlist chooser: the control-panel "Add to Playlist" opens an XMB-style chooser
  (mMpPlChooser*: "New Playlist..." + existing playlists, default on the first existing)
  instead of jumping straight to the OSK, mirroring the web mpOpenAddChooser.
- Storage-ready scan gate: musicScanAsync defers (mMusicScanPending) until external
  storage is mounted (sys.boot_completed + a configured folder is openable), retried each
  frame from the main loop. The scan worker also refuses to publish an empty result when a
  configured folder is unreadable, so a fast boot into Music can never wipe the saved
  library. The Music Folders screen gains a "Refresh" row (PS3_MUSIC_REFRESH, icon 8) that
  rescans on demand.

## Visualizers vs the home wallpaper (2026-06-15)

- The XMB Waves visualizer (vis 0) ALWAYS composites the wave morph in the Now-Playing
  screen, even when the home wallpaper is a non-wave effect. The home render path now
  composites ps3bg to the screen when mMpActive && mMpVis==0 (NanoMenuRender.cpp), so a
  Plasma/Fire/etc. home wallpaper no longer replaces the wave morph in the player.
- Square cycles MORE visualizers: XMB Waves, Canyon, Globe, then the full-screen
  procedural wallpaper effects (Plasma, Fire, Aurora, Ripple, Checkerboard, Spiral, the
  XMB ribbon). mMpVis 0..2 are the dedicated visualizers; mMpVis 3.. map to kMpExtraVis[]
  (mpVisCount/mpVisEffectId). renderEffect renders mpVisEffectId() as the Now-Playing
  background when mMpVis>=3 (a stateless shader, so no particle-pool conflict with the
  home wallpaper). The particle effects (Snow/Rain/etc.) stay home wallpapers only.

## Track Information + universal volume + brightness gating (2026-06-15)

- Track Information: Triangle on a music track -> Information now shows the full tag
  set. The dialog probes the file fresh (NanoAudioPlayer::probe, which reads
  title/artist/album/genre/year/cdtracknum + sample rate/channels/bitrate via the fixed
  getFileFormat) and lists Title, Artist, Album, Genre, Release Year, Track No., Playing
  Time, Format, Sample Rate, Channels, Bitrate and File. Built in xmbOptAction's "info"
  branch for PS3_MUSIC_TRACK; other items keep the name + description page.
- Universal volume: a volume press sets EVERY audible stream (music/system/ring/
  notification/alarm) to the same proportional level so output is consistent across the
  XMB, cold boot, the wallpaper-home and in-app (RetroArch). PhoneWindowManager is the
  single authority (nanoSyncAllStreamsVolume) and publishes persist.gammaos.nano.volume
  (index) + persist.gammaos.nano.volmax (range); the nano launcher syncs its slider from
  those so the nano HUD and PWM HUD always show the same value. nano injects the volume
  keyevent (devices are EVIOCGRAB'd) so PWM does the real change.
- Brightness gating: SELECT+volume = brightness only while SELECT is genuinely held.
  The check now reads the live key state via EVIOCGKEY (selectKeyHeld) instead of the
  sticky mSelectHeld, so a missed SELECT release can no longer make a plain volume press
  change brightness.
