# ES-DE textlist primary

`<textlist>` is one of the three navigation primaries (with `<carousel>` and `<grid>`): a vertical
list of entries - the systems in the system view, the games in a gamelist. nano implements it in the
`t == "textlist"` branch of `renderEsde()` in `NanoThemeEngine.cpp`, checked against
`es-core/src/components/primary/TextListComponent.h`. The entries themselves are `TextComponent`s, so
the font / alignment / marquee model in [METADATA.md](METADATA.md) applies to each row; this document
covers the list layout and the two selection highlights.

Device-verified against the control (`org.es_de.frontend`) on SuperStation One Menu (a centre-aligned
PS1-style system list) and simplemenu.

## Rows and pitch

- **entry pitch** = `fontSize * lineSpacing` (`lineSpacing` default 1.5, clamped [0.5, 3.0]). The
  visible row count is `floor((size.y + lineSpacingHeight/2) / pitch)` where `lineSpacingHeight =
  pitch - fontSize` (ES-DE's ceil-ish fit, which can show one more row than a plain `floor(h/pitch)`).
- the cursor is kept centred: the first drawn entry is `cursor - rows/2`, clamped so the list does not
  scroll past either end.
- **colours**: `primaryColor` for a normal row, `secondaryColor` for a folder row, and `selectedColor`
  for the cursor row. `selectedColor` falls back to `primaryColor` when the theme omits it (ES-DE), not
  to white.
- the selected label marquees when it overflows the list width (`textHorizontalScrolling`, default on)
  with the same speed/delay/gap as a horizontal text container - see [METADATA.md](METADATA.md).

## The two selection highlights

A theme highlights the cursor row with either (or both) of:

### `selectorColor` - a full-row bar

A solid bar spanning the **whole row width** behind the selected entry, default `0x333333FF`. Because
it is row-width it is alignment-independent. A theme that wants no bar sets `00000000` (art-book-next,
SuperStation One Menu, which both prefer the text-hugging plate below). Positioned at the row top +
`selectorVerticalOffset`, width offset by `selectorHorizontalOffset`; a `selectorColorEnd` different
from `selectorColor` draws it as a gradient.

A theme can replace the bar with a graphical cursor via **`selectorImagePath`** (an svg/webp capsule,
frame or pointer - epic-noir's white nav dot, aura/canvas/cathode's capsules). ES-DE stretches it to
`selectorWidth` x `selectorHeight` (`mSelectorImage` setResize) at the same offsets and tints it by
`selectorColor` -> `selectorColorEnd` (`setColorShift`), drawing it instead of the colour bar. nano
loads it through `esdeArtTex` and stretches it to the selector box; when unset (the common case) the
flat/gradient bar path is used unchanged. Verified 1:1 against the control on epic-noir's system list:
the flat bar became the theme's white nav dot at the matching position.

### `selectedBackgroundColor` - a plate hugging the text

A rounded rectangle sized to the **selected label**, not the row: width = `text width +
selectedBackgroundMargins`, corners rounded by `selectedBackgroundCornerRadius`. ES-DE anchors it to
the selected entry's *text position* (`TextListComponent` draws it at the entryName transform), so it
**follows the list `horizontalAlignment`**. nano offsets the plate by the same centre/right amount
`drawAligned` uses for the label:

```
aw = rowWidth - 2*horizontalMargin
textLeft = rowLeft + horizontalMargin
         + (align==center ? (aw-textWidth)/2 : align==right ? (aw-textWidth) : 0)
plate at textLeft - selectedBackgroundMargins.x
```

A plate pinned to the row's left edge (nano's old behaviour) sat *beside* a centred label instead of
behind it - the SuperStation One Menu system name spilled off its own highlight. `left`/unset
alignment (the ES-DE textlist default) keeps `textLeft = rowLeft + horizontalMargin`, so left-aligned
lists (art-book-next, Analogue) are unchanged. The plate is drawn without the row text-clip scissor so
its rounded corners are not sliced.

## Where it lives in nano

- parse: `NanoEsdeTheme.cpp` `elementPropertyMap()` `"textlist"` entry - `primaryColor`,
  `secondaryColor`, `selectedColor`, `selectorColor`/`selectorColorEnd`, `selectorHeight`,
  `selectorHorizontalOffset`/`selectorVerticalOffset`, `selectedBackgroundColor`,
  `selectedBackgroundMargins`, `selectedBackgroundCornerRadius`, `lineSpacing`, `horizontalAlignment`,
  `horizontalMargin`, `indicators`, `textHorizontalScroll*`.
- render: `NanoThemeEngine.cpp` `renderEsde()` `t == "textlist"` branch (via `drawPrimary`) - row fit,
  the `selectorColor` bar, the `selectedBackgroundColor` plate, and the per-entry label with its
  marquee.

## Verifying a change

The textlist system view renders on the control (unlike a gamelist's detail elements), so it is the
reliable A/B surface: use SuperStation One Menu or simplemenu, capture the system list and diff. To
check plate alignment specifically, measure the selected label's white plate bounding box on both -
its centre-x must match the label's (SuperStation: centre-x ~0.500 on both). A residual full-frame MAE
that is a progressive per-glyph horizontal drift is nano's font metrics vs ES-DE FreeType advances on
a pixel/monospace font, not a layout bug.
