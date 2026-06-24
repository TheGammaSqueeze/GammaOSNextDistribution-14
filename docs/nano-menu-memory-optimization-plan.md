# Nano Menu Memory Optimization Plan

## Implementation status (2026-06-24, uncommitted on develop, compile-clean)

DONE this pass:
- A1 glyph atlas 16->8 MB (1024x2048). LA8+emoji-split (~4MB more) left as follow-up.
- A2/A3/A4 adaptive icon/bevel right-sizing via `ps3::iconTexCap` (panel-resolution
  driven, 2x supersample headroom, capped at source) + mipmaps on category icons.
- A5 overlay backdrop RGB565 with FBO-completeness guard + RGBA fallback.
- B1 free 21 MB wave VBO on park (`ps3bg::freeWaveSeq`), lazy rebuild on live home.
- B2 free world-clock globe FBO+textures on TZ-picker close (`ps3globe::shutdown`).
- B3 free baked overlay backdrop on park (re-baked on raise).
- C6 ThinLTO on the nano binary.
- D2 theme-configurable primary font via `persist.gammaos.nano.font`.

REMAINING (smaller / higher-effort):
- B4 free glass scratch pyramid on park (~1-4 MB); B5 triple-buffer->2 while parked (~3 MB, delicate, judder risk).
- C1 kanji const char* -> offset blob (~168 KB private-dirty, needs header regen).
- C2 cache per-frame description word-wrap; C3 dedupe text/solid vertex .bss; C4 avoid the transient atlas-clear vector.
- D1 load all 3 Rodin weights + select by weight; D3 split embedded icons to an external themeable asset dir.

---


Deep analysis of `frameworks/base/cmds/gammaos-nano/` for GPU + CPU memory
reduction with **no perceived quality or performance loss**. Target: TrimUI
Brick, PowerVR Rogue GE8300, **GLES2 only**, 1024x768, 1 GB RAM, aarch64.

Baseline: overlay process ~140 MB PSS = **~89 MB GPU** (GL 77 + EGL 12) +
~8 MB native heap (+18 MB swapped) + shared code. GPU is the whole story.

Hard constraint discovered: the GL context is **GLES2** (`NanoMenu.cpp:652/780`,
`NanoMenuDrm.cpp:110`), so **ETC2/EAC compression is off the table**. The lever
is **format narrowing** (RGBA8888 -> LA8 / A8 / RGB565) and **right-sizing**
(source textures are drawn far below their resolution), not compression.

---

## Track A - Steady GPU texture reduction (~28 MB off the resident 89 MB)

Applies whenever the menu is visible (cold-boot home AND in-game menu). All
GLES2-safe, no compression, no perceptible change (every texture is drawn well
below its source resolution on a 1024x768 panel).

| # | Item | Site | Now | Change | Saved | Risk |
|---|------|------|-----|--------|-------|------|
| A1 | **Glyph atlas RGBA8888** (mono glyphs store constant RGB=255) | `NanoMenuRender.cpp:930-953` | 2048² RGBA = 16 MB | mono atlas -> **GL_ALPHA/LA8** + split color emoji to a 512² RGBA atlas | **~11-12** | med |
| A2 | **8 category icons** drawn <=168 px | `NanoMenuPS3Menu.cpp:147-193,305-316` | 512² RGBA = 8 MB | -> **256²** (+ optional A8; mono silhouettes) | **6-7.5** | low |
| A3 | **18 console icons** drawn 108 px, w/ mipmaps | `NanoMenuRender.cpp:147-159,411-433` | 256² RGBA+mip = 6.3 MB | -> **128²** (+ optional A8 for 17 mono) | **4.7-5.9** | low |
| A4 | **18 bevel normal maps** (blurred gradient) | `NanoMenuPS3Icons.cpp:577-638` | 256² RGBA = 4.7 MB | generate at **128²** | **3.5** | none |
| A5 | **Overlay bg + glass scratch** (opaque blur) | `NanoMenuRender.cpp:305-409,695-713` | ~7.2 MB RGBA | -> **RGB565** (verify FBO-complete on GE8300) | **3.6** | low |

