# ES-DE theme engine

The theme engine is a new, self-contained home theme that renders **ES-DE
(EmulationStation Desktop Edition) themes**. It parses a theme set's `theme.xml`
(and its includes), builds an in-memory element model, and draws the ES-DE `system`
and `gamelist` views with nano's existing GLES2 primitives, bound to nano's game
systems and scraped art.

It is the fourth home theme (`persist.gammaos.nano.ndstheme = 3`), **additive and off by
default**, so it cannot regress the XMB/DSi/Minima built-ins (see THEME_SYSTEM.md). The
goal is drop-in compatibility with real community ES-DE theme sets, scoped to a subset
that renders them acceptably on an A133 / 1 GB / GLES2 device.

Upstream reference: `git@gitlab.com:es-de/emulationstation-de.git`
(`es-core/src/ThemeData.{h,cpp}` and `THEMES.md`). This engine mirrors that parser's
data model and validation so themes authored for ES-DE load unmodified.

## Why ES-DE compatible

ES-DE has a large ecosystem of well-designed, freely available theme sets. Supporting
its format means GammaOS Nano gains a themeable, community-extensible front-end for
game browsing without inventing a new theme language, and users can install themes they
already know.

## ES-DE theme format (what the engine implements)

An ES-DE theme set is a directory (`.../themes/<name>/`) containing:

- **`capabilities.xml`** at the root. **Mandatory** and the validity gate: without it the
  set is ignored. It declares the `variant`, `colorScheme`, `fontSize`, `aspectRatio`,
  `language`, and `transitions` options the theme offers.
- **`theme.xml`** at the root (the default), and optionally a per-system `theme.xml` under
  a system subdirectory named by the systems config `theme` tag.
- Included fragments (colors, languages, navigation sounds, aspect-ratio layouts) pulled
  via `<include>`, and font/image assets under `core/` or `assets/`.

`theme.xml` structure:

```
<theme>
  <include>...</include>                 relative-path inline include
  <variables> ... </variables>            a global ${name} dictionary
  <aspectRatio name="16:9"> ... </aspectRatio>
  <variant name="all">
    <view name="system, gamelist">        comma/space list places elements in each view
      <image name="logo"> ... </image>    element: type = tag, name = mandatory key
      <text name="info"> ... </text>
      ...
    </view>
  </variant>
</theme>
```

Model (mirrors ES-DE `ThemeData`):

- A loaded theme is `view -> (type+name key) -> element -> property -> typed value`.
  Same `type`+`name` merges with **last-wins**; different types/views never collide.
- **Views**: `system`, `gamelist`, plus a pseudo `all` (used only for `sound`).
- **Property types**: `NORMALIZED_PAIR`, `NORMALIZED_RECT`, `PATH`, `STRING`, `COLOR`
  (hex, 6 digits gets full alpha appended -> 8), `UNSIGNED_INTEGER`, `FLOAT`, `BOOLEAN`.
- **Coordinates are normalized 0..1** over the screen; `origin` is the anchor a `pos`
  refers to. `fontSize`/height/corner-radius normalize to screen **height** in landscape
  and **width** in portrait, recomputed on every resize.
- **zIndex** default order: image/video 30, animation/badges 35, text/datetime 40,
  gamelistinfo/rating 45, primaries (carousel/grid/textlist) 50; helpsystem, systemstatus,
  and clock always draw on top.
- **Layering**: base, then selected `variant`, then `aspectRatio` (union, last-wins);
  `colorScheme`/`fontSize`/`language` contribute only variables. Triggers `noVideos`/`noMedia`
  swap the variant at runtime when media is absent.
- **Aspect ratio selection** (`NanoEsdeTheme::load`, mirrors ES-DE `ThemeData::setThemeData`):
  the `ThemeAspectRatio` setting is used verbatim if the theme declares it, else the theme's first
  declared ratio. `automatic` resolves to the declared ratio nearest the real screen aspect
  (`sAspectRatioMap` float per identifier), but seeded from **16:9**: the running best starts at
  16:9's distance from the screen and a declared ratio replaces it only when strictly closer. So
  when every declared ratio is farther from the screen than 16:9 is, the selection stays `16:9` even
  if the theme never declares it, in which case no `<aspectRatio>` block matches and the base layout
  is used. Seeding from `+inf` instead (picking the nearest declared ratio unconditionally) would
  diverge from ES-DE on, e.g., a 16:9 panel with a theme that declares 16:10 but not 16:9. The
  selected ratio is in the `esde: loaded '<set>' ... aspect='<r>'` log line.
- **Hard errors** (reject the theme, like ES-DE): unknown element/property, a
  `formatVersion`/legacy-inherit tag, an empty property value (except the skip sentinel),
  a missing `capabilities.xml`.

## Binding to nano's data

ES-DE views map directly onto nano's shared model (see ARCHITECTURE.md, THEME_SYSTEM.md):

- **`system` view** = nano's game systems. Source: `mXmbSystems` (id, name, short name,
  rom dir, icon, ROM count) or the `PS3_SYSTEM` items in the game column. The primary
  element (carousel/textlist) scrolls the systems; `staticImage`/`imageType=logo` binds
  the system logo.
- **`gamelist` view** = the ROMs of the selected system. Source: `buildRomSubmenu(sysIdx)`
  -> `PS3_ROM` rows. The primary element lists the games; secondary elements bind per-game
  media and metadata via `scrapeEntryFor(romPath)` and `romBoxartTex(romPath)`.
