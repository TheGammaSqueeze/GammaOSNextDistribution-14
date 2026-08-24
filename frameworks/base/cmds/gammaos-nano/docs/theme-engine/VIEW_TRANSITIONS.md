# ES-DE view transitions (system <-> gamelist)

ES-DE animates the change between the system view and a gamelist view, and nano now does too. The
animation is per theme: it comes from the theme's `<transitions>` profile in `capabilities.xml`.
This document records the ES-DE model and how nano implements it.

## Implemented

`esdeBeginTransition()` (from `esdeSelect`/`esdeBack`) starts a transition using the resolved
animation (an optional `persist.gammaos.nano.esde.transition` = `automatic`/`instant`/`slide`/`fade`
override wins); `renderEsdeHome()` wraps `renderEsde()` with it. INSTANT cuts as before; FADE draws
a full-screen black overlay over `renderEsde()` and swaps the view at the black point; SLIDE captures
each view to a full-screen FBO (`esdeEnsureFbo`) with the `mEsdeForceView` override and composites the
two at the pan offsets. Both views are static for the slide's duration (the selection is fixed), so
they are captured **once** at the start (`mEsdeXsSnapped`) and only the two cached textures are drawn
per frame, keeping the per-frame cost to two quads rather than re-rendering both views every frame
(A133-friendly). Navigation is ignored while a transition plays. Verified on device: slate and linear
slide, fade dims through black, instant/other themes cut unchanged, rapid enter/back stays stable.

## Profile parsing / resolution (also implemented)

`NanoEsdeTheme` parses every `<transitions name="...">` profile (per-view-change animation:
`systemToSystem` / `systemToGamelist` / `gamelistToGamelist` / `gamelistToSystem`, each `instant` /
`slide` / `fade`) into `Capabilities::transitions`, and `Theme::load()` resolves the active profile
exactly like ES-DE's `ThemeData::setThemeTransitions` under the default `ThemeTransitions=automatic`:
the **first declared profile** (INSTANT when the theme declares none). The resolved enter/leave
animations are exposed as `Theme::xsSystemToGamelist()` and `Theme::xsGamelistToSystem()` and logged
on the `esde: loaded` line (`xsSysToGl=` / `xsGlToSys=`).

Resolution is verified against the ES-DE source for the bundled themes: slate and linear resolve to
`slide`, while adroit / carbon / art-book-next / modern / gameos / catppuccin resolve to `instant`
(so nano's current cut already matches those six; only the slide themes diverge).

Not yet modelled (follow-ups, all rare): `ThemeTransitions` set to an explicit profile name or
`builtin-slide` / `builtin-fade`, and a variant-defined transition override
(`sVariantDefinedTransitions`). The renderer should also read an optional
`persist.gammaos.nano.esde.transition` override (`automatic` / `instant` / `slide` / `fade`).

## The ES-DE model (reverse-engineered from es-app/src/views/ViewController.cpp)

Views live in a virtual grid and a camera pans between them:

- The system view is at world position `(systemId * W, H)`; a system's gamelist view is at
  `(systemId * W, 2H)` (`ViewController.cpp:1213/1229`, `W`/`H` = screen size). The camera
  translation is `-viewPosition`, so viewing the system view means `camera.y = -H` and viewing the
  gamelist means `camera.y = -2H`. Entering a gamelist from its own system is therefore a **pure
  vertical pan** (x is identical); the gamelist sits directly below the system view.

### SLIDE (`MoveCameraAnimation`, es-core/src/animations/MoveCameraAnimation.h)

- Duration **400 ms**, easing **ease-out cubic**: `p = (t - 1)^3 + 1` for linear `t` in `0..1`
  (`camera = mix(start, target, p)`).
- system -> gamelist: camera pans from `y=-H` to `y=-2H`. On screen the outgoing system view moves
  up by `p*H` (screen `y = -p*H`) and the incoming gamelist enters from the bottom (screen
  `y = (1-p)*H`). At `p=0.5` the screen shows the system's lower half on top and the gamelist's
  upper half on the bottom.
- gamelist -> system: the reverse (outgoing gamelist slides down by `p*H`, incoming system enters
  from the top at `y = -(1-p)*H`).

### FADE (`ViewController::playViewTransition`)

- A full-screen black overlay whose opacity is `glm::mix(0, 1, t)` (linear).
- Timeline: **fade out 120 ms** (`FADE_DURATION`, opacity 0->1) -> **hold 200 ms** (`FADE_WAIT`,
  opacity 1, screen black) -> **fade in 120 ms** (opacity 1->0). The view is swapped at the fully
  black point (the fade-out finished-callback), so the incoming view is only ever revealed by the
  fade-in.

## nano implementation plan

Trigger points already exist: `esdeSelect()` sets `mEsdeInGamelist=true` (system->gamelist) and
`esdeBack()` sets it `false` (gamelist->system) in `NanoThemeEngine.cpp`. Wrap those so that, when
the resolved animation is not INSTANT, the swap starts a transition instead of flipping immediately
(FADE) or flips immediately but starts a camera slide (SLIDE). Keep INSTANT as the current direct
flip so the six instant themes are untouched (no regression).

State (in `NanoMenu.h`): `int mEsdeXsPhase` (0 none / slide / fade-out / fade-hold / fade-in),
`int64_t mEsdeXsStart` (uptimeMillis), `bool mEsdeXsToGamelist` (direction). Drive it from
`uptimeMillis()` each frame and set `mDisplayDirty` while active so the frame loop keeps rendering.

**FADE** (low risk, no FBO): in the `renderEsde()` caller (`NanoMenuRender.cpp` ~5889), when fading,
call `renderEsde()` as usual then draw a full-screen black quad at the phase opacity; flip
`mEsdeInGamelist` at the fade-out -> hold boundary. This needs no dual-view rendering.

**SLIDE** (needs two views on screen at once): render each view to a full-screen FBO via the
existing `ensureFbo(&fbo, &tex, mWidth, mHeight)` helper (NanoMenuPS3Bg.cpp), then composite the two
textures at the slide offsets. Add a render-view override (`int mEsdeForceView`: 0 auto / 1 system /
2 gamelist) that `renderEsde()` honours at the top (`bool gamelist = mEsdeForceView ? mEsdeForceView==2 : mEsdeInGamelist`) so the outgoing and incoming views can both be drawn from the stored
selection state. Per frame while sliding:
1. bind FBO-sys, viewport `0,0,W,H`, `mEsdeForceView=1`, `renderEsde()`;
2. bind FBO-gl, `mEsdeForceView=2`, `renderEsde()`;
3. restore the default framebuffer, `mEsdeForceView=0`;
4. compute `p = (t-1)^3 + 1` for `t = clamp(elapsed/400, 0, 1)`;
5. draw the FBO textures full-screen at the offsets above (watch the FBO texture V-flip; flip UVs if
   the composite is upside down). Cost is ~2x render for 400 ms, acceptable for a one-shot transition.

Fall back to the instant cut if an FBO allocation fails, so a transition can never black out the home.

## Verification

Transitions are animation, so the difference-image method does not apply frame-for-frame. Verify
qualitatively: for FADE capture the black-hold frame (screen fully black in both nano and the
control); for SLIDE capture a mid-transition frame and confirm both show the same half-and-half pan
in the same direction. Drive nano with `sys.gammaos.nano.nav enter/back` and capture at a delay
matching ~50% of the animation; drive the control per `docs/AB_TESTING.md`. The settled post-
transition state already matches the control (verified across ~10 themes).
