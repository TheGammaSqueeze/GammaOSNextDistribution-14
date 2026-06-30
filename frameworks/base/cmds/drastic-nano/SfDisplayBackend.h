// SfDisplayBackend.h
//
// SurfaceFlinger display backend for drastic-nano.
//
// This backend renders the emulated DS frame into one or more SurfaceFlinger
// layers and lets the system compositor scan them out, so drastic-nano runs on
// devices whose panels belong to SurfaceFlinger instead of a direct DRM-master
// grab. It holds a SurfaceComposerClient session, one opaque full-screen
// SurfaceControl per physical display it drives, and one EGL window surface per
// SurfaceControl sharing a single GLES2 context. present() hands each frame to
// SurfaceFlinger with eglSwapBuffers; the BufferQueue paces the producer to the
// panel vsync, so this backend keeps no flip ring and waits on no vblank ioctl.
//
// The backend never grabs DRM master. It coexists with SurfaceFlinger as an
// ordinary opaque client layer in the bootanim SELinux domain, the same way the
// gammaos-nano overlay does, so it needs no additional sepolicy. The recipe here
// mirrors gammaos-nano's NanoMenu::initSurfaceFlingerPath, which proves every
// call on-device.

#pragma once

#include "DisplayBackend.h"

#include <cstdint>
#include <condition_variable>
#include <mutex>

#include <binder/IBinder.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>
#include <gui/Surface.h>
#include <ui/DisplayId.h>
#include <utils/StrongPointer.h>

namespace drastic_nano {

// One driven physical display: its SurfaceFlinger layer, the EGL window surface
// that renders into it, and the layer's size. A single-screen session uses one
// of these (index 0); a dual-screen session uses two.
struct SfDisplay {
    android::sp<android::SurfaceControl> control;
    android::sp<android::Surface>        surface;     // the SurfaceControl's ANativeWindow
    EGLSurface                           eglSurface = EGL_NO_SURFACE;
    uint32_t                             width = 0;
    uint32_t                             height = 0;
    uint32_t                             layerStack = 0;
    android::sp<android::IBinder>        token;        // physical display token
};

class SfDisplayBackend : public IDisplayBackend {
public:
    SfDisplayBackend();
    ~SfDisplayBackend() override;

    bool createContext(DisplayEnv* env) override;

    // Host-surface mode: instead of creating our own SurfaceFlinger layer (which
    // strands on a DRM-boot device), render into a Surface handed to us by the
    // DrasticSf host activity over binder. The activity is an ordinary app whose
    // SurfaceView SurfaceFlinger composites normally, so the frame actually
    // reaches the panel. Set before createContext(). Off = own-layer mode (a
    // pure-SF device whose panels already belong to SurfaceFlinger).
    void setHostSurfaceMode(bool on) { mHostSurface = on; }

    // Called by the binder receiver when the activity provides its Surface.
    void onHostSurfaceReceived(const android::sp<android::Surface>& s);

    int  displayCount() const override { return mDisplayCount; }
    bool hasSecondary() const override { return mDisplayCount >= 2; }
    void primarySize(uint32_t* w, uint32_t* h) const override;
    void secondarySize(uint32_t* w, uint32_t* h) const override;

    ComposeMode composeMode() const override {
        // One window holding both screens when there is a single display; one
        // window per screen when two displays are driven.
        return (mDisplayCount >= 2) ? ComposeMode::kDualTarget
                                    : ComposeMode::kLayoutPreset;
    }

    FrameTargets acquireFrameTargets() override;
    void bindPrimary() override;
    void bindSecondary() override;

    void present(const FrameTargets& targets) override;
    void waitForVsync() override {}            // the BufferQueue paces eglSwapBuffers

    void rotationMatrix(float out[4]) const override;
    bool usesPanelNativeViewport() const override { return false; }

    void blank() override;
    void resume() override;
    void teardown() override;

    const char* name() const override { return "sf"; }

private:
    // Brings up one SurfaceControl + EGL window surface on the given physical
    // display. The control is opaque RGBX at native resolution, so SurfaceFlinger
    // occlusion-culls the home and overlay layers beneath the game and, where the
    // panel supports it, scans the buffer out on a dedicated hardware plane. The
    // returned EGL surface shares the backend's single GLES2 context, so the same
    // DS shaders and VBOs draw to either display without a second context.
    bool createDisplaySurface(const android::PhysicalDisplayId& id, SfDisplay* out);

    // Rebuild the host EGL window surface when the activity has handed us a newly
    // recreated SurfaceView producer (host-surface mode only). Runs on the render
    // thread from bindPrimary() so the EGL context is current. No-op unless a new
    // producer is pending.
    void maybeAdoptNewHostSurface();

    EGLDisplay mEglDpy = EGL_NO_DISPLAY;
    EGLContext mEglCtx = EGL_NO_CONTEXT;
    EGLConfig  mEglCfg = nullptr;

    android::sp<android::SurfaceComposerClient> mSession;
    android::sp<android::IBinder::DeathRecipient> mDeath;

    // Host-surface mode (render into the DrasticSf activity's Surface).
    bool                         mHostSurface = false;
    android::sp<android::IBinder> mHostReceiver;     // the binder service we publish
    std::mutex                   mHostMu;
    std::condition_variable      mHostCv;
    android::sp<android::Surface> mHostSurfaceObj;   // latest surface the activity sent
    // The producer currently bound to mDisplays[0].eglSurface. The activity hands
    // us a fresh Surface object on every surfaceCreated/Changed callback; we
    // compare the underlying PRODUCERS so a true SurfaceView recreation (the
    // DRM->SurfaceFlinger takeover relayout destroys + recreates it) triggers a
    // rebuild, while a re-send of the same producer is ignored. Without this,
    // drastic keeps swapping into the dead pre-takeover surface and the panel
    // stays black even though it renders at full rate.
    android::sp<android::Surface> mAdoptedHostSurface;
    bool                          mHostSurfaceDirty = false;
    DisplayEnv*                   mEnv = nullptr;     // for live updates on re-adopt

    SfDisplay mDisplays[2];
    int       mDisplayCount = 0;
};

} // namespace drastic_nano
