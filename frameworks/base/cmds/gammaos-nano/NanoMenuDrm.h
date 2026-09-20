/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef GAMMAOS_NANO_MENU_DRM_H
#define GAMMAOS_NANO_MENU_DRM_H

#include <stdint.h>
#include <vector>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <drm.h>
#include <drm_mode.h>
#include <android/hardware_buffer.h>

// ARM NEON intrinsics for fast AHB->DRM channel swap.
// Targeting ARMv8-A (Cortex-A55 on RK3568). NEON is mandatory on AArch64,
// so the __ARM_NEON define is always set when building 64-bit. Retain the
// scalar fallback for portability to hypothetical non-NEON targets.
#if defined(__ARM_NEON) || defined(__aarch64__)
#define GAMMAOS_NANO_HAVE_NEON 1
#else
#define GAMMAOS_NANO_HAVE_NEON 0
#endif

namespace android {

// ---------------------------------------------------------------------------
// DRM structs
// ---------------------------------------------------------------------------

struct DrmBuffer {
    uint32_t handle;
    uint32_t fbId;
    uint32_t pitch;
    size_t size;
    void* mapped;
    // Zero-copy GPU->DRM: DMA-BUF fd + EGLImage + GL texture + FBO for direct rendering
    int dmaFd;
    EGLImageKHR eglImage;
    GLuint glTexture;
    GLuint glFbo;
};

struct DrmDisplay {
    uint32_t crtcId;
    uint32_t connId;
    uint32_t w, h;
    DrmBuffer buffers[2];  // double buffer
    int activeBuffer;       // index currently being displayed
    struct drm_mode_modeinfo mode;
    // Cached primary plane + its FB_ID property, for the low-latency atomic
    // dual-CRTC flip (drmAtomicDualFlip). 0 until lazily discovered.
    uint32_t planeId = 0;
    uint32_t fbIdProp = 0;
    // AFBC + Cluster low-latency dual-DSI sync path (rk356x). When sDrmAfbcMode
    // the flip drives an AFBC-capable Cluster plane -- which, unlike the Smart
    // plane, does NOT carry the per-VP output-pipeline offset that desyncs the
    // two DSI panels -- instead of the Smart primary. All 0 until discovered by
    // drmEnsureClusterPlane; clConfigured latches after the one-time full
    // geometry commit (subsequent flips only touch FB_ID).
    uint32_t clPlaneId = 0;       // Cluster (AFBC) plane bound to this crtc
    uint32_t clFbIdProp = 0;
    uint32_t clCrtcIdProp = 0;
    uint32_t clCrtcXProp = 0, clCrtcYProp = 0, clCrtcWProp = 0, clCrtcHProp = 0;
    uint32_t clSrcXProp = 0, clSrcYProp = 0, clSrcWProp = 0, clSrcHProp = 0;
    uint32_t clBlendProp = 0, clAlphaProp = 0; uint64_t clBlendNone = 0;
    uint32_t clInFenceProp = 0;
    uint32_t smartCrtcIdProp = 0; // Smart primary CRTC_ID prop (to disable it)
    bool     clConfigured = false;
};

struct AhbRenderTarget {
    AHardwareBuffer* ahb;
    EGLImageKHR eglImage;
    GLuint glTexture;
    GLuint glFbo;
    uint32_t w, h;
    // DRM PRIME zero-copy: when non-zero, the AHB's underlying dma-buf was
    // imported into DRM as a scanout framebuffer (DRM_FORMAT_ABGR8888 since
    // the AHB is R8G8B8A8_UNORM in memory). drmFlipRingSlot can then page
    // flip directly to this fb_id, skipping the AHB-CPU-lock + memcpy +
    // DRM-dumb-buffer path entirely (~2-3 ms CPU saved per iter per
    // display on RG DS, ~5-8 ms on 1080p panels).
    uint32_t drmFbId;
    uint32_t drmGemHandle;
    // Turned panel (nano home DRM-direct): when non-zero, glFbo is a shared scratch
    // FBO the passes render into unchanged and scanFbo is the real AHB FBO that
    // drmResolveTurnedTargets() fills with the scratch image turned 180 degrees.
    uint32_t scanFbo;
};

// ---------------------------------------------------------------------------
// EGL extension typedefs
// ---------------------------------------------------------------------------

typedef EGLClientBuffer (EGLAPIENTRYP PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC) (const struct AHardwareBuffer *buffer);

#ifndef EGL_SYNC_NATIVE_FENCE_ANDROID
#define EGL_SYNC_NATIVE_FENCE_ANDROID 0x3144
#endif
#ifndef EGL_NO_NATIVE_FENCE_FD_ANDROID
#define EGL_NO_NATIVE_FENCE_FD_ANDROID -1
#endif

typedef EGLSyncKHR (EGLAPIENTRYP PFNEGLCREATESYNCKHRPROC_LOCAL)(EGLDisplay, EGLenum, const EGLint*);
typedef EGLBoolean (EGLAPIENTRYP PFNEGLDESTROYSYNCKHRPROC_LOCAL)(EGLDisplay, EGLSyncKHR);
typedef EGLint (EGLAPIENTRYP PFNEGLCLIENTWAITSYNCKHRPROC_LOCAL)(EGLDisplay, EGLSyncKHR, EGLint, EGLTimeKHR);
typedef EGLint (EGLAPIENTRYP PFNEGLDUPNATIVEFENCEFDANDROIDPROC_LOCAL)(EGLDisplay, EGLSyncKHR);

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

// Ring depth. Normally three slots are sufficient: render into N, fence in
// N-1, scanout in N-2. Frame-sync mode (sDrmFrameSync) delays the
// SECONDARY flip by one extra vblank so its logical content matches
// what the primary is showing at the same wall-clock moment. The
// existing flip order (secondary first, primary last) biases primary
// to land one vblank after secondary on RK3568 dual-DSI; delaying
// secondary by one userspace iter brings them back into logical
// alignment. That puts a fourth slot into the "in use" set (render N,
// fence N-1, primary scanout N-2, secondary scanout N-3), so we size
// the ring at five: four slots live at any time plus one spare to
// avoid re-using a slot while scanout still references it. Two extra
// AHBs of 512x192 ABGR is ~200 KiB -- negligible.
static constexpr int AHB_RING_DEPTH = 5;
static constexpr int kMaxCrtcTrack = 8;

// ---------------------------------------------------------------------------
// Extern variable declarations
// ---------------------------------------------------------------------------

extern int sDrmFd;
extern std::vector<DrmDisplay> sDrmDisplays;
extern bool sDrmActive;
extern int sDrmRotationDeg;
extern bool sDrmZeroCopy;
extern bool sDrmGlRotation;
extern bool sDrmYFlipForPrime;
// User-requested orientation correction for panels that scan out mirrored
// relative to the logical image. Read from persist.gammaos.nano.drm_flip_h
// and persist.gammaos.nano.drm_flip_v at boot by drmEarlySplash(). Applied
// to sDrmRotMat in initShaders() so vertex-space rendering is pre-flipped
// before PRIME scanout, and surfaced to fragment-space FX/XMB shaders via
// uXFlip and the composed uYFlip so procedural wallpapers stay consistent
// with the corrected vertex orientation. Defaults false (no-op).
extern bool sDrmFlipH;
extern bool sDrmFlipV;
extern bool sDrmVblankBroken;
// When true, delay the primary display's page flip by one refresh so it
// matches the secondary display's inherent 1-frame lag on dual-DSI
// setups that cannot be phase-locked (RK3568 VOP2: VP0/VP1 each run
// their own free-running clock; no kernel-level sync is exposed to
// userspace for independent content). Both panels end up 1 frame
// behind render time but stay visually synchronized with each other.
// Off by default -- adds 1 frame of input lag to the primary panel.
extern bool sDrmFrameSync;
// Low Latency Mode: when true, the drastic-nano AHB ring presents the previous
// frame (age 1) instead of the frame-before-that (age 2), removing ~one refresh
// (~16.7 ms) of input-to-photon latency at the cost of pipeline slack (the AHB
// fence wait can block under heavy GPU load). Read live in the render loop's
// present step. Inter-screen sync is handled by the kernel (rockchip,sync-vp-mask
// on the RG DS), so this is a pure latency-vs-throughput trade. Off by default.
extern bool sDrmLowLatency;
extern bool sDrmAfbcMode;
extern bool sDrmAfbcClient;      // drastic-nano opts into the AFBC combined-buffer path before setupDisplay()
extern int64_t sDrmLastVblankUs;
extern uint32_t sDrmSeamRotCrtc;
extern int sDrmSecondaryRotDeg;      // physical mount rotation of the VOP port 1 panel (persist.gsf.rot.1 / persist.gsf.sec_rot)
extern int sDrmVp1DisplayIdx;
extern bool sDrmPrimaryTurned;       // nano home: the primary ring scans the turned (port 1) panel
extern bool sDrmSecondaryTurned;     // nano home: the secondary ring scans the turned panel
// Before a ring slot's fence is created / it is flipped: copy each turned target's
// scratch image into its scanout AHB turned 180 degrees. No-op for untouched targets.
void drmResolveTurnedTargets(int idx);
// Physical mount rotation (degrees) of the panel on a display port, read from the
// same props SurfaceFlinger applies as that display's physical orientation
// (getPhysicalDisplayOrientation): persist.gsf.rot.<displayId> (a DSI panel's
// display id is its port) then persist.gsf.sec_rot for any non-primary port. On the
// SF path SurfaceFlinger composes it under every projection; the DRM-direct path
// has no SurfaceFlinger, so nano and drastic-nano apply it to that VOP port here.
int drmPanelMountRotationDeg(int port);
extern uint32_t sDrmAfbcHalfH;
extern int sPendingFlipEvents;
extern uint32_t sCrtcIds[kMaxCrtcTrack];
extern int sCrtcPending[kMaxCrtcTrack];
extern int sCrtcTrackCount;
extern int sVsyncEnabled;
extern bool sDrasticQrFastPath;
extern float sDrmRotMat[4];

// EGL extension function pointers
extern PFNEGLCREATEIMAGEKHRPROC sEglCreateImageKHR;
extern PFNEGLDESTROYIMAGEKHRPROC sEglDestroyImageKHR;
extern PFNGLEGLIMAGETARGETTEXTURE2DOESPROC sGlEGLImageTargetTexture2DOES;
extern PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC sEglGetNativeClientBufferANDROID;
extern PFNEGLCREATESYNCKHRPROC_LOCAL sEglCreateSyncKHR;
extern PFNEGLDESTROYSYNCKHRPROC_LOCAL sEglDestroySyncKHR;
extern PFNEGLCLIENTWAITSYNCKHRPROC_LOCAL sEglClientWaitSyncKHR;
extern PFNEGLDUPNATIVEFENCEFDANDROIDPROC_LOCAL sEglDupNativeFenceFDANDROID;
extern EGLDisplay sRingEglDpy;

// AHB ring arrays
extern AhbRenderTarget sAhbRingPrimary[AHB_RING_DEPTH];
extern AhbRenderTarget sAhbRingSecondary[AHB_RING_DEPTH];
extern EGLSyncKHR sAhbRingSyncPrimary[AHB_RING_DEPTH];
extern int sRingRenderIdx;
extern int sRingPresentIdx;
extern int sRingPrimedCount;

// Compat macros: existing code refers to sAhbTarget / sAhbTargetSecondary
// and used to be the SINGLE target before the ring landed. These now resolve
// to the currently-active ring slot (sRingRenderIdx) so every reader
// automatically follows the ring cursor without touching the ~40 call sites.
// When ring mode is disabled sRingRenderIdx stays 0, giving identical
// behavior to the pre-ring single-buffer world. Macros (not references)
// because references are bound once at init and can't follow a changing
// index.
#define sAhbTarget           (sAhbRingPrimary[sRingRenderIdx])
#define sAhbTargetSecondary  (sAhbRingSecondary[sRingRenderIdx])

extern int sDrmPrimaryIdx;
extern int64_t sDrmRescanDeadlineNs;

// ---------------------------------------------------------------------------
// DRM function declarations
// ---------------------------------------------------------------------------

// wantAlpha selects an EGL config with an 8-bit alpha channel (needed for the
// translucent overlay window so the SurfaceFlinger-blurred app shows through);
// the default (no alpha) is the opaque fast path used everywhere else.
EGLConfig getEglConfig(const EGLDisplay& display, bool wantAlpha = false);
int drmCrtcSlot(uint32_t crtcId);
void drmPaceWithoutVsync();
bool drmCreateDumbBuffer(int fd, uint32_t w, uint32_t h, DrmBuffer* out);
bool drmTryAddDisplay(int fd, uint32_t crtcId, uint32_t connId, const char* stage);
void drmRescanDisplays();
void drmEarlySplash(int existingFd = -1);
// Tear down everything drmEarlySplash() set up: RMFB the dumb buffer fb_ids,
// DROP_MASTER, close the DRM fd, clear sDrmActive/sDrmDisplays/sDrmZeroCopy,
// and publish sys.gammaos.nano.drm_active=0. Used by readyToRun() to abandon
// the DRM-direct boot path when the headless EGL setup fails (e.g. EX8 Mali-G57
// where libEGL needs SurfaceFlinger binder to return a usable EGL config) so
// that the SF window-surface fallback path can take over without leaking master.
void drmReleaseEarly();
double drmProbePrimaryRefreshHz();
int64_t drmLastGpuDoneUs();
bool drmDeferDrainActive();
void drmSetPacerLocked(bool locked);   // presenter tells the flip path whether the vblank lock is engaged   // AFBC deferred flip drain in effect (presenter must not drain itself)
bool drmAllocAhbTarget(EGLDisplay eglDpy, uint32_t w, uint32_t h,
                        AhbRenderTarget* target, const char* label);
void drmSetupZeroCopy(EGLDisplay eglDpy);
void drmBindNextFbo();
void blitAhbToDrmBuffer(const void* ahbPtr, uint32_t ahbStride,
                         uint32_t srcW, uint32_t srcH,
                         void* dstMapped, uint32_t dstPitch,
                         uint32_t dstW, uint32_t dstH,
                         int blitRotation);
void drmFlipRingSlot(int idx, bool skipNonPrimary = false);
// True when the slot's GPU fence has signalled (or there is no fence): a
// zero-timeout probe, so a caller can present an older slot instead of
// blocking on this one.
bool drmSlotFenceReady(int idx);
void drmFlipAll();
bool drmAnyCrtcPending();
void drmDrainPageFlipEvents();

// GammaRGB Follow-Screen sampler (NanoMenuRgbFollow.cpp). Called once per home
// render iteration; samples the just-presented AHB frame and publishes
// persist.gammaos.primary.rgb_hex when in DRM mode + rgb.effect=follow. Cheap
// no-op otherwise.
void nanoRgbFollowSample();
inline void drmFrameBegin() {
    if (sDrmActive && sDrmZeroCopy) {
        drmBindNextFbo();
    } else if (!sDrmActive) {
        // SurfaceFlinger path (force-SF, e.g. Unisoc/Spreadtrum): there is no AHB scanout
        // FBO to bind - render straight to the default framebuffer (the EGL window surface)
        // so drmFrameEnd's eglSwapBuffers presents it. Without this, callers that rely on
        // drmFrameBegin to bind the present target (the quick-resume preview loops for both
        // drastic and libretro, and the exit splash) would render into whatever FBO happened
        // to be bound and eglSwapBuffers would present an untouched (black) window.
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
}
inline void drmFrameEnd(EGLDisplay dpy, EGLSurface surf) {
    if (sDrmActive && sDrmZeroCopy) {
        // glFinish, not glFlush. The single-buffer PRIME path in
        // drmFlipRingSlot skips its EGL-fence and glFinish-fallback
        // branches when no EGL fence is set (sAhbRingSyncPrimary[0]
        // stays EGL_NO_SYNC_KHR on callers that use drmFrameEnd
        // instead of the triple-buffer ring). Without this barrier
        // the kernel's implicit dma-fence on the AHB dma-buf is the
        // only thing keeping scanout from racing the GPU -- and on
        // RK3568 that sync is flaky enough to produce visible tearing
        // in libretro QR. glFinish here mirrors what drmFlipRingSlot
        // already does for the legacy blit path (!fenceUsed &&
        // !primeActive), just moved to the right place for PRIME.
        drmResolveTurnedTargets(sRingRenderIdx);
        glFinish();
        drmFlipAll();
    } else if (sDrmActive) {
        // Non-zero-copy fallback: would need width/height. Skip.
    } else {
        eglSwapBuffers(dpy, surf);
    }
}
void drmPushFrame(uint32_t glWidth, uint32_t glHeight);
void drmStop();

// Build the logical->panel "install" transform that the XMB applies in
// NanoMenu::initShaders(), derived purely from the DRM orientation + user
// flip props (sDrmRotationDeg / sDrmFlipH / sDrmFlipV). Lets a caller that
// never runs initShaders (the drastic single-panel composite) honor the
// exact same panel correction as the home menu instead of hard-coding one.
// Column-major 2x2 NDC matrix written to out[4].
void drmBuildInstallMatrix(float out[4], int degrees = -1);
// Rotate nano's own SF-overlay rendering to follow the logical display rotation (0..3). No-op
// DRM-direct. See the definition in NanoMenuDrm.cpp.
void nanoSetOverlayRenderRotation(int rot);
// Re-commit the modeset + reset flip bookkeeping after a kernel
// suspend/resume cycle (resume re-enables the CRTCs with no planes; every
// legacy page flip then EBUSYs forever). Call once at the wake point, on
// the render thread, before relighting the backlight.
void drmResumeRecommit();
// True once per completed kernel suspend cycle (/sys/power/suspend_stats/success
// advanced since the last look; polled at most every 500 ms, so safe to call per
// frame). A caller that ran its own sleep/wake path calls drmSuspendMarkSeen() on
// wake so that cycle is not reported again.
bool drmSuspendCycleDetected();
void drmSuspendMarkSeen();
// Parked home: take DRM master back after drastic-nano exited and rebuild the ring.
void drmRememberInstallMatrix();   // call before drmStop() when the home will park
bool drmReacquireForHome(EGLDisplay eglDpy, int timeoutMs);

} // namespace android

#endif // GAMMAOS_NANO_MENU_DRM_H
