# ES-DE metadata elements (text / datetime / rating / gamelistinfo)

The gamelist's textual chrome - the game name, description, release date, developer, rating stars, the
"N Games" counter - is drawn by the `<text>`, `<datetime>`, `<rating>` and `<gamelistinfo>` elements.
nano implements them in `renderEsde()` in `NanoThemeEngine.cpp` (the `t == "text" || "gamelistinfo"`,
`t == "datetime" || "clock"` and `t == "rating"` branches). This document records their conformance
model, checked against the ES-DE source (`es-core/src/components/TextComponent.cpp`,
`DateTimeComponent.cpp`, `RatingComponent.cpp`), and the state of each item from the source-level audit -
what is verified, what is a deferred gap.

## `<text>` and `<gamelistinfo>`

`gamelistinfo` is a plain `TextComponent` in ES-DE (a synthesized "N Games (M Favorites)" label), so it
shares the text pipeline. The properties nano honours:

- **content**: a literal `<text>`, or a `<metadata>` binding (description/developer/genre/players/...),
  or `<systemdata>`; `<defaultValue>` when the bound value is empty. `<systemdata>` (system view) tokens
  are lower-case: `name` -> the internal es_systems name (`getName`, nano's `romDir`), `fullname` -> the
  display full name (`getFullName`, via nano's ES-DE full-name table), and the `gamecount*` variants.
  Matching lower-case `fullname` (ES-DE emits no camelCase `fullName`) is what makes the system-name
  heading render in artflix / ps5-menu / xmb-menu / codywheel.
- **container scroll**: `container` + `containerType` + `containerScrollSpeed` / `containerStartDelay` /
  `containerResetDelay` - the auto-scrolling synopsis. A settled frame is static; capture within
  `containerStartDelay` to diff it, since the scroll is the usual source of a per-frame MAE. A container
  is an **auto-height** `ScrollableContainer` (`mSize.y == textHeight`), so ES-DE's vertical-offset
  branch never fires and the content is **always top-anchored**; nano therefore ignores a container's
  `verticalAlignment` (a single-line `verticalAlignment=bottom` container - artflix's system-name
  heading - otherwise sinks to the box bottom and overlaps the rows below). A non-container sized box
  keeps its `verticalAlignment`.
- **font**: `fontPath` -> a theme TTF face, `fontSize` (normalized to `min(W,H)`), `letterCase`
  (uppercase/lowercase/capitalize), `lineSpacing` (**clamped to [0.5, 3.0]** to match
  `TextComponent::setLineSpacing`), `horizontalAlignment` / `verticalAlignment`.
- **paint**: `color` (+ `backgroundColor` plate).

Audit gaps (deferred, verify against the control before changing):
- **default `fontSize`**: ES-DE's `TextComponent` defaults to `FONT_SIZE_MEDIUM` = 0.045 (landscape) /
  0.040 (vertical) of `min(W,H)`; nano's `<text>` branch defaults to 0.035 (gamelistinfo already
  defaults 0.045). Only bites a `<text>` element that omits `fontSize` - rare, since detailed themes set
  it, so it never fired on the verified set. An orientation-aware default (0.040 vertical) also needs a
  portrait panel to test.
- **default `color`**: `TextComponent`'s base is `0x000000FF` (black) for a plain text element; nano
  defaults to white. Again only matters when a theme omits `<color>`.
- **`letterCase=capitalize`**: ES-DE uses an ICU word BreakIterator (punctuation starts a word); nano's
  is ASCII-whitespace-only, so `hello-world` -> `Hello-world` not `Hello-World`.
- **`<rotation>` on a text element**: nano's glyph renderer (`drawText`) lays out axis-aligned quads,
  so a rotated text is handled by `esdeDrawRotatedText`: the string is rendered upright into an
  offscreen FBO (`esdeEnsureFbo`) and that texture is blitted rotated about the element's
  rotationOrigin (default origin) through the existing image-rotation path (`drawIconTex` rotRad,
  `flipV` for the FBO origin, premultiplied blend so anti-aliased edges are not darkened twice).
  Opt-in: `rotation == 0` falls straight through to the normal draw, byte-identical. Verified on
  CarAlt, whose two `-90` game-count gauges (`gamecount` at x=0.03, `gamecount2` at x=0.06) now render
  as thin vertical labels 30px apart instead of overlapping each other and the carousel logo.

## `<datetime>` (and `<clock>`)

`datetime` shows a per-game metadata date (`releasedate` / `lastplayed`); `clock` shows the wall clock.
nano parses the stored `YYYY-MM-DD` (or bare `YYYY`) and re-emits it through the element's strftime
`<format>`; `lastplayed` auto-enables `displayRelative` ("3 days ago" / "never").

- **Single-line, origin-anchored**: `DateTimeComponent` auto-sizes its **height** to one line
  regardless of the theme `<size>`, so a `<size>` height never stretches the vertical box - nano seats
  the one line by the element origin at `pos.y` (`y = pos.y*H - origin.y*textHeight`) rather than
  aligning within the box height. This is what makes Cathode's clock, a full-screen `size 1 1` box
  corner-anchored via `origin 1 1 / pos .995 .996`, sit at the bottom-right over the theme's LCD
  overlay instead of at the screen top. The horizontal box (for `horizontalAlignment`) keeps the theme
  `size.x`.
- **`<clock>` is gated by `DisplayClock`**, which ES-DE defaults **off**; the bundled themes all define
  a clock but show none until the user opts in. nano mirrors this with `persist.gammaos.nano.esde.clock`
  (default off) and synthesises the ES-DE `clock_default` fallback when it is on and the theme defines
  no clock.
- **Background plate (`esdeDrawPlate`, shared with `helpsystem` / `systemstatus`)**: a `backgroundColor`
  element grows a rounded plate around the tight content box by `backgroundHorizontalPadding` /
  `backgroundVerticalPadding`. These are ES-DE `NORMALIZED_PAIR`s of **(near, far)** sides: the plate is
  translated top-left by the *near* pad and grown by *near + far*, horizontal pads scaled by screen
  width and vertical by screen height, each clamped to `[0,1]`; `backgroundCornerRadius` clamps to
  `[0,0.5]` of the width (`HelpComponent.cpp` / `DateTimeComponent.cpp` / `SystemStatusComponent.cpp`).
  ES-DE has **no** height-proportional padding, so with no padding the plate hugs the text; nano no
  longer adds an intrinsic `textHeight*k` pad or reads only the near side (which had drawn every themed
  help / status / clock plate oversized and mis-anchored).

- **Verified**: a bare-year date (`"2020"`) through a `%Y-%m-%d` format resolves to `2020-01-01` (the
  parse tm is seeded `tm_mday=1` like ES-DE's `stringToTime`, so it is not `2020-01-00`).
- Gaps (deferred): with **no** `<format>` ES-DE still applies its constructor default `%Y-%m-%d`
  (nano emits the raw string); an unset date with no `<defaultValue>` renders `unknown` in ES-DE
  (nano renders nothing, except a relative `lastplayed` which becomes `never`); a non-clock
  `datetime` that omits `<color>` is black in ES-DE (nano white); ES-DE's `timeToString` only
  implements `%Y %m %d %H %M %S` (nano uses the full libc strftime token set). These are default/format
  edge cases; the common `<format>%Y-%m-%d</format>` with a full scraped date matches.

## `<rating>`

Five star cells; the first `round(rating*5)` filled, the rest unfilled, with a partial star via a clip
at the fill fraction. `filledPath`/`unfilledPath` (else ES-DE's built-in stars), `color`, `size.y`
(star height), `imageInterpolation`.

- The per-star **width** derives from the filled-star image aspect (`mImageRatio`); nano assumes square
  stars, which matches every bundled/reference theme (their star SVGs are square, e.g. viewBox 0 0 32
  32), so a non-square custom star is the only case that would need the aspect derivation.
- `overlay` (default true) is honoured: `false` (Slate/Linear/Catppuccin) clips the unfilled row to the
  right of the fill instead of drawing it full-width under the filled row, matching
  `RatingComponent::setValue`. For opaque stars (every reference theme) this is visually identical to the
  overlay path, so it is a no-op there and only affects a semi-transparent filled star.
- **Default `origin`**: `RatingComponent` sets its own `mSize` but not `mOrigin`, so it inherits
  `GuiComponent`'s (0,0) - the star row anchors its top-left at `pos`. nano now matches this
  (`origin.y` default 0.0, was 0.5). A theme that omits `<origin>` (codywheel, modern, showcase, slate)
  previously drew its rating half a star-height too high, overlapping the "Rating:" label; verified on
  modern's gamelist that the row now seats in the value slot below the label like its sibling metadata
  rows, and confirmed harmless to the themes that set `<origin>` explicitly (art-book-next's 4:3 layout
  pins `origin 1 0.5`, canvas's base theme.xml pins `0.5 0.5`, so both are unchanged).

## Verifying a change

Install the theme both sides and diff the metadata panel, aligned on the same game (see AB_TESTING.md).
For a metadata value you need present, edit the ES-DE `gamelists/<sys>/gamelist.xml` (add `<rating>`,
`<releasedate>`, etc. inside the game's `<game>` block - nano and the control read the same file), then
restore it. Use a theme the control renders detailed (carbon / art-book-next / linear); slate is a bare
list on the control's ES-DE 3.4.0-56, which blocks the rating-origin / text-default A/Bs that only fire
on default-relying themes. Trust the metadata-region MAE, not the scrolling description.
