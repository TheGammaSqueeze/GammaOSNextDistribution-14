# Architecture

GammaOS Nano is one native process that owns the screen and renders the home UI,
media players, and boot animation with OpenGL ES 2.0. There is no Java/Views layer in
the hot path: the launcher talks to DRM/KMS (or SurfaceFlinger as a fallback), EGL,
FreeType, libpng, and the media NDK directly. This keeps it fast and small enough for
Allwinner A133 / 1 GB devices.

## Process model

`main.cpp` parses the invocation, acquires DRM master (or an early splash), constructs
a `sp<NanoMenu>`, and calls `run()`. Two instances exist:

- **Home** (`GammaOSNano`) — the boot-time home / launcher.
- **Overlay** (`GammaOSNanoOverlay`) — a translucent in-game overlay (Quick Menu,
  clock, control center) composited over a running app.

`NanoMenu` (declared in `NanoMenu.h`) is an AOSP `Thread` and an `IBinder::DeathRecipient`.
It aggregates the entire application state: EGL/GL objects, the home data model, every
media player, the scraper index, input state, and the theme flags. `readyToRun()` sets
up EGL/DRM; `threadLoop()` is the application.

> `NanoMenu.h` is large because it is the single class the whole program hangs off.
> Member variables are grouped by subsystem with a short banner comment per group.
> When adding state, place it in the matching group and document the group, not each
> field, unless a field is subtle.

## Render thread and frame loop

`threadLoop()` (NanoMenu.cpp) has two parts:

1. **One-time setup** — `SCHED_FIFO` priority + `nice -20`, the Bluetooth-stability
   thread, `mallopt` + `mlockall(MCL_CURRENT|MCL_FUTURE)` to keep pages resident, a
   wait for `ro.persistent_properties.ready`, locale init, the setup-wizard gate, and
   the **one-time theme-flag reads** (see THEME_SYSTEM.md).
2. **The frame loop** — `while (!exitPending() && !mExitRequested)`:
   - bump `mRenderHeartbeat` (a background watchdog aborts into a tombstone if the
     render thread hangs, instead of freezing the device);
   - fast-path `continue`s that skip rendering while still beating the watchdog
     (screen-off, DRM occlusion, overlay idle);
   - pick a frame budget: 60 fps for the DRM/XMB/procedural path, dropping to
     `persist.gammaos.nano.ps3xmb.idlefps` (default 30) after 60 s idle, lower for
     pure-particle or idle text screens;
   - measure `mFrameDt` (CLOCK_MONOTONIC, clamped to [1 ms, 100 ms]) for
     frame-rate-independent animation;
   - `pollInput()`, then `render()`, then LED sampling + ADPF perf hint;
   - pace to the budget; the present (DRM page-flip or `eglSwapBuffers`) happens in
     `render()`'s tail.

## The `render()` dispatcher

`render()` (NanoMenuRender.cpp) is the per-frame orchestrator:

1. **Shared per-frame work** — OTA pump, scraper live-reload poll, glyph-atlas reset,
   `updateEffect()` (wave/particle time), `ps3bg::newFrame()`, and the video-wallpaper
   ticks. This must run once regardless of theme.
2. **Secondary pass** — renders the bottom panel (RG-DS style dual screen) into the
   secondary DRM AHB or EGL surface. It temporarily remaps `mWidth`/`mHeight` to the
   secondary panel dimensions around each theme helper (those helpers project via
   `mWidth`/`mHeight`); the save/restore must be exact or text mis-projects on rotated
   single-display devices.
3. **Primary pass** — the main menu panel: drastic quick-resume split, setup wizard,
   OSK-over-app, then the theme dispatch (XMB / DSi / Minima / ES-DE), then the shared
   overlays (color picker, gamepad test, scrape progress, OSK, launch fades).

Both passes select the active theme with the same flags; see THEME_SYSTEM.md for the
dispatch structure and the planned `activeTheme()` accessor.

## Backend: DRM-direct vs SurfaceFlinger

`NanoMenuDrm.cpp` owns presentation and is fully theme-agnostic: themes render into
whatever FBO/viewport is bound and never touch backend code.

- `sDrmActive` — a DRM display was enumerated; nano scans out directly.
- `sDrmZeroCopy` — render straight into the scanout AHB (an `sAhbTarget` ring), no blit.
- `drmBindNextFbo()` / `drmFlipAll()` present the primary; `sAhbTargetSecondary` feeds
  every non-primary display.
- If no DRM display is available, nano falls back to an EGL window surface on
  SurfaceFlinger (`mSecondaryEglSurfaces` for the bottom panel) and `eglSwapBuffers`.

The global DRM rotation matrix (`sDrmGlRotation`, `NanoMenuDrm.h`) is uploaded to every
shader once per frame (upload-on-change) so all geometry comes out panel-native.

