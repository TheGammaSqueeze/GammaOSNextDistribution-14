#define LOG_TAG "azahar-nano"

#include "Hud.h"

#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <log/log.h>

#include <algorithm>
#include <cmath>
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
    // Same convention as the menu: the logical space is the panel as the user sees
    // it; the upright matrix maps it onto the portrait buffer.
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

void Hud::render(const Frame& f) {
    if (!mReady || !makeCurrent()) return;
    const float W = (float)mGfx.viewportW();
    const float H = (float)mGfx.viewportH();
    glViewport(0, 0, mBufW, mBufH);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    mGfx.beginFrame();
    if (f.fps) {
        // drastic-nano's drawFpsHud geometry, scaled from the panel's logical size.
        // W is the panel's logical width, so W/1280 is drastic's H/720 on a 16:9 panel.
        const float sf = W / 1280.0f;
        const float scale = (24.0f * sf) / mGfx.fontBasePx();
        const float lineH = mGfx.fontLineH() * scale;
        const float pad = 6.0f * sf;
        char l1[24]; snprintf(l1, sizeof(l1), "BLIT %.0f", f.blitFps);
        char l2[24]; snprintf(l2, sizeof(l2), "GAME %.0f", f.gameFps);
        float tw = mGfx.measure(l1, scale);
        { const float t2 = mGfx.measure(l2, scale); if (t2 > tw) tw = t2; }
        const float bw = tw + 2.0f * pad;
        float bh = 2.0f * lineH + 2.0f * pad;
        if (bh > H - 2.0f) bh = H - 2.0f;
        const float bx = W - bw - 8.0f * sf, by = std::min(8.0f * sf, H - bh);
        mGfx.fillRect(bx, by, bw, bh, rgba(0.0f, 0.0f, 0.0f, 0.55f));
        mGfx.text(l1, bx + pad, by + pad, scale, rgba(0.3f, 0.8f, 1.0f, 1.0f));
        mGfx.text(l2, bx + pad, by + pad + lineH, scale,
                  f.slow ? rgba(1.0f, 0.35f, 0.3f, 1.0f) : rgba(0.2f, 1.0f, 0.4f, 1.0f));
    }
    if (f.ff) drawFastForward(f.speedPct);
    if (f.toastAlpha > 0.0f) drawToast(std::min(f.toastAlpha, 1.0f), f.spin);
    mGfx.endFrame();
    eglSwapBuffers(mDpy, mSurf);
    eglMakeCurrent(mDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
}

// Fast forward: drastic-nano's ">>" badge (two amber triangles on a dark rounded box) in the
// top left corner, followed by the speed in percent inside the same box.
void Hud::drawFastForward(float speedPct) {
    const float W = (float)mGfx.viewportW();
    const float sf = W / 1280.0f;
    const float m = 10.0f * sf;
    const float s = 20.0f * sf;          // triangle height
    const float tw = s * 0.85f;          // triangle width
    const float gap = 3.0f * sf;
    const float pad = 6.0f * sf;
    const float scale = (24.0f * sf) / mGfx.fontBasePx();
    const float lineH = mGfx.fontLineH() * scale;
    char label[16];
    snprintf(label, sizeof(label), "%.0f%%", speedPct);
    const float textW = mGfx.measure(label, scale);
    const float inner = std::max(s, lineH);
    const float bw = 2.0f * tw + gap + 8.0f * sf + textW + 2.0f * pad;
    const float bh = inner + 2.0f * pad;
    const float bx = m, by = m;
    mGfx.roundedRect(bx, by, bw, bh, 4.0f * sf, rgba(0.0f, 0.0f, 0.0f, 0.5f));
    const auto amber = rgba(1.0f, 0.75f, 0.2f, 1.0f);
    const float ty = by + pad + (inner - s) * 0.5f;
    float tx = bx + pad;
    mGfx.triangle(tx, ty, tx, ty + s, tx + tw, ty + s * 0.5f, amber);
    tx += tw + gap;
    mGfx.triangle(tx, ty, tx, ty + s, tx + tw, ty + s * 0.5f, amber);
    mGfx.text(label, tx + tw + 8.0f * sf, by + pad + (inner - lineH) * 0.5f, scale, amber);
}

// Spinner (a ring of dots with a fading tail) and "Compiling shaders" in the bottom
// left corner, white with a light drop shadow so it reads over any scene.
void Hud::drawToast(float alpha, float spin) {
    const float W = (float)mGfx.viewportW();
    const float H = (float)mGfx.viewportH();
    const float sf = W / 1280.0f;
    const float scale = (22.0f * sf) / mGfx.fontBasePx();
    const float lineH = mGfx.fontLineH() * scale;
    const float margin = 18.0f * sf;
    const float ringR = 9.0f * sf, dot = 3.6f * sf;
    const float cy = H - margin - lineH * 0.5f;
    const float cx = margin + ringR + dot * 0.5f;
    const float textX = cx + ringR + dot + 10.0f * sf;
    const float textY = cy - lineH * 0.5f;
    const char* label = "Compiling shaders";
    constexpr int kDots = 10;
    constexpr float kTwoPi = 6.2831853f;
    const float head = spin - std::floor(spin);
    auto drawAll = [&](float ox, float oy, bool shadow, float a) {
        for (int i = 0; i < kDots; i++) {
            // Dot i trails the head by i steps; the tail fades out.
            const float ang = (head - (float)i / kDots) * kTwoPi - kTwoPi / 4.0f;
            const float x = cx + std::cos(ang) * ringR + ox, y = cy + std::sin(ang) * ringR + oy;
            const float fade = 1.0f - (float)i / kDots;
            const float da = a * (0.15f + 0.85f * fade * fade);
            mGfx.roundedRect(x - dot * 0.5f, y - dot * 0.5f, dot, dot, dot * 0.5f,
                             shadow ? rgba(0.0f, 0.0f, 0.0f, da) : rgba(1.0f, 1.0f, 1.0f, da));
        }
        mGfx.text(label, textX + ox, textY + oy, scale,
                  shadow ? rgba(0.0f, 0.0f, 0.0f, a) : rgba(1.0f, 1.0f, 1.0f, a));
    };
    // Black drop shadow: a soft wide pass, two strong offset passes and a thin dark
    // outline so the white text stays readable over bright scenes.
    drawAll(4.0f * sf, 4.0f * sf, true, 0.35f * alpha);
    drawAll(3.0f * sf, 3.0f * sf, true, 0.60f * alpha);
    drawAll(2.0f * sf, 2.0f * sf, true, 0.85f * alpha);
    const float o = 1.2f * sf;
    drawAll(-o, 0.0f, true, 0.55f * alpha);
    drawAll(o, 0.0f, true, 0.55f * alpha);
    drawAll(0.0f, -o, true, 0.55f * alpha);
    drawAll(0.0f, o, true, 0.55f * alpha);
    drawAll(0.0f, 0.0f, false, alpha);
}

} // namespace azahar_nano
