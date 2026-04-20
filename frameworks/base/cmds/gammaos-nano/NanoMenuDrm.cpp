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

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <vector>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <poll.h>
#include <string.h>
#include <errno.h>

#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>
#include <utils/Timers.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <drm.h>
#include <drm_mode.h>
#include <drm_fourcc.h>

#include <android/hardware_buffer.h>
#include <vndk/hardware_buffer.h>  // AHardwareBuffer_getNativeHandle
#include <cutils/native_handle.h>  // native_handle_t layout

// Include NanoMenuDrm.h BEFORE arm_neon.h so GAMMAOS_NANO_HAVE_NEON is defined
#include "NanoMenuDrm.h"

#if GAMMAOS_NANO_HAVE_NEON
#include <arm_neon.h>
#endif

namespace android {

// ---------------------------------------------------------------------------
// DRM variable definitions
// ---------------------------------------------------------------------------

int sDrmFd = -1;
std::vector<DrmDisplay> sDrmDisplays;
bool sDrmActive = false;
int sDrmRotationDeg = 0;
bool sDrmZeroCopy = false;
bool sDrmGlRotation = false;
bool sDrmYFlipForPrime = false;
bool sDrmFlipH = false;
bool sDrmFlipV = false;
bool sDrmVblankBroken = false;
bool sDrmFrameSync = true;
int sPendingFlipEvents = 0;
uint32_t sCrtcIds[kMaxCrtcTrack] = {0};
int sCrtcPending[kMaxCrtcTrack] = {0};
int sCrtcTrackCount = 0;
int sVsyncEnabled = -1;
bool sDrasticQrFastPath = false;
float sDrmRotMat[4] = {1.0f, 0.0f, 0.0f, 1.0f}; // identity

// EGL extension function pointers
PFNEGLCREATEIMAGEKHRPROC sEglCreateImageKHR = nullptr;
PFNEGLDESTROYIMAGEKHRPROC sEglDestroyImageKHR = nullptr;
PFNGLEGLIMAGETARGETTEXTURE2DOESPROC sGlEGLImageTargetTexture2DOES = nullptr;
PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC sEglGetNativeClientBufferANDROID = nullptr;
PFNEGLCREATESYNCKHRPROC_LOCAL sEglCreateSyncKHR = nullptr;
PFNEGLDESTROYSYNCKHRPROC_LOCAL sEglDestroySyncKHR = nullptr;
PFNEGLCLIENTWAITSYNCKHRPROC_LOCAL sEglClientWaitSyncKHR = nullptr;
PFNEGLDUPNATIVEFENCEFDANDROIDPROC_LOCAL sEglDupNativeFenceFDANDROID = nullptr;
EGLDisplay sRingEglDpy = EGL_NO_DISPLAY;

// AHB ring arrays
AhbRenderTarget sAhbRingPrimary[AHB_RING_DEPTH] = {};
AhbRenderTarget sAhbRingSecondary[AHB_RING_DEPTH] = {};
// Explicit default-init to EGL_NO_SYNC_KHR (typedef'd to void* 0) for
// every ring slot. Zero-init is fine but the explicit per-slot default
// is harmless and clarifies intent. Sized off AHB_RING_DEPTH so bumping
// the ring does not silently leave uninitialized entries.
EGLSyncKHR sAhbRingSyncPrimary[AHB_RING_DEPTH] = {};
int sRingRenderIdx = 0;
int sRingPresentIdx = 0;
int sRingPrimedCount = 0;

int sDrmPrimaryIdx = 0;
int64_t sDrmRescanDeadlineNs = 0;

// ---------------------------------------------------------------------------
// EGL setup
// ---------------------------------------------------------------------------

EGLConfig getEglConfig(const EGLDisplay& display) {
    EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 0, EGL_DEPTH_SIZE, 0, EGL_NONE
    };
    EGLint numConfigs = 0;
    EGLConfig config = nullptr;
    eglChooseConfig(display, attribs, &config, 1, &numConfigs);
    return config;
}

// ---------------------------------------------------------------------------
// Per-CRTC tracking
// ---------------------------------------------------------------------------

int drmCrtcSlot(uint32_t crtcId) {
    for (int i = 0; i < sCrtcTrackCount; i++) {
        if (sCrtcIds[i] == crtcId) return i;
    }
    if (sCrtcTrackCount < kMaxCrtcTrack) {
        sCrtcIds[sCrtcTrackCount] = crtcId;
        return sCrtcTrackCount++;
    }
    return -1;
}

// Helper: when vsync is disabled, cap iter rate at 60 fps via usleep on a
// monotonic deadline. Same call site as the vsync gate so the loop runs at
// the same effective rate -- only the pacing source changes.
void drmPaceWithoutVsync() {
    static int64_t sNextDeadlineUs = 0;
    constexpr int64_t kFrameUs = 16667;  // ~60 Hz
    int64_t nowUs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
    if (sNextDeadlineUs == 0) {
        sNextDeadlineUs = nowUs + kFrameUs;
        return;
    }
    if (nowUs < sNextDeadlineUs) {
        usleep((useconds_t)(sNextDeadlineUs - nowUs));
        nowUs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
    }
    // If we were already past the deadline (slow frame), reset to "now" so
    // we don't try to catch up by submitting back-to-back -- just keep
    // pacing at 60 fps from the current moment.
    if (nowUs > sNextDeadlineUs + kFrameUs) {
        sNextDeadlineUs = nowUs + kFrameUs;
    } else {
        sNextDeadlineUs += kFrameUs;
    }
}

// ---------------------------------------------------------------------------
// DRM buffer and display setup
// ---------------------------------------------------------------------------

// Create and map a double-buffered dumb buffer pair for a DRM CRTC. Shared
// by drmEarlySplash (first-pass enumeration) and drmRescanDisplays (late
// re-probe for displays that weren't ready at splash time).
bool drmCreateDumbBuffer(int fd, uint32_t w, uint32_t h, DrmBuffer* out) {
    struct drm_mode_create_dumb create = {};
    create.width = w; create.height = h; create.bpp = 32;
    if (ioctl(fd, DRM_IOCTL_MODE_CREATE_DUMB, &create) != 0) return false;

    struct drm_mode_map_dumb mapReq = {};
    mapReq.handle = create.handle;
    if (ioctl(fd, DRM_IOCTL_MODE_MAP_DUMB, &mapReq) != 0) return false;

    void* mapped = mmap(nullptr, create.size, PROT_READ | PROT_WRITE,
                       MAP_SHARED, fd, mapReq.offset);
    if (mapped == MAP_FAILED) return false;

    // Fill with dark background so the display isn't garbage on first scanout
    uint32_t* px = (uint32_t*)mapped;
    for (uint32_t i = 0; i < w * h; i++) px[i] = 0xFF1A0D0D;

    struct drm_mode_fb_cmd fbCmd = {};
    fbCmd.width = w; fbCmd.height = h;
    fbCmd.pitch = create.pitch; fbCmd.bpp = 32; fbCmd.depth = 24;
    fbCmd.handle = create.handle;
    if (ioctl(fd, DRM_IOCTL_MODE_ADDFB, &fbCmd) != 0) {
        munmap(mapped, create.size); return false;
    }
    out->handle = create.handle;
    out->fbId = fbCmd.fb_id;
    out->pitch = create.pitch;
    out->size = create.size;
    out->mapped = mapped;
    out->dmaFd = -1;
    out->eglImage = EGL_NO_IMAGE_KHR;
    out->glTexture = 0;
    out->glFbo = 0;
    return true;
}

// Try to bring up a single DRM CRTC with the given connector. Returns true
// if the CRTC was added to sDrmDisplays. Non-blocking: if the mode is not
// valid (display not ready), returns false immediately -- caller can retry
// later via drmRescanDisplays().
bool drmTryAddDisplay(int fd, uint32_t crtcId, uint32_t connId, const char* stage) {
    struct drm_mode_crtc crtc = {};
    crtc.crtc_id = crtcId;
    if (ioctl(fd, DRM_IOCTL_MODE_GETCRTC, &crtc) != 0 || !crtc.mode_valid) {
        return false;
    }

    uint32_t w = crtc.mode.hdisplay, h = crtc.mode.vdisplay;
    DrmBuffer buf0, buf1;
    if (!drmCreateDumbBuffer(fd, w, h, &buf0)) return false;
    if (!drmCreateDumbBuffer(fd, w, h, &buf1)) {
        munmap(buf0.mapped, buf0.size);
        return false;
    }

    crtc.fb_id = buf0.fbId;
    crtc.set_connectors_ptr = (uint64_t)(uintptr_t)&connId;
    crtc.count_connectors = 1;
    int ret = ioctl(fd, DRM_IOCTL_MODE_SETCRTC, &crtc);

    int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    ALOGW("NanoMenu DRM %s: crtc %u (%ux%u) conn %u → %s at T+%lldms",
          stage, crtcId, w, h, connId, ret == 0 ? "OK" : strerror(errno), now);

    if (ret != 0) {
        munmap(buf0.mapped, buf0.size);
        munmap(buf1.mapped, buf1.size);
        return false;
    }

    DrmDisplay d = {};
    d.crtcId = crtcId; d.connId = connId;
    d.w = w; d.h = h;
    d.buffers[0] = buf0;
    d.buffers[1] = buf1;
    d.activeBuffer = 0;
    d.mode = crtc.mode;
    sDrmDisplays.push_back(d);
    return true;
}