- **Metadata bindings** (`text`/`datetime`/`rating` `metadata=...`): name, description,
  developer, publisher, genre, players, rating, releasedate, lastplayed, playtime, playcount,
  systemName, systemFullname. These resolve
  through `esdeMetaFor(romPath)`, which prefers the game's entry in ES-DE's own
  `gamelists/<system>/gamelist.xml` (parsed once per system by `esdeEnsureGamelistLoaded`,
  matched to nano's scanned roms by basename) so the engine shows the identical text real
  ES-DE does, and falls back to nano's `scrapeEntryFor` scrape store when no ES-DE gamelist
  is present. The ISO release date is sliced to `YYYY-MM-DD` without `mktime`/`localtime`
  so it is never timezone-shifted. Unset string fields (genre/developer/publisher/players)
  render ES-DE's `"unknown"` default. **lastplayed** (a `datetime`, auto-`displayRelative`) reads
  "never" for an unplayed game or the relative age "N days/hours/minutes/seconds ago" for one played
  in ES-DE (parsing the ISO time as UTC so the age is timezone-clean, matching `DateTimeComponent`);
  **playtime** (a `text`) formats the second count exactly like ES-DE's `getPlayTimeString`
  ("unknown" / "N minutes" / "N.t hours"). Both read the same gamelist.xml the control does, so a
  played game shows the identical value on both. A **`text metadata="rating"`** shows the 0..5 star
  count (`RatingComponent::getRatingValue`, e.g. 0.8 -> "4"); **playcount** the raw launch tally
  (default 0); **systemName** / **systemFullname** the focused system's short / full name (identical
  to `sourceSystem*` outside a collection view, which nano does not model). Verified pixel-identical
  to the control on adroit's List variant.
- **Media bindings** (`image`/`video` `imageType=...`): cover/marquee/screenshot/
  titlescreen/fanart resolve to ES-DE's shared `downloaded_media/<system>/<type>/` tree via
  `esdeGameMediaPath`, falling back to nano's own scraped cover; missing art uses the element
  `default`. A `<gameselector>`-bound background binds to a picked game from the focused
  system (preferring one that actually has the requested media).

Navigation reuses the shared verbs: change system = `ps3XmbLeft/Right`, scroll games =
`ps3XmbUp/Down`, enter/launch = `ps3XmbSelect`, back = `ps3XmbBack`. Nano's Settings and
media categories are reached through the existing Quick Menu / shared modals, not through
ES-DE views (ES-DE has no concept of them). This keeps the engine a pure game-front-end
skin over the shared model, with zero model changes.

## Element / construct support matrix (MVP)

Scoped for least cost and most real-theme compatibility on GLES2/A133. Validated against
the default `slate-es-de` and `linear-es-de` sets, which ship textlist variants.

**Implement first**

- Parsing: capabilities gate; variants, colorSchemes, fontSizes, aspectRatios, languages,
  transitions; the view->(type+name) map with all property types, key-merge last-wins,
  fixed parse order recursing includes, `${variable}` substitution, path resolution
  relative to the current file; `noVideos`/`noMedia` trigger override; `automatic` aspect
  nearest-match; normalized coordinate engine with resize recompute and zIndex sort.
- Elements: `image` (raster + SVG, cached to a texture), `text`, `textlist`
  (primary nav), `carousel` (horizontal + vertical, wheel variants), `grid` (primary nav),
  `datetime`, `rating`, `gamelistinfo`, `helpsystem`, `clock`, `systemstatus`, `badges`
  (favorite slot), `animation` (GIF), `gameselector` (non-visual binding).

**Stub / degrade gracefully**

- `video` -> a game-media `<video>` (one with an `imageType`) renders the game's scraped
  media (its stand-in), both as the static gamelist image and as the item media in a
  gamelist carousel/grid. A fixed-file `<video>` (a `<path>` and no `imageType`, e.g. Adroit's
  Animated `backgroundvideo` -> an mp4) is **played** through the hardware H.264 decoder as a
  looping theme background. The `noMedia`/`noVideos` variant triggers are applied per system
  so a media-less system falls back to the theme's no-media variant (matching `ViewController`).
- Theme art decodes through one universal path: AImageDecoder (PNG/JPG/WEBP/GIF/HEIF/AVIF),
  then stb_image, then libpng, with an integer downscale to the GL texture cap (Adroit's
  Animated jpg poster is 2560x1440); SVG rasterizes via nanosvg. All cached, including misses.
- `animation` -> **GIF is rendered** (all frames decoded via the AImageDecoder frame API,
  advanced on the wall clock, drawn like an image). Lottie (`.json`) is a follow-up.
- `grid` primary -> implemented (GridComponent: windowed row scroll, focus scale/dim, cover art).

**Skip initially**

- Wheel carousels, reflections, gradients, blur, rounded corners, saturation/brightness
  shaders; use instant transitions and English labels only.

Skips degrade layout, not correctness: a theme still loads and navigates; unsupported
visual flourishes are simply not drawn. The matrix is tracked per element in the engine
docs and expands over time.

## Implemented rendering (device-verified against real ES-DE)

