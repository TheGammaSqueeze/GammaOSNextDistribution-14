# GammaOS Shader Pipeline

GPU post-processing shaders applied by SurfaceFlinger's SkiaRenderEngine to every
rendered frame. Controlled via `persist.gammaos.shader.*` system properties and the
ShaderControl system app (`packages/apps/ShaderControl`).

## Architecture

```
ShaderControl app / QS tile
        │  setprop persist.gammaos.shader.*
        ▼
SkiaRenderEngine::drawLayersInternal()
        │  checks persist.gammaos.shader.enable + .type
        ▼
Gamma*Shader::apply()          ◄── one of the built-in types or custom
        │  builds SkRuntimeEffect, draws src→dst
        ▼
Composited frame on display
```

All shaders run inside SurfaceFlinger (system UID). `Layer.cpp` forces GPU
composition (`forceClientComposition`) when shaders are enabled so that
`drawLayersInternal()` is always called.

## Built-in Shader Types

| Type | File | Description |
|------|------|-------------|
| `crt-simple` | `GammaCrtSimple.cpp` | Scanlines, curvature, vignette. Hardcoded SkSL. |
| `lcd3x` | `GammaLcd3x.cpp` | LCD RGB sub-pixel grid. Hardcoded SkSL. |
| `lcd-shader` | `GammaLcdShader.cpp` | LCD with response-time simulation. Hardcoded SkSL. |
| `blur-fill` | `GammaBlurFill.cpp` | Background blur for non-fullscreen content. Hardcoded SkSL. |
| `custom` | `GammaCustomShader.cpp` | RetroArch .slangp preset transpiler (GLSL → SkSL). |

## Custom Shader Transpiler (`GammaCustomShader.cpp`)

Loads RetroArch-compatible `.slangp` preset files, transpiles each `.slang` pass
from Vulkan GLSL to SkSL, compiles via `SkRuntimeEffect::MakeForShader()`, and
chains them as multi-pass post-processing.

### Pipeline

```
ensurePresetLoaded()
  ├── parseSlangPreset()         parse .slangp key=value
  ├── extractSlangFragment()     strip vertex stage, #include resolution
  ├── transpileToSkSL()          GLSL → SkSL conversion (4700+ lines)
  │     ├── Type renaming        vec4→float4, mat4→float4x4, etc.
  │     ├── Semantic mapping     params.X→X, SourceSize→sourceSize, etc.
  │     ├── texture()→src.eval() sampler2D→shader child
  │     ├── Loop conversion      while/do-while → bounded for loops
  │     ├── Const promotion      non-const globals → const or relocated
  │     ├── Int/float fixing     implicit casts → explicit float()/int()
  │     ├── Parameter inlining   #pragma parameter → const float NAME = VALUE
  │     ├── Varying proxying     vertex outputs → per-function _gp params
  │     └── 25+ other transforms (see shader-transpiler memory notes)
  └── loadAndCompilePreset()     SkRuntimeEffect compile per pass

apply() [called per frame]
  ├── Read .shader_params file   parameter overrides → recompile if changed
  ├── Per-pass loop:
  │     ├── computePassSize()    source/viewport/absolute scaling
  │     ├── SkRuntimeEffectBuilder  bind uniforms + child shaders
  │     └── drawRect()           render pass to intermediate/final surface
  └── Increment frame counter
```

### Preset Location

Shader presets (`.slangp` files) are loaded from paths set via the
`persist.gammaos.shader.custom.preset` system property. FUSE paths are
automatically resolved to their backend equivalents:

- `/sdcard/...` → `/data/media/0/...`
- `/storage/emulated/0/...` → `/data/media/0/...`

The primary source of shader presets is the RetroArch app data directory at
`/data/data/com.retroarch.aarch64/shaders/shaders_slang/`. Users select a
`.slangp` file via DocumentsUI (the system file picker) in the ShaderControl app.

### Shader Categories (from RetroArch)

