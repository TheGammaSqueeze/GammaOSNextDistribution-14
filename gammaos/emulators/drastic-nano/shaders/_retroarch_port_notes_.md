# Porting RetroArch GLSL shaders to the DraStic post-FX format (drastic-nano, RG DS Plus)

You port a fixed list of single-pass RetroArch GLSL shaders to DraStic's `.dfx` + `.dsd` format.
Faithful ports: keep the original math, authors, licences and parameter defaults. Do not
"improve" the look. Do not deploy anything; only write files.

Sources: `/tmp/claude-1000/-work-GammaOSNextDistribution-A14/24a49aed-669c-4b61-ac16-ba06b611f376/scratchpad/ra/data/data/com.retroarch.aarch64/shaders/shaders_glsl/<handheld|crt>/`
(`<preset>.glslp` names the `shader0` file under `shaders/`).
Format reference: `/work/GammaOSNextDistribution-A14/gammaos/emulators/drastic-nano/shaders/_shader_format_.txt`.
Finished reference ports to copy the structure from (read them first):
`/work/GammaOSNextDistribution-A14/gammaos/emulators/drastic-nano/shaders/LCD1x.dfx` + `lcd1x.dsd`,
`LCD3X.dfx` + `lcd3x.dsd`, `LCD1x_NDS.dfx` + `lcd1x_nds.dsd`.
Output directory (write ONLY here): `/tmp/claude-1000/-work-GammaOSNextDistribution-A14/24a49aed-669c-4b61-ac16-ba06b611f376/scratchpad/ports/`

## Files per shader
- `<Name>.dfx`: `<options> name=<Name> textures=1`, the `<fheader>` precision block (copy from
  LCD1x.dfx; add `#extension GL_OES_standard_derivatives : enable` as the FIRST line of the
  fheader if the fragment uses fwidth/dFdx/dFdy), `<texture:0> input=framebuffer` with
  `min_filter`/`mag_filter` = GL_LINEAR when the preset has `filter_linear0 = true`, else
  GL_NEAREST, and one `<pass> shader=<name>.dsd sampler:u_texture=0`.
- `<name>.dsd` (lower-case file name): `<options> filter=nearest|linear </options>` matching the
  above, `<vertex>` and `<fragment>` blocks.
- Name: the RetroArch preset name, keeping its case and hyphens (e.g. `crt-pi`, `zfast-crt`,
  `lcd-grid-v2`, `CRT-beam`, `crt-Guest.r-mini` -> use `crt-Guest-r-mini`), max 31 chars.
  A preset that only sets parameters on a shared shader file is its own port with those
  parameter values baked in.
- Top of the dsd: the original header comment (author, licence) inside the `<!-- -->` of the
  XML prologue exactly as in lcd1x.dsd, plus one line "drastic-nano port: ..." describing any
  deviation (normally just the DS pixel-space rule below).

## Language: GLSL ES 1.00 only
The DraStic runtime compiles plain GLSL ES 1.00. No `#version`, no `in`/`out`, no `texture()`,
no `round()`, no integer bit operations, no `switch`, no `%` on floats (use `mod`), no
`texture2DLod` in fragments, no non-constant array indexing except in `for` loops with
constant bounds. Use `attribute`/`varying`/`texture2D`/`gl_FragColor`. Resolve every
`#if defined(VERTEX)/FRAGMENT`, `COMPAT_*`, `__VERSION__` and `PARAMETER_UNIFORM` branch by
hand into clean code: the vertex part goes in `<vertex>`, the fragment part in `<fragment>`.
Precision: the fheader already declares the default float precision; you may still write
`mediump`/`highp` qualifiers where the original had COMPAT_PRECISION, or drop them.

## Uniform and attribute mapping (DraStic built-ins)
- `VertexCoord` -> `attribute vec2 a_vertex_coordinate;` with
  `gl_Position = vec4(a_vertex_coordinate.xy, 0.0, 1.0);` (drop MVPMatrix, COLOR, COL0).
- `TexCoord` -> `attribute vec2 a_texture_coordinate;`; the varying `TEX0.xy` / `vTexCoord`
  becomes `varying vec2 v_texture_coordinate;` (drop the `* 1.0001` fudge).
- `Texture` / `Source` -> `uniform sampler2D u_texture;` (this exact name, it is the sampler in
  the dfx pass line).
- `TextureSize` and `InputSize` -> `uniform vec4 u_texture_size;` which is
  `(1.0/w, 1.0/h, w, h)` of the input texture, so `TextureSize` = `u_texture_size.zw`,
  `SourceSize` = `vec4(u_texture_size.zw, u_texture_size.xy)`. For the DS core InputSize ==
  TextureSize (no padding), so both map to `u_texture_size.zw` and any
  `TextureSize / InputSize` ratio is 1.0.
- `OutputSize` -> `uniform vec2 u_target_size;` (pixels of the on-screen target rectangle).
- `FrameCount` -> `uniform float u_time;` (seconds), use `floor(u_time * 60.0)` as a float
  frame counter (never an int uniform).
- `OrigInputSize`/`OriginalSize` -> same as TextureSize here (single pass).
- `#pragma parameter NAME "label" default min max step` -> `const float NAME = default;` with
  the label and range in a trailing comment, placed in whichever block uses it (both if both).
  A preset file that overrides a parameter (`NAME = "value"` in the .glslp) uses that value.

## The DS pixel-space rule (the one deliberate deviation, apply it everywhere)
The DS framebuffer texture is 256x192, but under "hi-res 3D" it is 512x384 (the 2D layers are
still 256x192 content scaled 2x). RetroArch users run native size, so any periodic pattern the
shader keys to SOURCE PIXELS (scanline phase, LCD cell/grid phase, aperture mask period,
"which source line am I on", brightness of the current scanline) must be computed from
`v_texture_coordinate * vec2(256.0, 192.0)` (define `const vec2 DS_SCREEN = vec2(256.0, 192.0);`)
instead of `vTexCoord * TextureSize`, so hi-res 3D never doubles the pattern.
Everything that addresses TEXELS (neighbour fetches, bilinear weights, `1.0/TextureSize` offsets,
Quilez-style sharpening of the fetch coordinate) must keep using the real `u_texture_size`.
Read each shader carefully and split its uses of TextureSize/InputSize into those two classes.
Masks keyed to OUTPUT pixels (e.g. `floor(vTexCoord.x * OutputSize.x)` aperture masks) keep
using `u_target_size`; those adapt to the panel by design.

## Quality bar
- Every port must be self-contained and compile as ES 1.00: check every identifier is declared,
  every function is defined before use, every vector constructor has the right component count,
  every `mix`/`clamp`/`pow` gets matching types (`pow(vec3, vec3)` not `pow(vec3, float)`), and
  no leftover `COMPAT_`, `FragColor`, `Texture`, `TextureSize`, `OutputSize`, `FrameCount`,
  `MVPMatrix` identifiers remain (grep your output for them).
- No multi-pass, no lookup textures. If a listed shader turns out to need either, or uses
  something impossible in ES 1.00, skip it and say so in your final report.
- Final report: one line per shader: name, what TextureSize uses you classified as
  texel-addressing vs source-pixel-period, and anything skipped or uncertain. Keep it short.
