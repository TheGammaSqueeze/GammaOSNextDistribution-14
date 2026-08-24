# ES-DE grid component

The grid is ES-DE's other primary element (alongside the carousel and textlist): a multi-column,
row-scrolling matrix of cells, used for cover walls on the gamelist and icon walls on the system view.
nano implements it in `NanoThemeEngine.cpp` inside `renderEsde()` (the `isGrid` branch of `drawPrimary`).
This document records the model and where each piece lives, checked against the ES-DE source
(`/work/emulationstation-de/es-core/src/components/primary/GridComponent.h` and the shared fit math in
`ImageComponent.cpp`).

Verified 1:1 against the control on art-book-next, catppuccin and shinretro-revisited grids, and across
the 4:3, 16:9, 1:1 and 21:9 aspect ratios (the layout changes per aspect and nano tracks the control at
`MAE 0`). shinretro-revisited specifically exercises `imageFit=cover` (crop-to-fill) cells: the covers
are cover-cropped identically to the control at 4:3 (cell 80x101) and at 1:1 (the grid reflows to a
single row, selected cell 110x109), with only cover-texture antialiasing in the diff, no crop-slice or
geometry divergence.

## Cell layout

- `itemSize` (normalized pair, default `0.15 x` of screen) - the unscaled cell box. nano computes
  `itemW = itemSize.x * mWidth`, `itemH = itemSize.y * mHeight`, so the grid is resolution/aspect
  adaptive (a normalized value times the screen size).
- `itemScale` (float, default `1.05`) - the selected cell is drawn slightly larger.
- `itemSpacing` (normalized pair, default `0 0`) - gap between cells; nano derives the column count and
  the row pitch from `size`, `itemSize` and `itemSpacing`.
- `scaleInwards` (bool) - whether the selected cell grows inward at the grid edges instead of spilling
  out of the band.
- `fractionalRows` (bool) - allow a partial row at the bottom; otherwise the visible height snaps to a
  whole number of rows.

The grid keeps a row window: it draws only the rows around the cursor and scrolls the window as the
selection moves. The whole grid is clipped to its band (`scissorLogicalRect(x, y, w, dimY)` at the top
of the branch), so partial rows at the top/bottom edge are cut cleanly.

## Cell image fit (contain / fill / cover)

Each cover cell fits its image with the same three modes as the standalone image (ES-DE
`GridComponent::applyTheme` maps them to `setMaxSize` / `setResize` / `setCroppedSize`):

- `imageFit` **contain** (default) - scale to fit inside `itemSize * imageRelativeScale`, letterboxed,
  **never cropped**.
- `imageFit` **fill** - stretch to the cell box (aspect ignored).
- `imageFit` **cover** - scale to fill the box and clip the overflow, honoring `imageCropPos`.
- `imageRelativeScale` (float, default `1.0`) - shrinks the image inside the cell (leaving a margin for
  the background plate / selector).
- `imageCropPos` (normalized pair, default `0.5 0.5`) - for cover, which slice survives the crop:
  `cropPos.y = 0` keeps the top of a tall cover, `1` the bottom, `0.5` centres (ES-DE `coverFitCrop`
  `mCropOffset`). nano offsets the centred draw by `(0.5 - cropPos) * (fitted - box)` on the overflowing
  axis. **This must be honored** - a theme like basic-es-de sets `imageCropPos 0.5 0.95` to keep the
  bottom of a screenshot, and centre-cropping shows the wrong slice.

Only `cover` (and an explicit `cropSize`) ever clips pixels; `contain` and `fill` never do. When a cover
cell clips, it must **restore the grid's outer band clip afterwards**, not blanket-disable the scissor,
or every later cell that frame bleeds outside the grid band.

## Cell decorations

- `imageColor` / `imageColorEnd` / `imageSelectedColor` - per-cell tint (and a gradient / a distinct
  selected tint); `imageBrightness` / `imageSaturation` route through the FX shader.
- `imageCornerRadius` - rounds the cover corners.
- background plate: `backgroundImage` / `backgroundColor` (+ `backgroundColorEnd`) /
  `backgroundCornerRadius` / `backgroundRelativeScale` - a plate drawn behind the cover.
- selector: `selectorImage` / `selectorColor` / `selectorRelativeScale` / `selectorCornerRadius` /
  `selectorLayer` - the focus highlight, drawn under or over the cover per `selectorLayer`.
- coverless cells: a game (or system) with no media of the cell `imageType` draws the `defaultImage`,
  else its `text` (name), styled by `text*` / `fontPath` / `fontSize` / `letterCase`. A **system** cell
  uses the system's ES-DE **full name** (`getFullName`, e.g. "Sony PlayStation"), not the short id, the
  same as the carousel/textlist. The label sits in a box inset by `textRelativeScale` and is
  `ALIGN_CENTER`; with `textHorizontalScrolling` ES-DE marquee-scrolls a label that overflows that box.
  nano has no per-cell scroll, so it renders the marquee's **start frame**: an overflowing scrolling
  label left-anchors at the box (its beginning shows) rather than centring (which would show the clipped
  middle), and either way is clipped to the box so it cannot bleed into the neighbour. This is what makes
  a system-name grid (X-Grid) match the control - short ids ("psx") became full names and the long ones
  clip from the left exactly like ES-DE. A coverless **game** cell behaves the same: with
  `textHorizontalScrolling` it left-anchors + marquee-clips a long name (X-Grid's `gamelistGrid`, an
  `imageType=none` name grid, showed "Spacegulls The Odi..." like the control instead of wrapping
  mid-word), and **without** the flag it wraps to multiple lines centred in the box (ES-DE wraps a
  non-scrolling label).

## Content resolution

Same priority as the carousel: per-game media (`esdeGameMediaTex`, keyed by `imageType`) on a gamelist
grid, or the per-system `staticImage` art on a system grid, else the `defaultImage`, else the `text`
label.

## Where it lives in nano

- parse: `NanoEsdeTheme.cpp` `elementPropertyMap()` `"grid"` entry (image cell, background plate,
  selector, text, `imageFit`, `imageCropPos`, `imageInterpolation`).
- render: `NanoThemeEngine.cpp` `drawPrimary` `isGrid` branch - the outer band clip, the row window, and
  `drawCell` for each cell.
- the image fit / crop / clip semantics are shared in spirit with the carousel (`CAROUSEL.md`) and the
  standalone image element; keep the three in sync when changing the fit math.

## Verifying a change

Per `AB_TESTING.md`: install the theme on both nano and the control, boot both into the same gamelist
(`persist.gammaos.nano.esde.gotosys` / `StartupSystem`+`StartupView`), and diff. Diff the **cover
region** specifically for crop correctness - a cover must be shown whole (letterboxed) unless the theme
asked for `cover`/`cropSize`. Re-run under several `ThemeAspectRatio` values (the grid geometry comes
from a per-aspect layout file, so 4:3, 16:9, 1:1 and 21:9 each exercise a different cell size); nano
selects the same aspect file as the control, so a settled frame should reach `MAE 0` at every aspect.