| Category | Description |
|----------|-------------|
| `crt/` | CRT emulation (zfast-crt, crt-geom, crt-hyllian, crt-lottes, crt-gdv-mini, etc.) |
| `handheld/` | Handheld LCD (gameboy, lcd3x, sameboy-lcd, retro-v3, simpletex_lcd, etc.) |
| `scalefx/` | ScaleFX upscaling variants |
| `xbr/` | xBR pixel-art upscaling |
| `sharpen/` | Adaptive sharpen, fast-sharpen |
| `reshade/` | ReShade ports (FilmGrain, MagicBloom, LUT) |
| `anti-aliasing/` | SMAA, reverse-AA |
| `scanlines/` | Simple scanline overlays |
| `blurs/` | Gaussian, kawase, dual-filter blur |

### Compatibility (as of 2026-03-08)

- CRT shaders: **26/116** compile (22%) — best: zfast-crt, crt-geom-mini, crt-hyllian-3d
- Handheld LCD: **36/80** compile (45%) — best: zfast-lcd, lcd3x, sameboy-lcd, retro-v3, simpletex_lcd
- Most single-pass shaders with simple math work well
- Multi-pass chains work when all passes compile

### SkSL Limitations

These GLSL features cannot be transpiled to SkSL runtime effects:

- `while` / `do-while` loops (converted to bounded `for` with break)
- Bitwise operators (`<<`, `>>`, `|`, `&`)
- `switch`/`case` statements
- `texelFetch` / `texelFetchOffset`
- Multi-dimensional arrays
- Non-const loop bounds
- `sampler2D` function parameters (removed, handled via globals)

### Runtime Parameter Control

Shader parameters from `#pragma parameter` are inlined as `const float` values
during transpilation. The ShaderControl app writes parameter overrides to
`/data/media/0/GammaShader/.shader_params` (one `id=value` per line), which
triggers automatic recompilation with the new values baked in.

Parameter metadata is written to `/data/media/0/GammaShader/.shader_param_meta`
(format: `id|description|initial|min|max|step`) for the ShaderControl app to
build dynamic UI sliders.

## System Properties

| Property | Type | Description |
|----------|------|-------------|
| `persist.gammaos.shader.enable` | bool | Master on/off |
| `persist.gammaos.shader.type` | string | `crt-simple`, `lcd3x`, `lcd-shader`, `blur-fill`, `custom` |
| `persist.gammaos.shader.custom.preset` | string | Path to .slangp file |
| `persist.gammaos.shader.custom.res_scale` | string | Downscale factor: `1/2`, `1/3`, `1/4`, or decimal |
| `persist.gammaos.shader.debug` | bool | Dump SkSL to `.shader_debug_sksl` |
| `persist.gammaos.shader.crt-simple.*` | float | CRT-simple parameters |
| `persist.gammaos.shader.lcd3x.*` | float | LCD3x parameters |
| `persist.gammaos.shader.lcd-shader.*` | float | LCD-shader parameters |
| `persist.gammaos.shader.blur-fill.*` | float | Blur-fill parameters |

## SELinux Policy

SurfaceFlinger needs additional permissions to read shader presets from user
storage and write parameter metadata. Added in `system/sepolicy/private/surfaceflinger.te`
(and mirrored to `prebuilts/api/34.0/` and `prebuilts/api/202404/`):

- Read `/data/media/0/GammaShader/` (media_rw_data_file)
- Write `.shader_param_meta`, `.shader_params`, `.shader_debug_sksl`
- Read RetroArch app data for shader includes (app_data_file)

SurfaceFlinger is also added to the `media_rw` group in `surfaceflinger.rc`.

## Files

```
frameworks/native/libs/renderengine/
├── Android.bp                          # build: adds GammaCustomShader.cpp
└── skia/
    ├── SkiaRenderEngine.cpp            # dispatch: type=="custom" → GammaCustomShader
    └── filters/
        ├── GammaCrtSimple.cpp/.h       # built-in CRT
        ├── GammaLcd3x.cpp/.h          # built-in LCD grid
        ├── GammaLcdShader.cpp/.h       # built-in LCD + response time
        ├── GammaBlurFill.cpp/.h        # built-in background blur
        ├── GammaCustomShader.cpp/.h    # .slangp transpiler + runner
        └── README.md                   # this file

frameworks/native/services/surfaceflinger/
├── Layer.cpp                           # forceClientComposition when shader on
└── surfaceflinger.rc                   # media_rw group

system/sepolicy/private/
└── surfaceflinger.te                   # shader file access rules

packages/apps/ShaderControl/            # system app for shader selection + params
```
