# ES-DE theme layering (variant / colorScheme / fontSize / aspectRatio / variables)

An ES-DE theme is not one flat document: a base layer is overlaid by the selected `variant`,
`colorScheme`, `fontSize` and `aspectRatio` blocks, plus `${variables}` that any layer can define and
later layers override. Getting this layering right is what lets ONE theme adapt to every
option/panel; getting the precedence wrong silently corrupts colours, sizes and which layout file
loads. nano implements it in `NanoEsdeTheme.cpp` `Theme::walk()` (a recursive descent over the XML,
following `<include>`s); this document records the model and where it matches - and where it does not
yet match - the ES-DE source (`es-core/src/ThemeData.cpp`).

## Selection (which block of each kind is active)

Resolved once at load in `Theme::load()`, each against the theme's declared capabilities:

| kind | nano member | selection rule (matches ThemeData) |
|------|-------------|-------------------------------------|
| variant | `mSelVariant` | the `ThemeVariant` setting if the theme declares it (and it is `selectable`), else the first declared variant. A `<variant>` block applies if its (comma-list) `name` contains the selection **or `all`**. |
| colorScheme | `mSelColorScheme` | the setting if declared, else the first. A block applies only on an exact `name` match - **no `all` wildcard** (ES-DE has none for colorScheme; `custom` is a real selectable scheme in some themes, so treating it as a wildcard would leak that block everywhere). |
| fontSize | `mSelFontSize` | the setting if declared, else the first. Applies on a `name`-list match **or `all`**. |
| aspectRatio | `mSelAspect` | the setting if declared, else the first; `automatic` resolves to the declared ratio nearest the real screen aspect, **seeded from 16:9** (see IMAGE.md / the load code) so it falls back to 16:9 when nothing is closer. Applies on an exact `name`-list match. |
| language | - | only `en_US` / `en_GB` (and unnamed) blocks apply; nano is single-language here. |

A block whose condition fails is walked with `active = false`, so its `<variables>`/`<view>`/`<include>`
contribute nothing - exactly as if it were absent.

## The two passes

`walk()` runs in two phases so a variable defined anywhere is known before any view that references it:

- **Phase 0 - variables.** Descends the whole active tree and records every active `<variables>`
  child into `mVars[name] = value`. Used by `subst()` for `${name}` expansion in later property values
  and `<include>` paths (so `./${system.theme}/colors.xml` and `${scaledimagetype}` resolve). Because
  `<include>`s are resolved *during* this descent, each node's own `<variables>` are gathered in a
  short pre-pass **before** its sibling `<include>`s - mirroring ES-DE's `parseVariables`-before-
  `parseIncludes` order - so an include resolves with the file's variables set even when the
  `<variables>` block is written after it (artflix declares `<rootpath>` below the
  `${rootpath}/${system.theme}` metadata includes; without the pre-pass those loaded with an empty path
  and every `${systemDescription}` / `${systemReleaseYear}` came out blank).
- **Phase 1 - views, layered.** Run once per layer `L = 0,1,2` (base, variant, aspectRatio); a
  `<view>` is collected only when its enclosing layer equals `L`, so later layers **override** earlier
  ones element-by-element (a union, last-wins). `variant` bumps the layer to >=1, `aspectRatio` to >=2;
  `colorScheme`/`fontSize`/`language` keep the current layer (they contribute variables and includes,
  not their own view layer). This yields **base < variant < aspectRatio** for view/property overrides,
  matching ThemeData's parse order for those three.

## Variable precedence (known gap vs ES-DE)

ThemeData applies the layers by a **fixed call sequence** - `parseVariables` (base), then
`parseFontSizes`, then `parseVariants`, then `parseAspectRatios` - at the top level and inside every
include and every selected block, with `mVariables[key] = value` overwriting. So the guaranteed
precedence is **base < fontSize < variant < aspectRatio**, *regardless of the order the blocks appear
in the XML*.

nano's phase-0 collection is a single **document-order** walk: whichever active `<variables>` block is
encountered later overwrites earlier ones. For the conventional theme (which writes base, then
fontSize, then variant, then aspectRatio) document order already equals the layer order, so the result
is identical. It diverges only for a theme that (a) redefines the **same** variable in two different
layers **and** (b) orders those blocks against the convention (e.g. an `<aspectRatio>` block before a
`<variant>` block that set the same variable) - then nano takes the later-in-file value while ES-DE
takes the higher-layer value.

Fixing this generically means tagging each `<variables>` assignment with its layer (0 base, 1 fontSize,
2 variant, 3 aspectRatio) and applying them in ascending layer order rather than document order. It is
deferred because it is a parser restructure (it also needs fontSize promoted to its own numbered layer
between base and variant, without disturbing the phase-1 base/variant/aspectRatio view layering) for a
low-frequency case; no reference theme has been observed to trigger it. Verify a fix by A/B-ing a theme
that deliberately cross-defines a variable out of order, at the affected option, against the control.

## fontSize as a layer

`fontSize` is a real layer in ES-DE (`parseFontSizes` contributes variables + includes when the
selected size matches), not just a font scale. Themes use it to swap `${variables}` per size - e.g.
Canvas switches `scaledimagetype` (the gamelist media type) and metadata visibility between
`small/medium/large` and `x-large`, so choosing a font size can change **which boxart type and layout**
the gamelist shows. nano matches a `<fontSize name="...">` block against `mSelFontSize` (or `all`) and
walks its variables/includes; the medium default and the min(W,H) normalization live in `fontPx`
(see IMPLEMENTATION.md).

## Where it lives in nano

- selection: `NanoEsdeTheme.cpp` `Theme::load()` (`resolveOne` for variant/colorScheme, the
  16:9-seeded automatic aspect loop, fontSize fallback).
- layering walk: `NanoEsdeTheme.cpp` `Theme::walk()` - the `variant`/`colorScheme`/`fontSize`/
  `aspectRatio`/`language` cases set `active && sel` and bump `layer`; phase 0 collects variables,
  phase 1 collects views per `targetLayer`.
- `${var}` expansion: `Theme::subst()`.

## Verifying a change

Set the option on both nano (`persist.gammaos.nano.esde.{variant,colorscheme,fontsize,aspectratio}`)
and the control (`es_settings.xml` `Theme{Variant,ColorScheme,FontSize,AspectRatio}`, relaunch), and
diff. Exercise the axes the reference set actually varies: art-book-next / catppuccin declare several
`colorScheme`s and `fontSize`s; art-book-next declares 12 `aspectRatio`s. Watch the variant-trigger
confound (a `noVideos`/`noMedia`/`noGameMedia` trigger can swap the variant when media is absent) -
disable `ThemeVariantTriggers` on the control for a clean same-variant comparison.
