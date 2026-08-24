# ES-DE image and video elements

The `<image>` element draws static art (per-system logos, backgrounds, frames) and per-game scraped
media (box, cover, screenshot, miximage, marquee, fanart). The `<video>` element plays a per-game video
and falls back to a still image with the **same** fit rules. Both are secondary elements (they sit at
their `zIndex` among the chrome), unlike the primary carousel/grid/textlist. nano implements them in the
`t == "image"` and `t == "video"` branches of `renderEsde()` in `NanoThemeEngine.cpp`.

This document records the fit model (the heart of "boxart must not be cropped unnecessarily") and where
each path lives, checked against `/work/emulationstation-de/es-core/src/components/ImageComponent.cpp`.

## The fit model (contain / fill / cover)

ES-DE `ImageComponent::applyTheme` chooses exactly one sizing mode from the theme, then `resize()` does
the math:

| theme markup | ES-DE call | result | crops? |
|---|---|---|---|
| `<maxSize>` or `<size>` with **one** axis 0 | `setMaxSize` | scale to **fit inside** the box, preserve aspect (letterbox) | never |
| `<size>` with **both** axes > 0 | `setResize` | **stretch** to exactly fill the box, aspect ignored | never |
| `<cropSize>` | `setCroppedSize` | scale to **fill** the box, clip the overflow | yes |

So an image is cropped **only** when the theme used `<cropSize>` (or, for the primary carousel/grid,
`imageFit=cover`). A plain cover with `<maxSize>` or a single-axis `<size>` is letterboxed and shown
whole. This is the invariant to preserve: never crop where ES-DE letterboxes.

Fit precedence when several are present: ES-DE `applyTheme` (`ImageComponent.cpp:530-556`) is an
`if / else if` chain that checks **`size` first, then `maxSize`, then `cropSize`** - so a `<size>`
wins over a `<cropSize>` on the same element (stretch, not crop). nano evaluates `cropSize` first in
its image paths, i.e. the reverse order. This deviation is inert in practice because no known theme
sets both `<size>` and `<cropSize>` on one element (they are contradictory), and every element that
uses `<cropSize>` uses it alone; if a theme ever set both, ES-DE would stretch where nano crops. Only
one of the three is normally present, so the effective result matches.

### cover crop position (`cropPos`)

For `cropSize`/cover, `cropPos` (default `0.5 0.5`) picks which slice survives. ES-DE
`coverFitCrop()` crops the overflowing axis and offsets it:
`cropSize = 1 - round(target)/round(fitted)`, `cropOffset = cropSize*(cropPos + 0.5) - cropSize`.
`cropPos.y = 0` keeps the **top** of a tall image, `1` the bottom, `0.5` centres. nano reproduces this
by offsetting the centred draw by `(0.5 - cropPos) * (fitted - box)` on the overflow axis and clipping
to the box (an inset-guarded scissor - a full-screen scissor is skipped because it suppresses the draw
on the DRM path).

## Anchoring

ES-DE anchors the **fitted** image (not the box) by the element `origin` against the fitted size:
`getTransform` translates by `-origin * mSize`, where `mSize` is the fitted size. nano matches this:
`dx = pos.x*mWidth - origin.x*fittedW` (and for cover, the box is anchored and the image is offset
within it by `cropPos`). A one-axis-0 `<size>` derives the zero axis from the image aspect so the fixed
axis is filled.

## nano's three image paths

The same fit model is implemented in three places; keep them in sync:

1. **static-art / cropSize path** (`t=="image"`, ~2007-2045) - a `<path>` image (per-system art,
   wallpapers). `esdeArtTex` loads it; `cropSize` cover-crops via native dims (`mEsdePngDims`).
2. **scraped-cover path** (`t=="image"`, ~2112-2175) - an `<imageType>` image (per-game media, resolved
   by `esdeGameMediaTex`). Honors `cropSize` (cover-crop + cropPos), both-axes `size` (stretch), else
   contain. The `<default>` fallback draws when the game has no media of that type.
3. **video-still path** (`t=="video"`, ~2472-2530) - `VideoComponent`'s static image while no video
   plays; already modelled cover for `fullBg || cropSize`. The still is rounded by `imageCornerRadius`
   (what ES-DE applies to `mStaticImage`, `VideoComponent.cpp:287`), falling back to `videoCornerRadius`
   so a tile the theme rounds only on its live video is not drawn hard-cornered; both clamp to
   `[0,0.5] * screenWidth` and route through `drawIconTexFx` like the `<image>` `cornerRadius`. Effective
   on a contain-fit still; a cover-cropped still is scissor-clipped to a square box, so the rounding is
   inert there (nano cannot round a cropped box corner, matching the overflow being clipped).

