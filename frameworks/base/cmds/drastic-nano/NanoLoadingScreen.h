/*
 * Copyright (C) 2026 GammaOS
 *
 * NanoLoadingScreen: a transient loading screen (dark backdrop + centred
 * action text + an activity progress bar) drawn on drastic-nano's own GL
 * context BEFORE the game render loop starts, so a zipped-ROM extraction or a
 * multi-second cold load never shows a blank panel.
 *
 * It works on BOTH display backends:
 *   - SurfaceFlinger (own-layer / host-surface): renders into the backend's
 *     window surface via acquireFrameTargets()/present().
 *   - DRM-direct: renders into the primary AHB ring slot and drmFlipAll()s it,
 *     the same standalone draw+present idiom doSleep() uses.
 *
 * Instantiate after the display backend is up and the EGL context is current;
 * call frame()/frameThrottled() to present one loading frame; call shutdown()
 * before the render loop creates its own OverlayGfx.
 */

#pragma once

#include <cstdint>

namespace android { namespace drastic_gfx { class OverlayGfx; } }
namespace drastic_nano { class IDisplayBackend; }

namespace android {
namespace drastic_load {

// Progress sentinels for frame()/frameThrottled():
//   >= 0     determinate: fill + "N%".
constexpr float kIndeterminate = -1.0f;  // animated marquee (redraw to animate)
constexpr float kBusy          = -2.0f;  // static "working" (empty track, no %)

class LoadingScreen {
public:
    LoadingScreen() = default;
    ~LoadingScreen();

    // sfBackend != nullptr selects the SurfaceFlinger path; nullptr selects the
    // DRM-direct path. fallbackW/H are used only for internal sizing hints.
    // Always succeeds cheaply; whether a frame can actually be drawn is decided
    // per-frame (a torn-down / not-yet-ready surface is a safe no-op).
    void init(drastic_nano::IDisplayBackend* sfBackend, int fallbackW, int fallbackH);
    void shutdown();

    // Present one loading frame. label is the current action ("Extracting
    // ROM..."). progress is [0,1] for a determinate bar, or a sentinel above.
    void frame(const char* label, float progress);

    // Same, but draws at most ~every 40ms (progress>=1.0 always draws), so a
    // high-frequency progress callback does not flood the panel with flips.
    void frameThrottled(const char* label, float progress);

private:
    void drawInto(int fbW, int fbH, int logicalW, int logicalH,
                  const float* rot, const char* label, float progress);

    drastic_nano::IDisplayBackend* mSf = nullptr;
    android::drastic_gfx::OverlayGfx* mGfx = nullptr;
    int     mFallbackW = 0, mFallbackH = 0;
    int     mGfxW = 0, mGfxH = 0;   // logical size the OverlayGfx is inited for
    bool    mGfxInited = false;
    int     mFrame = 0;
    int64_t mLastDrawMs = 0;
};

}  // namespace drastic_load
}  // namespace android
