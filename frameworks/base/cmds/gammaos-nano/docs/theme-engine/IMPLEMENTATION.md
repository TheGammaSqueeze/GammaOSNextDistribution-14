# ES-DE theme engine internals

This documents the engine's code so a contributor can extend it. For the format and the
high-level design see [../THEME_ENGINE.md](../THEME_ENGINE.md); for how it fits the theme
system see [../THEME_SYSTEM.md](../THEME_SYSTEM.md).

## Files

| File | Role |
| --- | --- |
| `NanoEsdeTheme.h` / `.cpp` | GL-free parse model + parser. Turns a theme set into `view -> (type+name) -> element -> typed property`. Mirrors ES-DE `es-core/src/ThemeData`. Links `libtinyxml2`. |
| `NanoThemeEngine.cpp` | The renderer + navigation, as `NanoMenu::` methods (`renderEsde`, `renderEsdeSecondary`, `ensureEsdeTheme`, `esdeRebuildSysList`, `esdeNav`, `esdeSelect`, `esdeBack`). Draws with nano's GLES2 primitives; binds to the shared game model. |
| `NanoMenu.h` | Owns the engine state: `mEsdeTheme` (gate), `mEsdeDoc` (parsed theme), `mEsdeSetName`, `mEsdeSysSel`/`mEsdeGameSel`/`mEsdeInGamelist` (nav), `mEsdeSysList` (reused), `mEsdeTexCache` (raster), `mEsdeSvgCache` (rasterized SVG). |
| `nanosvg/` | Vendored `nanosvg` (zlib) SVG parser + rasterizer, built as `libnanosvg_nano`. `esdeRasterSvg` rasterizes theme logo/console SVGs to RGBA GL textures, cached. |

## Parse model (`nanoesde::Theme`)

- `Prop` — one typed value (`PropType`): normalized pair/rect, path (stored absolute),
  string, color (rgba 0..1 + packed), uint, float, bool.
- `Element` — `type` + `name` + `props` map, with typed accessors (`getF`, `getPair`,
  `getColor`, `getS`, `getPath`, `zIndex`).
- `View` — `elements` keyed by `type\x1fname`, plus `drawOrder` (zIndex-sorted).
- `Capabilities` — variants / colorSchemes / fontSizes / aspectRatios / languages, parsed
  from `capabilities.xml` (the validity gate).

### `Theme::load()` flow

1. `parseCapabilities()` — read `capabilities.xml`; abort if missing (the theme is invalid).
2. Resolve the selected variant / colorScheme / fontSize, and the aspect ratio
   (`automatic` picks the nearest declared ratio to the panel aspect).
3. Seed `${system.*}` variables, then load the system theme file (`<set>/<system>/theme.xml`
   if present, else `<set>/theme.xml`).
4. `walk()` runs four times: phase 0 once collects `<variables>` from every active context;
   phase 1 runs three ordered passes with `targetLayer` 0, 1, 2 (base, then variant, then
   aspectRatio). A `<view>` block is applied only when its nesting layer equals the pass's
   `targetLayer`, and all passes accumulate into the same `view -> (type+name)` element map,
   so precedence is **base < variant < aspectRatio** with last-wins per property (an
   element whose `<fontPath>` lives in the variant file and `<size>` in the aspect file
   merges into one element).
5. `finalize()` builds each view's `drawOrder` (zIndex ascending, ES-DE default z per type)
   and frees the XML documents (all values are copied out by then).

`walk()` follows `<include>` inline, honors the selected `variant`/`colorScheme`/
`fontSize`/`aspectRatio` (unselected branches are inactive), and applies
`${variable}` substitution to every property value. Unknown element types and unknown
properties are skipped, not fatal (real ES-DE themes only use known ones; being lenient
survives table gaps). The element/property type table is `elementPropertyMap()` and
mirrors `ThemeData::sElementMap`.

## Renderer (`NanoMenu::renderEsde`)

1. `ensureEsdeTheme()` — (re)load the set named by `persist.gammaos.nano.esde.themeset`
   (default `slate-es-de`) when the selection changes. Latches on failure so it does not
   retry every frame. Frees the texture cache on reload.
2. `esdeRebuildSysList()` — refill `mEsdeSysList` (enabled, non-empty systems) into a
   reused vector (no per-frame allocation).
3. Pick the view (`gamelist` if a system is entered, else `system`). If the theme is
   invalid or the view is missing, draw a readable notice instead of a blank panel.
4. Walk `view->drawOrder` and draw each element by type (see the matrix below). The
   primary element (carousel / textlist) is drawn last so it sits above secondary chrome,
   as ES-DE intends.

