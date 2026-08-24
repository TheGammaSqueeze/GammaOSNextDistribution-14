# ES-DE carousel component

The carousel is ES-DE's most-used primary element: it is the horizontal system-icon strip on the
system view and, in many themes, the game strip on the gamelist view. nano implements it in
`NanoThemeEngine.cpp` inside `renderEsde()`; this document records the model it follows and where each
piece lives, so a future change can be checked against the ES-DE source
(`/work/emulationstation-de/es-core/src/components/primary/CarouselComponent.h`) rather than guessed.

Everything below is verified 1:1 against the control (`org.es_de.frontend`) at the same resolution: the
system and gamelist carousels of slate, carbon, art-book-next, PS5 Menu, XMB Menu and aura all match at
`MAE 0` (byte-identical) on the 1024x768 4:3 panel.

The **gamelist carousel of covers** (a horizontal boxart strip, the mandate's "carousel items" boxart
case) is verified on retrofix-revisited's `gamelist-carousel` variant: the selected cover is drawn large
and centred, the neighbours scaled down, and every cover is shown **whole** (contain-fit, not cropped),
matching the control cover-for-cover (aspect auto-selected to `16:10` on both). The only diff is the
faded per-game **video** background drawn behind the strip (a live video on the control, its still on
nano); mask it (diff the cover quads, not the full band) - the control's own frame-to-frame self-diff is
non-zero there, which is the tell that the residual MAE is that background animation, not the covers.

## Types and the scroll axis

The `<type>` child is one of four camelCase tokens (nano reads it verbatim via
`primary->getS("type", "horizontal")`, matching ES-DE's `CarouselType`):

| token            | axis       | nano treats as |
|------------------|------------|----------------|
| `horizontal`     | horizontal | flat strip, items slide along X |
| `vertical`       | vertical   | flat strip, items slide along Y |
| `horizontalWheel`| horizontal | see "Wheel geometry" |
| `verticalWheel`  | vertical   | see "Wheel geometry" |

`isVertical` is `type == "vertical" || type == "verticalWheel"`. The two help-prompt sites (which axis
icon to show: left/right vs up/down) test the same tokens. The tokens are camelCase with no underscore;
an earlier snake_case (`vertical_wheel`) test never matched a real theme and has been removed.

## Item layout

The visible items are the selected entry plus its neighbours. The geometry constants:

- `itemSize` (normalized pair) - the unscaled item box, relative to screen. Default `0.25 0.155`.
- `itemScale` (float, default `1.2`) - the selected item is drawn `itemScale`x; neighbours interpolate
  down to `1.0` by `1 - |distance|`, where `distance` is the signed offset from the cursor in item
  units. nano clamps the same way ES-DE does so the growth curve matches.
- `maxItemCount` (float, default `3`) - how many items span the carousel's main axis; it sets the
  per-item spacing so `maxItemCount` items fill the carousel `size`.
- `itemsBeforeCenter` / `itemsAfterCenter` (uint, default `8`/`8`) - used by the wheel types to decide
  how many items to draw on each side of the cursor.

The selected item sits at the carousel centre; each other item is offset by `distance * spacing` along
the scroll axis. nano computes this in the `drawItem` lambda (`slotCx`/`slotCy`).

## Offsets (three independent kinds)

These are easy to confuse; ES-DE applies all three and so does nano:

1. `horizontalOffset` / `verticalOffset` (float, clamped [-1,1]) - shift the **whole** carousel by a
   fraction of its size. nano: `cx += w * clamp(horizontalOffset)`, `cyc += h * clamp(verticalOffset)`
   (`CarouselComponent.h` applies `mSize * mHorizontalOffset` to `xOff`/`yOff`).
2. `selectedItemMargins` (normalized pair) - open a gap **around** the selected item along the scroll
   axis: items before it shift by `-margins.x`, items after by `+margins.y`, scaled by screen height
   (vertical) or width (horizontal), and by `|distance|` for the near items during a slide.
3. `selectedItemOffset` (normalized pair) - nudge the selected item (and its near neighbours) **off**
   the rail as it settles. Both components scale by one screen dimension (width for a horizontal
   carousel, height for a vertical one) and fall off with `1 - |distance|`, so only `|distance| < 1`
   items move. This is what raises the focused controller tile in the PS5 menu.

All three are `[-1,1]`-clamped and, like ES-DE, only affect items within one step of the cursor for the
margin/offset pair.

## Item appearance

- `unfocusedItemOpacity` / `unfocusedItemDimming` (float, default `0.5`/`1.0`) - non-selected items are
  faded/darkened; the value interpolates back to full for the near neighbours by `1 - |distance|`.
- `imageSaturation` / `unfocusedItemSaturation` (float) - per-item saturation; the unfocused value (when
  present) interpolates to `imageSaturation` at the cursor. Applied via nano's FX shader path.
- `imageInterpolation` (`linear`/`nearest`) - the texture magnify filter for the item art. Note the
  **name**: the carousel and grid use `imageInterpolation`, whereas image/video/rating use
  `interpolation`. ES-DE (and nano) always minify with linear and only switch the magnify filter, so
  this is visible only when an item image is upscaled. See `esdeImageMagFilter`.
- `imageCornerRadius` (float) - rounds the item art corners (fraction of screen width).

## Item content

Each item shows, in priority order:
1. the per-game media for a gamelist carousel (`esdeGameMediaTex`, keyed by `imageType`), else
2. the per-system `staticImage` art resolved through `esdeResolveSystemPath` (`${system.theme}` ->
   `romDir`, etc.), else
3. the carousel `defaultImage` (`primary->getPath("defaultImage")`), else
4. the item `text` (the system/game name), styled by `fontPath`/`fontSize`/`letterCase`/`textColor`/
   `textSelectedColor` and the `textRelativeScale`.

`imageType` is a comma list (`marquee`, `cover`, `screenshot`, ...); nano resolves the first that has a
matching file. The text fallback is what draws the "PSP"/"NINTENDO 64" style labels when a system has no
logo art.

`letterCase` is applied to that label through the shared `esdeLetterCase` helper (the same
uppercase/lowercase/capitalize transform used by the grid, textlist and `<text>` paths). ES-DE cases the
entry text when it builds the list, not at draw time - `SystemView`/`GamelistBase` run
`mPrimary->getLetterCase()` over each system/game name - so a themed `uppercase` carousel that falls back
to text renders `NINTENDO ENTERTAINMENT SYSTEM`, not the raw `Nintendo Entertainment System`. Unset (the
common case) is a no-op, so an image carousel that never shows text is unaffected.

## Background band

The carousel paints a full-width band behind the items using `color` -> `colorEnd` with the
`gradientType` direction. ES-DE's default `color` is `0xFFFFFFD8` (a light translucent band), so nano
keeps that default when a theme omits `color` - this is why carbon's light band survives. A theme that
wants no band sets `color=00000000`.

## Wheel geometry (deferred)

`horizontalWheel` / `verticalWheel` fan the items along an arc instead of a straight line. In ES-DE
(`CarouselComponent.h:1123-1180`) each item is translated to a pivot at
`mItemRotationOrigin * mItemSize` (default origin `{-3.0, 0.5}`, i.e. three item-widths to the side),
rotated by `mItemRotation * distance` degrees (default `7.5`), then translated back; `horizontalWheel`
additionally spins each item `-90deg`, and `itemAxisHorizontal` keeps the items upright while still
following the arc.

nano currently renders wheels as straight carousels (`isVertical` picks the Y axis; there is no
per-item rotation). On the reference themes this is **not** a visible divergence, which is why it is
deferred - the arc's horizontal displacement is negligible for their configs:
- aura's `fullscreen-*` variants use `horizontalWheel` but show a single large centred item (neighbours
  are rotated off-screen), so wheel-vs-flat is moot - `MAE 0`.
