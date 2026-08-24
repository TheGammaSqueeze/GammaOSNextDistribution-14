# Subsystems

The ~120 source files group into 15 subsystems. Each file has a single clear role.
Deep-dive docs for individual subsystems live under `docs/` and are linked as they land.

## Core menu + rendering infrastructure

- `main.cpp` — process entry; arg parsing (`--overlay`), DRM/binder init, runner setup.
- `NanoMenu.h` / `NanoMenu.cpp` — the central `NanoMenu` class (all state) + the render
  thread and frame loop. See [ARCHITECTURE.md](ARCHITECTURE.md).
- `NanoMenuRender.cpp` — EGL/GLES2 frame assembly, the `render()` dispatcher, quad/text
  primitives, FBO management, and the DSi `renderNds*` family.
- `NanoMenuInput.cpp` — gamepad/touch dispatch, D-pad repeat/acceleration, per-theme nav
  routing, BT low-power gate.
- `NanoMenuState.cpp` — legacy text-menu state model (not part of the XMB theme dispatch).
- `NanoMenuSystem.cpp` — system polling (battery, time, volume, brightness), slider HUD.
- `NanoMenuDrm.cpp` / `NanoMenuDrm.h` — DRM/KMS presentation, rotation matrix, sleep.
- `NanoMenuRgbFollow.cpp` — GammaRGB LED ambient-follow sampler.
- `NanoMenuXmb.cpp` — XMB nav bindings + the `XmbSystem` model (systems/ROMs persistence).
- `NanoMenuShaders.cpp` / `.h` — GLES2 program sources + compilation.
- `NanoMenuEffects.cpp` — procedural/particle wallpaper effects.
- `NanoMenuOverlay.cpp` — in-game overlay lifecycle (blur capture, Z-order, translucency).
- `NanoMenuUtils.h` — path/storage inline helpers.

## PS3 XMB visuals + layout

- `NanoMenuPS3Layout.cpp` — the adaptive `LAYOUT_*` model (virtual space -> any panel).
- `NanoMenuPS3Bg.cpp` / `.h` — per-month gradient + cloth wave (cached FBO, tonemap).
- `NanoMenuPS3Particles.cpp` / `.h`, `NanoMenuPS3ParticleCloud.h` — glitter particle field.
- `NanoMenuPS3Icons.cpp`, `NanoMenuSbIcons.h` — glass icon relight (normal map, fresnel).
- `NanoMenuPS3IconGrid.cpp` — responsive icon/cover grid, lazy atlases.
- `NanoMenuPS3Globe.cpp` / `.h` — 3D Earth for the timezone screen.
- `NanoMenuPS3Menu.cpp` — the XMB home render, the shared model builders, the nav verbs,
  the settings-binding table, the per-item Options menu. The largest file; the model heart.
- `NanoMenuPS3Data.h` — the static PS3 XMB category/item tree.
- `NanoMenuPS3Clock.cpp`, `NanoMenuPS3ClockGlyphs.h` — the PSP-style clock + timezone globe.
- `NanoMenuPS3Boot.cpp` — cold-boot sequence + per-theme boot overlays + sfx dispatchers.
- `NanoMenuPS3EmuCatalog.cpp` — Daijishou platform catalog parser (emulator resolution).
- `NanoMenuPS3Folder.cpp` — folder picker (mounts, SMB, USB) for ROM/media import.

## Home themes (skins over the shared model)

- XMB — in `NanoMenuPS3Menu.cpp` (canonical).
- DSi — in `NanoMenuRender.cpp` (`renderNds*`).
- Minima — `NanoMenuMinima.cpp`.
- ES-DE engine — `NanoThemeEngine.cpp` (renderer + nav) / `NanoEsdeTheme.h` / `.cpp`
  (GL-free parse model, links `libtinyxml2`). See [THEME_SYSTEM.md](THEME_SYSTEM.md),
  [THEME_ENGINE.md](THEME_ENGINE.md), and [theme-engine/IMPLEMENTATION.md](theme-engine/IMPLEMENTATION.md).

## Media library + playback

- `NanoMenuMusic.cpp`, `NanoMenuMedia.cpp` — music library + generic async media scanner.
- `NanoMenuMusicCanyon.cpp` / `.h`, `NanoMenuMusicGlobe.cpp` / `.h`,
  `NanoMenuMusicGlobeScenes.h` — music visualizers.