Notes:
- A1 is the single biggest, lowest-risk win. LA8 (keep L=255) is byte-identical
  output with **zero shader change**; GL_ALPHA saves more but needs a one-line
  shader white-broadcast. Color emoji (`NotoColorEmoji`) writes BGRA into the
  same atlas today (`:1115-1120`) - it must move to a small separate RGBA atlas.
- A2/A3/A4 are pure asset/resolution right-sizing: imperceptible at these draw
  sizes. Keep PICO-8 colored console icon (index 14) RGBA.
- A5: start with the sampled-only `mOverlayBgTex` (guaranteed-safe 1.5 MB), then
  the FBO scratch targets after a `glCheckFramebufferStatus` RGB565 check.

**Track A total: ~28 MB resident reclaimed (~32% of GPU), all imperceptible.**

---

## Track B - Free GL when parked over a game (~28-31 MB reclaimed during gameplay)

The most valuable track for actual play: the foreground game needs RAM most.
**Current park path frees ZERO GPU memory** - on park it only `munlockall()`s
CPU pages (`NanoMenu.cpp:3253-3271`); the full ~77 MB GL stays resident and GPU
memory is **not swappable**, so it cannot be reclaimed for the game at all.

| # | Item | Site | Size | Change | Saved | Risk |
|---|------|------|------|--------|-------|------|
| B1 | **Frozen wave VBO** - dead weight while frozen over a game | `NanoMenuPS3Bg.cpp:169,605-610,973-977` | **21.3 MB** VBO | free on scrim-freeze; lazy reload from `wave_seq2.bin` on home | **~21** | none |
| B2 | World-clock globe FBO+maps - `shutdown()` defined, never called | `NanoMenuPS3Globe.cpp:129,256-269,312-322` | 3 MB FBO + maps | call existing `shutdown()` when TZ picker closes | **~3+** | none |
| B3 | `mOverlayBgTex` - re-baked on every raise anyway | `NanoMenuRender.cpp:346-408` | 3 MB | free on park | **~3** | none |
| B4 | Glass scratch pyramid + `mGlassTex` | `NanoMenuRender.cpp:695-713,628` | 1-4 MB | free after bake / on park | **1-4** | none |
| B5 | Overlay triple-buffer (hardcoded, prop is dead) | `NanoMenu.cpp:283,1100` | 3 MB GraphicBuffer | drop to 2 **while parked only**, restore 3 on raise | **~3 parked** | none parked / judder if active |

Implementation vehicle (B-common): extend the park transition next to the
existing `munlockall()` (`NanoMenu.cpp:3261/3265`) to free B1-B4, and re-create
**lazily, spread across frames on raise** (the established boxart/canyon/globe
warm-up pattern). **NEVER rebuild synchronously on the render thread** - that is
the exact render-watchdog SIGABRT hazard the original mlock-on-raise hit.
B5 must stay at 3 for the visible menu (it fixes a documented 60->50fps judder).

**Track B total: ~28-31 MB reclaimed for the foreground game.**

---

## Track C - CPU heap + binary

| # | Item | Site | Type | Saved | Risk |
|---|------|------|------|-------|------|
| C1 | **Kanji `const char*` arrays -> flat UTF-8 blob + `uint32_t` offsets** (RELRO reloc -> clean .rodata) | `NanoOskKanji.h:13,2870` | resident private-dirty, present even if JP OSK never opened | **~168 KB** | low |
| C2 | Cache per-frame description word-wrap (recompute on focus/width change only) | `NanoMenuPS3Menu.cpp:3070-3094` | heap churn -> keeps scudo grown (feeds the 18 MB swap) | stops growth | low-med |
| C3 | Dedupe/shrink text+solid vertex batch `.bss` (per-quad color, not per-vert x6) | `NanoMenuRender.cpp:498-500,1211-1215` | resident-dirty | ~120-180 KB | low-med |
| C4 | Avoid 16 MB transient atlas-clear `std::vector` (tiled glTexSubImage2D / FBO clear) | `NanoMenuRender.cpp:950` | transient spike / fragmentation | 16 MB spike | low |
| C5 | Trim `synopsis`/long fields from in-RAM scrape index; re-read on Info open | `NanoMenu.h:1585` | resident heap | up to ~1 MB | low |
| C6 | Enable ThinLTO | `Android.bp` | binary .text | ~100-300 KB | low |

