// Hud: the frame rate counter strip on the top panel, drawn with OverlayGfx into
// the DRM presenter's HUD window (an alpha blended plane over the game).
#pragma once

#include <EGL/egl.h>
#include <string>

#include "OverlayGfx.h"

struct ANativeWindow;

namespace azahar_nano {

class Hud {
public:
    // win: the HUD window; bufW/bufH its buffer size; upright: the strip is the
    // left columns of a portrait buffer (drawn rotated like the menu).
    bool init(ANativeWindow* win, int bufW, int bufH, bool upright);
    void shutdown();
    // Draws the counter box (BLIT = panel flips/s, GAME = emulated frames/s,
    // the same two lines as drastic-nano) and presents it.
    void render(float blitFps, float gameFps, bool slow);
    bool ready() const { return mReady; }

private:
    bool makeCurrent();
    EGLDisplay mDpy = EGL_NO_DISPLAY;
    EGLSurface mSurf = EGL_NO_SURFACE;
    EGLContext mCtx = EGL_NO_CONTEXT;
    android::drastic_gfx::OverlayGfx mGfx;
    bool mReady = false;
    int mBufW = 0, mBufH = 0;
    bool mUpright = false;
};

} // namespace azahar_nano