- `NanoMenuAudioBands.cpp` / `.h` — FFT band tap for visualizers.
- `NanoMenuPhotos.cpp` — photo library + full-screen viewer.
- `NanoMenuVideo.cpp` — video library UI + playback screen.
- `NanoMenuRadio.cpp`, `NanoMenuIptv.cpp` — internet radio + IPTV.
- `NanoMenuSearch.cpp` — global search across library/media/settings.

## Audio + video engines

- `NanoAudio.cpp` / `.h` — standalone audio decode (AMediaCodec worker + AAudio, FFT).
- `NanoVideo.cpp` / `.h` — hardware video decode (AMediaCodec -> GLConsumer surface).

## Demuxers + codecs

- `NanoTsDemux.cpp` / `.h`, `NanoTsDescramble.cpp` / `.h` — MPEG-TS demux + CA masking.
- `NanoAviDemux.cpp` / `.h` — RIFF/AVI container parser.
- `NanoHls.cpp` / `.h`, `NanoIcyDemux.cpp` / `.h` — HLS fetch + raw stream demux.
- `NanoDvbSub.cpp` / `.h`, `NanoCea608.cpp` / `.h` — subtitle/caption decoders.
- `NanoAc3.cpp` / `.h` (over vendored `liba52`), `NanoAacDec.cpp` / `.h` — audio codecs.

## On-screen keyboard

- `NanoOsk.cpp` / `.h`, `NanoOskTypes.h`, `NanoOskInput.h`, `NanoOskLayouts.h`,
  `NanoOskLayoutsExtra.h` — engine + layouts.
- `NanoOskArabic.h`, `NanoOskHangul.h`, `NanoOskKana.h`, `NanoOskKanji.h`,
  `NanoOskPinyin.h` — per-script input methods.

## Game runners

- `LibretroRunner.cpp` / `.h`, `libretro.h` — libretro core driver.
- `DrasticRunner.cpp` / `.h`, `FakeJNI.cpp` / `.h` — DS emulator runner + JNI shim.
- `rcheevos/` — vendored RetroAchievements client (static `librcheevos_nano`).

## Content scraper

- `NanoScraper.cpp` / `.h` — scraper engine (ScreenScraper / TheGamesDB fetch, cache).
- `NanoMenuScraper.cpp` — scraper UI + manifest (`ScrapeEntry`, index.json, art textures).
- `NanoScraperDevCreds.h` / `.gen.h` — compiled-in obfuscated credentials.

## Network, shares, OTA

- `NanoMenuNet.cpp` — Wi-Fi/BT status + icons.
- `NanoMenuShares.cpp` — network shares editor (delegates to gammaos-sharefs).
- `NanoMenuOta.cpp`, `NanoOtaCheck.cpp` / `.h` — OTA check + update UI.

## Settings

- `NanoMenuSettings.cpp`, `NanoMenuSettingsTree.cpp` / `.h`, `NanoMenuSettingsRender.cpp`
  — settings tree, bindings, and rendering (the legacy tree; the XMB path reuses the
  read/write primitives).

## File management + setup

- `NanoMenuFileExplorer.cpp` — generic file browser.
- `NanoMenuSetupWizard.cpp` — first-boot wizard.

## Strings + i18n

- `NanoMenuStrings.cpp` / `.h` — UI string table.
- `NanoI18n.cpp` / `.h` — runtime translation loader (per-language JSON).

## Boot + hardware audio

- `NanoBootChime.cpp` / `.h` — RG-DS direct-ALSA boot chime.
- `NanoHalChime.cpp` / `.h` — SPRD vendor audio-HAL boot chime.
- `NanoBtStable.cpp` / `.h` — A2DP stability monitor.
- `NanoBacklight.h`, `NanoSliderHud.h` — backlight + slider HUD helpers.

## Support + vendored

- `NanoJson.cpp` / `.h` — lightweight JSON parser (no-throw).
- `stb_image.h` — single-header PNG/JPG decoder.
- `xmb_icons.h` — XMB icon atlas metadata.
- `liba52/` — vendored AC-3 decoder (static `liba52_nano`).

## Build

`Android.bp` defines the `gammaos-nano` binary (ThinLTO, Cortex-A55 tuning, `-O3`
`-ffast-math`), the vendored static libs, the `drastic_nano_shared_srcs` filegroup, and
~20 `prebuilt_etc` asset packages (fonts, wave/gradient/boot/icon/normalmap/glass
textures, globe maps, RetroArch + UI icons, DSi assets, Daijishou catalogs, i18n JSON,
player/photo icons, backgrounds). Assets install to `/system/etc/nano_xmb/` and
`/system/etc/gammaos-nano/`; loaders check `/data/system/nano_xmb/` first for dev pushes.