// Re-probe DRM CRTCs that weren't ready at drmEarlySplash() time. Called
// periodically from the main loop so a slow-to-come-up display can be
// brought in without blocking the fast path. Bounded by sDrmRescanDeadlineNs
// so we stop burning ioctls after the boot window.
void drmRescanDisplays() {
    if (sDrmFd < 0) return;
    if (sDrmRescanDeadlineNs == 0) return;
    if (systemTime(SYSTEM_TIME_MONOTONIC) > sDrmRescanDeadlineNs) {
        sDrmRescanDeadlineNs = 0; // disable further scans
        return;
    }

    struct drm_mode_card_res res = {};
    if (ioctl(sDrmFd, DRM_IOCTL_MODE_GETRESOURCES, &res) != 0 || res.count_crtcs == 0) {
        return;
    }
    uint32_t numCrtcs = res.count_crtcs, numConns = res.count_connectors;
    std::vector<uint32_t> crtcs(numCrtcs), connectors(numConns);
    struct drm_mode_card_res res2 = {};
    res2.count_crtcs = numCrtcs;
    res2.count_connectors = numConns;
    res2.crtc_id_ptr = (uint64_t)(uintptr_t)crtcs.data();
    res2.connector_id_ptr = (uint64_t)(uintptr_t)connectors.data();
    if (ioctl(sDrmFd, DRM_IOCTL_MODE_GETRESOURCES, &res2) != 0) return;

    for (uint32_t c = 0; c < numCrtcs && c < 2; c++) {
        // Skip CRTCs already in our display list.
        bool already = false;
        for (const auto& d : sDrmDisplays) {
            if (d.crtcId == crtcs[c]) { already = true; break; }
        }
        if (already) continue;

        uint32_t connId = (c < numConns) ? connectors[c] : 0;
        if (drmTryAddDisplay(sDrmFd, crtcs[c], connId, "rescan")) {
            ALOGW("NanoMenu DRM rescan: brought up late CRTC %u (now %zu displays)",
                  crtcs[c], sDrmDisplays.size());
            // A new display came up. We don't re-allocate the secondary AHB
            // here because the GL context for AHB allocation lives on the
            // render thread and drmRescanDisplays is called from the main
            // loop, which IS the render thread -- but drmSetupZeroCopy uses
            // the EGL display. The simplest behavior: leave AHB setup alone.
            // The new display will mirror the primary (sAhbTarget) via the
            // fallback path in drmFlipAll. Good enough for the edge case.
        }
    }
}