### Coordinate model

All geometry is normalized 0..1 of the panel. For an element:
`W = size.x * mWidth`, `H = size.y * mHeight`, `X = pos.x*mWidth - origin.x*W`,
`Y = pos.y*mHeight - origin.y*H`. Font size is a fraction of screen height (landscape):
the nano `drawText` scale is `fontSize * mHeight / FONT_CHAR_H`. Colors are ES-DE hex
(6 digits gets full alpha), multiplied by the element `opacity`.

### Data binding

- **system view**: items = `mEsdeSysList` -> `mXmbSystems[i].name`. The primary
  carousel/grid shows each system's `staticImage` art (resolved per system) or a text
  placeholder; a `gameselector` lets a system-view element pull one of that system's game
  media items.
- **gamelist view**: items = the selected system's games, each labelled by its ES-DE `<name>`
  metadata (falling back to nano's filename-derived `displayName`), exactly like the control;
  `curRom` = its ROM path. `image`/`video`/`grid` with `imageType` bind the game's scraped media via
  `esdeGameMediaTex` (ES-DE `downloaded_media/<system>/<type>/`, falling back to nano's own
  boxart); `text`/`datetime` with `metadata` bind `esdeMetaFor(curRom)` (which reads ES-DE's
  own `gamelist.xml` when present, else nano's scrape store) across the full metadata set
  in the matrix; `rating`/`gamelistinfo`/`badges` read the same source.
- Launch reuses the shared path: `esdeSelect()` sets `mXmbSystemIndex`/`mXmbGameIndex` and
  calls `launchXmbGame()`.

## Element support matrix (current)

Every element type in `elementPropertyMap()` has a render branch in `renderEsde()`. The
matrix below tracks per-element property coverage; a skipped property degrades layout,
never load or navigation.

| Element | Status | Notes |
| --- | --- | --- |
| image | Implemented | pos/size/origin, `color`/`colorEnd` gradient, `opacity`, `rotation`+`rotationOrigin`, `cornerRadius`, `brightness`/`saturation`. Raster (PNG/JPG/WEBP/GIF/HEIF via AImageDecoder, stb, libpng) and SVG (nanosvg) both cached. Fit modes: aspect-fit, `tile`, `cropSize`/`cropPos` (cover-crop), `imageFit`. `imageType` binds scraped game media; `default` is the fallback art. `flipHorizontal`/`flipVertical` are a follow-up. Full fit model: `IMAGE.md`. |
| text | Implemented | static `text`, `metadata`, `systemdata`; `fontSize`, `fontPath` (theme typeface), `color`, horizontal + vertical alignment, `letterCase`, `defaultValue`, `backgroundColor` plate. `container` = ES-DE auto-scroll: vertical crawl (a multi-line description) or `containerType=horizontal` single-line marquee (an overflowing metadata value left-anchors and scrolls left after `containerStartDelay`, honoring `containerScrollSpeed`/`containerScrollGap`). Baseline seated on ES-DE's `buildTextCache` metrics (single + wrapped). Metadata bound: name, description, developer, publisher, genre, players, rating (as text), releasedate, lastplayed, playtime, playcount, systemName/systemFullname, sourceSystemName/Fullname. |
| datetime / clock | Implemented | `format` via strftime, `displayRelative` (lastplayed "N days ago"), alignment/color. `clock` renders the wall clock, gated by `persist.gammaos.nano.esde.clock` (mirrors ES-DE `DisplayClock`, default off). When the prop is on and the theme declares no `<clock>`, a default clock is drawn top-left (0.018,0.016, small font) like ES-DE's `clock_default`. |
| textlist | Implemented (primary) | windowed list, selector bar, primary/selected/secondary colors, alignment; horizontal marquee scroll on the selected entry when its name overflows. A favourited game gets the ES-DE favourite indicator (Font Awesome star U+F005, or `* ` in `indicators=ascii`; `indicators=none` disables it) prepended, drawn through the FA fallback face. |
| carousel | Implemented (primary) | `horizontal`/`vertical`/`horizontalWheel`/`verticalWheel`; per-item `staticImage` art (resolved per system) or placeholder `text`; `itemScale`, `itemSize`, `maxItemCount`, the three offset kinds (`horizontalOffset`/`verticalOffset`, `selectedItemMargins`, `selectedItemOffset`), `imageInterpolation`, selection highlight. Background band uses `color`/`colorEnd` (`gradientType` direction) with the upstream default `0xFFFFFFD8` when the theme omits `color`, so carbon-style light bands survive (a theme wanting none sets `color=00000000`). Full model: `CAROUSEL.md`. |
| grid | Implemented (primary) | `itemSize`, `itemScale` + `scaleInwards`, `itemSpacing`, `imageFit` (contain default / fill / cover) + `imageCropPos`, `imageType`, `imageRelativeScale`, `imageCornerRadius`, `unfocusedItemOpacity`/`Dimming`/`Saturation`, coverless text cells (wrapped + centred), system-view logo cells. Full model: `GRID.md`. |
| rating | Implemented | on/off star glyphs from the game's rating value. |
| gamelistinfo | Implemented | game count + favorites line (favourites counted from nano's store or the ES-DE `<favorite>` metadata, like the `systemdata=gamecount` counter). |
| systemstatus | Implemented | battery / Wi-Fi / Bluetooth icons + battery % in the theme's slot. When the theme declares no `<systemstatus>`, a default cluster is drawn top-right (0.982,0.016, origin 1,0, height 0.035, textRelativeScale 0.9, white) exactly like ES-DE's `systemstatus_default`, so minimal themes still show the status bar. |
| helpsystem | Implemented | per-view button prompts, theme icons/colors. (ES-DE's fallback default help bar for a theme that declares none is a follow-up; every bundled/downloadable theme provides its own helpsystem.) |
| gameselector | Implemented | selects a game's media (by `selection`/`gameCount`) so system-view art can show a game's cover/screenshot. |
| video | Implemented (static stand-in) | a `<video>` with an `imageType` shows that scraped media as a still (nano has no live game video); a fixed-`path` background video plays through the HW decoder. The still follows `ImageComponent` fit: `size`/`maxSize` contain-fit it inside the box, `cropSize` cover-crops it, and a boxless element covers the screen. `pillarboxes` governs only a live video's black-bar frame, never the still, so `pillarboxes=false` does not crop a contained cover. |
| animation | Implemented (GIF) | animated GIF via the AImageDecoder frame API, advanced on the wall clock. Lottie (`.json`) is a follow-up. |
| badges | Implemented | every slot binds to the game's ES-DE metadata like `GamelistView`: favorite/completed/kidgame/broken from the `true` bool fields, controller/altemulator from their string fields, manual when a `manuals/<rom>` media file exists. Each draws the theme's `customBadgeIcon` or the bundled ES-DE default `badge_<slot>.svg`, tinted by `badgeIconColor`; the controller badge overlays the specific controller icon at `controllerSize`. Flexbox layout honours `itemsPerLine`/`lines`/`itemMargin` (with the `-1` mirror sentinel), `direction` and left/center/right `horizontalAlignment`. The default badge + controller SVGs install under `/data/system/nano_esde_assets/{badges,controllers}/` (alongside the help glyphs). `folder`/`collection` never activate (nano's ES-DE gamelist has no folder entries or collection editing). |

The system<->gamelist view transition (slide / fade / instant) is implemented per the theme's
resolved `<transitions>` profile (ES-DE `setThemeTransitions`); see
[VIEW_TRANSITIONS.md](VIEW_TRANSITIONS.md). Known follow-ups: Lottie animation and live game
video.

## Performance

- Parse once (on set change), never per frame.
- Texture cache (`mEsdeTexCache`, path -> GL texture) caches decodes, including misses (0),
  and is freed on reload. Game covers reuse the shared bounded async art path.
- `mEsdeSysList` is a reused vector; the per-frame path only walks the resolved element
  list and draws. GLES2 only; no new shaders.

## Gating and theme sets

- Enable: `persist.gammaos.nano.ndstheme = 3` (Settings -> Theme Settings -> Home Theme ->
  ES-DE). Off by default.
- Active set: `persist.gammaos.nano.esde.themeset` (default `slate-es-de`).
- Optional overrides: `persist.gammaos.nano.esde.variant` / `.colorscheme` / `.fontsize`.
- Install a set at `/data/system/nano_esde_themes/<name>/` (dev-pushable) or
  `/system/etc/nano_esde_themes/<name>/`; `/data` wins. A set is valid only if it has
  `capabilities.xml` at its root.

## Adding an element renderer

1. Confirm its properties are in `elementPropertyMap()` (add them if missing, typed).
2. In `renderEsde()`'s element loop, add a `else if (t == "yourtype")` branch: compute the
   rect with `rectOf`, read colors/opacity with `colorOf`, font with `fontPx`, then draw
   with the nano primitives (`drawQuad`, `drawRoundedRect`, `drawIconTex`, `drawText`).
3. If it is a primary (navigation) element, route it through the `primary` path so it draws
   last and reads the current selection.
4. Update this matrix and keep the per-frame path allocation-free.
