#define LOG_TAG "azahar-nano"

#include "Hud.h"

#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <log/log.h>

#include <algorithm>
#include <cstdio>

using android::drastic_gfx::rgba;

namespace azahar_nano {

bool Hud::init(ANativeWindow* win, int bufW, int bufH, bool upright) {
    mBufW = bufW;
    mBufH = bufH;
    mUpright = upright;
    mDpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (mDpy == EGL_NO_DISPLAY || !eglInitialize(mDpy, nullptr, nullptr)) return false;
    const EGLint cfgAttrs[] = {EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE,
                               EGL_OPENGL_ES2_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
                               EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
    EGLConfig cfg;
    EGLint n = 0;
    if (!eglChooseConfig(mDpy, cfgAttrs, &cfg, 1, &n) || n < 1) return false;
    const EGLint ctxAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    mCtx = eglCreateContext(mDpy, cfg, EGL_NO_CONTEXT, ctxAttrs);
    mSurf = eglCreateWindowSurface(mDpy, cfg, (EGLNativeWindowType)win, nullptr);
    if (mCtx == EGL_NO_CONTEXT || mSurf == EGL_NO_SURFACE) {
        ALOGE("hud: EGL context/surface failed 0x%x", eglGetError());
        return false;
    }
    if (!makeCurrent()) return false;
    // Same convention as the menu: the logical space is the strip as the user sees
    // it (wide and short); the upright matrix maps it onto the portrait buffer's
    // left columns.
    const int logicalW = upright ? bufH : bufW;
    const int logicalH = upright ? bufW : bufH;
    const float rotUpright[4] = {0.0f, 1.0f, -1.0f, 0.0f};
    const float rotNone[4] = {1.0f, 0.0f, 0.0f, 1.0f};
    if (!mGfx.init(logicalW, logicalH, upright ? rotUpright : rotNone)) {
        ALOGE("hud: OverlayGfx init failed");
        return false;
    }
    mReady = true;
    eglMakeCurrent(mDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    ALOGI("hud: ready %dx%d logical %dx%d", bufW, bufH, logicalW, logicalH);
    return true;
}

bool Hud::makeCurrent() {
    return eglMakeCurrent(mDpy, mSurf, mSurf, mCtx) == EGL_TRUE;
}

void Hud::shutdown() {
    if (mDpy == EGL_NO_DISPLAY) return;
    if (mReady && makeCurrent()) mGfx.shutdown();
    mReady = false;
    eglMakeCurrent(mDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (mSurf != EGL_NO_SURFACE) eglDestroySurface(mDpy, mSurf);
    if (mCtx != EGL_NO_CONTEXT) eglDestroyContext(mDpy, mCtx);
    mSurf = EGL_NO_SURFACE;
    mCtx = EGL_NO_CONTEXT;
    mDpy = EGL_NO_DISPLAY;
}

void Hud::render(float blitFps, float gameFps, bool slow) {
    if (!mReady || !makeCurrent()) return;
    const float W = (float)mGfx.viewportW();
    const float H = (float)mGfx.viewportH();
    glViewport(0, 0, mBufW, mBufH);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    mGfx.beginFrame();
    // drastic-nano's drawFpsHud geometry, scaled from the panel's logical height
    // (the strip is a slice of a 1080 px tall logical panel, so sf is 1.5 there).
    // W is the panel's logical width, so W/1280 is drastic's H/720 on a 16:9 panel.
    const float sf = W / 1280.0f;
    const float scale = (24.0f * sf) / mGfx.fontBasePx();
    const float lineH = mGfx.fontLineH() * scale;
    const float pad = 6.0f * sf;
    char l1[24]; snprintf(l1, sizeof(l1), "BLIT %.0f", blitFps);
    char l2[24]; snprintf(l2, sizeof(l2), "GAME %.0f", gameFps);
    float tw = mGfx.measure(l1, scale);
    { const float t2 = mGfx.measure(l2, scale); if (t2 > tw) tw = t2; }
    const float bw = tw + 2.0f * pad;
    float bh = 2.0f * lineH + 2.0f * pad;
    if (bh > H - 2.0f) bh = H - 2.0f;
    const float bx = W - bw - 8.0f * sf, by = std::min(8.0f * sf, H - bh);
    mGfx.fillRect(bx, by, bw, bh, rgba(0.0f, 0.0f, 0.0f, 0.55f));
    mGfx.text(l1, bx + pad, by + pad, scale, rgba(0.3f, 0.8f, 1.0f, 1.0f));
    mGfx.text(l2, bx + pad, by + pad + lineH, scale,
              slow ? rgba(1.0f, 0.35f, 0.3f, 1.0f) : rgba(0.2f, 1.0f, 0.4f, 1.0f));
    mGfx.endFrame();
    eglSwapBuffers(mDpy, mSurf);
    eglMakeCurrent(mDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
}

} // namespace azahar_nano
