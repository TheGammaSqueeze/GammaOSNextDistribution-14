# ES-DE animation element

The `<animation>` element plays a looping animation as chrome (a HUD badge, an animated frame, a
glow). ES-DE supports two backends: GIF (`GIFAnimComponent`) and Lottie vector JSON
(`LottieAnimComponent`). nano decodes and plays **GIF** (`esdeAnimGet` decodes every frame once);
a Lottie `.json` path is skipped rather than mis-drawn. It is a secondary element (it sits at its
`zIndex` among the other chrome), implemented in the `t == "animation"` branch of `renderEsde()` in
`NanoThemeEngine.cpp`, checked against
`/work/emulationstation-de/es-core/src/components/GIFAnimComponent.cpp` and `LottieAnimComponent.cpp`.

## The fit model (contain / stretch, never crop)

Unlike `<image>`, the animation element has **no cover/crop mode** - `ThemeData.cpp` does not list
`cropSize` among its valid properties, so a theme cannot ask an animation to crop. `resize()` gives
just two outcomes, from `size` / `maxSize`:

| theme markup | result |
|---|---|
| `<size>` with **both** axes > 0 | stretch to exactly fill the box (aspect ignored) |
| `<size>` with **one** axis 0, or `<maxSize>` | scale to fit, preserving the frame aspect (letterbox) |
| neither | draw at the native frame pixels |

nano derives the zero axis of a single-axis `<size>` from the frame aspect so the fixed axis is
filled, contain-fits a `<maxSize>` box, and anchors the fitted result by `origin` (`getTransform`
translates by `-origin * fittedSize`) - the same anchoring as `<image>`. There is deliberately no
`cropSize` branch: it would misrepresent the component and can never fire.

## Tint and effects

ES-DE tints and filters the animation exactly like `<image>`, through the same shader path
(`GIFAnimComponent::applyTheme` / `LottieAnimComponent::applyTheme`):

- `color` -> `colorEnd` with `gradientType` (`horizontal` left->right, or `vertical` top->bottom) -
  a tint gradient across the quad; `color` alone is a flat tint. The default is white
  (`0xFFFFFFFF`), i.e. no tint - the frames are drawn as decoded. Themes use this to recolour a
  white/greyscale animation per colorScheme: Cathode's `hud-ele` GIF is `<color>${gifColor}</color>`,
  where `gifColor` is a colorScheme variable (white for the classic schemes, `1463FF` blue for
  Nautical, `F063F2` magenta for Grape, ...). Without honoring `color` the HUD stayed white in every
  scheme.
- `opacity` (default 1) - folded into the alpha of the tint, and of both gradient stops, matching
  ES-DE `updateColors` (nano's `colorOf` folds `opacity` into the start alpha; the `colorEnd` alpha
  is multiplied by `opacity` explicitly).
- `saturation` (default 1) and `brightness` (default 0) - routed through the FX shader.
- `cornerRadius` (clamp `0..0.5 * screenWidth`) - rounds the quad, via the FX shader.
- `interpolation` (`linear` / `nearest`) - the magnify filter. ES-DE's default is **nearest**
  (`GIFAnimComponent` constructor `setLinearMagnify(false)`), flipping to linear only for an
  arbitrary (non-90-degree) rotation or an explicit `linear`. nano routes this through the shared
  `esdeImageMagFilter`, so the default matches.
- `rotation` about `rotationOrigin` (default centre), in degrees clockwise.

These all default to no-op, so a plain animation (Cathode's `frame.gif`, Adroit's, Canvas') is
unchanged; only a theme that sets them sees the effect. `color`/`colorEnd`/`gradientType`/
`brightness`/`saturation`/`cornerRadius`/`rotation`/`interpolation` had to be **registered in the
parser's animation property map** (`NanoEsdeTheme.cpp`) as well as honored in the renderer - an
unregistered property is dropped at parse, which is why animation `rotation` (already read by the
renderer) had been silently inert.

## Playback

`esdeAnimGet` decodes the GIF's frames and per-frame delays once (via `AImageDecoder`) and caches
them. The branch picks the current frame from the wall clock modulo the total duration, and sets
`mEsdeWantsFastFrame` + `mDisplayDirty` so the render loop keeps advancing a multi-frame animation.
`speed` / `direction` / `iterationCount` are parsed (ES-DE-valid) but nano plays the native GIF
timing forward-looping.

To bound memory a native size over 512px is downscaled with `AImageDecoder_setTargetSize` - but
**that call is not supported for animated images** and returns an error, so the scaled dimensions
are adopted only when the decoder accepts them; otherwise the frames are decoded at native size.
Without this guard the target buffer was sized for the (rejected) scaled dimensions while
`decodeImage` still expected the native size, so every frame failed with `BAD_PARAMETER` and any
animation larger than 512px in either axis (Cathode's 800x167 `hud-ele`, its wide `frame`) rendered
nothing at all - which masked the tint entirely until the decode was fixed.

## Verifying a change

Per `AB_TESTING.md`, install the theme both sides and compare the animation's region specifically -
an animation over a per-system background **video** varies per frame, so crop to the element box
(Cathode's `hud-ele` is `pos 0.05 0.75`, `size 0.2 0.1` in the list view) rather than diffing the
whole frame. Switch colorSchemes (Cathode Nautical / Grape / Virtualboy) to exercise the `color`
tint - the HUD GIF should take the scheme's `gifColor`, matching the control, not stay white.
