// SfDisplayBackend.cpp
//
// Implements the SurfaceFlinger backend. See SfDisplayBackend.h for the model.
// The bring-up mirrors gammaos-nano's NanoMenu::initSurfaceFlingerPath so the SF
// client behaves the same way the on-device overlay already does.

#include "SfDisplayBackend.h"

#include <chrono>
#include <cstdlib>
#include <signal.h>
#include <unistd.h>

#include <android/native_window.h>
#include <binder/Binder.h>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <binder/ProcessState.h>
#include <cutils/properties.h>
#include <gui/view/Surface.h>
#include <log/log.h>
#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>
#include <ui/Rect.h>
#include <ui/Rotation.h>
#include <ui/Size.h>

#include "NanoMenuDrm.h"   // android::getEglConfig(EGLDisplay, bool)

using android::sp;
using android::String8;
using android::IBinder;
using android::SurfaceComposerClient;
using android::SurfaceControl;
using android::Surface;
using android::PhysicalDisplayId;

namespace drastic_nano {

namespace {

// Death recipient for the SurfaceFlinger session. When the compositor dies the
// game must not keep painting into a dead BufferQueue, so the process exits hard
// and init brings drastic-nano (or the home) back cleanly. This mirrors the
// overlay's binderDied behavior.
class SfComposerDeath : public IBinder::DeathRecipient {
public:
    void binderDied(const android::wp<IBinder>&) override {
        ALOGE("drastic-nano: SurfaceFlinger died; exiting");
        kill(getpid(), SIGKILL);
    }
};

// Binder service the DrasticSf host activity calls to hand us its SurfaceView
// Surface. One transaction (FIRST_CALL_TRANSACTION, oneway) carrying a
// view::Surface, written by the Java side with Surface.writeToParcel under the
// interface token below. Permissive SELinux on these builds lets the platform
// app reach this root-published service without a custom service_contexts entry
// (a production enforcing build would add one).
class HostSurfaceReceiver : public android::BBinder {
public:
    explicit HostSurfaceReceiver(SfDisplayBackend* be) : mBackend(be) {}
    android::status_t onTransact(uint32_t code, const android::Parcel& data,
                                 android::Parcel* reply, uint32_t flags) override {
        if (code == android::IBinder::FIRST_CALL_TRANSACTION) {
            data.enforceInterface(
                    android::String16("com.gammaos.drasticsf.IDrasticSurface"));
            android::view::Surface vs;
            if (vs.readFromParcel(&data) == android::OK &&
                vs.graphicBufferProducer != nullptr) {
                sp<Surface> surf = new Surface(vs.graphicBufferProducer,
                                               /*controlledByApp=*/true);
                mBackend->onHostSurfaceReceived(surf);
            } else {
                ALOGE("drastic-nano: SF host surface parcel invalid");
            }
            return android::OK;
        }
        return android::BBinder::onTransact(code, data, reply, flags);
    }
private:
    SfDisplayBackend* mBackend;
};

} // namespace

void SfDisplayBackend::onHostSurfaceReceived(const sp<Surface>& s) {
    std::lock_guard<std::mutex> lk(mHostMu);
    // Dedup by the underlying buffer producer. The activity re-sends a NEW Surface
    // object wrapping the SAME producer on every surfaceCreated/Changed callback,
    // so comparing Surface identities would force a rebuild every frame. Only a
    // genuinely recreated SurfaceView (the DRM->SurfaceFlinger takeover relayout
    // destroys + recreates it) carries a different producer and must be adopted.
    if (s != nullptr && mAdoptedHostSurface != nullptr &&
        s->getIGraphicBufferProducer() ==
                mAdoptedHostSurface->getIGraphicBufferProducer()) {
        return;
    }
    mHostSurfaceObj = s;
    mHostSurfaceDirty = true;
    mHostCv.notify_all();
    ALOGI("drastic-nano: SF host surface received");
}

// Render-thread side of the surface handoff. When the activity's SurfaceView is
// recreated during the DRM->SurfaceFlinger takeover, the producer we adopted at
// createContext() is dead -- eglSwapBuffers into it is silently dropped and the
// panel freezes black while drastic still renders at full rate. Rebuild the EGL
// window surface on the new producer so frames reach the live layer. Called from
// bindPrimary() (context current, no concurrent draw) before each frame's draws.
void SfDisplayBackend::maybeAdoptNewHostSurface() {
    sp<Surface> ns;
    {
        std::lock_guard<std::mutex> lk(mHostMu);
        if (!mHostSurfaceDirty) return;
        mHostSurfaceDirty = false;
        ns = mHostSurfaceObj;
    }
    if (ns == nullptr) return;
    if (mAdoptedHostSurface != nullptr &&
        ns->getIGraphicBufferProducer() ==
                mAdoptedHostSurface->getIGraphicBufferProducer()) {
        return;  // already on this producer
    }
    // Fully unbind before destroying the old window surface (avoids needing the
    // surfaceless-context extension), then build the new one and make it current.
    eglMakeCurrent(mEglDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (mDisplays[0].eglSurface != EGL_NO_SURFACE) {
        eglDestroySurface(mEglDpy, mDisplays[0].eglSurface);
        mDisplays[0].eglSurface = EGL_NO_SURFACE;
    }
    ANativeWindow* win = ns.get();
    EGLSurface es = eglCreateWindowSurface(mEglDpy, mEglCfg, win, nullptr);
    if (es == EGL_NO_SURFACE) {
        ALOGE("drastic-nano: SF host re-adopt eglCreateWindowSurface failed: 0x%x",
              eglGetError());
        // Re-arm so the next frame retries; leave the old (now destroyed) surface
        // null. eglMakeCurrent below would fail, so restore the previous binding is
        // impossible -- a retry on the next dirty signal is the recovery path.
        std::lock_guard<std::mutex> lk(mHostMu);
        mHostSurfaceDirty = true;
        return;
    }
    int w = ANativeWindow_getWidth(win);
    int h = ANativeWindow_getHeight(win);
    mDisplays[0].surface = ns;
    mDisplays[0].eglSurface = es;
    if (w <= 0) eglQuerySurface(mEglDpy, es, EGL_WIDTH, &w);
    if (h <= 0) eglQuerySurface(mEglDpy, es, EGL_HEIGHT, &h);
    if (w > 0) mDisplays[0].width = (uint32_t)w;
    if (h > 0) mDisplays[0].height = (uint32_t)h;
    eglMakeCurrent(mEglDpy, es, es, mEglCtx);
    eglSwapInterval(mEglDpy, 0);
    {
        std::lock_guard<std::mutex> lk(mHostMu);
        mAdoptedHostSurface = ns;
    }
    if (mEnv != nullptr) {
        mEnv->eglSurf = es;
        mEnv->width = (int)mDisplays[0].width;
        mEnv->height = (int)mDisplays[0].height;
    }
    ALOGI("drastic-nano: SF host surface RE-ADOPTED %ux%u",
          mDisplays[0].width, mDisplays[0].height);
}

SfDisplayBackend::SfDisplayBackend() = default;

SfDisplayBackend::~SfDisplayBackend() {
    teardown();
}

bool SfDisplayBackend::createContext(DisplayEnv* env) {
    // A SurfaceComposerClient and its death link need a live binder thread, which
    // a DRM-direct binary otherwise never starts.
    android::ProcessState::self()->startThreadPool();

    // Primary swap interval (SF path only). Default 0 = free-run + main.cpp's
    // nanosleep cap (the shipped pacing). sf_vsync=1 phase-locks the present loop
    // to the panel vblank (blocks in eglSwapBuffers), which is smoother ONLY when
    // the frame already fits in the ~16.67 ms vblank budget: on-device A/B on
    // Pokemon White 2 (TrimUI Brick, max perf) showed the SF frame is GPU-bound at
    // ~53 fps (~18.9 ms) so it overruns the vblank, and vsync-lock then drops it to
    // ~45 fps (each missed vblank waits a whole frame) -- worse than the ~53 fps
    // free-run. So it stays OFF until the per-frame GPU cost is cut below 16.67 ms
    // (drop the layout-offscreen blit + the twice-per-frame fxSetup + 16-bit fb);
    // then flipping this to 1 gives a clean locked 60. Kept as a ready toggle.
    mSfSwapInterval =
            property_get_int32("persist.gammaos.drastic_nano.sf_vsync", 0) != 0
                    ? 1
                    : 0;

    // EGL display + a GLES2 context shared by every window surface this backend
    // creates. The config comes from the shared helper, which returns an
    // RGBX/window-capable config (the overlay creates window surfaces with it).
    mEglDpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (mEglDpy == EGL_NO_DISPLAY || !eglInitialize(mEglDpy, nullptr, nullptr)) {
        ALOGE("drastic-nano: SF eglInitialize failed: 0x%x", eglGetError());
        return false;
    }
    mEglCfg = android::getEglConfig(mEglDpy, /*wantAlpha=*/false);
    const EGLint ctxAttrs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    mEglCtx = eglCreateContext(mEglDpy, mEglCfg, EGL_NO_CONTEXT, ctxAttrs);
    if (mEglCtx == EGL_NO_CONTEXT) {
        ALOGE("drastic-nano: SF eglCreateContext failed: 0x%x", eglGetError());
        return false;
    }

    // Host-surface mode: publish a binder service, wait for the DrasticSf
    // activity to hand us its SurfaceView Surface, and render straight into it.
    // SurfaceFlinger composites the activity's window like any app, so the frame
    // reaches the panel -- our own client layer strands on a DRM-boot device.
    if (mHostSurface) {
        android::ProcessState::self()->startThreadPool();
        mHostReceiver = new HostSurfaceReceiver(this);
        android::defaultServiceManager()->addService(
                android::String16("drastic.sf.surface"), mHostReceiver);
        ALOGI("drastic-nano: SF host-surface mode -- waiting for DrasticSf surface");
        sp<Surface> surf;
        {
            std::unique_lock<std::mutex> lk(mHostMu);
            mHostCv.wait_for(lk, std::chrono::seconds(20),
                             [&] { return mHostSurfaceObj != nullptr; });
            surf = mHostSurfaceObj;
        }
        if (surf == nullptr) {
            ALOGE("drastic-nano: SF host surface never arrived");
            return false;
        }
        ANativeWindow* win = surf.get();
        int w = ANativeWindow_getWidth(win);
        int h = ANativeWindow_getHeight(win);
        mDisplays[0].surface = surf;
        mDisplays[0].eglSurface =
                eglCreateWindowSurface(mEglDpy, mEglCfg, win, nullptr);
        if (mDisplays[0].eglSurface == EGL_NO_SURFACE) {
            ALOGE("drastic-nano: SF host eglCreateWindowSurface failed: 0x%x",
                  eglGetError());
            return false;
        }
        if (w <= 0) eglQuerySurface(mEglDpy, mDisplays[0].eglSurface, EGL_WIDTH, &w);
        if (h <= 0) eglQuerySurface(mEglDpy, mDisplays[0].eglSurface, EGL_HEIGHT, &h);
        mDisplays[0].width  = (uint32_t)w;
        mDisplays[0].height = (uint32_t)h;
        mDisplayCount = 1;
        eglMakeCurrent(mEglDpy, mDisplays[0].eglSurface, mDisplays[0].eglSurface,
                       mEglCtx);
        eglSwapInterval(mEglDpy, 0);
        env->eglDpy = mEglDpy;
        env->eglCtx = mEglCtx;
        env->eglSurf = mDisplays[0].eglSurface;
        env->width = w;
        env->height = h;
        env->backendName = "sf";
        // Record what we adopted and keep the env so the render thread can rebuild
        // this window surface if the activity's SurfaceView is recreated (the
        // DRM->SurfaceFlinger takeover relayout) and a new producer arrives.
        {
            std::lock_guard<std::mutex> lk(mHostMu);
            mAdoptedHostSurface = surf;
            mHostSurfaceDirty = false;
        }
        mEnv = env;
        ALOGI("drastic-nano: SF host-surface up %dx%d", w, h);
        return true;
    }

    // Wait for SurfaceFlinger to be reachable, then open the session.
    {
        sp<android::IServiceManager> sm = android::defaultServiceManager();
        const android::String16 svc("SurfaceFlinger");
        for (int i = 0; i < 3000 && sm->checkService(svc) == nullptr; i++) {
            usleep(10000);   // up to ~30 s
        }
    }
    mSession = new SurfaceComposerClient();
    if (mSession->initCheck() != android::NO_ERROR) {
        ALOGE("drastic-nano: SurfaceComposerClient init failed");
        return false;
    }
    mDeath = new SfComposerDeath();
    mSession->linkToComposerDeath(mDeath);

    // Enumerate displays and decide single vs dual. screen_mode forces the count;
    // auto drives every panel that enumerates (capped at two).
    std::vector<PhysicalDisplayId> ids = SurfaceComposerClient::getPhysicalDisplayIds();
    for (int i = 0; i < 3000 && ids.empty(); i++) {
        usleep(10000);
        ids = SurfaceComposerClient::getPhysicalDisplayIds();
    }
    if (ids.empty()) {
        ALOGE("drastic-nano: SF no physical displays");
        return false;
    }

    char modeProp[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.drastic_nano.screen_mode", modeProp, "auto");
    const bool forceSingle = (strcmp(modeProp, "single") == 0);
    const bool forceDual   = (strcmp(modeProp, "dual") == 0);

    // Order the ids so the configured primary port leads, matching the DRM path
    // and the home so both backends pick the same panel.
    char primProp[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.primary_display", primProp, "0");
    const int wantPort = atoi(primProp);
    PhysicalDisplayId primaryId = ids.front();
    PhysicalDisplayId secondaryId{};
    bool haveSecondary = false;
    for (const PhysicalDisplayId& id : ids) {
        if (static_cast<int>(id.getPort()) == wantPort) primaryId = id;
    }
    for (const PhysicalDisplayId& id : ids) {
        if (!(id == primaryId)) { secondaryId = id; haveSecondary = true; break; }
    }

    const bool wantDual = forceDual || (!forceSingle && haveSecondary);

    if (!createDisplaySurface(primaryId, &mDisplays[0])) {
        ALOGE("drastic-nano: SF primary surface failed");
        return false;
    }
    mDisplayCount = 1;

    if (wantDual && haveSecondary) {
        // SurfaceFlinger parks a second panel inactive after an SF client, so
        // bring it on before claiming it (the exact wake call the home uses).
        sp<IBinder> secToken = SurfaceComposerClient::getPhysicalDisplayToken(secondaryId);
        if (secToken != nullptr) {
            SurfaceComposerClient::setDisplayPowerMode(secToken, 2 /*ON*/);
        }
        if (createDisplaySurface(secondaryId, &mDisplays[1])) {
            mDisplayCount = 2;
        } else {
            ALOGW("drastic-nano: SF secondary surface failed; single-screen fallback");
        }
    }

    // Make the primary current and report it to the loop. Swap interval 0 keeps
    // eglSwapBuffers from blocking on a vsync the compositor may not be ticking;
    // the BufferQueue still paces the producer by holding buffers until the layer
    // composites, the same choice the gammaos-nano overlay makes.
    eglMakeCurrent(mEglDpy, mDisplays[0].eglSurface, mDisplays[0].eglSurface, mEglCtx);
    eglSwapInterval(mEglDpy, 0);
    env->eglDpy = mEglDpy;
    env->eglCtx = mEglCtx;
    env->eglSurf = mDisplays[0].eglSurface;
    env->width  = (int)mDisplays[0].width;
    env->height = (int)mDisplays[0].height;
    env->backendName = "sf";
    ALOGI("drastic-nano: SF backend up, %d display(s), primary %ux%u",
          mDisplayCount, mDisplays[0].width, mDisplays[0].height);
    return true;
}

bool SfDisplayBackend::createDisplaySurface(const PhysicalDisplayId& id, SfDisplay* out) {
    out->token = SurfaceComposerClient::getPhysicalDisplayToken(id);
    if (out->token == nullptr) return false;

    android::ui::DisplayState state;
    android::ui::LayerStack stack = android::ui::DEFAULT_LAYER_STACK;
    const bool haveState =
            SurfaceComposerClient::getDisplayState(out->token, &state) == android::NO_ERROR;
    if (haveState) stack = state.layerStack;
    out->layerStack = stack.id;

    android::ui::DisplayMode mode;
    if (SurfaceComposerClient::getActiveDisplayMode(out->token, &mode) != android::NO_ERROR) {
        ALOGE("drastic-nano: SF getActiveDisplayMode failed");
        return false;
    }
    const android::ui::Size res = mode.resolution;
    const int panelW = res.getWidth();
    const int panelH = res.getHeight();

    // Decide the layer size and whether to set the display projection.
    //
    // On a device whose panels belong to SurfaceFlinger, the framework has
    // already projected each display, including any install rotation: its
    // layerStackSpaceRect is the logical, pre-rotation size apps render into,
    // and the projection rotates that onto the physical panel. A layer placed on
    // that display's layerStack inherits the projection, so the right thing is to
    // render at the logical size and NOT touch the projection. Overriding it
    // (which an earlier version did from ro.surface_flinger.primary_display_orientation,
    // a property the player's SELinux domain cannot even read) replaces the
    // framework's transform and blanks a rotated panel.
    //
    // The imposed path remains as a fallback for a display SurfaceFlinger has
    // never projected -- a unit whose home renders DRM-direct, where the
    // layer-stack space comes back empty -- so the layer still maps to the panel.
    int logicalW = 0, logicalH = 0;
    bool inheritProjection = false;
    android::ui::Rotation rot = android::ui::ROTATION_0;
    if (haveState && state.layerStackSpaceRect.getWidth() > 0 &&
        state.layerStackSpaceRect.getHeight() > 0) {
        logicalW = state.layerStackSpaceRect.getWidth();
        logicalH = state.layerStackSpaceRect.getHeight();
        inheritProjection = true;
    } else {
        char o[PROPERTY_VALUE_MAX] = {};
        property_get("ro.surface_flinger.primary_display_orientation", o, "");
        if (!strcmp(o, "ORIENTATION_90"))       rot = android::ui::ROTATION_90;
        else if (!strcmp(o, "ORIENTATION_180")) rot = android::ui::ROTATION_180;
        else if (!strcmp(o, "ORIENTATION_270")) rot = android::ui::ROTATION_270;
        const bool swapWH = (rot == android::ui::ROTATION_90 ||
                             rot == android::ui::ROTATION_270);
        logicalW = swapWH ? panelH : panelW;
        logicalH = swapWH ? panelW : panelH;
    }
    out->width  = (uint32_t)logicalW;
    out->height = (uint32_t)logicalH;
    ALOGI("drastic-nano: SF display logical %dx%d panel %dx%d (%s projection)",
          logicalW, logicalH, panelW, panelH,
          inheritProjection ? "inherit" : "impose");

    // Opaque RGBX layer at the logical size so SurfaceFlinger occlusion-culls the
    // home / overlay underneath the game and can scan it out on a hardware plane.
    out->control = mSession->createSurface(
        String8("drastic-nano"), logicalW, logicalH,
        android::PIXEL_FORMAT_RGBX_8888, android::ISurfaceComposerClient::eOpaque);
    if (out->control == nullptr || !out->control->isValid()) {
        ALOGE("drastic-nano: SF createSurface failed");
        return false;
    }

    // The layer sits just above the home XMB (0x40000001). Only impose a
    // projection when the framework has not already configured one (see above).
    SurfaceComposerClient::Transaction t;
    if (!inheritProjection) {
        const android::Rect layerRect(0, 0, logicalW, logicalH);
        const android::Rect panelRect(0, 0, panelW, panelH);
        t.setDisplayProjection(out->token, rot, layerRect, panelRect);
    }
    t.setLayer(out->control, 0x40000002);
    t.setLayerStack(out->control, stack);
    t.setFrameRate(out->control, 60.0f,
                   ANATIVEWINDOW_FRAME_RATE_COMPATIBILITY_DEFAULT,
                   ANATIVEWINDOW_CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
    t.show(out->control);
    t.apply();

    out->surface = out->control->getSurface();
    out->surface->setMaxDequeuedBufferCount(3);   // steady 60, avoids release-paced dequeue stall
    out->eglSurface = eglCreateWindowSurface(mEglDpy, mEglCfg, out->surface.get(), nullptr);
    if (out->eglSurface == EGL_NO_SURFACE) {
        ALOGE("drastic-nano: SF eglCreateWindowSurface failed: 0x%x", eglGetError());
        return false;
    }
    return true;
}

void SfDisplayBackend::primarySize(uint32_t* w, uint32_t* h) const {
    if (w) *w = mDisplays[0].width;
    if (h) *h = mDisplays[0].height;
}

void SfDisplayBackend::secondarySize(uint32_t* w, uint32_t* h) const {
    const SfDisplay& d = (mDisplayCount >= 2) ? mDisplays[1] : mDisplays[0];
    if (w) *w = d.width;
    if (h) *h = d.height;
}

FrameTargets SfDisplayBackend::acquireFrameTargets() {
    FrameTargets ft;
    ft.primaryFbo = 0;                    // the window's default framebuffer
    ft.primaryW = mDisplays[0].width;
    ft.primaryH = mDisplays[0].height;
    ft.secondaryFbo = 0;
    if (mDisplayCount >= 2) {
        ft.secondaryW = mDisplays[1].width;
        ft.secondaryH = mDisplays[1].height;
    }
    ft.slot = 0;
    return ft;
}

void SfDisplayBackend::bindPrimary() {
    // Host-surface mode: pick up a SurfaceView that was recreated during the
    // DRM->SurfaceFlinger takeover before binding, so this frame draws into the
    // live producer instead of the dead one. Idempotent (no-op unless dirty).
    if (mHostSurface) maybeAdoptNewHostSurface();
    eglMakeCurrent(mEglDpy, mDisplays[0].eglSurface, mDisplays[0].eglSurface, mEglCtx);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

void SfDisplayBackend::bindSecondary() {
    if (mDisplayCount < 2) return;
    eglMakeCurrent(mEglDpy, mDisplays[1].eglSurface, mDisplays[1].eglSurface, mEglCtx);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

void SfDisplayBackend::present(const FrameTargets&) {
    // Hand each window's frame to SurfaceFlinger. The secondary swaps first so the
    // primary is the last current surface for the next frame's draws, matching the
    // DRM flip order and leaving the loop on the primary by default.
    //
    // The swap interval is (re)asserted per surface each frame because it is a
    // property of the current draw surface and present() re-makes each surface
    // current: the secondary always swaps at interval 0 so it returns immediately
    // and never adds a second vblank wait, then the primary swaps at
    // mSfSwapInterval. With interval 1 the primary eglSwapBuffers blocks until the
    // panel vblank, which is what phase-locks the whole loop to the display refresh
    // (smooth pacing); main.cpp's trailing nanosleep then sees the full frame
    // already elapsed and becomes a no-op, and remains a safety cap if a device
    // ever reports interval 1 but does not actually block.
    if (mDisplayCount >= 2) {
        eglMakeCurrent(mEglDpy, mDisplays[1].eglSurface, mDisplays[1].eglSurface, mEglCtx);
        eglSwapInterval(mEglDpy, 0);
        eglSwapBuffers(mEglDpy, mDisplays[1].eglSurface);
    }
    eglMakeCurrent(mEglDpy, mDisplays[0].eglSurface, mDisplays[0].eglSurface, mEglCtx);
    eglSwapInterval(mEglDpy, mSfSwapInterval);
    eglSwapBuffers(mEglDpy, mDisplays[0].eglSurface);
}

void SfDisplayBackend::rotationMatrix(float out[4]) const {
    // Identity: the SF layer is already display-space and the compositor owns any
    // panel rotation, so the loop draws with a logical viewport and no pre-rotate.
    out[0] = 1.0f; out[1] = 0.0f; out[2] = 0.0f; out[3] = 1.0f;
}

void SfDisplayBackend::blank() {
    // Push one black frame to each display so the panel shows nothing while the
    // device sleeps; the layer stays parented so resume() needs no rebuild.
    for (int i = 0; i < mDisplayCount; i++) {
        eglMakeCurrent(mEglDpy, mDisplays[i].eglSurface, mDisplays[i].eglSurface, mEglCtx);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, (GLsizei)mDisplays[i].width, (GLsizei)mDisplays[i].height);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        eglSwapBuffers(mEglDpy, mDisplays[i].eglSurface);
    }
    eglMakeCurrent(mEglDpy, mDisplays[0].eglSurface, mDisplays[0].eglSurface, mEglCtx);
}

void SfDisplayBackend::resume() {
    // The layers are still parented and shown, so the next rendered frame appears
    // on its own; the panel power and backlight are handled by the caller's sleep
    // logic. Re-assert the power mode in case SurfaceFlinger parked a panel.
    for (int i = 0; i < mDisplayCount; i++) {
        if (mDisplays[i].token != nullptr) {
            SurfaceComposerClient::setDisplayPowerMode(mDisplays[i].token, 2 /*ON*/);
        }
    }
}

void SfDisplayBackend::teardown() {
    // Reparent each layer to null so SurfaceFlinger recomposites the home behind
    // it, then drop the EGL surfaces. The shared EGL display/context are released
    // by the caller's EGL teardown.
    if (mSession != nullptr) {
        SurfaceComposerClient::Transaction t;
        for (int i = 0; i < mDisplayCount; i++) {
            if (mDisplays[i].control != nullptr) {
                t.reparent(mDisplays[i].control, nullptr);
            }
        }
        t.apply();
    }
    for (int i = 0; i < 2; i++) {
        if (mDisplays[i].eglSurface != EGL_NO_SURFACE && mEglDpy != EGL_NO_DISPLAY) {
            eglDestroySurface(mEglDpy, mDisplays[i].eglSurface);
            mDisplays[i].eglSurface = EGL_NO_SURFACE;
        }
        mDisplays[i].surface.clear();
        mDisplays[i].control.clear();
    }
    mDisplayCount = 0;
    // The session and its death link drop with the strong pointers below; the
    // process is exiting or relaunching, so there is no live composite to detach
    // from first.
    mDeath.clear();
    mSession.clear();
}

} // namespace drastic_nano
