# nanosvg (vendored)

Header-only SVG parser (`nanosvg.h`) + rasterizer (`nanosvgrast.h`) from
github.com/memononen/nanosvg, zlib license. The ES-DE theme engine
(`../NanoThemeEngine.cpp`) uses it to rasterize theme logo/console SVGs to RGBA and
upload them as GL textures (cached). `nanosvg_impl.c` is the single translation unit
that instantiates the implementation; it is built as the static lib `libnanosvg_nano`
with warnings relaxed (the upstream source is not `-Werror` clean), mirroring the
`liba52_nano` / `librcheevos_nano` vendoring pattern.