`rectOf` derives the box from `size` then `maxSize` (**not** `cropSize`), so a cropSize-only element has
a 0x0 box and the cropSize branch must take its box from `cropSize * screen` directly.

## Content resolution (scraped)

`imageType` is a comma list; nano resolves the first type that has a file for the game (or a
`gameselector`-picked game for system-view background art). Per-system `<path>` art resolves
`${system.theme}` / `${system.fullName}` / `${system.name}` via `esdeResolveSystemPath`.

`imageType=none` (`mImageTypeNone`) means the element shows **no static image at all** - not even nano's
own scraped-boxart fallback. `esdeGameMediaTex` returns 0 for the whole-string value `none`, so a
grid/carousel with `imageType=none` (X-Grid's `gamelistGrid`) renders its **name** text cells rather than
box art. Without this the `romBoxartTex` fallback (which correctly stands in for a missing *real*
imageType) painted those text cells with covers.

The `<default>` fallback (drawn when the game/system has no media of the element's `imageType`, or a
per-system `<path>` art file is missing) is loaded and then routed through the **same fit block** as the
resolved image, matching ES-DE `ImageComponent::setDefaultImage` -> `resize()`: a both-axis `<size>`
stretches it, a `<cropSize>` cover-crops it (honoring `cropPos`), a `<maxSize>`/single-axis contains it.
Because `esdeArtTex` returns the *contain-fit* size, drawing the default at that size was already correct
for `maxSize`, but a **`cropSize` default drew nothing at all** (a cropSize-only element has a 0x0
`rectOf` box, so `esdeArtTex(def, 0, 0)` returned empty) and a both-axis-`size` default contained instead
of stretching. Device-verified against the control on elementerial (`cover_list`, `cropSize 1 0.5` +
`_default.webp`, nes art removed): the fallback collage now cover-fills the top-half band identically to
real ES-DE (the art region diffs to black; only text AA and the clock differ), where before it was blank.

## Tint and effects (both elements)

- `color` -> `colorEnd` with `gradientType` (horizontal / vertical) - a tint gradient across the quad;
  `color` alone is a flat tint. A tiled 1px `color` image is ES-DE's idiom for a solid colour band.
- `tile` with a **real multi-pixel** `path` (not the 1px band): ES-DE repeats the image across the box at
  its native size (or `tileSize`) instead of scaling it. A `cropSize=1 1` tiled background (MinUI's
  colour-scheme backgrounds are a tiny gradient PNG, e.g. 4x4, tiled into a fine wash) is drawn with
  `GL_REPEAT` and the UV spanning `box / tileSize`. GLES2 forbids `GL_REPEAT` on a non-power-of-two
  texture, so a NPOT tile falls back to the cover-fit (the common tiled sources - 4x4, 8x8 - are POT).
- `saturation` (default 1) and `brightness` (default 0) - routed through the FX shader; `cornerRadius`
  (clamp 0..0.5 * screen width) rounds the quad, also via the FX shader.
- `interpolation` (`linear` / `nearest`) - the magnify filter (note: carousel/grid use
  `imageInterpolation`; see `CAROUSEL.md`). Minify is always linear, matching ES-DE.
- `rotation` about `rotationOrigin` (default centre).

## Verifying a change

Per `AB_TESTING.md`, install the theme both sides and diff the **cover region** specifically. A cover
must be whole (letterboxed) unless the theme used `cropSize`/`cover`. Re-verify across `ThemeAspectRatio`
and `ThemeFontSize` - the box is a normalized value times the screen size, so the fit is resolution and
aspect independent. Watch two confounds when A/B-ing a background: a `<video>`/scroll animation varies
per frame (mask it), and a theme may darken a full-screen background with an overlay, making a
stretch-vs-letterbox difference subtle - trust the cover-region MAE, not the eye.

Device-verified against the control (art-book-next gamelist boxart, Thin Ice Rescue) across `ThemeAspectRatio`
4:3, 16:9, 1:1 and the extreme wide **21:9**: at every aspect the layout fills the full panel (the aspect
include's normalized coordinates map to the whole screen exactly as ES-DE, content bbox ~1000x700 on both,
no letterbox) and the boxart stays whole and identically fit (cover-region MAE ~0.007-0.01, compression
noise only; the only per-frame difference is the auto-scrolling description). A wide `ThemeAspectRatio`
forced on a narrower panel does NOT letterbox in either engine - it fills, since coordinates are
screen-relative not aspect-relative.
