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
    struct Frame {
        // Counter box (BLIT = panel flips/s, GAME = emulated frames/s, the same two
        // lines as drastic-nano) in the top right corner.
        bool fps = false;
        float blitFps = 0.0f, gameFps = 0.0f;
        bool slow = false;
        // Fast forward badge (drastic-nano's amber ">>") in the top left corner with the
        // emulation speed in percent of real time (100% = full speed whatever frame rate
        // the game targets).
        bool ff = false;
        float speedPct = 100.0f;
        // "Compiling shaders" notice in the bottom left corner: opacity 0..1 (the
        // caller fades it) and the spinner angle in turns.
        float toastAlpha = 0.0f;
        float spin = 0.0f;
    };
    // Draws the frame and presents it.
    void render(const Frame& f);
    bool ready() const { return mReady; }

private:
    bool makeCurrent();
    void drawToast(float alpha, float spin);
    void drawFastForward(float speedPct);
    EGLDisplay mDpy = EGL_NO_DISPLAY;
    EGLSurface mSurf = EGL_NO_SURFACE;
    EGLContext mCtx = EGL_NO_CONTEXT;
    android::drastic_gfx::OverlayGfx mGfx;
    bool mReady = false;
    int mBufW = 0, mBufH = 0;
    bool mUpright = false;
};

} // namespace azahar_nano