- canvas's `carousel-carousel` gamelist uses `verticalWheel` for the marquee strip; the static frame
  matches (the only per-frame difference is the independently auto-scrolling description container).
- CodyWheel's system view is a `verticalWheel` of system logos with a real arc (`itemRotation -10`,
  `itemRotationOrigin 4.5 0.5`); nano matches the control at `MAE 0` (logo positions identical). The
  large rotation origin (far pivot = large radius) makes the arc almost flat, the selected item has zero
  rotation by definition, and the neighbours are dimmed, so straight coincides with the arc.

If a future theme uses a **tight** wheel (small `itemRotationOrigin`, large `itemRotation`) with several
prominent items on-screen, implement the arc by giving nano's `drawItem` a per-item rotation about the
pivot above; the rest of the item pipeline (scale, opacity, content) is unchanged.

## Reflections (deferred)

`reflections` draws a mirrored, faded copy of each carousel item below it (`reflectionsOpacity`,
`reflectionsFalloff` gradient). nano does not implement it (the three properties are not parsed). Only
aura uses it, on its boxart gamelist carousel at `reflectionsOpacity 0.2`; nano matches the control at
`MAE 0` on snes and nds - the reflection is clipped by the carousel box / too faint at 0.2 to register,
so it does not visibly manifest. Deferred for the same reason as the wheel: a real feature with no
visible divergence on any reference theme. Implement (mirror the item quad below with the falloff alpha)
only if a theme surfaces a visibly-diverging reflection.

## Where it lives in nano

- parse: `NanoEsdeTheme.cpp` `elementPropertyMap()` `"carousel"` entry registers every property above.
- render: `NanoThemeEngine.cpp` `renderEsde()` - the carousel branch builds `drawItem(iRaw)` and calls it
  for each visible index around the cursor; `esdeImageMagFilter`, `esdeResolveSystemPath` and
  `esdeGameMediaTex` are the shared helpers.
- selection/scroll animation and the system<->gamelist change are documented in `VIEW_TRANSITIONS.md`.

## Verifying a change

Follow `AB_TESTING.md`: install the theme on both nano and the control, put both on the **same** system
(read the on-screen title - carousel up/down and left/right each move the cursor, so a paired nav does
not reliably net to zero), capture, and diff the carousel region. Use
`persist.gammaos.nano.esde.gotosys=<system>` to boot nano straight into a gamelist carousel. A settled
static frame should reach `MAE 0`; a non-zero result that **varies** between recaptures is an
animation-phase mismatch (item scroll or an auto-scrolling text container), not a layout bug - capture
within the container's `containerStartDelay` window to compare it statically.