## Adaptive layout

`NanoMenuPS3Layout.cpp` (`ps3::layoutComputeNative`, the `LAYOUT_*` model) maps a virtual
design space to the real panel for any resolution, aspect ratio, and orientation. New
UI should compute its geometry from `mWidth`/`mHeight` through this model (or an
equivalent normalized model) so adaptivity is centralized, not re-derived per screen.
The ES-DE engine uses ES-DE's own normalized 0..1 coordinate system for the same reason.

## The shared home data model

All home themes read and mutate one model (see THEME_SYSTEM.md and the input-model
notes):

- `mPs3Cats` — the home columns (categories). Built once by `buildPs3Cats()` from a
  table-driven copy of the PS3 XMB tree (`NanoMenuPS3Data.h`) plus runtime nano content
  (emulator systems, ROMs, Recently Played, Applications, Favorites, Collections, media
  libraries). Rebuilt only on rescan/edit, never per frame.
- `mPs3Stack` — the drill stack of pushed submenu levels.
- `ps3CurItems()` / `ps3CurSel()` — depth-agnostic accessors for the current list and
  selection, so nav code works the same at any depth.
- `mXmbSystems` (`XmbSystem`) — the game systems (id, rom dir, launch routing, icon,
  scan sources, ROM paths + display names). This is the ES-DE "system".
- `scrapeEntryFor(romPath)` / `romBoxartTex(romPath)` — box-art, fan-art, and metadata
  for a ROM, resolved by path with storage-alias normalization. Art decode is async and
  the texture cache is bounded (~96 covers) to protect VRAM on low-RAM devices.

The activate/drill verbs (`ps3XmbSelect/Back/Left/Right/Up/Down`) are the single source
of truth for mutating this model. Themes never reimplement them; DSi/Minima nav wrappers
delegate into them.

## Subsystem map

The ~120 source files group into 15 subsystems (core + render, XMB visuals, media
players, demuxers, codecs, OSK, runners, scraper, net/shares/OTA, boot/chime, settings,
file management, i18n/strings, support). See [SUBSYSTEMS.md](SUBSYSTEMS.md) for the full
file-by-file index and per-subsystem docs.

## Build and deploy

- **Build**: `buildtv_cc.sh nosync` from the repo root (LineageOS TV64VN, CC boot image
  for old-kernel devices). A fast compile check is `m gammaos-nano`, but the LTO image
  build (`buildtv_cc.sh`) is the authoritative check.
- **Compress**: `sudo /work/brick/system_erofs/build_system_erofs.sh` turns the raw
  ext4 `system.img` into an lz4hc EROFS image for fastbootd flashing.
- **Flash**: fastbootd (`fastboot flash system`), per-partition on the Brick.
- **Assets** ship via `prebuilt_etc` to `/system/etc/nano_xmb/` and
  `/system/etc/gammaos-nano/`; loaders also check `/data/system/nano_xmb/` first so
  assets can be pushed during development without reflashing.
- **Screenshot**: `setprop sys.gammaos.nano.shot 1` writes `/data/local/tmp/nano_shot.ppm`
  (SurfaceFlinger/fbdev capture is black on the DRM-direct path).

`Android.bp` defines the `gammaos-nano` binary (ThinLTO, Cortex-A55 tuning, `-O3`
`-ffast-math` for the render hot path), the vendored static libs (`liba52_nano`,
`librcheevos_nano`), and the prebuilt asset packages.

## Performance invariants (non-negotiable on low-end SoCs)

These are load-bearing. A change that breaks one will regress FPS or memory on the A133
class:

- **No `property_get` on the render thread per frame.** Cache prop reads; re-read only
  on an explicit generation/serial bump. (~2 us/read adds up at 60 fps on Cortex-A55.)
- **No heap allocation in the hot path.** Reuse VBOs/FBOs and scratch buffers; use
  16-bit indices; do not build strings per frame.
- **Cache derived GPU work.** The background gradient is baked to a texture and rebuilt
  only when time-of-day/month/layout changes; the wave uses a single reusable mesh; the
  bottom clock has a 30 fps re-present cache. New themes must slot into the same caches.
- **Lazy-load and bound caches.** Textures/atlases load on demand; the box-art cache is
  capped; the scraper art worker stops fully at idle (zero threads/CPU when nothing is
  happening).
- **Off-thread I/O.** File, decode, and network work runs on detached workers with
  mutex-protected handoff; the render thread only samples results.
- **Avoid overdraw and expensive per-fragment effects on weak GPUs.** Half-res gating
  exists for the wave and clock; the per-icon half-res path was disabled because the
  PowerVR TBDR tile flush cost more than it saved (documented at the call site).