void drmEarlySplash() {
    int64_t t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    int fd = open("/dev/dri/card0", O_RDWR | O_CLOEXEC);
    if (fd < 0) return;

    ioctl(fd, DRM_IOCTL_SET_MASTER, 0); // try, OK if fails

    // Get DRM resources
    struct drm_mode_card_res res = {};
    if (ioctl(fd, DRM_IOCTL_MODE_GETRESOURCES, &res) != 0 || res.count_crtcs == 0) {
        close(fd); return;
    }
    uint32_t numCrtcs = res.count_crtcs, numConns = res.count_connectors;
    std::vector<uint32_t> crtcs(numCrtcs), connectors(numConns);
    struct drm_mode_card_res res2 = {};
    res2.count_crtcs = numCrtcs;
    res2.count_connectors = numConns;
    res2.crtc_id_ptr = (uint64_t)(uintptr_t)crtcs.data();
    res2.connector_id_ptr = (uint64_t)(uintptr_t)connectors.data();
    if (ioctl(fd, DRM_IOCTL_MODE_GETRESOURCES, &res2) != 0) { close(fd); return; }

    sDrmFd = fd;

    // Enumerate CRTCs independently -- a slow display does NOT hold back a
    // fast one. Any CRTC that isn't ready here is retried by drmRescanDisplays().
    int attempted = 0, addedCount = 0;
    for (uint32_t c = 0; c < numCrtcs && c < 2; c++) {
        attempted++;
        uint32_t connId = (c < numConns) ? connectors[c] : 0;
        if (drmTryAddDisplay(fd, crtcs[c], connId, "splash")) addedCount++;
    }
    sDrmActive = !sDrmDisplays.empty();
    // GammaOS: publish DRM ownership to SurfaceFlinger. While this is "1",
    // SurfaceFlinger's commit() short-circuits composition so SystemUI
    // overlays (volume bar, brightness bar, etc.) cannot blank the panel
    // by racing a HWC present against NanoMenu's direct DRM flips.
    property_set("sys.gammaos.nano.drm_active", sDrmActive ? "1" : "0");
    if (sDrmActive) {
        ALOGW("NanoMenu DRM splash: %d/%d CRTCs active for direct rendering",
              addedCount, attempted);
    }

    // Enable late-display re-probe for the first 5 seconds of the process.
    // This is a bounded window: if a panel hasn't come up by then, it is
    // either broken or never going to, so we stop spending ioctls on it.
    sDrmRescanDeadlineNs = systemTime(SYSTEM_TIME_MONOTONIC) + 5000000000LL;

    // GammaOS: Read persist.gammaos.nano.primary_display to choose which
    // enumerated CRTC receives the XMB/menu AHB. All other displays receive
    // wallpaper-only output via sAhbTargetSecondary. The property value is a
    // CRTC enumeration index (0 = first, 1 = second, ...). Invalid values
    // fall back to 0. This mirrors the same property used post-boot to pick
    // the physical display port for the EGL/SurfaceFlinger path.
    {
        char primaryProp[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.primary_display", primaryProp, "0");
        int wantIdx = atoi(primaryProp);
        if (wantIdx < 0 || wantIdx >= (int)sDrmDisplays.size()) {
            wantIdx = 0;
        }
        sDrmPrimaryIdx = wantIdx;
        if (sDrmDisplays.size() > 1) {
            ALOGW("NanoMenu DRM: primary_display prop='%s' → CRTC index %d (of %zu)",
                  primaryProp, sDrmPrimaryIdx, sDrmDisplays.size());
        }
    }

    // GammaOS: Read the install orientation so DRM direct rendering can rotate
    // content to match the physical panel orientation. Without this, DRM direct
    // rendering outputs unrotated content and the menu appears sideways on devices
    // with a rotated install orientation (e.g. portrait panel used landscape).
    {
        char orient[PROPERTY_VALUE_MAX] = {};
        property_get("ro.surface_flinger.primary_display_orientation", orient, "");
        if (!strcmp(orient, "ORIENTATION_90")) sDrmRotationDeg = 90;
        else if (!strcmp(orient, "ORIENTATION_180")) sDrmRotationDeg = 180;
        else if (!strcmp(orient, "ORIENTATION_270")) sDrmRotationDeg = 270;
        else sDrmRotationDeg = 0;
        if (sDrmRotationDeg != 0) {
            ALOGI("NanoMenu DRM: installOrientation=%s → rotate %d°",
                  orient, sDrmRotationDeg);
        }
    }

    // GammaOS: Read user-requested flip props. Some panels scan out mirrored
    // relative to the logical image and need a software correction that
    // SurfaceFlinger's install-orientation string can't express. These props
    // are applied on top of sDrmRotationDeg in initShaders(), and their state
    // is surfaced to fragment-space procedural shaders so wallpaper FX stay
    // consistent with the corrected vertex orientation.
    //   persist.gammaos.nano.drm_flip_h : 1 → mirror horizontally (left <-> right)
    //   persist.gammaos.nano.drm_flip_v : 1 → mirror vertically   (top  <-> bottom)
    {
        char flipProp[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.drm_flip_h", flipProp, "0");
        sDrmFlipH = (flipProp[0] == '1' || flipProp[0] == 't' ||
                     flipProp[0] == 'T' || flipProp[0] == 'y' ||
                     flipProp[0] == 'Y');
        flipProp[0] = 0;
        property_get("persist.gammaos.nano.drm_flip_v", flipProp, "0");
        sDrmFlipV = (flipProp[0] == '1' || flipProp[0] == 't' ||
                     flipProp[0] == 'T' || flipProp[0] == 'y' ||
                     flipProp[0] == 'Y');
        if (sDrmFlipH || sDrmFlipV) {
            ALOGI("NanoMenu DRM: user flip correction flip_h=%d flip_v=%d",
                  sDrmFlipH ? 1 : 0, sDrmFlipV ? 1 : 0);
        }
    }
}

// ---------------------------------------------------------------------------
// AHB zero-copy setup
// ---------------------------------------------------------------------------

// Build an AHB-backed FBO of the given dimensions. Fills out `target` on
// success; leaves it zeroed and returns false on failure. Factored out so we
// can allocate both primary and secondary render targets from the same code.
bool drmAllocAhbTarget(EGLDisplay eglDpy, uint32_t w, uint32_t h,
                        AhbRenderTarget* target, const char* label) {
    AHardwareBuffer_Desc desc = {};
    desc.width = w;
    desc.height = h;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    // CPU_READ_OFTEN is required on RK3568 Mali gralloc for the AHB
    // to be allocated LINEAR (ABGR8888). Removing it picks an AFBC
    // compressed / tiled layout and DRM_IOCTL_MODE_ADDFB2 rejects
    // with EINVAL, breaking PRIME. COMPOSER_OVERLAY is a hint that
    // the buffer is for direct composition -- some gralloc impls
    // use it to pick a DRM-scanout-friendly (coherent, linear)
    // layout. Kept alongside CPU_READ so the blit fallback path
    // still has a CPU mapping if PRIME ever fails at runtime.
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER |
                 AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE |
                 AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY |
                 AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN;
    if (AHardwareBuffer_allocate(&desc, &target->ahb) != 0 || !target->ahb) {
        ALOGW("NanoMenu DRM zero-copy: AHardwareBuffer_allocate(%s) failed", label);
        return false;
    }

    EGLClientBuffer clientBuf = sEglGetNativeClientBufferANDROID(target->ahb);
    if (!clientBuf) {
        ALOGW("NanoMenu DRM zero-copy: eglGetNativeClientBufferANDROID(%s) failed", label);
        AHardwareBuffer_release(target->ahb); target->ahb = nullptr;
        return false;
    }

    EGLint imgAttrs[] = { EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE };
    target->eglImage = sEglCreateImageKHR(eglDpy, EGL_NO_CONTEXT,
                                           EGL_NATIVE_BUFFER_ANDROID, clientBuf, imgAttrs);
    if (target->eglImage == EGL_NO_IMAGE_KHR) {
        ALOGW("NanoMenu DRM zero-copy: eglCreateImageKHR(%s) failed", label);
        AHardwareBuffer_release(target->ahb); target->ahb = nullptr;
        return false;
    }

    glGenTextures(1, &target->glTexture);
    glBindTexture(GL_TEXTURE_2D, target->glTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    sGlEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)target->eglImage);
    if (glGetError() != GL_NO_ERROR) {
        ALOGW("NanoMenu DRM zero-copy: glEGLImageTargetTexture2DOES(%s) failed", label);
        glDeleteTextures(1, &target->glTexture); target->glTexture = 0;
        sEglDestroyImageKHR(eglDpy, target->eglImage); target->eglImage = EGL_NO_IMAGE_KHR;
        AHardwareBuffer_release(target->ahb); target->ahb = nullptr;
        return false;
    }

    glGenFramebuffers(1, &target->glFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, target->glFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, target->glTexture, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        ALOGW("NanoMenu DRM zero-copy: AHB FBO(%s) incomplete 0x%x", label, status);
        glDeleteFramebuffers(1, &target->glFbo); target->glFbo = 0;
        glDeleteTextures(1, &target->glTexture); target->glTexture = 0;
        sEglDestroyImageKHR(eglDpy, target->eglImage); target->eglImage = EGL_NO_IMAGE_KHR;
        AHardwareBuffer_release(target->ahb); target->ahb = nullptr;
        return false;
    }

    target->w = w;
    target->h = h;

    // Best-effort DRM PRIME import: try to make this AHB directly
    // scanout-able by the DRM panel, so drmFlipRingSlot can page flip
    // straight to it instead of CPU-blit'ing AHB->dumb buffer.
    //
    // Sequence: native_handle's first fd is the dma-buf for the AHB's
    // backing store -> PRIME_FD_TO_HANDLE in our DRM context returns
    // a GEM handle -> ADDFB2 with DRM_FORMAT_ABGR8888 (matches AHB's
    // R8G8B8A8 memory order) gives us a fb_id.
    //
    // If any step fails (driver doesn't accept foreign dma-buf imports,
    // or the AHB has a non-trivial gralloc layout), we leave drmFbId=0
    // and the flip path falls back to the blit path. So this is a
    // no-risk experiment.
    target->drmFbId = 0;
    target->drmGemHandle = 0;
    if (sDrmFd >= 0) {
        const native_handle_t* nh = AHardwareBuffer_getNativeHandle(target->ahb);
        if (nh && nh->numFds > 0) {
            int dmabufFd = nh->data[0];
            // Pull the AHB's stride (in pixels) for fb pitch.
            AHardwareBuffer_Desc d = {};
            AHardwareBuffer_describe(target->ahb, &d);
            uint32_t pitch = d.stride * 4;  // R8G8B8A8 = 4 bytes/px

            struct drm_prime_handle ph = {};
            ph.fd = dmabufFd;
            ph.flags = 0;
            ph.handle = 0;
            if (ioctl(sDrmFd, DRM_IOCTL_PRIME_FD_TO_HANDLE, &ph) == 0
                && ph.handle != 0) {
                struct drm_mode_fb_cmd2 cmd = {};
                cmd.width = w;
                cmd.height = h;
                cmd.pixel_format = DRM_FORMAT_ABGR8888;
                cmd.flags = 0;
                cmd.handles[0] = ph.handle;
                cmd.pitches[0] = pitch;
                cmd.offsets[0] = 0;
                if (ioctl(sDrmFd, DRM_IOCTL_MODE_ADDFB2, &cmd) == 0
                    && cmd.fb_id != 0) {
                    target->drmFbId = cmd.fb_id;
                    target->drmGemHandle = ph.handle;
                    ALOGW("NanoMenu DRM PRIME: AHB(%s) imported as fb_id=%u "
                          "(gem=%u dmabuf_fd=%d pitch=%u)",
                          label, target->drmFbId, target->drmGemHandle,
                          dmabufFd, pitch);
                } else {
                    ALOGW("NanoMenu DRM PRIME: ADDFB2 failed for AHB(%s) "
                          "(errno=%d) -- will fall back to blit path",
                          label, errno);
                    // GEM handle leaks slightly; close via GEM_CLOSE
                    struct drm_gem_close gc = {};
                    gc.handle = ph.handle;
                    ioctl(sDrmFd, DRM_IOCTL_GEM_CLOSE, &gc);
                }
            } else {
                ALOGW("NanoMenu DRM PRIME: PRIME_FD_TO_HANDLE failed for "
                      "AHB(%s) (errno=%d) -- will fall back to blit path",
                      label, errno);
            }
        }
    }

    ALOGW("NanoMenu DRM zero-copy: AHB(%s) ENABLED — fbo=%u tex=%u (%ux%u) drmFb=%u",
          label, target->glFbo, target->glTexture, w, h, target->drmFbId);
    return true;
}

// Set up fast GPU->DRM rendering via AHardwareBuffer.
// GPU renders into an AHB-backed FBO, then we lock the AHB for CPU read
// (fast, no driver format conversion) and memcpy to DRM dumb buffer.
// This avoids glReadPixels (~170ms on Mali G52) entirely.
//
// Allocates two targets: PRIMARY (wallpaper + menu) and SECONDARY (wallpaper
// only). The secondary is only created if sDrmDisplays has more than one
// entry. Secondary allocation is best-effort: if it fails, the secondary
// display simply mirrors the primary (same behavior as before this patch).
void drmSetupZeroCopy(EGLDisplay eglDpy) {
    if (!sDrmActive) return;

    // Resolve extension functions
    sEglCreateImageKHR = (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
    sEglDestroyImageKHR = (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
    sGlEGLImageTargetTexture2DOES = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)
            eglGetProcAddress("glEGLImageTargetTexture2DOES");
    sEglGetNativeClientBufferANDROID = (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC)
            eglGetProcAddress("eglGetNativeClientBufferANDROID");
    // EGL_ANDROID_native_fence_sync: used by the triple-buffer ring to
    // pass a per-slot dma-fence fd to AHardwareBuffer_lock so the wait
    // is scoped to ONE slot's GPU work, not the whole kbase queue.
    sEglCreateSyncKHR = (PFNEGLCREATESYNCKHRPROC_LOCAL)
            eglGetProcAddress("eglCreateSyncKHR");
    sEglDestroySyncKHR = (PFNEGLDESTROYSYNCKHRPROC_LOCAL)
            eglGetProcAddress("eglDestroySyncKHR");
    sEglClientWaitSyncKHR = (PFNEGLCLIENTWAITSYNCKHRPROC_LOCAL)
            eglGetProcAddress("eglClientWaitSyncKHR");
    sEglDupNativeFenceFDANDROID = (PFNEGLDUPNATIVEFENCEFDANDROIDPROC_LOCAL)
            eglGetProcAddress("eglDupNativeFenceFDANDROID");
    sRingEglDpy = eglDpy;

    if (!sEglCreateImageKHR || !sGlEGLImageTargetTexture2DOES || !sEglGetNativeClientBufferANDROID) {
        ALOGW("NanoMenu DRM zero-copy: EGL/GL ext functions not available");
        return;
    }
    const bool haveFenceSync = sEglCreateSyncKHR && sEglDestroySyncKHR &&
                               sEglDupNativeFenceFDANDROID;
    ALOGW("NanoMenu DRM zero-copy: EGL native fence sync %s",
          haveFenceSync ? "ENABLED" : "NOT AVAILABLE (ring will fall back to glFinish)");

    const char* exts = eglQueryString(eglDpy, EGL_EXTENSIONS);
    if (!exts || !strstr(exts, "EGL_ANDROID_image_native_buffer") ||
        !strstr(exts, "EGL_ANDROID_get_native_client_buffer")) {
        ALOGW("NanoMenu DRM zero-copy: AHB EGL extensions not supported");
        return;
    }

    // AHB is always at PANEL NATIVE dimensions of the selected primary display.
    // When the install orientation is non-zero, GL rotation (via uRotation mat2
    // in vertex shaders) maps logical coords to the panel-native AHB -- so the
    // blit is always a fast straight copy with no per-pixel rotation.
    const uint32_t primaryW = sDrmDisplays[sDrmPrimaryIdx].w;
    const uint32_t primaryH = sDrmDisplays[sDrmPrimaryIdx].h;

    // Allocate all AHB_RING_DEPTH primary slots. Slot 0 is the "classic"
    // single-buffered target used by XMB and the default QR path;
    // slots 1..2 only get used when the QR loop opts into the triple-
    // buffer ring path (Step 4). We pay the +30 MB up-front so the ring
    // can be enabled/disabled at runtime via a prop without reallocating.
    for (int i = 0; i < AHB_RING_DEPTH; i++) {
        char label[32];
        snprintf(label, sizeof(label), "primary[%d]", i);
        if (!drmAllocAhbTarget(eglDpy, primaryW, primaryH,
                               &sAhbRingPrimary[i], label)) {
            // Release any previously-allocated slots and bail. We stay
            // in the pre-AHB path (readback via glReadPixels) because
            // the ring has to be "all or nothing" -- a partially-allocated
            // ring would corrupt any attempt to advance renderIdx.
            for (int j = 0; j < i; j++) {
                if (sAhbRingPrimary[j].glFbo) {
                    glDeleteFramebuffers(1, &sAhbRingPrimary[j].glFbo);
                }
                if (sAhbRingPrimary[j].glTexture) {
                    glDeleteTextures(1, &sAhbRingPrimary[j].glTexture);
                }
                if (sAhbRingPrimary[j].eglImage != EGL_NO_IMAGE_KHR) {
                    sEglDestroyImageKHR(eglDpy, sAhbRingPrimary[j].eglImage);
                }
                if (sAhbRingPrimary[j].ahb) {
                    AHardwareBuffer_release(sAhbRingPrimary[j].ahb);
                }
                sAhbRingPrimary[j] = {};
            }
            return;
        }
    }

    sDrmZeroCopy = true;

    // Allocate a secondary AHB for wallpaper-only rendering when more than
    // one display is active. Use the first non-primary display's dimensions;
    // if other non-primary displays have different resolutions the blit
    // tolerates mismatch (clips/pads in blitAhbToDrmBuffer).
    if (sDrmDisplays.size() > 1) {
        uint32_t secW = 0, secH = 0;
        for (size_t i = 0; i < sDrmDisplays.size(); i++) {
            if ((int)i == sDrmPrimaryIdx) continue;
            secW = sDrmDisplays[i].w;
            secH = sDrmDisplays[i].h;
            break;
        }
        if (secW > 0 && secH > 0) {
            for (int i = 0; i < AHB_RING_DEPTH; i++) {
                char label[48];
                snprintf(label, sizeof(label), "secondary-wallpaper[%d]", i);
                if (!drmAllocAhbTarget(eglDpy, secW, secH,
                                       &sAhbRingSecondary[i], label)) {
                    // Partial secondary allocation. Roll back so either
                    // all secondary slots exist or none do -- matches the
                    // primary-ring policy. Secondary displays then mirror
                    // the primary AHB (same as single-display fallback).
                    ALOGW("NanoMenu DRM: secondary AHB slot %d alloc failed, "
                          "rolling back to mirror", i);
                    for (int j = 0; j < i; j++) {
                        if (sAhbRingSecondary[j].glFbo) {
                            glDeleteFramebuffers(1, &sAhbRingSecondary[j].glFbo);
                        }
                        if (sAhbRingSecondary[j].glTexture) {
                            glDeleteTextures(1, &sAhbRingSecondary[j].glTexture);
                        }
                        if (sAhbRingSecondary[j].eglImage != EGL_NO_IMAGE_KHR) {
                            sEglDestroyImageKHR(eglDpy, sAhbRingSecondary[j].eglImage);
                        }
                        if (sAhbRingSecondary[j].ahb) {
                            AHardwareBuffer_release(sAhbRingSecondary[j].ahb);
                        }
                        sAhbRingSecondary[j] = {};
                    }
                    break;
                }
            }
        }
    }

    // GammaOS: DRM PRIME path requires Y-flip in the vertex shader.
    // The legacy blit path was implicitly Y-flipping while copying
    // (srcRow = ahbPtr + (srcH - 1 - dy) * ahbStride). With PRIME we
    // page-flip the AHB directly to scanout, so the GL-y-up output
    // displays upside-down unless we pre-flip Y in clip space. Apply
    // the flip to sDrmRotMat (multiply by [1,0,0,-1] on the left =
    // negate row-1 of the post-rotation matrix) and force the vertex
    // shader's matrix path on (sDrmGlRotation = true) even at 0
    // install rotation. drastic gets the updated matrix via the
    // setRotationMatrix call inside the QR loop, which runs after
    // this setup completes.
    if (sAhbRingPrimary[0].drmFbId != 0 && !sDrmYFlipForPrime) {
        sDrmRotMat[1] = -sDrmRotMat[1];
        sDrmRotMat[3] = -sDrmRotMat[3];
        sDrmGlRotation = true;
        sDrmYFlipForPrime = true;
        ALOGW("NanoMenu DRM PRIME: applied Y-flip to rotation matrix "
              "(rotMat=[%g %g %g %g], glRotation forced ON)",
              sDrmRotMat[0], sDrmRotMat[1], sDrmRotMat[2], sDrmRotMat[3]);
    }
}

// ---------------------------------------------------------------------------
// Rendering helpers
// ---------------------------------------------------------------------------

// Bind the AHB-backed FBO for rendering. Call before render().
void drmBindNextFbo() {
    if (!sDrmZeroCopy) return;
    glBindFramebuffer(GL_FRAMEBUFFER, sAhbTarget.glFbo);
    glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
}

// NEON-accelerated row blit: AHB (R8G8B8A8, memory order [R,G,B,A]) ->
// DRM dumb buffer (XRGB8888, memory order [B,G,R,X=0xFF]). Processes 16
// pixels per vector iteration using vld4q/vst4q for a zero-shuffle channel
// swap. Scalar tail handles any sub-16-pixel remainder.
//
// Cortex-A55 characteristics: 128-bit NEON, 64-byte cache line, in-order.
// vld4q_u8 de-interleaves 16 RGBA pixels into 4x16 byte planes in ~4 cycles.
// Channel swap is free -- we just re-interleave in a different order.
// Prefetch hint at +128 bytes (32 pixels ahead) keeps the L2 fed on A55.
inline void blitRowRgbaToXrgbNeon(const uint32_t* __restrict src,
                                   uint32_t* __restrict dst,
                                   uint32_t count) {
#if GAMMAOS_NANO_HAVE_NEON
    const uint32_t vecPixels = count & ~15u; // round down to multiple of 16
    const uint8x16_t alphaMask = vdupq_n_u8(0xFF);
    const uint8_t* s8 = (const uint8_t*)src;
    uint8_t* d8 = (uint8_t*)dst;
    for (uint32_t i = 0; i < vecPixels; i += 16) {
        __builtin_prefetch(s8 + 128, 0, 0); // next-next cache line read
        uint8x16x4_t p = vld4q_u8(s8);
        // p.val[0]=R, [1]=G, [2]=B, [3]=A (from memory order R,G,B,A)
        // DRM XRGB8888 little-endian stores [B,G,R,X] in memory.
        uint8x16x4_t q;
        q.val[0] = p.val[2]; // B
        q.val[1] = p.val[1]; // G
        q.val[2] = p.val[0]; // R
        q.val[3] = alphaMask; // X=0xFF
        vst4q_u8(d8, q);
        s8 += 64; d8 += 64;
    }
    for (uint32_t i = vecPixels; i < count; i++) {
        uint32_t rgba = src[i];
        dst[i] = 0xFF000000u |
                 ((rgba >> 16) & 0xFFu) |
                 (rgba & 0xFF00u) |
                 ((rgba & 0xFFu) << 16);
    }
#else
    for (uint32_t i = 0; i < count; i++) {
        uint32_t rgba = src[i];
        dst[i] = 0xFF000000u |
                 ((rgba >> 16) & 0xFFu) |
                 (rgba & 0xFF00u) |
                 ((rgba & 0xFFu) << 16);
    }
#endif
}

// Blit a single AHB into a single DRM dumb buffer, handling Y-flip and
// optional 90/180/270 rotation. The 0 degree case uses the NEON row helper above.
void blitAhbToDrmBuffer(const void* ahbPtr, uint32_t ahbStride,
                         uint32_t srcW, uint32_t srcH,
                         void* dstMapped, uint32_t dstPitch,
                         uint32_t dstW, uint32_t dstH,
                         int blitRotation) {
    uint8_t* dst = (uint8_t*)dstMapped;
    if (blitRotation == 0) {
        const uint32_t copyW = std::min(srcW, dstW);
        const uint32_t copyH = std::min(srcH, dstH);
        for (uint32_t dy = 0; dy < copyH; dy++) {
            uint32_t* dstRow = (uint32_t*)(dst + dy * dstPitch);
            const uint32_t* srcRow = (const uint32_t*)((const uint8_t*)ahbPtr
                                     + (srcH - 1 - dy) * ahbStride);
            blitRowRgbaToXrgbNeon(srcRow, dstRow, copyW);
            // If dst is wider than src, clear the right margin.
            if (dstW > copyW) {
                memset(dstRow + copyW, 0, (dstW - copyW) * 4);
            }
        }
        // If dst is taller than src, clear the bottom margin.
        for (uint32_t dy = copyH; dy < dstH; dy++) {
            memset(dst + dy * dstPitch, 0, dstW * 4);
        }
        return;
    }
    // Rotated paths: scalar per-pixel (rare -- only used if GL rotation fallback).
    for (uint32_t dy = 0; dy < dstH; dy++) {
        uint32_t* dstRow = (uint32_t*)(dst + dy * dstPitch);
        for (uint32_t dx = 0; dx < dstW; dx++) {
            uint32_t sx, sy;
            switch (blitRotation) {
            case 90:  sx = dy; sy = dx; break;
            case 180: sx = srcW-1-dx; sy = dy; break;
            case 270: sx = srcW-1-dy; sy = srcH-1-dx; break;
            default:  sx = dx; sy = srcH-1-dy; break;
            }
            if (sx >= srcW || sy >= srcH) continue;
            const uint32_t* srcRow = (const uint32_t*)((const uint8_t*)ahbPtr
                                     + sy * ahbStride);
            uint32_t rgba = srcRow[sx];
            dstRow[dx] = 0xFF000000u |
                         ((rgba >> 16) & 0xFFu) |
                         (rgba & 0xFF00u) |
                         ((rgba & 0xFFu) << 16);
        }
    }
}

// ---------------------------------------------------------------------------
// Page flip and presentation
// ---------------------------------------------------------------------------

// Present one ring slot to all DRM displays. Each display reads from its
// assigned AHB in the given ring slot:
// - sDrmPrimaryIdx -> sAhbRingPrimary[idx] (wallpaper + menu)
// - every other display -> sAhbRingSecondary[idx] (wallpaper only)
// If the secondary AHB isn't allocated (single-display hardware, or allocation
// failed), every display falls back to the primary AHB (mirrored).
// Non-blocking page flip per display: displays are independent -- if one fails
// to flip, the others still present.
//
// When called with idx=0, this is exactly the classic single-buffered path
// (drmFlipAll). The triple-buffer QR path calls it with idx=0,1,2 on a
// rolling 2-frame-old schedule so glFinish() observes mostly-complete GPU
// work.
//
// skipNonPrimary: when true, page flip is only submitted to the primary
// CRTC. Used by the QR loop to flip the secondary at half the rate
// (~30 fps) since on the dual-display RG DS roughly half of the
// secondary flips were getting EBUSY'd anyway due to cross-CRTC vblank
// drift, and the secondary's bottom-DS-screen content rarely changes
// fast enough that 30 fps is visible. AHB locks are skipped too so
// the CPU blit cost goes away on those iters.
void drmFlipRingSlot(int idx, bool skipNonPrimary) {
    if (idx < 0 || idx >= AHB_RING_DEPTH) return;
    AhbRenderTarget& prim = sAhbRingPrimary[idx];
    AhbRenderTarget& sec  = sAhbRingSecondary[idx];
    if (!sDrmZeroCopy || !prim.ahb) return;

    // Frame-sync mode: delay the SECONDARY CRTC's flip by one refresh so
    // its logical content matches what the PRIMARY CRTC is showing at
    // the same wall-clock moment.
    //
    // Rationale: on RK3568 dual-DSI (RG DS), VP0 and VP1 each scan out
    // on independent clocks with no phase lock. The existing flip order
    // (secondaries submitted first, primary last -- see comment at the
    // per-display loop) biases the primary to take the worst-case
    // vblank miss, so the primary (bottom) panel consistently lands
    // about one refresh AFTER the secondary (top) panel. Without frame
    // sync the user sees the top screen update first and the bottom
    // screen update ~16 ms later, which reads as a subtle tearing /
    // rolling between the two screens when content changes quickly.
    // Holding the secondary back by one userspace iter lines up the
    // logical frame each panel shows at any given instant.
    //
    // Implementation: the secondary CRTC flips to the AHB slot that was
    // the current-iter slot on the PREVIOUS call. That slot's GPU work
    // is already complete (its fence was consumed then), and nothing
    // has touched the slot between then and now because the render
    // cursor moved forward and the ring is sized to keep the hold slot
    // free (AHB_RING_DEPTH=5 gives: render N, fence N-1, primary
    // scanout N-2, secondary scanout N-3, spare).
    //
    // First iter: no previous slot exists -- fall back to flipping both
    // CRTCs from the same slot. This produces one frame of unsynced
    // output at startup; subsequent frames are phase-aligned.
    //
    // Caveats:
    //   - skipNonPrimary (libretro QR half-rate) disables the delay
    //     since the secondary isn't being flipped anyway.
    //   - Single-display boxes disable the delay (no secondary, no
    //     point deferring anything).
    static int sFrameSyncHoldSlot = -1;
    AhbRenderTarget* secSrcForSecondaryCrtc = &sec;
    bool frameSyncUsingHoldSlot = false;
    if (sDrmFrameSync && !skipNonPrimary && sDrmDisplays.size() > 1 &&
        sec.ahb) {
        if (sFrameSyncHoldSlot >= 0 &&
            sFrameSyncHoldSlot < AHB_RING_DEPTH &&
            sAhbRingSecondary[sFrameSyncHoldSlot].ahb) {
            secSrcForSecondaryCrtc = &sAhbRingSecondary[sFrameSyncHoldSlot];
            frameSyncUsingHoldSlot = true;
        }
        sFrameSyncHoldSlot = idx;
    } else {
        // Feature disabled or single-display: reset hold so a later
        // re-enable starts clean instead of flipping to a stale slot.
        sFrameSyncHoldSlot = -1;
    }

    static int sFlipCount = 0;
    // Log timing for the first 5 flips (boot window) then once per
    // second thereafter so we can observe steady-state latency
    // without flooding logcat. A "slow" flip (total > 10 ms) also
    // emits unconditionally so microhitches are captured, but
    // rate-limited to once per second to keep logs clean.
    bool periodic = (sFlipCount < 5) || (sFlipCount % 60 == 0);
    bool verbose = true; // always capture timing; filter at print time
    int64_t t0 = (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL);

    // Unbind FBO so subsequent GL calls don't mess with AHB
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    const bool primeActive = (prim.drmFbId != 0) &&
                             (!sec.ahb || sec.drmFbId != 0);
    const bool haveSecondary = (sec.ahb != nullptr);
    const int blitRotation = sDrmGlRotation ? 0 : sDrmRotationDeg;

    // GPU fence wait. Two paths:
    //
    // 1. PRIME: DRM page-flips directly to the AHB -- no CPU read, so
    //    AHardwareBuffer_lock is unnecessary. Use eglClientWaitSyncKHR
    //    on the slot's fence directly. Saves ~500 us of lock + 20 us
    //    of unlock per iter on RG DS -- these add up to ~3 % CPU.
    //
    // 2. Legacy blit: need a CPU mapping to memcpy AHB -> dumb buffer,
    //    so AHB_lock is unavoidable. Pass the fence fd so the lock
    //    waits on it (rather than letting AHB's broken implicit
    //    dma-fence sync on RK3568 lead to tearing).
    //
    // 3. No fence + non-PRIME: fallback to glFinish() (global drain).
    int primaryFenceFd = -1;
    bool fenceUsed = false;
    if (sAhbRingSyncPrimary[idx] != EGL_NO_SYNC_KHR &&
        sEglDupNativeFenceFDANDROID && sEglDestroySyncKHR &&
        sRingEglDpy != EGL_NO_DISPLAY) {
        if (primeActive && sEglClientWaitSyncKHR) {
            // PRIME path: sync via eglClientWaitSyncKHR, no fd needed.
            sEglClientWaitSyncKHR(sRingEglDpy,
                                  sAhbRingSyncPrimary[idx],
                                  EGL_SYNC_FLUSH_COMMANDS_BIT_KHR,
                                  100000000);  // 100 ms timeout
            fenceUsed = true;
        } else {
            primaryFenceFd = sEglDupNativeFenceFDANDROID(
                    sRingEglDpy, sAhbRingSyncPrimary[idx]);
            if (primaryFenceFd >= 0) fenceUsed = true;
        }
        sEglDestroySyncKHR(sRingEglDpy, sAhbRingSyncPrimary[idx]);
        sAhbRingSyncPrimary[idx] = EGL_NO_SYNC_KHR;
    }
    if (!fenceUsed && !primeActive) {
        // Legacy blit path needs a pre-flip barrier since the AHB lock
        // below is passing fence_fd=-1. PRIME path doesn't need this --
        // if there was no fence, the GPU must already be idle (nothing
        // to sync for a slot that was never rendered).
        glFinish();
    }

    int64_t tFinish = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Pre-query strides once per AHB (may be padded beyond width).
    // Only needed for the legacy blit path; PRIME flips use fb_id alone.
    AHardwareBuffer_Desc descPrimary = {};
    uint32_t primaryStride = 0;
    AHardwareBuffer_Desc descSecondary = {};
    uint32_t secondaryStride = 0;
    void* primaryPtr = nullptr;
    void* secondaryPtr = nullptr;

    if (!primeActive) {
        AHardwareBuffer_describe(prim.ahb, &descPrimary);
        primaryStride = descPrimary.stride * 4;
        if (haveSecondary) {
            AHardwareBuffer_describe(sec.ahb, &descSecondary);
            secondaryStride = descSecondary.stride * 4;
        }

        // Lock primary AHB for CPU read. fenceUsed means we have a
        // dup'd fence fd; AHB_lock will wait on it and take ownership.
        int lockErr = AHardwareBuffer_lock(prim.ahb,
                                            AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                                            fenceUsed ? primaryFenceFd : -1,
                                            nullptr, &primaryPtr);
        if (lockErr != 0 || !primaryPtr) {
            if (fenceUsed && primaryFenceFd >= 0) {
                close(primaryFenceFd); // ownership not transferred on failure
            }
            if (verbose) ALOGW("NanoMenu DRM: primary AHB lock failed %d (slot=%d)",
                               lockErr, idx);
            return;
        }
    } else if (primaryFenceFd >= 0) {
        // PRIME path already waited via eglClientWaitSyncKHR, but we
        // never actually used the fence_fd -- close it to avoid a fd leak.
        close(primaryFenceFd);
    }

    // Lock secondary AHB (legacy blit path only). Same slot's fence
    // covers both primary and secondary renders (they're done in
    // sequence, fence inserted after both). Since the fence fd was
    // consumed by the primary lock, pass -1 here -- the primary's wait
    // has already guaranteed both surfaces' GPU work is complete. PRIME
    // path skips this: no CPU read of secondary AHB is needed, the
    // page flip reads from the dma-buf directly.
    if (!primeActive && haveSecondary && !skipNonPrimary) {
        int serr = AHardwareBuffer_lock(sec.ahb,
                                         AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                                         -1, nullptr, &secondaryPtr);
        if (serr != 0 || !secondaryPtr) {
            if (verbose) ALOGW("NanoMenu DRM: secondary AHB lock failed %d (slot=%d)",
                               serr, idx);
            secondaryPtr = nullptr;
        }
    }

    int64_t tLock = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Blit + flip each display independently. Order matters on dual-DSI
    // (RG DS): the two panels run on independent vblank clocks with no
    // phase lock, so whichever CRTC we ioctl first has the shorter queue
    // time and tends to latch its flip one vblank earlier. Submitting
    // secondaries FIRST puts the worst-case phase lag on the primary
    // instead of the top screen, which is where users actually notice
    // motion (HUD / gameplay) on drastic-nano.
    //
    // A failure on one display does not prevent the others from
    // presenting; both flips are non-blocking ioctls.
    for (size_t outer = 0; outer < sDrmDisplays.size(); outer++) {
        // Iterate secondaries first, primary last: map outer index
        // to the real display index so primary lands in the final
        // slot of the loop.
        size_t i;
        if (sDrmDisplays.size() == 1) {
            i = outer;
        } else if ((int)outer == (int)sDrmDisplays.size() - 1) {
            i = (size_t)sDrmPrimaryIdx;
        } else {
            // Pick the next non-primary index in order.
            size_t count = 0;
            i = 0;
            for (size_t j = 0; j < sDrmDisplays.size(); j++) {
                if ((int)j == sDrmPrimaryIdx) continue;
                if (count == outer) { i = j; break; }
                count++;
            }
        }
        auto& d = sDrmDisplays[i];
        const bool isPrimary = ((int)i == sDrmPrimaryIdx);

        // Skip non-primary CRTCs when caller asked us to (QR loop's
        // secondary-rate-halving optimization). Secondary keeps the
        // pixels it had on its last successful flip; visually that's
        // a 30 fps update on the bottom screen which is below DS
        // perception threshold for typical content.
        if (!isPrimary && skipNonPrimary) continue;

        // Select source AHB. Secondary displays use sec AHB if available
        // (PRIME: always, legacy: only if lock succeeded); else fall
        // back to primary (mirror mode).
        const bool useSecondaryAhb = !isPrimary && haveSecondary &&
                (primeActive || secondaryPtr != nullptr);
        // Frame-sync secondary routing: when enabled, the secondary
        // CRTC flips to the secondary-AHB slot that was the current
        // iter's slot on the PREVIOUS call, delaying its visible
        // content by one userspace iter so it matches what the
        // primary panel is showing at the same wall-clock moment.
        // Restricted to the PRIME path because the legacy blit path
        // relies on the secondary AHB being CPU-locked for the
        // current slot, and we don't want to add a second lock on the
        // hot path when RG DS runs PRIME 100 % of the time.
        const bool useFrameSyncSecondary = !isPrimary && useSecondaryAhb &&
                frameSyncUsingHoldSlot &&
                secSrcForSecondaryCrtc->drmFbId != 0;
        const AhbRenderTarget& srcAhb = useFrameSyncSecondary
                ? *secSrcForSecondaryCrtc
                : (useSecondaryAhb ? sec : prim);

        int bufIdx = 1 - d.activeBuffer;
        DrmBuffer& buf = d.buffers[bufIdx];

        // DRM PRIME zero-copy path: when the source AHB was successfully
        // imported as a DRM scanout fb at allocation time, we skip the
        // CPU memcpy entirely and page flip straight to the AHB's
        // fb_id. Saves ~2-3 ms CPU per iter per display on RG DS.
        const bool primePath = (srcAhb.drmFbId != 0);
        uint32_t targetFbId = primePath ? srcAhb.drmFbId : buf.fbId;

        if (!primePath) {
            const void* srcPtr = useSecondaryAhb ? secondaryPtr : primaryPtr;
            uint32_t srcStride = useSecondaryAhb ? secondaryStride : primaryStride;
            uint32_t srcW = useSecondaryAhb ? sec.w : prim.w;
            uint32_t srcH = useSecondaryAhb ? sec.h : prim.h;
            blitAhbToDrmBuffer(srcPtr, srcStride, srcW, srcH,
                                buf.mapped, buf.pitch, d.w, d.h, blitRotation);
        }

        // Page flip -- non-blocking. flags=0 means no vblank event is
        // requested, just swap the buffer on next vsync.
        //
        // Error handling (2026-04-13):
        //   -EBUSY: previous page flip for this CRTC has not landed
        //     yet. Previously we fell through to drmModeSetCrtc here,
        //     which is SYNCHRONOUS and blocks until the modeset takes
        //     effect on the next vblank. Per-frame timing showed the
        //     fallback hitting 13-32 ms on microhitch frames, driving
        //     the occasional 30-50 ms stall. On -EBUSY the correct
        //     action is to drop this flip -- the still-pending flip
        //     already has a newer buffer queued than what is on
        //     screen, so we simply keep the current active buffer
        //     index and let the next iteration try again.
        //   Other errors: fall back to drmModeSetCrtc, which handles
        //     the one-time "CRTC not enabled yet" case at the
        //     startup splash where the CRTC needs to be primed.
        // Page flip submission. Two pacing modes:
        //
        // - Working vblank (default, RG DS / RK3568): flags=0. The render
        //   loop sync's via DRM_IOCTL_WAIT_VBLANK. No page-flip events
        //   requested -> nothing to drain, and on dual-display setups we
        //   avoid waiting for cross-CRTC event timing skew (which on RG DS
        //   was adding ~8ms to the drain on top of WAIT_VBLANK -> dropping
        //   us into 30-fps pacing). This preserves the 60-fps behavior
        //   that landed in commit 10885f101d7.
        //
        // - Broken vblank (RK3576 / RGVITA, DSI command-mode): flags=
        //   DRM_MODE_PAGE_FLIP_EVENT. The render loop sync's via
        //   drmDrainPageFlipEvents which reads the events the kernel posts
        //   when the flip latches. WAIT_VBLANK on these panels never fires
        //   so it's skipped after detection (sDrmVblankBroken=true).
        //
        // sDrmVblankBroken flips true the first time WAIT_VBLANK exceeds
        // 100ms, after which all flips request the EVENT flag.
        struct drm_mode_crtc_page_flip flip = {};
        flip.crtc_id = d.crtcId;
        flip.fb_id = targetFbId;
        // Only request EVENT when we're going to drain it (vsync gate ON
        // and broken-vblank path active). When vsync is disabled at runtime
        // we'd never call the drainer, and unread events would pile up in
        // the DRM fd's queue until the kernel drops them.
        // Request page-flip completion events on:
        //  - broken-vblank panels (RK3576 DSI command-mode): kernel's vblank
        //    queue never wakes so WAIT_VBLANK is useless.
        //  - multi-CRTC setups (RG DS dual DSI): the two panels have separate
        //    vblank clocks that drift relative to each other. WAIT_VBLANK on
        //    the primary CRTC alone mis-paces the secondary CRTC's flips,
        //    producing EBUSY storms on the non-primary display whenever the
        //    phase drifts into alignment. drmDrainPageFlipEvents waits for
        //    every submitted flip to latch, giving correct cross-CRTC sync.
        const bool wantEvent =
                (sDrmVblankBroken || sDrmDisplays.size() > 1) &&
                (sVsyncEnabled != 0);
        flip.flags = wantEvent ? DRM_MODE_PAGE_FLIP_EVENT : 0;
        // Pre-check: if this CRTC still has a pending flip from a prior
        // iteration, don't submit another one. The kernel would EBUSY us
        // anyway, and submitting too early is what caused the tearing
        // storm after the 60Hz panel retiming (EBUSY count climbed ~200/min).
        // The previous iteration's drain should have consumed the event, but
        // on multi-CRTC setups with phase skew, stale events can decrement
        // sPendingFlipEvents for a flip that hasn't actually landed yet.
        // Per-CRTC tracking fixes this: wait until this specific CRTC's
        // event has arrived before submitting a new flip to it.
        int crtcSlot = wantEvent ? drmCrtcSlot(d.crtcId) : -1;
        if (crtcSlot >= 0 && sCrtcPending[crtcSlot] > 0) {
            if (!primePath) d.activeBuffer = bufIdx;
            continue;
        }
        int flipRc = ioctl(sDrmFd, DRM_IOCTL_MODE_PAGE_FLIP, &flip);
        if (flipRc == 0 && wantEvent) {
            sPendingFlipEvents++;
            if (crtcSlot >= 0) sCrtcPending[crtcSlot]++;
        }
        if (flipRc != 0) {
            if (errno == EBUSY) {
                // Drop this flip. Keep the previous buffer as active
                // so the next iteration's page flip targets the
                // correct slot for the double-buffer rotation.
                //
                // Rate-limited diagnostic: EBUSY means the last flip
                // for this CRTC has not landed yet. Visible as a
                // stutter on the affected display. Counts accumulate
                // per CRTC so cross-display correlation is possible.
                static int64_t sLastEbusyLogMs = 0;
                static uint32_t sEbusyCount[8] = {0};
                if (i < 8) sEbusyCount[i]++;
                int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
                if (nowMs - sLastEbusyLogMs >= 500) {
                    sLastEbusyLogMs = nowMs;
                    ALOGW("NanoMenu DRM flip EBUSY: display=%zu primary=%d "
                          "flipCount=%d ebusy=[%u/%u/%u/%u]",
                          i, (int)(isPrimary ? 1 : 0), sFlipCount,
                          sEbusyCount[0], sEbusyCount[1],
                          sEbusyCount[2], sEbusyCount[3]);
                }
                continue;
            }
            struct drm_mode_crtc crtc = {};
            crtc.crtc_id = d.crtcId;
            crtc.fb_id = targetFbId;
            crtc.set_connectors_ptr = (uint64_t)(uintptr_t)&d.connId;
            crtc.count_connectors = 1;
            crtc.mode = d.mode;
            crtc.mode_valid = 1;
            ioctl(sDrmFd, DRM_IOCTL_MODE_SETCRTC, &crtc);
        }
        // Track activeBuffer only when we used a dumb buffer; PRIME path
        // doesn't have alternating buffers (the AHB IS the framebuffer).
        if (!primePath) d.activeBuffer = bufIdx;
    }

    int64_t tCopy = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Unlock AHBs only if we locked them (legacy blit path).
    if (!primeActive && primaryPtr) {
        AHardwareBuffer_unlock(prim.ahb, nullptr);
    }
    if (secondaryPtr) {
        AHardwareBuffer_unlock(sec.ahb, nullptr);
    }

    {
        int64_t tEnd = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
        int64_t tot = tEnd - t0;
        // Rate-limit slow-flip logging to once per second so a burst
        // does not flood logcat. Periodic samples always emit.
        static int64_t sLastSlowLogMs = 0;
        int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        bool logSlow = (tot > 10000) &&
                       (nowMs - sLastSlowLogMs >= 1000);
        if (periodic || logSlow) {
            if (logSlow) sLastSlowLogMs = nowMs;
            ALOGW("NanoMenu AHB flip #%d slot=%d sync=%s: wait=%lldus "
                  "lock=%lldus blit+flip=%lldus unlock=%lldus "
                  "total=%lldus displays=%zu primary=%d sec=%d",
                  sFlipCount, idx, fenceUsed ? "fence" : "glFinish",
                  tFinish - t0, tLock - tFinish,
                  tCopy - tLock, tEnd - tCopy, tot,
                  sDrmDisplays.size(), sDrmPrimaryIdx,
                  haveSecondary ? 1 : 0);
        }
    }
    sFlipCount++;
}

// Legacy single-buffered path. Preserves XMB behavior and default QR flow
// (triple_buffer=0). Flips slot 0, which is exactly what every non-ring
// caller was doing before the ring landed.
void drmFlipAll() {
    drmFlipRingSlot(0);
}

// ---------------------------------------------------------------------------
// Vsync / page flip drain
// ---------------------------------------------------------------------------

// Drain all pending page-flip completion events from the DRM fd. Called
// once per render iteration AFTER the page flips have been submitted --
// blocks until every submitted flip has latched, giving us vsync-accurate
// pacing on both video-mode and command-mode panels.
//
// Replaces DRM_IOCTL_WAIT_VBLANK on panels where the kernel doesn't
// generate periodic vblank interrupts (RK3576 DSI command-mode). Works
// uniformly on all DRM drivers because every DRM driver that implements
// page flipping also implements the flip-complete event.
//
// Uses poll() with a 100 ms timeout per event so a dropped/missing event
// (driver bug) doesn't wedge the render thread forever. If an event is
// lost we log once and bail; sPendingFlipEvents will drift and the next
// iteration's EBUSY drops will naturally re-sync.
bool drmAnyCrtcPending() {
    for (int i = 0; i < sCrtcTrackCount; i++) {
        if (sCrtcPending[i] > 0) return true;
    }
    return false;
}

void drmDrainPageFlipEvents() {
    if (sDrmFd < 0) return;
    if (sPendingFlipEvents <= 0 && !drmAnyCrtcPending()) return;
    char buf[4096];
    static int64_t sLastTimeoutLogMs = 0;
    // Per-CRTC arrival tracking for slow-drain diagnostics. On multi-CRTC
    // setups we want to know WHICH display's flip event is arriving late.
    const int64_t drainT0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
    int64_t crtcArrivalUs[4] = {-1, -1, -1, -1};
    uint32_t crtcIds[4] = {0, 0, 0, 0};
    int numCrtcs = 0;
    // Loop until every CRTC we submitted to has received its event. The
    // per-CRTC gate is authoritative -- the global counter exists only for
    // legacy single-CRTC pacing. On multi-CRTC setups stale events can
    // decrement the global without matching any tracked CRTC, so waiting
    // on per-CRTC ensures we don't return until the flips we actually
    // submitted have all landed.
    while (sPendingFlipEvents > 0 || drmAnyCrtcPending()) {
        struct pollfd pfd = {};
        pfd.fd = sDrmFd;
        pfd.events = POLLIN;
        int pr = poll(&pfd, 1, 100);
        if (pr < 0) {
            if (errno == EINTR) continue;
            // fd broken -- abandon the drain so we don't spin
            sPendingFlipEvents = 0;
            for (int i = 0; i < sCrtcTrackCount; i++) sCrtcPending[i] = 0;
            return;
        }
        if (pr == 0) {
            // Timeout. Rate-limit the log to once per second so a panel
            // that never posts events doesn't flood logcat.
            int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            if (nowMs - sLastTimeoutLogMs >= 1000) {
                sLastTimeoutLogMs = nowMs;
                ALOGW("NanoMenu: page-flip event drain timeout, "
                      "pending=%d -- resyncing",
                      sPendingFlipEvents);
            }
            // Reset so we don't accumulate stale debt.
            sPendingFlipEvents = 0;
            for (int i = 0; i < sCrtcTrackCount; i++) sCrtcPending[i] = 0;
            return;
        }
        ssize_t n = read(sDrmFd, buf, sizeof(buf));
        if (n <= 0) {
            if (n < 0 && errno == EINTR) continue;
            sPendingFlipEvents = 0;
            for (int i = 0; i < sCrtcTrackCount; i++) sCrtcPending[i] = 0;
            return;
        }
        // Walk the event records. Each one starts with a drm_event header
        // (type + length). Events of type DRM_EVENT_FLIP_COMPLETE match
        // the flips we just issued; other event types (rare) are ignored.
        char* p = buf;
        char* end = buf + n;
        while (p + sizeof(struct drm_event) <= end) {
            struct drm_event* ev = (struct drm_event*)p;
            if (ev->length == 0 || p + ev->length > end) break;
            if (ev->type == DRM_EVENT_FLIP_COMPLETE &&
                sPendingFlipEvents > 0) {
                sPendingFlipEvents--;
                // Decrement per-CRTC pending count for this specific
                // CRTC so the next iteration's submit for the same CRTC
                // will only proceed when its previous flip has landed.
                if (ev->length >= sizeof(struct drm_event_vblank)) {
                    struct drm_event_vblank* vb =
                            (struct drm_event_vblank*)p;
                    int slot = drmCrtcSlot(vb->crtc_id);
                    if (slot >= 0 && sCrtcPending[slot] > 0) {
                        sCrtcPending[slot]--;
                    }
                    // Capture per-CRTC arrival time for the slow-drain log.
                    if (numCrtcs < 4) {
                        int64_t arrivalUs =
                                systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL -
                                drainT0;
                        crtcIds[numCrtcs] = vb->crtc_id;
                        crtcArrivalUs[numCrtcs] = arrivalUs;
                        numCrtcs++;
                    }
                }
            }
            p += ev->length;
        }
    }
    // If the drain was slow, log per-CRTC arrivals so we can tell which
    // display is dragging. Rate-limited to once per second.
    const int64_t drainTotalUs =
            systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL - drainT0;
    if (drainTotalUs > 15000 && numCrtcs > 1) {
        static int64_t sLastSlowDrainMs = 0;
        int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        if (nowMs - sLastSlowDrainMs >= 1000) {
            sLastSlowDrainMs = nowMs;
            ALOGW("NanoMenu slow drain: total=%lldus "
                  "crtc[0]=%u@%lldus crtc[1]=%u@%lldus",
                  (long long)drainTotalUs,
                  crtcIds[0], (long long)crtcArrivalUs[0],
                  crtcIds[1], (long long)crtcArrivalUs[1]);
        }
    }
}

// ---------------------------------------------------------------------------
// Frame helpers
// ---------------------------------------------------------------------------

// Helper: bind AHB FBO before rendering a frame (no-op if DRM inactive).
// Use this INSTEAD of glViewport at the start of ad-hoc render blocks
// (quick resume screens, loading screens, libretro overlays).
// Push GL framebuffer to DRM dumb buffers.
// Optimized for 60fps: GL_BGRA readback + NEON copy + double-buffered page flip.
void drmPushFrame(uint32_t glWidth, uint32_t glHeight) {
    if (!sDrmActive || sDrmDisplays.empty()) return;

    static int sPushCount = 0;
    static bool sBgraSupported = true; // try BGRA first, fall back to RGBA
    bool verbose = (sPushCount < 3);

    int64_t tStart = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;
    if (verbose) glFinish();
    int64_t tFinish = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Determine target buffer for first display; we'll reuse the readback for both
    DrmDisplay& d0 = sDrmDisplays[0];
    int targetIdx = 1 - d0.activeBuffer;
    uint8_t* dst0 = (uint8_t*)d0.buffers[targetIdx].mapped;
    uint32_t dstStride0 = d0.buffers[targetIdx].pitch;

    // glReadPixels directly into the DRM mmap'd buffer (avoids intermediate copy).
    // Use GL_BGRA_EXT if supported -- matches DRM's XRGB8888 natively (no swap needed).
    // GL reads bottom-up; we'll flip with a vertical y-inversion via pitch trick below.
    // Actually glReadPixels doesn't support negative stride, so read into linear buf,
    // then flip+copy to DRM. But we can read directly into the DRM buffer if we're OK
    // with upside-down output, OR read upside-down and flip during blit to display 2.
    //
    // Simplest fast path: read into a reusable static scratch buffer, then memcpy rows
    // in reverse order into each DRM buffer. This is ~1.2MB read + 2x1.2MB memcpy.
    static std::vector<uint8_t> scratch;
    size_t pixelBytes = glWidth * glHeight * 4;
    if (scratch.size() != pixelBytes) scratch.resize(pixelBytes);

    GLenum fmt = sBgraSupported ? GL_BGRA_EXT : GL_RGBA;
    glReadPixels(0, 0, glWidth, glHeight, fmt, GL_UNSIGNED_BYTE, scratch.data());
    if (glGetError() != GL_NO_ERROR && sBgraSupported) {
        // Fall back to RGBA if BGRA not supported
        sBgraSupported = false;
        glReadPixels(0, 0, glWidth, glHeight, GL_RGBA, GL_UNSIGNED_BYTE, scratch.data());
    }

    int64_t tReadback = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Row-by-row flipped copy into each display's inactive buffer.
    uint32_t rowBytes = glWidth * 4;
    for (auto& d : sDrmDisplays) {
        int idx = 1 - d.activeBuffer;
        DrmBuffer& buf = d.buffers[idx];
        uint8_t* dst = (uint8_t*)buf.mapped;
        uint32_t copyH = std::min(glHeight, d.h);
        uint32_t copyRowBytes = std::min(rowBytes, buf.pitch);

        if (sBgraSupported) {
            // Direct row copy (BGRA matches XRGB8888)
            for (uint32_t y = 0; y < copyH; y++) {
                uint8_t* srcRow = scratch.data() + (glHeight - 1 - y) * rowBytes;
                memcpy(dst + y * buf.pitch, srcRow, copyRowBytes);
            }
        } else {
            // RGBA -> BGRA swap via manual loop
            for (uint32_t y = 0; y < copyH; y++) {
                uint32_t* srcRow = (uint32_t*)(scratch.data() + (glHeight - 1 - y) * rowBytes);
                uint32_t* dstRow = (uint32_t*)(dst + y * buf.pitch);
                uint32_t cw = std::min(glWidth, d.w);
                for (uint32_t x = 0; x < cw; x++) {
                    uint32_t rgba = srcRow[x];
                    dstRow[x] = 0xFF000000 |
                                ((rgba >> 16) & 0xFF) |          // R -> B
                                ((rgba & 0xFF00)) |              // G
                                ((rgba & 0xFF) << 16);           // B -> R
                }
            }
        }
    }

    int64_t tCopy = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Page flip each display
    for (auto& d : sDrmDisplays) {
        int idx = 1 - d.activeBuffer;
        struct drm_mode_crtc_page_flip flip = {};
        flip.crtc_id = d.crtcId;
        flip.fb_id = d.buffers[idx].fbId;
        flip.flags = 0;
        if (ioctl(sDrmFd, DRM_IOCTL_MODE_PAGE_FLIP, &flip) != 0) {
            // Fallback to setCrtc
            struct drm_mode_crtc crtc = {};
            crtc.crtc_id = d.crtcId;
            crtc.fb_id = d.buffers[idx].fbId;
            crtc.set_connectors_ptr = (uint64_t)(uintptr_t)&d.connId;
            crtc.count_connectors = 1;
            crtc.mode = d.mode;
            crtc.mode_valid = 1;
            ioctl(sDrmFd, DRM_IOCTL_MODE_SETCRTC, &crtc);
        }
        d.activeBuffer = idx;
    }

    if (verbose) {
        int64_t tEnd = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
        ALOGW("NanoMenu DRM push #%d: bgra=%d glFinish=%lldus readback=%lldus copy=%lldus flip=%lldus total=%lldus",
              sPushCount, sBgraSupported ? 1 : 0,
              tFinish - tStart, tReadback - tFinish, tCopy - tReadback, tEnd - tCopy, tEnd - tStart);
    }
    (void)dst0; (void)dstStride0;
    sPushCount++;
}

// ---------------------------------------------------------------------------
// Teardown
// ---------------------------------------------------------------------------

// Stop DRM direct rendering (HWC has taken over)
void drmStop() {
    if (!sDrmActive) return;
    sDrmActive = false;
    // GammaOS: release the SurfaceFlinger composition gate now that HWC
    // owns the display again. Mirrors the set at drmEarlySplash().
    property_set("sys.gammaos.nano.drm_active", "0");
    sDrmZeroCopy = false;
    // Reset GL rotation to identity for the SF EGL path
    sDrmGlRotation = false;
    sDrmRotMat[0] = 1.0f; sDrmRotMat[1] = 0.0f;
    sDrmRotMat[2] = 0.0f; sDrmRotMat[3] = 1.0f;
    ALOGW("NanoMenu DRM splash: stopping direct rendering, HWC has taken over");

    // Rebind default framebuffer before destroying FBOs
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    // Release all ring slots. sAhbTarget / sAhbTargetSecondary are refs to
    // slot 0 so they tear down with the ring. The EGLDisplay is retrieved
    // once and reused for every slot's eglDestroyImageKHR.
    EGLDisplay eglDpy = eglGetCurrentDisplay();
    auto releaseSlot = [&](AhbRenderTarget& t) {
        if (t.glFbo) { glDeleteFramebuffers(1, &t.glFbo); t.glFbo = 0; }
        if (t.glTexture) { glDeleteTextures(1, &t.glTexture); t.glTexture = 0; }
        if (t.eglImage != EGL_NO_IMAGE_KHR && eglDpy != EGL_NO_DISPLAY &&
            sEglDestroyImageKHR) {
            sEglDestroyImageKHR(eglDpy, t.eglImage);
        }
        t.eglImage = EGL_NO_IMAGE_KHR;
        // Release DRM PRIME fb + GEM handle if we imported one.
        if (t.drmFbId != 0 && sDrmFd >= 0) {
            ioctl(sDrmFd, DRM_IOCTL_MODE_RMFB, &t.drmFbId);
            t.drmFbId = 0;
        }
        if (t.drmGemHandle != 0 && sDrmFd >= 0) {
            struct drm_gem_close gc = {};
            gc.handle = t.drmGemHandle;
            ioctl(sDrmFd, DRM_IOCTL_GEM_CLOSE, &gc);
            t.drmGemHandle = 0;
        }
        if (t.ahb) { AHardwareBuffer_release(t.ahb); t.ahb = nullptr; }
        t.w = 0; t.h = 0;
    };
    for (int i = 0; i < AHB_RING_DEPTH; i++) {
        releaseSlot(sAhbRingPrimary[i]);
        releaseSlot(sAhbRingSecondary[i]);
        // Release any lingering EGL fence for this slot. These normally
        // get destroyed when drmFlipRingSlot consumes them, but a
        // pending fence can outlive the loop if QR was interrupted
        // mid-render.
        if (sAhbRingSyncPrimary[i] != EGL_NO_SYNC_KHR &&
            sEglDestroySyncKHR && sRingEglDpy != EGL_NO_DISPLAY) {
            sEglDestroySyncKHR(sRingEglDpy, sAhbRingSyncPrimary[i]);
        }
        sAhbRingSyncPrimary[i] = EGL_NO_SYNC_KHR;
    }
    // Reset ring cursors so a subsequent drmSetupZeroCopy starts fresh.
    sRingRenderIdx = 0;
    sRingPresentIdx = 0;
    sRingPrimedCount = 0;
    sRingEglDpy = EGL_NO_DISPLAY;
    // Drain any outstanding page flip events before handing the DRM fd
    // back (or closing it). Otherwise the kernel holds buffers hostage
    // and the next CRTC user (SurfaceFlinger on restart, for instance)
    // gets stuck because its first commit is blocked on our unconsumed
    // flip. 50 ms is plenty -- events arrive at panel refresh rate.
    drmDrainPageFlipEvents();
    sPendingFlipEvents = 0;
    for (int i = 0; i < sCrtcTrackCount; i++) sCrtcPending[i] = 0;

    for (auto& d : sDrmDisplays) {
        for (int i = 0; i < 2; i++) {
            DrmBuffer& buf = d.buffers[i];
            if (buf.mapped) { munmap(buf.mapped, buf.size); buf.mapped = nullptr; }
        }
    }
    sDrmDisplays.clear();
    if (sDrmFd >= 0) { close(sDrmFd); sDrmFd = -1; }
}

} // namespace android
