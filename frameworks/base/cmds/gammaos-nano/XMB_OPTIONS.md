# Home XMB option menu + Wallpaper picker

Ported from the web XMB (`/work/ps3/xmb-app/index.html`, the `optMenu` context
sidebar). Replaces the old X/Y "cycle the wallpaper" behaviour with the real PS3
paradigm: Triangle opens a per-item option list, and the wallpaper changer becomes
its own side menu under Theme Settings.

## Button remap (NanoMenuInput.cpp, pollInput)

On a Nintendo-layout pad the XMB decodes face buttons by position: `BTN_NORTH` is
the TOP button (X on a Nintendo pad, the PS3 Triangle position) and `BTN_WEST` is
the LEFT button (Y / PS3 Square). Both used to cycle the procedural wallpaper effect
inline.

- `BTN_NORTH` (X / Triangle): on the home XMB now toggles the option menu
  (`openXmbOpt` / `closeXmbOpt`). The music player keeps its own Triangle = control
  panel. The `tri` nav hook mirrors this (music panel, else home option menu).
- `BTN_WEST` (Y / Square): the home-column wallpaper cycle is removed; Y is otherwise
  unused on the home column now (search OSK in legacy xmb_mode is kept).
- The wallpaper cycle is gone from every state, including the in-game overlay (X no
  longer cycles FX there either).

## Option menu (NanoMenuPS3Menu.cpp, NanoMenu.h mPs3Opt*)

A self-contained modal (separate from the theme chooser `mPs3Dlg*` so an action can
open a dialog without colliding). `openXmbOpt` snapshots the focused item and builds a
context row list; `renderXmbOpt` draws the same black-scrim right-side panel as the
theme / performance choosers (renderPs3Dialog kind 1: a 50%-black left edge fading to
transparent at the screen's right edge, over a frosted-glass backdrop) for visual
consistency; navigation is Up/Down = move, Cross = activate, Circle/Left = dismiss.

Per-item rows (all real actions, dispatched in `xmbOptAction`):

- Game (`PS3_ROM` / `PS3_RECENT`) and App (`PS3_APP` / `PS3_LAUNCH_PKG`):
  `Start` (overlay-aware launch, same path as Cross) + `Information`.
- Music album (`PS3_MUSIC_ALBUM`): `Play` (the album's tracks) + `Information`.
- Music track (`PS3_MUSIC_TRACK`): `Play` (the surrounding list at the row) + `Information`.
- Music playlist (`PS3_MUSIC_PLAYLIST`): `Play` + `Information`.
- Anything else (settings / data leaves): `Information` (shows the item description).

`Information` opens a kind-0 fullscreen dialog with the item's label + description. The
primary action row draws a `START` pill. Gated to the home column - it self-guards off
when another modal owns input or over a live overlay app
(`mOverlayMode && !mOverlayWallpaper`).

Submenus (the web's Sort By / Group Content) are intentionally not ported: nano's Music
column is a flat album list, not a groupable content grid, so there is nothing to sort
or group yet.

## Wallpaper picker (Settings > Theme Settings > Wallpaper)

A new `Wallpaper` leaf in `kThemeSettingsCh` opens the standard side-panel chooser
(`mPs3DlgKind == 1`, theme key 11). Options are the enabled procedural effects
(`kEffectNames` over `kActiveEffects`); the highlighted effect is live-previewed while
scrolling (`previewThemeSetting` case 11 sets `sActiveEffectIdx` / `mCurrentEffect` +
`initEffects` for particle effects), committed and persisted to
`persist.gammaos.nano.wallpaper` on Cross (`applyThemeSetting` case 11), reverted on
Circle. The row's inline value (`resolvePs3ItemValue`) shows the current effect name.
This is the same effect catalog the old X/Y cycle walked, now a selectable list.