Verified by difference-image comparison on the TrimUI Brick (4:3, 1024x768) against the same
panel running upstream ES-DE, across the Art Book Next, Linear, Canvas, Aura, Atari 50 Menu,
Analogue OS Menu and Catppuccin theme sets, and across the list, metadata, grid and grid-system
variants, the dark and light colour schemes, and the medium font size. The system and gamelist
views match to font-antialiasing level; the only expected differences are game-preview videos
(ES-DE plays the `.mp4`, the engine shows the static `imageType` fallback) and the momentary
scroll offset of an auto-scrolling description. (A static ES-DE view stops redrawing when idle, so
it is captured with `DisplayClock` on to keep it live.)

- **Draw order is true zIndex.** The primary nav element (carousel/textlist/grid) is drawn
  inline at its own zIndex, not last, so higher-zIndex chrome (logo, clock, systemstatus,
  helpsystem) layers on top exactly as ES-DE composits.
- **System carousel** matches `CarouselComponent`: items at fixed `itemSpacing` slots, a
  floating camera offset that eases between cursors over 400ms with the `1-(1-t)^2` curve and
  the shortest wrapped path, and a **wrapping index** so the strip loops with no black edges.
  Each system draws its own art (`staticImage` resolved per system, PNG or SVG, contain-fit).
