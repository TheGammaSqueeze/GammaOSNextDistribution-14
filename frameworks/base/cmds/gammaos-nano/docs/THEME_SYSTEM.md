# Theme system

GammaOS Nano's home themes are **render + input + audio skins over one shared state
machine**. They are not separate menus. Understanding this is the key to adding a theme
without regressing the others.

## One model, many skins

All themes read and mutate the same model (see ARCHITECTURE.md "The shared home data
model"):

- `mPs3Cats` (columns) + `mPs3Stack` (drill stack) + `mNdsAtRoot` (root vs drilled).
- Cursors `mPs3CatIdx` / `mPs3ItemIdx`, accessed depth-agnostically through
  `ps3CurItems()` / `ps3CurSel()`.
- The activate/drill verbs `ps3XmbSelect/Back/Left/Right/Up/Down` (NanoMenuPS3Menu.cpp)
  are the single source of truth for mutating the model.

The XMB theme (`renderPs3Xmb`) is the canonical implementation: it owns the model logic,
the special screens (Wi-Fi/BT setup, photo/icon grid, media players, global search,
timezone globe), and the shared per-frame lifecycle ticks. DSi and Minima **reuse all of
that** and only swap the home look, navigation feel, sounds, and boot intro.

## Theme selection

`persist.gammaos.nano.ndstheme` is the selector, read once at startup in `threadLoop()`:

| Value | Theme | Flag |
| --- | --- | --- |
| `0` | GammaOS XMB (default) | `mPs3Xmb && !mNdsTheme && !mMinimaTheme` |
| `1` | DSi Menu | `mNdsTheme` |
| `2` | Minima | `mMinimaTheme` |
| `3` | ES-DE engine | `mEsdeTheme` (see THEME_ENGINE.md) |

Two subtleties that are load-bearing:

- **`mPs3Xmb` means "the XMB home infrastructure is in use", not "the XMB theme is
  active".** DSi, Minima, and ES-DE all force `mPs3Xmb = true` so they inherit the XMB
  boot/input/overlay/OSK/modal stack. The true XMB theme is
  `mPs3Xmb && !mNdsTheme && !mMinimaTheme && !mEsdeTheme`.
- **Priority is resolved before any dispatch** (Minima wins over DSi). Resolve the new
  theme's flag in the same block so exactly one home flag is set.

The theme can also be switched live from Settings ("Home Theme"), which is a normal
`kPs3Bindings` row whose `closePs3Dialog` hook flips the flags in memory, ensures the
theme's assets are loaded, and resets navigation to the home root. A new theme extends
that one hook; it must fully reset its own animation/scroll state on switch so it does
not corrupt the other themes' shared state.

## The theme surface (what a theme owns)

Each theme provides the same ~11 responsibilities. Today these are expressed as
`if (mNdsTheme) ... else if (mMinimaTheme) ... else <XMB>` chains in `render()`
(NanoMenuRender.cpp) and the input handlers (NanoMenuInput.cpp, NanoMenuXmb.cpp). A new
theme adds one branch to each; the ES-DE engine follows this exactly.

| # | Responsibility | XMB | DSi | Minima |
| --- | --- | --- | --- | --- |
| 1 | Home render (primary) | `renderPs3Xmb` | `renderNds` (`renderNdsTop`+`renderNdsCarousel`) | `renderMinima` (`renderMinimaList`) |
| 2 | Secondary (bottom) render | shared wave/clock else-branch | `renderNdsCarousel` (interactive) | `renderMinimaSecondary` (passive) |
| 3 | Boot/intro overlay | `renderPs3BootOverlay` | `renderNdsBootOverlay` | `renderMinimaBootOverlay` (DSi render, inverted palette) |
| 4 | Option menu / side panel | `renderXmbOpt` | `renderNdsSidePanel` | `renderMinimaSidePanel` |
| 5 | Dialog / message box | `renderPs3Dialog` | `renderNdsDialog` | `renderMinimaDialog` |
| 6 | Info page (game/app) | XMB dialog game-info path | `renderNdsInfoPage` | `renderMinimaInfoPage` |
| 7 | Picker list (timezone/language) | native list / globe | `renderNdsPickerList` | falls back to XMB |
| 8 | Nav Up/Down/Left/Right/A/B/L/R | `ps3Xmb*` | `ndsNav*` (D-pad navigates, touch launches) | `ndsNavHoriz` (A launches, L/R page by 6) |
| 9 | Touch hit-testing | `xmbTouchFrame` | `ndsTouchFrame`/`ndsSubmenuTouch`+modal touch | `minimaListTouch`+modal touch |
| 10 | SFX | `ps3Sfx` (per nav callsite) | `ndsSfxTick` (per-frame state diff) | `minimaSfxTick` (per-frame state diff) |
| 11 | Ambiance / BGM | `ps3EarlyAudioTick` | `ndsAmbianceTick` (opt-in) | none |

The boot **clock** (`ps3BootUpdate`) is shared and stays in the base; only the boot
**render** is per-theme. Classification helpers used by both non-XMB themes
(`ndsInModal`, `ndsDlgIsSidePanel`, `ndsCurLevelIsList`, `ndsGameInfoActive`,
`ndsFocusSel/Count/Depth`) are shared and belong in the base, not in a theme.

## Shared vs theme-private state

- **Shared** (never put in a theme): `mPs3Cats`, `mPs3Stack`, `mNdsAtRoot`, cursors, all
  modal state that `ndsInModal()` aggregates, the launch handshake, `scrapeEntryFor`,
  and the per-frame lifecycle ticks (`scraperArtTick`, `appInfoTick`, `fbTick`, `feTick`,
  `nsTick`, `gsAutoAddTick`, ...). DSi/Minima call these ticks explicitly because
  `renderPs3Xmb` (which normally runs them) is skipped for their home. **A new theme
  must pump these ticks too**, or App Info / File Explorer / bulk-add hang on "Loading...".
- **DSi-private**: carousel camera, stack mode, cat-cards cache (invalidated on rebuild),
  fling/scrub, drill fade, font preference.
- **Minima-private**: eased pill glide, eased scroll, sfx state snapshots; forces flat
  text; reads the solid-background prop.

## Fallback matrix

DSi and Minima deliberately fall back to XMB chrome where they have no themed equivalent:
media players, the summoned PSP slide clock, the Wi-Fi/BT setup wizard, global search,
the color picker, and the gamepad test/calibration screens (all shared XMB overlays).
Minima additionally skins its own side panel, dialog, info page, search results, slider
HUD, and scrape-progress modal; it falls back for the timezone/language pickers and the
photo grid. A new theme should reuse the same fallback, implementing only the surfaces
that materially differ, so it inherits every special screen for free.

## Adding a theme (the safe pattern)

1. Add the flag (`mEsdeTheme`) and read it in the one-time theme block; force
   `mPs3Xmb = true`; resolve priority so exactly one home flag is set.
2. Extend the "Home Theme" binding options and its `closePs3Dialog` hook (flip flag,
   ensure assets, reset to root).
3. Add one branch per responsibility alongside the DSi/Minima branches in `render()`
   (primary + secondary) and the input handlers. Pump the shared lifecycle ticks in the
   home branch (mirror the Minima branch).
4. Reuse the fallback matrix for everything you do not skin.
5. Respect the performance invariants (ARCHITECTURE.md): no per-frame `property_get`, no
   per-frame model rebuild, bounded art cache, exact secondary-pass `mWidth/mHeight`
   save/restore.

The ES-DE engine (THEME_ENGINE.md) is the first theme added this way, and is the model
for a future clean `activeTheme()`/`INanoTheme` refactor: the built-ins are left
untouched (byte-identical) while the new theme is a self-contained module.

## Regression traps (from mapping the built-ins)

- Do not remove the `mPs3Xmb = true` force for non-XMB themes; their boot/OSK/modals
  break without it.
- Nav semantics differ per theme (DSi buttons never launch; Minima A launches; DSi vs
  Minima axis remap; per-theme hold-repeat cadence). Keep them distinct.
- `renderMinimaBootOverlay` is `renderNdsBootOverlay` with a palette-inversion flag; the
  two boot animations are meant to stay identical except color.
- The Home-Theme switch must fully reset the incoming theme's private state.
- Theme is latched at startup for asset/model coherence; live switching is only safe
  through the settings hook that re-ensures assets and resets to root.
