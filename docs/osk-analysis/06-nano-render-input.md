# Nano Render + Input Primitives, and the Current-UI-Language Source

This document inventories the native GammaOS "Nano" rendering and input primitives that a 1:1
Android TV Leanback on-screen keyboard (OSK) reimplementation can build on, plus the single
source of truth for the active UI language. All paths are absolute. Line numbers refer to the
state of the files at the time of writing (branch `develop`).

Files covered:

- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuRender.cpp`
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenu.h`
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuShaders.h`
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuShaders.cpp`
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/xmb_icons.h`
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuInput.cpp`
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuStrings.cpp`
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuStrings.h`
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuUtils.h`
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuXmb.cpp` (existing OSK)
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuSetupWizard.cpp` (language picker)
- `/work/GammaOSNextDistribution-A14/frameworks/base/cmds/gammaos-nano/NanoMenuSettings.cpp` (password OSK)

---

## 1. Drawing primitive signatures and semantics

All four primitives are public-ish member functions of `class NanoMenu` (declared in `NanoMenu.h`,
defined in `NanoMenuRender.cpp`). They all share the same pixel-to-NDC convention and rotation
handling. There are exactly two GL primitives: a flat-color quad (`drawQuad`) and a textured quad
(`drawIcon` for icon atlas textures, plus the internal text batcher in `drawText`). There is no
rounded rect, ninepatch, gradient, or arbitrary textured-quad helper.

### Coordinate space (shared)

Pixel coordinates are in surface pixels with origin at the TOP-LEFT, x to the right, y DOWNWARD.
`mWidth` and `mHeight` are the LOGICAL surface dimensions (members of `NanoMenu`, declared in
`NanoMenu.h:427-428`). Each primitive converts a pixel rect to OpenGL clip-space NDC
(`-1..+1`, y up) like this (from `drawQuad`, `NanoMenuRender.cpp:309-312`):

```
float x0 = (x / mWidth) * 2.0f - 1.0f;
float y0 = 1.0f - ((y + h) / mHeight) * 2.0f;
float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;
float y1 = 1.0f - (y / mHeight) * 2.0f;
```

So an OSK laid out in the same pixel coordinate system used elsewhere in Nano (top-left origin,
y down) will compose correctly. Always use `mWidth`/`mHeight` for layout math, NOT the AHB
panel-native dimensions; the rotation is handled entirely in the vertex shader (see below).

### Rotation handling (shared, automatic)

Every shader program multiplies vertex positions by a global `mat2 uRotation` uniform. The
vertex shaders are in `NanoMenuShaders.cpp`:

- Flat quad (`VERTEX_SHADER`): `gl_Position = vec4(uRotation * aPosition.xy, aPosition.zw);`
- Text/icon (`TEXT_VERTEX_SHADER`): `gl_Position = vec4(uRotation * aPosition, 0.0, 1.0);`

The matrix is the global `sDrmRotMat[4]` (declared `NanoMenuDrm.h:157`), uploaded to all five
programs once per frame in `render()` via the `uploadRotationMatrices` lambda
(`NanoMenuRender.cpp:844-853`). Callers of `drawQuad`/`drawText`/`drawIcon` therefore do NOT
deal with rotation at all; they emit logical-landscape pixel coordinates and the panel rotation
(0/90/180/270, `sDrmRotationDeg`, `NanoMenuDrm.h:129`) is applied transparently. The one place a
caller must remap by rotation is `glScissor` clipping (because the scissor box is in post-rotation
FBO pixels), handled in `NanoMenuRender.cpp:1188-1208`. An OSK that does not need scissor clipping
can ignore rotation entirely.

### `drawQuad` (flat filled rectangle)

Declared `NanoMenu.h:406-407`, defined `NanoMenuRender.cpp:307-325`.

```
void NanoMenu::drawQuad(float x, float y, float w, float h,
                        float r, float g, float b, float a);
```

- `x,y` = top-left corner in surface pixels; `w,h` = width/height in pixels.
- `r,g,b,a` = color in 0..1 float range; `a` is alpha (premultiplied-over blending is set up by
  the caller with `glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` -- see
  `render()` at `NanoMenuRender.cpp:975-976`. Blending is enabled for the whole menu render pass).
- Uses `mShaderProgram` (the flat-color program), uploads `uColor` via `glUniform4f(mLocColor,...)`,
  emits 6 vertices (2 triangles) from a client-memory pointer.
- IMPORTANT detail: it calls `glBindBuffer(GL_ARRAY_BUFFER, 0)` first to unbind any VBO left bound
  by `DrasticRunner::drawDsQuad`, otherwise the client pointer is misread as a VBO byte offset
  (`NanoMenuRender.cpp:314-318`). Any new primitive added must do the same unbind.

This is THE rectangle primitive used everywhere (selection bars, OSK background, key cells,
separators). The existing OSK draws each key cell as a `drawQuad` highlight (see section 2 / the
existing OSK at `NanoMenuXmb.cpp:1610-1614`).

### `drawText` (UTF-8 text, batched, with drop-shadow/outline)

Declared `NanoMenu.h:402-403`, defined `NanoMenuRender.cpp:555-677`.

```
void NanoMenu::drawText(const char* str, float px, float py, float scale,
                        float r, float g, float b, float a);