- **Per-system logos** via `${logoSource}/${system.theme}.svg` (nanosvg), `maxSize`-fit.
- **helpsystem** builds the view's full prompt set the way `SystemView`/`GamelistView::
  getHelpPrompts` (+ `ViewController`'s global `start=menu`) do under the reference settings
  (QuickSystemSelect=leftrightshoulders, FavoritesAddButton on, RandomEntryButton=games,
  ScreensaverControls off): the system view emits choose/select/menu, the gamelist emits
  system/select/back/view-media/options/random/favorites/menu. The **`<entries>`** allow-list
  on the helpsystem element then filters that set to the icons the theme lists (ABN trims the
  gamelist bar to `back,start,a` = OPTIONS/MENU/SELECT); `all`/absent keeps every prompt, as
  `HelpComponent::updateGrid` does. The **choose** icon tracks the system primary (vertical
  carousel/textlist -> up/down, horizontal -> left/right, grid -> all four); the **system**
  icon mirrors `mLeftRightAvailable` + `getQuickSystemSelectLeftButton` (vertical/textlist
  gamelist -> `left/right` dpad, horizontal-carousel/grid -> `lr` shoulders). Prompts are
  deduped by icon and sorted by ES-DE's fixed priority (as `Window::setHelpPrompts` does),
  then laid out icon+label centered on the element with a rounded plate. Icons are ES-DE's
  built-in button graphics tinted by `iconColor`, unless the theme overrides a button with
  `<customButtonIcon button="button_a_XBOX|dpad_leftright|...">` (Analogue OS Menu ships a full
  minimalist set), which wins per the active (XBOX) controller key. Labels and icons are sized as
  `HelpComponent` does: the label font scales by `entryRelativeScale` (Art Book Next uses 0.7), and
  each icon is sized to the help font's letter (cap) height times 1.25, not the raw em - so the bar
  is not oversized when a theme shrinks its help.
- **Default typeface (Akrobat-SemiBold).** ES-DE renders any element that sets no `<fontPath>` in
  its bundled default typeface. That default is `getDefaultPath()` = `FONT_PATH_REGULAR`, which
  ES-DE defines as **Akrobat-SemiBold** (not Akrobat-Regular, which is `FONT_PATH_LIGHT`). Every
  default `TextComponent` / `HelpComponent` starts on `Font::get(..., getDefaultPath())`, so the
  engine ships Akrobat-SemiBold and uses it as the default face for no-fontPath ES-DE text (help
  prompts, the system-view game counter, unstyled labels) to match ES-DE's weight and width.
  Akrobat-Regular is still shipped as a fallback. XMB / DSi / Minima text keeps nano's own UI font.
  Note: an older ES-DE Android build (r50, Nov 2025) was observed to render no-fontPath elements in
  the theme's own font instead of Akrobat; that behaviour is not present in the mainline ES-DE
  source of any era (the game-count component is always a default `TextComponent`), so the engine
  follows the source and uses Akrobat-SemiBold.
- **systemstatus** draws wifi + battery (icon by charge level + percent) from the theme's own
  `customIcon` SVGs, right-anchored, with the status plate.
- **clock / datetime** with the rounded background plate; the current time (clock) or the scraped
  release date re-emitted through the element's strftime `<format>` (datetime). An unsized element's
  `<origin>` is applied against its measured glyph box, so `origin 0 0.5` centres the text vertically
  on its pos (Catppuccin's clock) instead of hanging below. Gated behind `esde.clock` (default off,
  matching ES-DE's `DisplayClock`); `scope=none` hides it per view (ABN gamelist).
- **animation** (animated GIF, e.g. Canvas' `background-animation`): every frame is decoded once
  through `AImageDecoder`'s frame API into its own texture (downscaled to a 512px cap to bound VRAM
  on A133-class parts, sequential decode into one buffer so GIF disposal/blend composites for us),
  with each frame's declared duration; the render branch advances by wall clock (`uptimeMillis`) and
  keeps the loop live via `mDisplayDirty` while more than one frame exists. Placed like an `<image>`:
  a `<size>` with one 0 axis derives the other from the frame aspect (Canvas' `size 0 1` = full-height
  centred background), both axes stretch, `<maxSize>` contain-fits, `<cropSize>` cover-fills; anchored
  by `origin`, faded by `opacity`. Lottie (`.json`) is not supported and draws nothing.
- **Gamelist**: a textlist with the `selectedBackgroundColor` rounded row highlight AND the
  `selectorColor` marker drawn at its themed `selectorWidth`/`selectorHorizontalOffset` (a full
  row by default, or a thin left cursor when the theme sets a small width, e.g. Linear's 0.003).
  Each entry's text and its selector/plate are anchored the way `TextListComponent` does: centered
  in a `fontSize*1.5` box at the row TOP (not the full `fontSize*lineSpacing` pitch), so a theme
  with a larger row `lineSpacing` (Art Book Next's 1.944) does not sink the row. The **selected**
  entry marquees when its text overflows the element width, matching `TextListComponent::update`:
  after a delay it scrolls left at a font-relative speed (the summed advance of the 26 capitals
  times `0.247`) with a trailing gap and a second copy so the wrap is seamless; `textHorizontalScroll`
  `Speed`/`Delay`/`Gap` (defaults `1.0`/`3s`/`1.5`) tune it, `textHorizontalScrolling=false` disables
  it. Also: the
  `<video>` element rendered as the static cover art; `rating` stars from the theme's
  filled/unfilled SVGs, or ES-DE's built-in default star graphics (solid + outline, tinted) when
  the theme omits the paths; a word-wrapped, height-clipped synopsis that seats its first line at
  ES-DE's `(cap-height + em*lineSpacing)/2` baseline (a real top margin, not stacked from the box
  top) and auto-scrolls with `ScrollableContainer` timing (4.5s hold, crawl, 7s reset); and
  release-date / metadata bound to the ES-DE gamelist entry.
- **gamelistinfo** (Linear, Slate, Modern): the persistent count line ES-DE's `GamelistView`
  populates, showing the system's game count and favorite count each preceded by a FontAwesome glyph
  (controller `U+F11B`, star `U+F005`). Rendered through the shared text path with the element's own
  `fontPath`/`fontSize`/`color`/alignment. (nano does not model per-folder entry or search filters
  here, so the folder-char and filtered-count string variants are not emitted.)
- **FontAwesome fallback**: `fontawesome-webfont.ttf` is loaded as a fallback face so ES-DE's
  private-use-area icon codepoints (the gamelistinfo counters, folder/filter markers, and similar)
  resolve the way ES-DE's merged font stack does. It carries no ASCII, so it never shadows normal
  text; `ensureGlyph` reaches it only after the Latin/CJK/emoji faces miss a codepoint.
- **Carousel/primary clipping**: the carousel is scissored to its own box (ES-DE's
  `pushClipRect`) so it shows exactly `maxItemCount` items instead of spilling past its band; the
  clip is skipped for a full-screen box to avoid suppressing the draw on the DRM path.
- **Theme fonts**: every `fontPath` is loaded on demand into a shared FreeType face array (TTF and
  OTF); the cap is sized so a theme's full font set (several faces) loads without later fonts
  silently falling back to the default face.
- **Tiled spacer panels**: a 1px spacer tinted with a color renders as a solid color fill
  (ES-DE's idiom for backgrounds and bands), so the gamelist background is a correct field
  plus textlist column rather than a centered square.
- **`customIcon` / `customButtonIcon` / `customBadgeIcon`** captured by the parser as
  attribute-keyed path properties.
- **Grid** (`GridComponent`, see `NanoThemeEngine.cpp` drawPrimary grid branch), used for both the
  gamelist AND the system view (Catppuccin's grid-of-logos): columns from `size/itemSize/itemSpacing`
  with the margin rule, `fractionalRows`, a vertical row-window scroll eased toward the cursor's row,
  and the focus scale + opacity + dimming + saturation ease on the selected cell (`scaleInwards` keeps
  the scaled cell inside the grid at edges). A gamelist cell binds its cover via `imageType`, a system
  cell its `staticImage` logo, both falling back to `defaultImage` then a text plate; rows are windowed
  so only visible art loads. The focused cell's selector is a `selectorImage` tinted by `selectorColor`
  (else a `selectorColor` rect) drawn at `selectorLayer` (default TOP, over the cell art). Column-aware
  up/down nav (grids), single-row wrap (textlists).

## Overlay mode (in-game scrim)

Like the built-in XMB/DSi/Minima homes, the ES-DE home is also the resident SurfaceFlinger overlay
layer, so it must go translucent when it is composited over a still-running app and let that app show
through, then return to the opaque wallpaper home when the app exits. The overlay lifecycle and the
scrim itself live in the dispatcher (`NanoMenuRender.cpp`), not in the theme: `mOverlayMode` marks the
overlay process, `mOverlayWallpaper` is false while a live app is behind (true for the launcher), and
the primary frame clear already bakes a translucent black scrim (`glClearColor(0,0,0, sOvDim)`,
`persist.gammaos.nano.overlay.dim` default 0.90) in that state. The **only** thing the theme must do is
not repaint an opaque background over that scrim.

The built-in themes gate a single nano-synthesized background on `inGameScrim = mOverlayMode &&
!mOverlayWallpaper`. ES-DE has no synthesized background - the wallpaper is an ordinary theme-XML
`<image>`/`<video>`/`<animation>` element that merely spans the viewport - so `renderEsde` reproduces
the same effect generically:

- `esdeInGameScrim` mirrors the other themes' guard verbatim; when false (launcher / app exited /
  non-overlay DRM home) every draw runs unchanged, so the opaque home is fully restored with no
  regression.
- `esdeCoversScreen(e)` classifies an element as a screen-covering opaque background by **geometry +
  tint opacity** (an `image`/`video`/`animation` whose `cropSize`/`size`/`maxSize`/`fullBg` box is
  >= 98.5% of the screen on both axes and whose resolved `color` alpha is >= 0.98), not by name.
  In scrim mode such an element is skipped so the app shows through; a partial element (chrome) or a
  translucent one (already composites the app through) is left alone. The carousel background band uses
  the same full-screen-opaque test, the secondary panel fill early-returns, and the system<->gamelist
  slide/fade transition (whose FBO blit and black quad are opaque by construction) is cut to a direct
  `renderEsde()` while summoned over an app.

Device-verified on the control (`overlay_home=1`, ndstheme=3): a wallpaper theme (adroit) drops its
full-screen background in scrim mode so the running app is visible with only the system-logo chrome on
top, and returns to the full opaque animated background when the app exits.

## Image decode and fit (`esdeArtTex`)

Every raster the engine draws (system art, wallpapers, logos, scraped covers, badge/help icons)
goes through one function, `esdeArtTex(path, boxW, boxH)`, which returns a cached GL texture plus
its contain-fit size in the box. The decode chain is picked per extension, with a universal
fallback so a theme never silently draws nothing:

- `.svg` -> nanosvg, rasterized at the box size (vector, aspect-fit, cached).
- `.png` -> libpng (`loadColorIconTexAbs`).
- `.jpg` / `.jpeg` / `.bmp` -> stb_image.
- `.webp` / `.gif` / `.heif` / `.avif` -> **Android `AImageDecoder`** (`esdeLoadImageDecoderTex`),
  the same NDK decoder the photo viewer uses (linked via `libjnigraphics`). Straight
  (unpremultiplied) RGBA to match the libpng/stb paths and the `SRC_ALPHA` blend, downscaled on
  decode so neither side exceeds the 2048 GPU texture cap.
- If the extension-specific decoder fails, the engine falls back to `AImageDecoder` (which also
  reads png/jpg/webp/gif/heif/bmp), then stb, then libpng, so a mislabelled or unusual file still
  loads. Native dimensions for the contain-fit come from `stbi_info`, or an `AImageDecoder`
  header probe (`esdeImageDecoderDims`) for the formats stb cannot read.

This matters because a large share of modern ES-DE themes (Linear, Canvas, Aura, Epic Noir) ship
their system art and wallpapers as WebP; without the `AImageDecoder` path those elements drew
nothing and the carousel fell back to a text label.

**Fit modes** follow `ImageComponent::resize()`:

- `<size>` with both axes > 0 -> **stretch** to the box (aspect ignored): bands, frames, spacers.
- `<size>` with one axis 0 -> **derive** the zero axis from the image aspect against the fixed axis.
- `<maxSize>` -> **contain**-fit (scale down keeping aspect so the whole image fits in the box),
  anchored by `origin` against the fitted size.
- `<cropSize>` -> **cover**-fit (`mTargetIsCrop` + `coverFitCrop`): scale to fill a box of
  `cropSize * screen` keeping aspect, cropping the overflow, with `cropPos` choosing which slice of
  the overflow is kept (default `0.5` = centred). Full-screen wallpapers that set only `cropSize`
  (Canvas' per-scheme `background-art.webp`) use this. The cover overflow is clipped to the box
  only when the box is inset from the screen edges; a full-screen box is already bounded by the
  viewport, and a full-screen scissor was found to suppress the draw on the image path.

## Start menu + theme downloader

Opened with the Start button on the ES-DE home (`NanoMenuEsdeMenu.cpp`, gated on `mEsdeTheme`):

- **Start menu** mirrors ES-DE's GuiMenu UI-settings subset: THEME, THEME VARIANT, THEME COLOR
  SCHEME, THEME ASPECT RATIO, THEME FONT SIZE. Options come from the loaded theme's
  `capabilities()` (and, for THEME, the installed sets enumerated via `parseCapabilities`).
  Left/Right cycle a value inline; A opens a full-list scrollable picker; each choice writes the
  `persist.gammaos.nano.esde.*` prop and live-reloads via `ensureEsdeTheme`. Panel geometry is in
  screen fractions (ES-DE MenuComponent) so it scales to any panel.
- **Theme downloader** fetches the ES-DE themes list (`themes.json`) and installs a chosen theme
  by downloading its repo zip archive, unzipping it and placing it under the data theme root
  (then it appears in the THEME picker). The native home has no network of its own, so the fetch
  and install downloads are routed through the `gammaos-net` framework helper (a `download` verb
  that `requestNetwork()`s an internet network and fetches over `Network.openConnection()`).
- **Applications** (`kEsdeRowApps`) opens a scrollable launcher listing the installed apps
  (`mAppEntries`, the same list the XMB Applications submenu uses; lazily populated by
  `loadInstalledApps`). A launches the highlighted app through `overlayLaunchPackage` (resolve
  the LAUNCHER activity, `am start` pinned to the panel) after closing the overlay; B returns to
  the root page. This gives the ES-DE home a way to reach any app without switching to the XMB
  home, mirroring how ES-DE lets a user quit to other applications.
- **Nano Settings** (`kEsdeRowNanoSettings`, the last root row) hands off to the shared XMB menu
  stack (Quick Menu / Full Settings), the same tree the built-in homes reach.

The root rows are addressed by name (`kEsdeRowApps` / `kEsdeRowNanoSettings` / `kEsdeRootRows`)
rather than raw `kEsdeMenuRowCount + N` offsets, so inserting a row updates one table and the
renderer, cycler and input handler stay in agreement.

Still open (fidelity follow-ups): the non-favorite `badges` slots (controller, completed,
collection, kidgame, broken, altemulator, manual), Lottie (`.json`) animation, live game
video (shown as its scraped still), the logo crossfade on a system change, system<->gamelist
view transitions, and variant triggers (per-system media-presence auto variant switch). Theme
TTF fonts are loaded (`fontPath` -> an FT face via `esdeFontFace`), so a theme's own typeface
renders; nano measures with whole-pixel hinted advances, so a very long paragraph can wrap a
word early versus ES-DE's fractional shaping (short labels match exactly).

## Module layout

New files under `frameworks/base/cmds/gammaos-nano/` (self-contained; the built-ins are
untouched):

- `NanoThemeEngine.h` / `NanoThemeEngine.cpp` - the engine object: load a theme set,
  hold the parsed model + selected layers, render `system`/`gamelist`, handle nav, own
  the texture/rasterization caches. Exposes `renderEsde*` entry points the `render()`
  dispatch calls for the `mEsdeTheme` branch.
- `NanoThemeParser.cpp` - `theme.xml` + `capabilities.xml` parsing into the element model
  (tinyxml2), includes, variables, the element/property type tables (mirrored from ES-DE
  `sElementMap`), and validation.
- `docs/theme-engine/` - deep-dive docs: the element support matrix with per-element
  status, the coordinate model, and the theme-set install layout.

Dependencies (both already suitable for a system binary / low-end target):

- **`libtinyxml2`** - XML DOM parser, already in the AOSP tree (used by frameworks/native
  libinput, apexd, libvintf). Linked via `static_libs`.
- **`nanosvg`** (vendored single-header, MIT) - rasterizes SVG logos/controller art to a
  bitmap once per size; the result is cached as a GL texture. ES-DE theme logos are SVG,
  so this is required for real-theme compatibility. Pairs with the existing `stb_image`
  for raster art.

## Gating and theme sets

- **Enable**: `persist.gammaos.nano.ndstheme = 3` (the "ES-DE" option in Settings ->
  Home Theme). Off by default.
- **Theme-set selection**: `persist.gammaos.nano.esde.themeset` names the active set; a
  Settings picker lists installed sets (built dynamically, like the browser/device
  pickers).
- **Install locations** (three roots, checked in this order by `esdeSetDir` and enumerated by
  `esdeMenuEnumerateInstalled`; a set is any subdir with a root `capabilities.xml`, deduped by
  directory name so an earlier root wins):
  1. `/data/system/nano_esde_themes/<name>/` - the downloader's install target; adb-pushable
     during development; survives binary-only updates.
  2. `/system/etc/nano_esde_themes/` - optional bundled defaults.
  3. `/storage/emulated/0/ES-DE/themes/<name>/` - the **user theme folder**. Drop a theme here
     (the same path real ES-DE reads on Android) and it appears in the THEME picker with no
     install step. This is the path a user copies a downloaded theme into over MTP / a file
     manager.
- **User theme monitoring**: `esdeSdcardThemesTick()` (pumped from `render()`, self-throttled to
  ~1.5s, cheap `stat`-only) watches the user folder so changes are picked up live without a
  restart. It keeps two signatures: the folder's own directory entry (a set added or removed ->
  re-enumerate the picker) and, when the active set lives there, that set's `theme.xml` /
  `capabilities.xml` mtimes (an in-place edit -> reparse just that set). Separating them means a
  sibling theme dropped in does not needlessly reload the active theme. inotify is avoided because
  it is unreliable on the sdcard FUSE mount.

## Performance strategy (A133 / 1 GB / GLES2)

- **Parse once.** The theme model is built on load / theme-set switch, never per frame.
  The per-frame path only walks the resolved element list and draws.
- **Cache rasterization.** SVG logos and scaled raster art are rasterized/decoded once
  and cached as GL textures keyed by (path, pixel size); re-rasterize only on a resize
  that changes the target size. Bound the cache and evict LRU.
- **Reuse the shared art path.** Game covers/fan-art use the existing async
  `romBoxartTex` / scraper worker and the bounded (~96) texture cache; never eagerly
  decode a whole gamelist on the render thread.
- **No per-frame allocations.** Resolve the active view's element vector once per view
  change; keep scratch geometry buffers; 16-bit indices; reuse VBOs/FBOs.
- **Cheap first.** Draw only visible carousel/list items (windowed), skip zero-alpha
  elements, honor the zIndex sort once. Expensive ES-DE features (video, gradients, blur)
  are skipped in the MVP precisely because they cost the most on this GPU class.
- **Slot into existing gates.** The engine's secondary pass uses the same
  `mWidth/mHeight` remap and the bottom-panel cache as the built-ins; it inherits the
  60 fps budget and idle FPS drop.

## Parity with ES-DE (source-level audit)

The engine is validated by comparing its output against the real ES-DE C++ source
(`es-core` CarouselComponent / GridComponent / TextListComponent / Text / Rating / Image /
DateTime, `ThemeData`, HelpComponent / SystemStatusComponent, and `es-app` SystemView /
GamelistView / ViewController), not by eyeballing alone, and then device-verified on the Brick
side by side against a control unit running real ES-DE. The layout math, animation curves and
timings, and colour/opacity models are translated formula-for-formula. Confirmed 1:1:

- **Carousel**: `itemSpacing = ((size - itemSize*maxItemCount)/maxItemCount) + itemSize`;
  `itemSize` relative to the **screen** with the ES-DE default `0.25 x 0.155` and a `0.05..1.0`
  clamp; wrapping index; 400 ms slide eased `t = 1-(1-t)^2` along the shortest wrapped path; the
  selected-item scale ramp for `itemScale > 1` **and** the neighbour-shrink ramp for
  `itemScale < 1`; per-item opacity fade to `unfocusedItemOpacity` (default 0.5) and the
  `unfocusedItemDimming` ramp; `maxItemCount`/`itemScale` clamps; the focused item's
  `imageSelectedColor` tint; a single-entry carousel draws one centred item (no ghost copies);
  the `color` background band; the per-system logo slides with the carousel during a system change.
- **Grid**: column derivation from `size`/`itemSize`/`itemSpacing` with the margin rule;
  `fractionalRows`; `scaleInwards` edge clamp; the 300 ms focus transition (scale + opacity +
  dimming) on the eased curve; windowed rows; row-membership guards so edge presses are no-ops.
  A grid in the **system view** binds each cell to its system logo (`imageSelectedColor` on the
  focus), with column-aware navigation. The focused cell's selector prefers a `selectorImage`
  (tinted by `selectorColor`) over the `selectorColor` rect and is drawn at `selectorLayer` (default
  TOP, i.e. over the cell art), matching Catppuccin's grid frame.
- **Theme resolution**: `base < variant < aspectRatio` layering; comma-listed
  `variant`/`colorScheme`/`aspectRatio`/`fontSize` name matching (no bogus wildcards);
  `${variable}` substitution including inside `<include>` paths; the aspect-ratio identifier
  table copied verbatim so automatic detection picks the same layout ES-DE would on any panel.
  Per-system data (info panels, per-system colours) is re-resolved as the carousel settles on a
  new system, so it tracks the selection instead of sticking on the first system.
- **Images**: `maxSize` (and a single-zero-axis `size`) contain-fit / aspect-derive, while a
  two-axis `size` stretches to fill (bands, frames); covers aspect-fit. The fitted image is
  anchored by the element **origin against its fitted size** (as `ImageComponent` translates by
  `-origin*mSize`), not centred inside the box, so an edge/corner origin (e.g. Art Book Next's
  bottom-centre game-art) lands exactly where ES-DE puts it. Tiled colour spacers become a solid fill.
- **Text**: an auto-sized `<text>` (no explicit `<size>` axis) is anchored by its **origin
  against the measured glyph box** - `x -= origin.x*textWidth`, `y -= origin.y*lineHeight` - the
  way `TextComponent` sets `mSize` from the rendered text, so a bottom origin (e.g. the Atari 50
  title / game-count at `origin 0 1`) seats the baseline on the anchor instead of hanging a line
  below it. `systemdata=gamecount` renders `N games (M favorites)` with singular/plural (and the
  `gamecountGames`/`Favorites`/`*NoText` variants) exactly as `SystemView::updateGameCount`.
- **System names**: the system-view carousel/list entry and `${system.fullName}` resolve to the
  ES-DE **full** name (`SystemData::getFullName`, e.g. `nes` -> "Nintendo Entertainment System"),
  not the short id, via a 195-entry `name`->`fullname` table (`NanoEsdeSystemNames.inc`) extracted
  verbatim from ES-DE's `es_systems.xml`; a non-standard system falls back to nano's own name.
- **Chrome / elements**: rating half-star clip fill with the ES-DE default star height
  `0.06*screenHeight`; textlist selector default `0x333333FF`; the textlist entry pitch reads the
  theme `lineSpacing` (default 1.5) and the visible-row count uses ES-DE's
  `floor((h + lineSpacingHeight/2)/entrySize)`; textlist rows are clipped to the element and apply
  the theme `letterCase`; all three `letterCase` modes including `capitalize` (Title Case); the
  description auto-scrolls with ScrollableContainer's computed speed (rowModifier / content-width /
  resolution) not a fixed rate; ES-DE battery buckets; the help bar scaled by `entryRelativeScale`
  with each icon sized to the help font's cap height x1.25 (not the raw em), and rendered in ES-DE's
  default Akrobat typeface when the element sets no `fontPath`; system status sized to the theme
  height; the clock hidden by default (ES-DE `DisplayClock`) and, when shown, centred on its pos by
  its `origin`;
  fonts scaled by the shorter screen dimension (correct on portrait); the `badges` flexbox renders
  the game's active states (nano tracks `favorite`).
- **Options menu**: the theme picker shows each capability's human `<label>` (localised, English
  preferred) exactly as ES-DE's menu does, an aspect ratio's `_vertical` suffix reads
  "&lt;ratio&gt; vertical", and a value too wide for its row shrinks to fit rather than colliding.
- **Variant triggers**: a variant's `<override>` `noMedia`/`noVideos` triggers (parsed from the
  child `<trigger>`/`<mediaType>`/`<useVariant>` elements, not an attribute) are applied per focused
  system exactly like `ViewController`: a system whose games have none of the listed media types
  renders with the override variant (`noMedia` beats `noVideos`). nano keeps one scraped image per
  game (the box, reused for the video element), so every image/video mediaType maps onto "has a box".
  Art Book Next's `gamelist-list-metadata-cover` falls back to `gamelist-list-basic` for a coverless
  system; slate's `withVideos` falls back to the centred `noGameMedia` carousel. Gated by
  `persist.gammaos.nano.esde.variant_triggers`.
- **One primary per view**: like `SystemView`/`GamelistView`, exactly one primary element (the first
  textlist/carousel/grid by element name) is kept per view and the rest skipped, so a variant that
  layers a carousel over the base textlist (Analogue OS Menu's `gamelist-carousel`) draws only the
  carousel. The renderer and the grid-nav model share `esdeChosenPrimary` so they agree.
- **Selection plate**: the `selectedBackgroundColor` plate is drawn at the selector height
  (`mSelectorHeight` = font pixel size x 1.5, or the theme `selectorHeight`), width = text width plus
  both margins (both denormalized by screen width), positioned at the text left minus the x margin
  with margins defaulting to zero; rows are indented by the theme `horizontalMargin` (default 0), not
  a hardcoded fraction. The plate is drawn without the row text-scissor (ES-DE truncates the row text
  instead of clipping), so a large `cornerRadius` with a left margin keeps its rounded left end
  instead of being sliced flat. Both the row text and the plate are anchored in a `fontSize*1.5` box
  at the row TOP (as `TextListComponent::addEntry` sizes the entry), not centred in the full
  `fontSize*lineSpacing` pitch, so a larger row `lineSpacing` (Art Book Next's 1.944) does not sink
  the selected row.
- **Vertical carousels**: the carousel `<type>` is honoured with ES-DE's exact camelCase tokens
  (`horizontal`/`vertical`/`horizontalWheel`/`verticalWheel`); the vertical axis computes the
  spacing from the box height and slides items along Y (`itemSpacing.y = ((size.y - itemSize.y*
  maxItemCount)/maxItemCount) + itemSize.y`) with the cross axis centred. A gamelist carousel binds
  each entry's scraped box (contain-fit) as the item media, standing in for ES-DE's imageType.
- **Navigation sounds**: the theme's `<sound name=...>` wavs (systembrowse, quicksysselect, select,
  back, scroll, favorite, launch) play through the shared low-latency SFX player on the matching nav
  event, cached at theme load so playback never races the per-system doc reload. The wav loader takes
  16/24/32-bit integer and 32-bit float PCM (down-converted to 16-bit), not just 16-bit. Gated by
  `persist.gammaos.nano.nav_sounds`.

Deliberately deferred (tracked, not silently dropped): the animated **view transition** (a theme
whose selected profile is `slide`/`fade`, e.g. slate/linear defaults; Art Book Next defaults to
`instant`, which nano matches, so this is not an ABN-parity gap); the per-system extras crossfade
during a transition (ES-DE renders the adjacent systems' entire element sets by camera offset;
nano slides the logo and re-resolves per-system data on settle, but other extras snap); the
`containerType="horizontal"` text marquee (long metadata values clip rather than scroll sideways);
the carousel band `colorEnd` gradient; the exact gamelist
help-prompt set for features nano does not implement (media viewer, per-game options). A
`datetime` value is now run through the element's strftime `<format>` and `letterCase` (so
`<format>%Y</format>` shows just the year); `displayRelative` is still a gap (nano tracks a
release date but not last-played/playtime per game). The
`gamelistinfo` and `animation` element types are parsed (so they never break a theme load) but not
yet rendered; the `badges` flexbox now tints icons by `badgeIconColor` and honours the slot order,
but not the `badgeIconColorEnd` gradient nor `direction`/`rotation`/`horizontalAlignment`, and
beyond the `favorite` slot it keys off game metadata states nano only partially tracks, so
multi-badge alignment is a narrow gap. The carousel applies `horizontalOffset`/`verticalOffset`
(uniform per-item shift) though not ES-DE's matching item-window recentring. Image `brightness` and
`saturation` are applied to covers and backdrops on every path (`image`, `video`, carousel and grid,
including the carousel/grid `unfocusedItemSaturation` per-item greyscale) via a small dedicated FX
shader program that the shared text/icon path never touches; it is used only when a theme sets a
non-default value and falls back to the plain draw if it fails to link, so it cannot regress or crash
the other homes. Image `colorEnd`/`gradientType` gradients and `cornerRadius` rounding are still gaps.
A `<video>` MP4 theme background IS
rendered (Adroit's `Animated` scheme plays its looping mp4 via the hardware decoder); only a
`<animation>` GIF/Lottie background is not (Adroit ships no gif, and its mp4 draws on top regardless).

## Roadmap

1. Parser + model + capabilities validation (host-checkable, no GL).
2. Minimal renderer: background image, system-view textlist/carousel of system logos,
   clock/systemstatus/helpsystem. Gate on `ndstheme=3`, dev-push a real theme, A/B on the
   Brick.
3. Gamelist view: game textlist/carousel + cover image + metadata text + rating +
   gamelistinfo, bound to the scraper.
4. Variants/colorSchemes/aspectRatios selection UI + the theme-set picker.
5. Expand the element matrix (grid, video static, animation first-frame) as budget allows.

Progress and screenshots (engine renders vs the theme's own reference images) are posted
per milestone.