The pinyin table (`NanoOskPinyin.h`) is already pointer-free/clean - C1 just
copies that pattern to kanji. Pixel loops (bevel/normal-map/downscale) run
once-per-icon-at-load and are already cached as GL textures, so NEON there buys
only init latency, not RAM - **not worth the risk**. scudo is already tuned
(`M_CACHE_COUNT_MAX=0` + mlockall); the win is reducing churn (C2/C4), not retune.

---

## Track D - Themeability + asset splitting (future direction)

Good news: nano is **already** a real-TTF + FreeType + on-demand-atlas
architecture - exactly the "smaller and themeable" design wanted. The web source
of truth (`/work/ps3/xmb-app/index.html:8-12`) uses three weights of **Rodin**
(`ps3-rodin-{regular,bold,light}.ttf`) + `sans-serif` fallback; icons are PNG/SVG
(92 files in `icons/`). nano ships all three weights but the gaps are:

- **D1** `initFonts()` only loads `ps3-rodin-regular.ttf`; **bold/light are
  installed but ignored** (`NanoMenuRender.cpp:889-899`, `Android.bp:213`). Load
  all three faces and select by weight in `drawText` (the web app uses bold for
  headings - also a fidelity fix).
- **D2** Make font paths **theme-configurable** (a theme manifest/prop instead of
  hardcoded `rodinPaths[]`/`fontPaths[]`). The `/data` override before `/system`
  already exists (`:889-892`), so a theme can drop its own `.ttf` today.
- **D3** **Asset splitting** (you are happy to do this): move `xmb_icons.h`
  (442 KB embedded PNGs) and the category/console icon sets out to an external
  themeable asset dir (`prebuilt_etc`, like the other `nano_xmb` assets), loaded
  on demand. Pairs naturally with A2/A3 (ship the right-sized themeable assets so
  the binary doesn't carry oversized PNGs). Binary -442 KB; enables per-theme
  icon packs. Combine with shipping icons as single-channel where mono.
- **D4** Optionally split the CJK IME tables (kanji/pinyin) to an lz4 asset loaded
  only when a CJK keyboard opens. C1 (reloc fix) is strictly better for RAM; D4
  is mainly a binary-size play. Lower priority.

Keep FreeType (do NOT switch to stb_truetype - it would lose the color-emoji /
CJK / RTL fallback machinery for no size win).

---

## Recommended sequencing (value x safety)

1. **A1** glyph atlas LA8 (~11 MB, zero-shader LA8 variant first) - biggest, safest.
2. **B1** free frozen wave VBO on park (~21 MB for the game) - biggest gameplay win.
3. **A2 + A3 + A4** icon/bevel right-sizing (~14-17 MB) - pure asset downsize.
4. **B2 + B3 + B4 + B5** park-time GL free (the rest of Track B) on the shared hook.
5. **A5** RGB565 overlay bg (after FBO-complete check).
6. **C1** kanji offset table, **C4** atlas-clear, **C2/C3** heap churn.
7. **D1/D2/D3** themeability + asset split (the longer-horizon track; D3 aligns
   with A2/A3 so do the right-size + externalize together).
8. **C6** ThinLTO last (whole-binary, validate carefully).

## Verification (so nothing breaks)

- Per change: `m gammaos-nano` to catch compile errors first (Soong -Werror),
  then `buildtv.sh` -> EROFS -> fastbootd flash (separate from build).
- GPU accounting A/B: `dumpsys meminfo <overlay-pid>` GL+EGL mtrack before/after.
- Visual: capture each affected screen (home, each category, OSK incl. CJK/emoji,
  TZ globe, in-game menu over a running game) via the in-nano PPM dump and diff
  against the current build - require pixel-identical or imperceptible.
- Park/raise stress: launch a game, open/close the in-game menu repeatedly, watch
  for the render-watchdog SIGABRT (lazy/incremental re-create must hold).
- Emoji + multi-script regression for A1 (separate color atlas correctness).
