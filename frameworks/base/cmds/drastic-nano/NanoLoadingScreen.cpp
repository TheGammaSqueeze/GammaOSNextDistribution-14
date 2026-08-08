/*
 * Copyright (C) 2026 GammaOS
 *
 * See NanoLoadingScreen.h.
 */

#include "NanoLoadingScreen.h"

#include <cmath>
#include <cstdio>
#include <cstring>

#include <GLES2/gl2.h>

#include <utils/SystemClock.h>

#include "DisplayBackend.h"     // drastic_nano::IDisplayBackend / FrameTargets
#include "NanoMenuDrm.h"        // android::sAhbRing* / sDrmRotMat / drmFlipAll
#include "OverlayGfx.h"

namespace android {
namespace drastic_load {

using android::drastic_gfx::Color;
using android::drastic_gfx::OverlayGfx;
using android::drastic_gfx::rgba;

static const float kIdentityMat[4] = {1.0f, 0.0f, 0.0f, 1.0f};

LoadingScreen::~LoadingScreen() { shutdown(); }

void LoadingScreen::init(drastic_nano::IDisplayBackend* sfBackend,
                         int fallbackW, int fallbackH) {
    mSf = sfBackend;
    mFallbackW = fallbackW > 0 ? fallbackW : 640;
    mFallbackH = fallbackH > 0 ? fallbackH : 480;
    mFrame = 0;
    mLastDrawMs = 0;
}

void LoadingScreen::shutdown() {
    if (mGfx) {
        mGfx->shutdown();
        delete mGfx;
        mGfx = nullptr;
    }
    mGfxInited = false;
    mGfxW = mGfxH = 0;
}

void LoadingScreen::drawInto(int fbW, int fbH, int logicalW, int logicalH,
                             const float* rot, const char* label, float progress) {
    if (fbW <= 0 || fbH <= 0 || logicalW <= 0 || logicalH <= 0) return;

    // Lazily (re)create the OverlayGfx when the logical size changes. The
    // rotation matrix can change per frame cheaply via setRotationMatrix.
    if (!mGfx) mGfx = new OverlayGfx();
    if (!mGfxInited || mGfxW != logicalW || mGfxH != logicalH) {
        mGfx->shutdown();
        if (!mGfx->init(logicalW, logicalH, rot ? rot : kIdentityMat)) return;
        mGfxInited = true;
        mGfxW = logicalW;
        mGfxH = logicalH;
    } else {
        mGfx->setRotationMatrix(rot ? rot : kIdentityMat);
    }

    glViewport(0, 0, fbW, fbH);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    // Opaque near-black backdrop (a touch of blue, console-loading feel).
    glClearColor(0.035f, 0.043f, 0.063f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const float W = (float)logicalW;
    const float H = (float)logicalH;
    const float shortSide = W < H ? W : H;

    mGfx->beginFrame();

    // Progress bar geometry: a comfortable width relative to the short side so
    // it reads well in both landscape and portrait, clamped to sane pixels.
    float barW = shortSide * 0.62f;
    if (barW > 560.0f) barW = 560.0f;
    if (barW > W * 0.86f) barW = W * 0.86f;
    float barH = shortSide * 0.022f;
    if (barH < 8.0f) barH = 8.0f;
    if (barH > 18.0f) barH = 18.0f;
    const float barX = (W - barW) * 0.5f;
    const float barY = H * 0.60f;
    const float radius = barH * 0.5f;

    // Action label, centred just above the bar.
    const int fpx = mGfx->fontBasePx() > 0 ? mGfx->fontBasePx() : 18;
    float labelScale = (H * 0.040f) / (float)fpx;
    if (labelScale < 0.65f) labelScale = 0.65f;
    if (labelScale > 3.0f) labelScale = 3.0f;
    if (label && label[0]) {
        float tw = mGfx->measure(label, labelScale);
        float lineH = (float)fpx * labelScale;
        mGfx->text(label, (W - tw) * 0.5f, barY - lineH - H * 0.035f,
                   labelScale, rgba(1.0f, 1.0f, 1.0f, 0.96f));
    }

    // Track.
    mGfx->roundedRect(barX, barY, barW, barH, radius, rgba(1.0f, 1.0f, 1.0f, 0.16f));

    const Color accent = rgba(0.32f, 0.68f, 1.0f, 0.98f);
    if (progress >= 0.0f) {
        float p = progress;
        if (p < 0.0f) p = 0.0f;
        if (p > 1.0f) p = 1.0f;
        float fw = barW * p;
        if (fw > 0.0f && fw < barH) fw = barH;   // keep a visible rounded cap
        if (fw > barW) fw = barW;
        if (fw > 0.0f)
            mGfx->roundedRect(barX, barY, fw, barH, radius, accent);
        // Percent under the bar.
        char pct[16];
        snprintf(pct, sizeof(pct), "%d%%", (int)(p * 100.0f + 0.5f));
        float pctScale = (H * 0.028f) / (float)fpx;
        if (pctScale < 0.5f) pctScale = 0.5f;
        float pw = mGfx->measure(pct, pctScale);
        mGfx->text(pct, (W - pw) * 0.5f, barY + barH + H * 0.028f, pctScale,
                   rgba(1.0f, 1.0f, 1.0f, 0.80f));
    } else if (progress <= kBusy) {
        // Static "working": leave the empty track (no marquee, no %), so a
        // single frame drawn before a blocking call never looks frozen.
    } else {
        // Indeterminate marquee: a segment that ping-pongs across the track.
        const float seg = barW * 0.30f;
        const int period = 96;
        float ph = (float)(mFrame % period) / (float)period;   // 0..1
        float pos = ph * 2.0f;
        if (pos > 1.0f) pos = 2.0f - pos;                      // ping-pong 0..1..0
        float fx = barX + (barW - seg) * pos;
        mGfx->roundedRect(fx, barY, seg, barH, radius, accent);
    }

    mGfx->endFrame();
}

void LoadingScreen::frame(const char* label, float progress) {
    if (mSf) {
        drastic_nano::FrameTargets t = mSf->acquireFrameTargets();
        if (t.primaryW == 0 || t.primaryH == 0) return;   // surface not ready
        mSf->bindPrimary();
        drawInto((int)t.primaryW, (int)t.primaryH,
                 (int)t.primaryW, (int)t.primaryH, kIdentityMat, label, progress);
        if (mSf->hasSecondary()) {
            mSf->bindSecondary();
            glViewport(0, 0, (int)t.secondaryW, (int)t.secondaryH);
            glClearColor(0.035f, 0.043f, 0.063f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        mSf->present(t);
    } else {
        if (!android::sDrmActive || !android::sDrmZeroCopy ||
            android::sAhbRingPrimary[0].glFbo == 0)
            return;
        const int pw = (int)android::sAhbRingPrimary[0].w;
        const int ph = (int)android::sAhbRingPrimary[0].h;
        glBindFramebuffer(GL_FRAMEBUFFER, android::sAhbRingPrimary[0].glFbo);
        drawInto(pw, ph, pw, ph, android::sDrmRotMat, label, progress);
        if (android::sAhbRingSecondary[0].glFbo != 0) {
            glBindFramebuffer(GL_FRAMEBUFFER, android::sAhbRingSecondary[0].glFbo);
            glViewport(0, 0, (int)android::sAhbRingSecondary[0].w,
                       (int)android::sAhbRingSecondary[0].h);
            glClearColor(0.035f, 0.043f, 0.063f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glFinish();
        android::drmFlipAll();
    }
    mFrame++;
    mLastDrawMs = android::elapsedRealtime();
}

void LoadingScreen::frameThrottled(const char* label, float progress) {
    const int64_t now = android::elapsedRealtime();
    const bool force = (progress >= 0.999f);
    if (!force && mLastDrawMs != 0 && (now - mLastDrawMs) < 40) return;
    frame(label, progress);
}

}  // namespace drastic_load
}  // namespace android
