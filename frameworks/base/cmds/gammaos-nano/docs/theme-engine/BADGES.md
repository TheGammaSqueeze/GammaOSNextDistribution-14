# ES-DE badges component

The `<badges>` element is a flexbox of small game-state icons (favorite, completed, controller,
manual, ...) bound to the focused game's metadata. nano implements it in the `t == "badges"` branch of
`renderEsde()` in `NanoThemeEngine.cpp`; this document records the model it follows and where each
piece lives, checked against the ES-DE source (`es-core/src/components/BadgeComponent.cpp` and the
layout engine `es-core/src/components/FlexboxComponent.cpp`).

Device-verified against the control (`org.es_de.frontend`) on carbon (right-aligned badges) and linear
(a right-side badge column): with a game marked favorite plus a `<controller>` set, the favorite,
controller and manual badges match ES-DE in slot set, order, position and the controller overlay.

## Slots and activation

The `slots` child is a comma list (or `all`, which expands to ES-DE's canonical order):
`collection, folder, favorite, completed, kidgame, broken, controller, altemulator, manual`. A slot
draws only when its metadata condition holds for the focused game:

| slot | condition (nano `esdeMetaFor` / helpers) |
|------|------------------------------------------|
| favorite | `<favorite>true` or nano's own favourite |
| completed / kidgame / broken | the matching `true` bool metadata |
| altemulator | `<altemulator>` non-empty |
| controller | `<controller>` non-empty (its value names the overlay glyph) |
| manual | a manual file exists under `downloaded_media/<sys>/manuals/<rom>` |
| folder / collection | never (nano's ES-DE gamelist has no folder/collection entries) |

Active slots are collected in the declared order and only that subset is laid out, so the grid packs
from the first active slot.

## Layout (FlexboxComponent)

The badges are packed into a grid inside the element `size` box:

- `direction` (`row` default / `column`) - the fill axis. `itemsPerLine` items fill along a **line**
  (a row for `row`, a **column** for `column`); `lines` is the number of lines. So a `row` element
  fills across then wraps down, a `column` element fills **down** then wraps across. nano's item->cell
  map is `col = i % itemsPerLine, row = i / itemsPerLine` for row and the **transpose**
  (`row = i % itemsPerLine, col = i / itemsPerLine`) for column - matching FlexboxComponent's
  y-inner iteration. (A `lines=1` column must stack vertically, not lay every badge in one row.)
- `itemMargin` (normalized pair, `-1` on an axis mirrors the other) - the gap between cells, scaled by
  width/height. The item size `bs` is the largest **square** that fits a cell
  (`min((size.x+m-gx*m)/gx, (size.y+m-gy*m)/gy)`), matching ES-DE's square-aspect `maxItemSize`.
- `horizontalAlignment` (`left` default / `center` / `right`) - because `bs` is square (usually
  height-driven for the typical wide-short badges box), the grid is often **narrower** than the box, so
  ES-DE offsets it two ways and nano applies both:
  1. a **base offset** on every item that repositions the whole grid inside the box:
     `base = width - (bs+margin)*gx + margin`; right shifts by the full `base`, center by `base/2`
     (rounded). Omitting this leaves a center/right cluster stuck at the box's **left** edge.
  2. a **partial-row** offset by the empty columns of the last row, so a non-full row is itself
     center/right-aligned within the grid.

`left` alignment needs neither offset, so a left/row badges element (e.g. art-book-next) is the plain
`x + col*pitch` case.

## The controller overlay

The `controller` badge is a base plate with a **specific controller glyph overlaid** (a GameCube pad,
an arcade stick, ...). ES-DE draws the two with independent tint and scale, and nano matches:

- the base **plate** is tinted with `badgeIconColor` (default white);
- the overlaid **glyph** is tinted with `controllerIconColor`, which **defaults to white** (so the
  glyph keeps its own SVG colours even when a theme recolours the plate via `badgeIconColor`), and is
  scaled to `controllerSize` of the base width, which **defaults to 0.5** (ES-DE
  `FlexboxItem::overlaySize{0.5f}`), not full size.

The glyph SVG is `nano_esde_assets/controllers/<controller>.svg` (the `<controller>` metadata value,
e.g. `gamepad_nintendo_gamecube`), falling back to `unknown.svg`.

## Icons and colour

- `badgeIconColor` / `badgeIconColorEnd` - the plate tint (a gradient with `...End`), default white.
- `customBadgeIcon badge="<slot>"` overrides a slot's default bundled SVG
  (`nano_esde_assets/badges/badge_<slot>.svg`).
- `opacity` multiplies the alpha of both the plate and the overlay.

## Where it lives in nano

- parse: `NanoEsdeTheme.cpp` `elementPropertyMap()` `"badges"` entry (slots, alignment, direction,
  lines/itemsPerLine, itemMargin, badgeIconColor/End, controllerSize, controllerIconColor,
  folderLinkSize/IconColor, customBadgeIcon).
- render: `NanoThemeEngine.cpp` `renderEsde()` `t == "badges"` branch - active-slot collection, the
  FlexboxComponent grid + base/partial offsets, the per-cell base plate and the controller overlay.

## Verifying a change

Badges only draw for the **focused** game's active slots, so force one: edit the ES-DE
`gamelists/<sys>/gamelist.xml` and add e.g. `<favorite>true</favorite>` and
`<controller>gamepad_nintendo_gamecube</controller>` inside the game's `<game>` block (nano reads it via
`esdeMetaFor` and the control reads the same file, so the badge appears on both). Install the theme on
both, focus that game, capture and diff the badge region. Use a theme the control renders detailed
(carbon has right-aligned badges, linear a right-side column) - not slate, which the control's ES-DE
3.4.0-56 renders as a bare list. Restore the gamelist from a backup afterwards. Editing the XML on the
host: use a writable dir (host `/tmp` may block `sed -i`'s rename), then re-`chown u0_a64:u0_a64` after
pushing.