```

- `px,py` = top-left anchor of the text box in surface pixels. The glyph baseline is computed as
  `baseline = py + mFontSize * pixelScale * 0.8f` (`NanoMenuRender.cpp:560`), i.e. `py` is the top
  of the text and text descends from there. The drawn glyph cell height is roughly
  `FONT_CHAR_H * scale` pixels (see metrics below).
- `scale` = a unitless size multiplier. Mapping to pixels (`NanoMenuRender.cpp:558`):
  `pixelScale = (FONT_CHAR_H * scale) / (float)mFontSize` where `FONT_CHAR_H = 16`
  (`NanoMenuShaders.h:65`) and `mFontSize = 48` (the FreeType render pixel size,
  `NanoMenuRender.cpp:353`). So `pixelScale = 16*scale/48 = scale/3`. A glyph rendered at 48px in
  the atlas is drawn at `48 * pixelScale = 16*scale` pixels tall. Practical line heights used in
  the codebase: `FONT_CHAR_H * scale` is treated as the nominal line height (e.g.
  `NanoMenuRender.cpp:1028-1031` computes `itemH = FONT_CHAR_H * menuScale`). Typical menu scales
  range from `1.5f` (footer) to `4.0f` (title); the existing OSK uses `oskScale = 2.5f * sf`.
- `r,g,b,a` = text color and alpha (0..1). For color-emoji glyphs the RGB is forced to white in
  the atlas, so the per-glyph color is overridden to 1,1,1 for emoji (`NanoMenuRender.cpp:647-649`)
  and the passed color only applies to monochrome glyphs.
- Shadow/outline: in XMB mode (`mXmbMode==true`) it emits one drop-shadow pass at offset (+off,+off)
  plus the main pass; in non-XMB mode it emits a 4-direction outline (left/right/up/down) plus the
  main pass (`NanoMenuRender.cpp:616-643`). Shadow alpha is `a * 0.8f`, shadow color black. This is
  automatic and free to the caller. Capacity is `TEXT_MAX_CHARS = 256` glyphs per call
  (`NanoMenuRender.cpp:522`); longer strings are truncated. The whole call is one `glDrawArrays`.
- Program used: `mTextProgram` (texture * vertex color), atlas texture `mGlyphAtlasTex` bound to
  unit 0.

### `measureText` (text width in pixels)

Declared `NanoMenu.h:404`, defined `NanoMenuRender.cpp:500-519`.

```
float NanoMenu::measureText(const char* str, float scale);
```

- Returns the advance-summed width in surface pixels for the same `scale` semantics as `drawText`
  (`pixelScale = FONT_CHAR_H * scale / mFontSize`). Sums `glyph.advance * glyph.scaleW * pixelScale`
  over the UTF-8-decoded codepoints, calling `ensureGlyph` to load missing glyphs first. There is no
  `measureTextHeight`; height is computed by callers as `FONT_CHAR_H * scale` (nominal) or
  `mFontSize * pixelScale` (atlas glyph box).
- This is the function to use for centering keys, sizing the candidate strip, right-aligning labels,
  etc. The existing OSK centers its grid using fixed `kOskCols * charW` math instead of measuring,
  because the search grid is monospaced-by-cell (see section 2).

### `drawIcon` (textured monochrome/colored icon quad)

Declared `NanoMenu.h:671-672`, defined `NanoMenuRender.cpp:267-301`.

```
void NanoMenu::drawIcon(int iconIdx, float x, float y, float size,
                        float r, float g, float b, float a);
