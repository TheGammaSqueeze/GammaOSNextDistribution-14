# nano ES-DE parser host test

`NanoEsdeTheme` (the ES-DE theme parser/model) is deliberately GL-free, so it can be
compiled and exercised on a normal host with no device and no Android. `esde_parser_check`
loads a real ES-DE theme set and dumps the resolved gamelist grid / textlist / system
carousel, letting you confirm the theme-resolution layering without a build+flash cycle.

## Build and run

From the repo root:

```sh
c++ -std=c++17 -DESDE_HOST_TEST \
    -I frameworks/base/cmds/gammaos-nano \
    -I frameworks/base/cmds/gammaos-nano/test \
    -I external/tinyxml2 \
    frameworks/base/cmds/gammaos-nano/test/esde_parser_check.cpp \
    frameworks/base/cmds/gammaos-nano/NanoEsdeTheme.cpp \
    external/tinyxml2/tinyxml2.cpp \
    -o /tmp/esde_parser_check

/tmp/esde_parser_check <themeDir> [system] [variant] [colorScheme] [aspect] [screenAspect]
```

`themeDir` is a directory containing `capabilities.xml` and `theme.xml` (an ES-DE theme
set, e.g. an `art-book-next-es-de` checkout). `screenAspect` is width/height; `aspect`
can be `automatic` (nearest declared ratio) or an explicit id like `16:9`.

## What it checks

Pointed at Art Book Next it auto-runs assertions against the theme's own documented
numbers, which together prove the theme-resolution behaviour:

1. **Per-system + `${systemCoverSize}` include substitution** and **comma-listed aspect
   matching**: `snes` on a 4:3 panel resolves its per-system `systemCoverSize=4-3` (from
   the `${system.theme}` include) and the `<aspectRatio name="4:3,5:4">` block, giving the
   grid `itemSize 0.33125 x 0.33125` plus the base grid properties (itemScale 1.2,
   scaleInwards, fractionalRows, unfocusedItemOpacity 0.5, pos.y 0.11667).
2. **Default cover size**: an unknown system falls back to `systemCoverSize=1-1`
   (`itemSize 0.2476562 x 0.3302083`).
3. **Aspect adaptivity**: the grid itemSize changes between 4:3 and 16:9.
4. **colorScheme is not a wildcard**: dark vs light schemes resolve to different textlist
   colours (`ffffff55` vs `444444`), i.e. `custom`/`all` do not leak a palette.

A run that prints `== OK (0 failures) ==` means the parser resolved the theme exactly as
its XML intends. This is a fast regression guard for the theme-resolution code in
`NanoEsdeTheme.cpp`; it does not exercise the GLES2 renderer (`NanoThemeEngine.cpp`), which
is verified on-device against the control unit.
