# GammaOS Nano

GammaOS Nano is the native launcher and home experience for GammaOS. It is a
single-process C++ application that renders directly to the display (DRM/KMS or
SurfaceFlinger) using OpenGL ES 2.0, targeting low-end ARM64 handhelds (Allwinner
A133 class, 1 GB RAM, Mali / Adreno / PowerVR GPUs).

It provides three built-in home "themes" (PS3 XMB, DSi Menu, Minima), a full media
suite (music, video, photos, internet radio, IPTV), game-system management with a
box-art scraper, emulator launching, an on-screen keyboard, network/share/OTA tools,
and a from-scratch cold-boot sequence.

## Documentation map

Detailed docs live next to the code they describe, under this `docs/` folder. Code
comments stay concise; the "why" and the cross-cutting design live here.

| Doc | Scope |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Process model, render thread + frame loop, the `render()` dispatcher, DRM/SF backend, adaptive layout, the shared home data model, build system, and the performance invariants every contributor must respect. |
| [THEME_SYSTEM.md](THEME_SYSTEM.md) | How the three built-in themes work as render/input/audio skins over one shared state machine, the responsibilities a theme owns, and the seam a new theme plugs into. |
| [THEME_ENGINE.md](THEME_ENGINE.md) | The ES-DE-compatible theme engine: the ES-DE theme format, how nano's data binds to ES-DE `system` / `gamelist` views, the element/property support matrix, module layout, and the low-end performance strategy. |
| [SUBSYSTEMS.md](SUBSYSTEMS.md) | Index of every source file grouped into 15 subsystems, with a one-line role for each and pointers to the per-subsystem docs. |
| [BUILD_AND_FLASH.md](BUILD_AND_FLASH.md) | How to build, EROFS-compress, flash via fastbootd, run the two-device A/B rig, and capture screenshots, with the operational gotchas. |
| [theme-engine/IMPLEMENTATION.md](theme-engine/IMPLEMENTATION.md) | ES-DE engine internals: parser flow, the element support matrix, coordinate model, and how to add an element. |
| [theme-engine/CAROUSEL.md](theme-engine/CAROUSEL.md) · [GRID.md](theme-engine/GRID.md) · [TEXTLIST.md](theme-engine/TEXTLIST.md) · [IMAGE.md](theme-engine/IMAGE.md) · [BADGES.md](theme-engine/BADGES.md) | Per-element deep dives: the carousel, grid and textlist primaries (the textlist selector bar + text-hugging selected-background plate), the image/video fit model (contain/fill/cover, cropSize/cropPos) that governs boxart cropping, and the badges flexbox (slots, alignment offsets, controller overlay). |
| [theme-engine/METADATA.md](theme-engine/METADATA.md) | The text / datetime / rating / gamelistinfo elements: content binding, container scroll, fonts and letterCase, the rating star model, and the verified-vs-deferred conformance items. |
| [theme-engine/LAYERING.md](theme-engine/LAYERING.md) | How the base layer is overlaid by the selected variant / colorScheme / fontSize / aspectRatio and their `${variables}`: selection rules, the two-pass walk, view precedence, and the known variable-precedence gap. |
| [theme-engine/VIEW_TRANSITIONS.md](theme-engine/VIEW_TRANSITIONS.md) | The system<->gamelist transition model (slide / fade / instant) and how nano reproduces it. |

Per-subsystem deep-dive docs (media players, OSK, scraper, boot, demuxers, ...) are
added incrementally under `docs/` and linked from `SUBSYSTEMS.md`.

## Quick orientation for a new contributor

- Entry point: `main.cpp` builds a `NanoMenu` (an AOSP `Thread`) and calls `run()`.
- The whole app is `NanoMenu::threadLoop()` (NanoMenu.cpp): one-time setup, then an
  unbounded frame loop that polls input, calls `render()`, and presents.
- `render()` (NanoMenuRender.cpp) is the per-frame orchestrator. It runs shared
  per-frame work, then a secondary (bottom-panel) pass and a primary (menu) pass.
- The active theme is selected by `persist.gammaos.nano.ndstheme`
  (`0`=XMB, `1`=DSi, `2`=Minima, `3`=ES-DE engine). See THEME_SYSTEM.md.
- Build for a test device with `buildtv_cc.sh nosync` from the repo root; see
  ARCHITECTURE.md "Build and deploy".

## Ground rules

- GLES2 only. No allocation, `property_get`, or synchronous I/O on the render thread.
- Adapt to any resolution / aspect / orientation through the layout model; never
  hardcode for one panel.
- No regressions: the three built-in themes are feature-frozen and pixel-stable. New
  work is additive and gated behind its own property.