```

- `iconIdx` = index 0..17 into `mIconTextures[18]` (`NanoMenu.h:673`). Out-of-range or
  unloaded index is a silent no-op (`NanoMenuRender.cpp:269`).
- `x,y` = top-left in surface pixels; `size` = square edge length in pixels (icons are square,
  drawn `size x size`).
- `r,g,b,a` = tint multiplied against the texture. Monochrome icons are stored as white+alpha so
  the tint sets the visible color; colored icons (only PICO-8, index 14) keep their RGB.
- Reuses `mTextProgram` (same texture * vertex-color shader as text), binds `mIconTextures[iconIdx]`.
- Same `glBindBuffer(GL_ARRAY_BUFFER, 0)` unbind guard as `drawQuad`.

Base metrics summary: `FONT_CHAR_W = 8`, `FONT_CHAR_H = 16` (`NanoMenuShaders.h:64-65`). These are
NOT a bitmap font cell; they are layout constants. With FreeType `mFontSize = 48`, the effective
pixel scale is `scale/3`, and a column-width estimate `FONT_CHAR_W * scale` (8*scale) is used by
callers for fixed-prefix layout (e.g. `charW = FONT_CHAR_W * menuScale` at
`NanoMenuRender.cpp:1073`). Real proportional widths come from `measureText`.

---

## 2. Filled rectangles and the absence of rounded-rect / textured-quad helpers

- Filled rectangles: use `drawQuad` (section 1). It is the only fill primitive. Color and alpha are
  per-call; there is no per-vertex gradient and no stroke/border. A border is faked by drawing a
  slightly larger quad behind a smaller one (the existing selection highlight does exactly this: a
  blue quad behind the key glyph -- `NanoMenuXmb.cpp:1610-1614`).
- Rounded rect: DOES NOT EXIST. A grep for `rounded|ninepatch|roundrect|drawTextured|drawRect`
  across all `*.cpp`/`*.h` returns only comment text (`NanoMenuNet.cpp:347` mentions a "rounded
  square" but draws it with plain filled quads, not a rounded-rect primitive). There is no SDF,
  no corner-radius shader, no border-radius support anywhere.
- Ninepatch: DOES NOT EXIST.
- Textured quad (general): the only texture sampling paths are `drawIcon` (icon atlas) and the
  `drawText` batcher (glyph atlas). Both go through `mTextProgram`. There is no caller-facing
  "draw this texture at this rect" helper beyond `drawIcon`.

Recommendation for "rounded key" look without writing a new shader:

1. Plain rectangle keys via `drawQuad` (simplest, matches the existing search OSK and the language
   picker rows). This is what the current code does and it is visually consistent with the rest of
   Nano.
2. Pseudo-rounded by compositing: draw the key fill as a central full-width/height `drawQuad` plus
   two slightly-inset `drawQuad`s top and bottom (a "plus" / capsule silhouette). Cheap, no shader
   change, approximates rounded corners at small radius.
3. True rounded corners require either a new fragment shader (corner-radius/SDF, add a
   `mRoundProgram` alongside the existing programs in `initShaders`) or a small pre-rendered
   ninepatch-style RGBA texture decoded via `loadPngFromMemory` and sampled with `drawIcon`-style
   UVs (would need a new draw helper that accepts custom UVs, since `drawIcon` hardcodes 0..1 UVs).

For a 1:1 Leanback OSK clone, option 1 (flat quads) is the pragmatic match; Leanback's actual key
backgrounds are flat rounded rects with a focus ripple, which at this resolution read fine as flat
quads with a brighter focus quad behind the focused key.

---

## 3. The icon system, and how to add OSK special-key glyphs

### How icons are stored and indexed

- `xmb_icons.h` is auto-generated and contains, per system, a `static const uint8_t kIconPng_<name>[]`
  byte array holding a full PNG file plus a `static const int kIconPngSize_<name>` length. Example:
  `kIconPng_nes` / `kIconPngSize_nes = 1280` (`xmb_icons.h:8` / `:91`).
- These are gathered into `struct EmbeddedIcon { const uint8_t* data; int size; }`
  (`xmb_icons.h:4483-4486`) and an array `static const EmbeddedIcon kEmbeddedIcons[]`
  (`xmb_icons.h:4488-4507`) with 18 entries (indices 0..17). The order is fixed:
  0=nes,1=snes,2=gb,3=gbc,4=gba,5=n64,6=nds,7=genesis,8=sms,9=gg,10=psx,11=psp,12=dreamcast,
  13=ngp,14=pico8,15=history,16=game,17=setting.
- At runtime `initIconTextures()` (`NanoMenuRender.cpp:243-265`) loops 0..17 and for each: first
  tries to load a high-res PNG file from `/data/system/nano_icons/<name>.png`
  (`kIconPngDir`, `NanoMenuRender.cpp:96`; filenames in `kIconPngNames[18]`,
  `NanoMenuRender.cpp:75-94`), and if that fails falls back to the embedded
  `kEmbeddedIcons[i].data/.size`. Decoding goes through libpng: `loadPngAsAlphaTexture` (file,
  `NanoMenuRender.cpp:155-194`) and `loadPngFromMemory` (memory, `NanoMenuRender.cpp:210-241`),
  both calling `decodePngToRGBA` (`NanoMenuRender.cpp:101-137`) and uploading via
  `createIconTexture` (`NanoMenuRender.cpp:140-152`, mipmapped, CLAMP_TO_EDGE). `monoWhite=true`
  forces RGB to white and keeps alpha (so the icon is tintable); index 14 (PICO-8) passes
  `monoWhite=false` to keep its colors.
- So `drawIcon(idx,...)` selects a texture purely by integer index into the 18-slot
  `mIconTextures` array. There is NO name lookup at draw time and NO enum; the indices are
  positional and shared between `kIconPngNames`, `kEmbeddedIcons`, and the XMB system table.

### How to add a NEW icon (shift, delete, space, symbols, mic, globe)

Three viable mechanisms, in increasing effort:

1. Draw a Unicode glyph instead of an icon (RECOMMENDED for the OSK). Because `drawText` can render
   any codepoint that exists in the loaded faces (see section 4), special keys can be drawn as text
   symbols with zero asset work. Good candidates that exist in Roboto/DroidSans/Noto:
   - Shift: up arrow U+2191 "↑" or U+21E7 "⇧" (Noto), or just the label "ABC"/"abc" (the existing
     OSK uses "[ABC]"/"[abc]" text, `NanoMenuXmb.cpp:1629`).
   - Backspace/Delete: U+232B "⌫" or U+2190 "←", or the label "DEL".
   - Enter/Submit: U+21B5 "↵" or U+23CE "⏎", or "OK"/"Enter".
   - Space: a wide blank key labeled "space" / "SPACE" (the existing OSK already maps ' ' and
     renders it as '_', `NanoMenuXmb.cpp:1619`).
   - Symbols/123: literal labels "?!#" / "123" / "ABC".
   - Globe / language switch: U+1F310 (color emoji face supplies it) or the label "ABC/中" etc., or
     just text of the next language code.
   - Mic: U+1F3A4 (emoji) if voice is ever wired; otherwise omit (Nano has no voice input path).
   This is the simplest path and keeps everything in one render call per key. Use `measureText` to
   center the symbol in the key.

2. Add an embedded PNG to `xmb_icons.h` and a slot in `kEmbeddedIcons`. You would: append a new
   `kIconPng_shift[]` + `kIconPngSize_shift`, add `{ kIconPng_shift, kIconPngSize_shift }` to
   `kEmbeddedIcons`, bump the hardcoded `18` in three places (`mIconTextures[18]` `NanoMenu.h:673`,
   the loop bound `i < 18` `NanoMenuRender.cpp:246`, and the range guard `iconIdx >= 18`
   `NanoMenuRender.cpp:269`), and add a matching `nullptr`/filename to `kIconPngNames[18]`
   (`NanoMenuRender.cpp:75-94`). More invasive; only worth it if you want pixel-perfect Leanback
   key art.

3. Runtime-decode a PNG from disk with `loadPngAsAlphaTexture` into a fresh `GLuint` you manage
   yourself, then draw it with a new custom-UV variant of `drawIcon`. Most flexible, most code.

Recommendation: use mechanism 1 (Unicode/text glyphs via `drawText`) for all OSK special keys. It
requires no new assets, no array-size edits, and renders through the already-validated text path
with correct tinting and shadow. Reserve mechanism 2/3 only if a designer insists on exact Leanback
key bitmaps.

---

## 4. UTF-8 handling and multi-script faces

### UTF-8 decode is confirmed in both measureText and drawText

`drawText` decodes UTF-8 to codepoints inline (`NanoMenuRender.cpp:571-578`):

```
uint8_t b0 = (uint8_t)*p;
if (b0 < 0x80) { cp = b0; p++; }
else if ((b0 & 0xE0) == 0xC0) { cp = ((b0 & 0x1F) << 6) | (p[1] & 0x3F); p += 2; }
else if ((b0 & 0xF0) == 0xE0) { cp = ((b0 & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F); p += 3; }
else if ((b0 & 0xF8) == 0xF0) { cp = ((b0 & 0x07) << 18) | ((p[1] & 0x3F) << 12) | ((p[2] & 0x3F) << 6) | (p[3] & 0x3F); p += 4; }
else { p++; continue; }
```

`measureText` uses the identical decoder (`NanoMenuRender.cpp:505-511`). So any UTF-8 std::string
buffer (1-4 byte sequences, including BMP and astral/emoji codepoints) renders and measures
correctly. The OSK typing into a plain `std::string` can append multibyte UTF-8 sequences directly
(the existing search OSK only appends single ASCII `char`s via `oskType(char)`, but the renderer
itself is fully UTF-8 capable -- a new OSK can append whole multibyte sequences).

### Faces loaded (arbitrary codepoints render)

`initFonts()` (`NanoMenuRender.cpp:331-374`) loads up to `MAX_FT_FACES = 6` faces
(`NanoMenu.h:686`) in this fallback order (`NanoMenuRender.cpp:337-343`):

1. `/system/fonts/Roboto-Regular.ttf` (Latin, Cyrillic, Greek)
2. `/system/fonts/DroidSans.ttf` (Latin fallback)
3. `/system/fonts/NotoSansCJK-Regular.ttc` (Chinese, Japanese, Korean)
4. `/system/fonts/NotoNaskhArabic-Regular.ttf` (Arabic)
5. `/system/fonts/NotoColorEmoji.ttf` (color emoji, BGRA strikes)

`ensureGlyph(cp)` (`NanoMenuRender.cpp:381-494`) walks the faces in order and uses the first face
whose `FT_Get_Char_Index(face, cp) != 0`; if none has it, it falls back to '?' in face 0
(`NanoMenuRender.cpp:396-401`). Glyphs are rendered once into a 2048x2048 RGBA atlas
(`mGlyphAtlasTex`, `NanoMenuRender.cpp:360-374`) with a simple row packer and cached in
`std::unordered_map<uint32_t, GlyphInfo> mGlyphCache` (`NanoMenu.h:694`). Color-emoji faces select
the nearest fixed strike (`NanoMenuRender.cpp:405-412`) and store a normalization scale so the emoji
draws at `mFontSize` square.

Net result: Latin, Cyrillic (Russian), CJK (Chinese/Japanese/Korean), Arabic, and emoji codepoints
all render from `drawText` with no extra setup. This directly supports a localized OSK (e.g. a
Cyrillic layout, a Japanese kana layout, Arabic letters as key faces) and localized hint/label text.

### RTL caveat (important for Arabic)

`drawText` lays glyphs strictly LEFT TO RIGHT, advancing `curX` by each glyph's horizontal advance
(`NanoMenuRender.cpp:602`). It does NOT do:

- BiDi reordering (Unicode UAX #9). An Arabic string stored in logical order will be displayed in
  logical (left-to-right) visual order, i.e. reversed and not right-aligned.
- Arabic contextual shaping / joining. FreeType is loaded WITHOUT HarfBuzz; `ensureGlyph` looks up
  the bare codepoint via `FT_Get_Char_Index`, so it gets the isolated/nominal glyph form, not the
  initial/medial/final joined forms. Arabic will render as disconnected isolated letters.

Implications for the OSK:

- Rendering Arabic LABELS / hint text 1:1 like Android Leanback is NOT achievable with the current
  renderer without adding shaping (HarfBuzz) and a BiDi pass. The strings table already stores
  Arabic UI strings (`NanoMenuStrings.cpp:40`, `:62`) but they will display unshaped/unreordered.
- For the OSK KEY FACES themselves this matters less: individual key glyphs are single isolated
  characters, which is exactly what FreeType returns, so an Arabic key grid showing isolated letters
  is acceptable (and arguably correct for a per-key display). The TYPED BUFFER, however, will show
  the Arabic text unshaped and in visual-logical order.
- Recommendation: ship the OSK key grid and Latin/Cyrillic/CJK text as-is. Treat Arabic typed-text
  shaping/RTL as a known limitation (flag it as a risk). If true Arabic support is required, add a
  shaping+BiDi step before handing strings to `drawText`; this is a renderer-wide change, not OSK
  specific.

---

## 5. The input model and the cleanest OSK hook point

### Button identifiers

Input is read directly from `/dev/input/event*` evdev nodes (no Android InputReader/InputConnection).
Devices are opened in `openInputDevices()` (`NanoMenuInput.cpp:58-95`) with optional `EVIOCGRAB`
exclusive grab gated by `persist.gammaos.nano.grab_input`. The poll loop is `pollInput()`
(`NanoMenuInput.cpp:658-949`), which `read()`s `struct input_event` from each fd and dispatches by
`ev.type`/`ev.code`/`ev.value`.

The Linux key/button codes actually handled (all from `<linux/input.h>`):

- D-pad as keys: `KEY_UP`, `KEY_DOWN`, `KEY_LEFT`, `KEY_RIGHT`.
- D-pad as HAT axes (`EV_ABS`): `ABS_HAT0X` (left/right), `ABS_HAT0Y` (up/down)
  (`NanoMenuInput.cpp:902-913`). HAT emits value -1/0/+1.
- Left analog stick (`EV_ABS`): `ABS_X`, `ABS_Y`, signed range -32768..32767 with a 90% deadzone
  threshold of `29490` (`NanoMenuInput.cpp:914-941`).
- Face buttons (Nintendo physical layout): `BTN_SOUTH` = A/confirm, `BTN_EAST` = B/back,
  `BTN_NORTH` = X (comment "Nintendo layout: BTN_NORTH = X", `NanoMenuInput.cpp:856`),
  `BTN_WEST` = Y (comment "Nintendo layout: BTN_WEST = Y", `NanoMenuInput.cpp:834`).
- Shoulders: `BTN_TL`/`KEY_L` = L1, `BTN_TR`/`KEY_R` = R1 (`NanoMenuInput.cpp:869`, `:890`).
- `BTN_START` = Start (OSK submit / setup start, `NanoMenuInput.cpp:817`, `:795`).
- `BTN_SELECT` = Select (`NanoMenuInput.cpp:675`; held-modifier + XMB rescan).
- `KEY_ENTER` = also confirm/submit; `KEY_BACK` = also back.
- Volume/power: `KEY_VOLUMEUP`, `KEY_VOLUMEDOWN` (volume, or brightness when Select held),
  `KEY_POWER` (sleep/shutdown, special inline loop).
- `ev.value`: 1 = key down, 2 = autorepeat, 0 = key up. Most menu actions fire only on `ev.value == 1`
  (initial press; `NanoMenuInput.cpp:778`). Directional keys route through `navPress`/`navRelease`
  for hold-to-repeat.

### How a frame of input is delivered

`pollInput()` is called once per render-loop iteration (from `threadLoop`). It drains ALL pending
events from every fd non-blocking, dispatching synchronously as it reads. There is no queued
"input frame" object; handlers mutate `NanoMenu` member state directly (e.g. `mOskCursorX`,
`mOskQuery`). After draining, it calls `tickNavRepeat()` (`NanoMenuInput.cpp:948`) which fires
accelerating `handleUp/Down/Left/Right` repeats while a direction is held
(`kNavInitialDelayMs=350`, `kNavInitialIntervalMs=180`, `kNavMinIntervalMs=45`,
`kNavAccelStepMs=10`, `NanoMenuInput.cpp:590-593`).

### Existing OSK dispatch (the model to extend)

The current search OSK is already wired into `pollInput` and the `handle*` functions, and it is the
template for the new OSK:

- `mOskActive` (bool, `NanoMenu.h:676`) gates OSK input at the TOP of each handler:
  - `handleSelect()` (`NanoMenuInput.cpp:291-297`): if `mOskActive`, reads
    `kOskLayout[mOskCursorY][mOskCursorX]`, lowercases if `!mOskShift`, calls `oskType(ch)`.
  - `handleUp()` (`NanoMenuInput.cpp:463-466`): `if (mOskActive) { if (mOskCursorY>0) mOskCursorY--; return; }`
  - `handleDown()` (`NanoMenuInput.cpp:514-517`): `if (mOskActive) { if (mOskCursorY<kOskRows-1) mOskCursorY++; return; }`
  - `handleLeft`/`handleRight` (in `NanoMenuXmb.cpp`, around `:1065`) move `mOskCursorX` with
    `maxCol = kOskCols - 1`.
- In `pollInput` directly (the `ev.value == 1` switch, `NanoMenuInput.cpp:806-898`):
  - `KEY_ENTER` / `BTN_START`: `if (mOskActive) oskConfirm();` (`:813-825`)
  - `BTN_NORTH` (X): `if (mOskActive) { oskBackspace(); break; }` (`:857`)
  - `BTN_WEST` (Y): in XMB toggles OSK open/close (`:836-844`)
  - `BTN_TL`/`KEY_L`: `if (mOskActive) { mOskShift = !mOskShift; ... }` (`:869-874`)
  - `BTN_EAST`/`KEY_BACK`: `handleBack()` which calls `closeOsk()` when `mOskActive`
    (`NanoMenuInput.cpp:249-252`).

OSK state lives in `NanoMenu.h:675-683`: `mOskActive`, `mOskShift`, `mOskQuery` (the std::string
buffer), `mOskCursorX`, `mOskCursorY`. The grid layout is `kOskLayout[5][10]` with
`kOskRows=5`, `kOskCols=10` (`NanoMenuUtils.h:33-41`), a fixed ASCII letters/digits/symbols grid.
`oskType/oskBackspace/oskConfirm/openOsk/closeOsk` are defined in `NanoMenuXmb.cpp:1487-1565`;
`renderOsk()` at `NanoMenuXmb.cpp:1567-1645`; the password variant
`openOskForPassword`/`renderPasswordPromptOverlay`/`maskPassword` in
`NanoMenuSettings.cpp:1065-1097`. `renderOsk()` is called from `render()` at
`NanoMenuRender.cpp:994` (setup wizard) and `:1002` (XMB), drawn LAST so it overlays Wi-Fi/BT.

### Cleanest hook point for a new Leanback-style OSK

The existing `mOskActive`-gated pattern is the idiomatic hook and the new OSK should follow it,
but it is a fixed grid with single-`char` typing and is purpose-built for search/password. For a
1:1 Leanback OSK reimplementation, the recommended approach:

1. Add a dedicated boolean (e.g. `mLeanbackOskActive`) plus its own state (cursor row/col, page,
   shift/caps, symbol page, and a `std::string` buffer the caller owns). Reuse the existing
   `mOskQuery` only if you want to inherit the search/password plumbing; otherwise keep a separate
   buffer to avoid coupling to `updateSearchResults()`.
2. Gate input the same way the search OSK does: add an early `if (mLeanbackOskActive) { ...; return; }`
   branch at the top of `handleUp/handleDown/handleLeft/handleRight/handleSelect/handleBack` and in
   the `pollInput` button switch (for X=backspace, Y=symbol/shift, L/R=shift/page, Start/Enter=commit,
   B=cancel). Because navigation already flows through `navPress -> handleUp/Down/Left/Right` with
   hold-to-repeat, the new OSK gets accelerating cursor movement for free.
3. Render via a new `renderLeanbackOsk()` called from `render()` immediately after the existing
   `renderOsk()` calls (`NanoMenuRender.cpp:994` / `:1002`) so it overlays everything, using only
   `drawQuad` (key cells + focus highlight + buffer field) and `drawText` (key faces, typed text,
   hints). Use `measureText` to center labels.
4. Type multibyte UTF-8 by appending the selected key's UTF-8 bytes to the buffer directly (not the
   single-`char` `oskType`), since the renderer is fully UTF-8 capable (section 4).

This keeps the new OSK self-contained, leaves the search/password OSK untouched, and rides the
existing evdev poll + nav-repeat machinery.

---

## 6. Current UI language: the single source of truth

### Source of truth

The active Nano UI language is a single process-global enum:

```
static NanoLocale sCurrentLocale = LOCALE_EN;   // NanoMenuStrings.cpp:23
```

It is read via `nanoGetLocale()` (`NanoMenuStrings.cpp:577`, returns `sCurrentLocale`) and used by
`tr(StringId id)` (`NanoMenuStrings.cpp:570-575`) to index the translation table
`kStrings[STR_COUNT][LOCALE_COUNT]`. Everything user-visible goes through `tr()`. So
`nanoGetLocale()` is THE function to call to learn the active language, and `NanoLocale` is THE
type.

### Value format

`NanoLocale` is the enum in `NanoMenuStrings.h:117-134`:

```
LOCALE_EN=0, LOCALE_ES, LOCALE_FR, LOCALE_DE, LOCALE_IT, LOCALE_PT, LOCALE_NL, LOCALE_RU,
LOCALE_JA, LOCALE_KO, LOCALE_ZH_CN, LOCALE_ZH_TW, LOCALE_AR, LOCALE_TR, LOCALE_PL, LOCALE_COUNT(=15)
```

To get a language CODE STRING for an enum value, call `nanoGetLocaleInfo(locale)`
(`NanoMenuStrings.cpp:585-587`) which returns `const LocaleInfo&` with fields
`{ code, regionCode, nativeName, englishName }` (`NanoMenuStrings.h:136-141`). The `code` is the
2-letter ISO 639-1 language code ("en","es","fr","de","it","pt","nl","ru","ja","ko","zh","zh","ar",
"tr","pl"), `regionCode` is the default region ("US","ES","FR","DE","IT","BR","NL","RU","JP","KR",
"CN","TW","SA","TR","PL"). The full table is `kLocaleInfo[LOCALE_COUNT]` at
`NanoMenuStrings.cpp:26-43`. Note both `LOCALE_ZH_CN` and `LOCALE_ZH_TW` have `code="zh"` and are
disambiguated by `regionCode` ("CN" vs "TW").

### The underlying system property (the persistent backing store)

The locale is initialized FROM, and written back TO, the Android system property
`persist.sys.locale` (a BCP-47 tag like `en-US`, `zh-TW`, `pt-BR`):

- `nanoInitLocaleFromSystem()` (`NanoMenuStrings.cpp:589-631`) reads
  `property_get("persist.sys.locale", locale, "en-US")`, splits on `-` into lang/region, matches
  the lang against `kLocaleInfo[i].code`, and for `zh` picks `LOCALE_ZH_TW` when region is
  `TW`/`HK` else `LOCALE_ZH_CN`. Falls back to `LOCALE_EN` if unmatched.
- `nanoApplyLocaleToSystem()` (`NanoMenuStrings.cpp:633-641`) formats `"%s-%s"` from
  `code`/`regionCode` and runs `setprop persist.sys.locale <tag>` via `system()`.

There is NO Nano-specific locale property. A grep for `nano.lang|nano.locale|gammaos.lang|
gammaos.locale` returns nothing; `persist.sys.locale` is the only external store.

### When the source of truth is set

- `nanoInitLocaleFromSystem()` is called at startup in the NanoMenu constructor area
  (`NanoMenu.cpp:1108`) and again from the settings tree (`NanoMenuSettingsTree.cpp:910`) and the
  setup wizard (`NanoMenuSetupWizard.cpp:135`).
- The setup-wizard language picker drives it: `startSetupWizard` seeds
  `mLangSelected = (int)nanoGetLocale()` (`NanoMenuSetupWizard.cpp:136`); the picker scrolls
  `mLangSelected` (`NanoMenuSetupWizard.cpp:447`, `:471`) and calls
  `nanoSetLocale((NanoLocale)mLangSelected)` live as a preview while rendering
  (`renderSetupLanguage`, `NanoMenuSetupWizard.cpp:1051`); on confirm
  `handleSetupLanguageSelect()` (`NanoMenuSetupWizard.cpp:1039-1042`) calls
  `nanoSetLocale(...)` then `nanoApplyLocaleToSystem()` to persist it.

### Recommendation for the OSK

For the OSK to pick a localized layout / labels, read `nanoGetLocale()` (in-process, no syscall)
and switch layout by the `NanoLocale` enum or by `nanoGetLocaleInfo(loc).code` (the 2-letter code).
Do NOT read `persist.sys.locale` directly from the OSK; `sCurrentLocale` is the live, authoritative
value (the property may lag a pending `setprop`). Use `tr(StringId)` for any OSK hint/label text so
it follows the active language automatically. If the OSK needs new strings (e.g. "Done", "Space",
"Shift"), add `StringId` entries to `NanoMenuStrings.h` and rows to `kStrings` in
`NanoMenuStrings.cpp` following the existing 15-column-per-row pattern.

---

## Appendix: quick reference of constants

- `FONT_CHAR_W = 8`, `FONT_CHAR_H = 16` (`NanoMenuShaders.h:64-65`)
- `mFontSize = 48` FreeType render px (`NanoMenuRender.cpp:353`); `pixelScale = scale/3`
- Glyph atlas: 2048x2048 RGBA, row packer (`NanoMenuRender.cpp:360-374`)
- `TEXT_MAX_CHARS = 256` glyphs per drawText call (`NanoMenuRender.cpp:522`)
- Icon slots: `mIconTextures[18]`, indices 0..17 (`NanoMenu.h:673`)
- Existing search OSK grid: 5 rows x 10 cols, `kOskLayout` (`NanoMenuUtils.h:33-41`)
- OSK state members: `mOskActive`, `mOskShift`, `mOskQuery`, `mOskCursorX`, `mOskCursorY`
  (`NanoMenu.h:676-680`)
- Buttons: A=`BTN_SOUTH`, B=`BTN_EAST`, X=`BTN_NORTH`, Y=`BTN_WEST`, L=`BTN_TL`/`KEY_L`,
  R=`BTN_TR`/`KEY_R`, Start=`BTN_START`, Select=`BTN_SELECT`; D-pad `KEY_UP/DOWN/LEFT/RIGHT` or
  `ABS_HAT0X/Y` or `ABS_X/ABS_Y` (deadzone 29490). `ev.value`: 1=down, 2=repeat, 0=up.
- Language: `sCurrentLocale` (`NanoMenuStrings.cpp:23`) read by `nanoGetLocale()`; codes via
  `nanoGetLocaleInfo().code`; backing prop `persist.sys.locale`.
- No rounded-rect / ninepatch / gradient / general textured-quad helper exists; only `drawQuad`
  (flat fill), `drawIcon` (icon atlas), and `drawText` (glyph atlas).
