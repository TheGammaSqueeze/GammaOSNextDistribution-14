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

#include <sched.h>
#include <condition_variable>
#include <mutex>
#include <algorithm>
#include <vector>
#include <thread>
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
#include <ui/GraphicBufferMapper.h>  // gralloc metadata (AFBC modifier/layout)
#include <ui/GraphicTypes.h>         // ui::PlaneLayout

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
bool sDrmLowLatency = false;
bool sDrmAfbcMode = false;  /* rk356x + Low Latency: AFBC buffers + Cluster planes */
bool sDrmAfbcClient = false; /* set by drastic-nano before its DRM setup: it renders both DS screens into the combined buffer */
uint32_t sDrmAfbcHalfH = 0; /* per-panel height; combined buffer is 2x this tall */
static int64_t sDrmReadyUs = 0; /* frame ready-to-present instant, latency probe */
int sPendingFlipEvents = 0;
uint32_t sDrmSeamRotCrtc = 0;
int sDrmSecondaryRotDeg = 0;       // mount rotation of the VOP port 1 panel (persist.gsf.rot.1 / persist.gsf.sec_rot)
int sDrmVp1DisplayIdx = -1;        // index in sDrmDisplays of the panel on VOP video port 1, -1 if none
bool sDrmPrimaryTurned = false;    // nano home: the primary ring's panel is the turned (port 1) one
bool sDrmSecondaryTurned = false;  // nano home: the secondary ring's panel is the turned one

// Turned-panel resolve. The passes keep rendering exactly as on an untouched
// panel (every matrix, flip and glyph path unchanged) into a shared scratch FBO;
// right before the slot's fence / flip the scratch image is drawn into the real
// scanout AHB with its texture coordinates turned 180 degrees. One fullscreen
// textured quad per turned panel per frame, well under a millisecond on the G52.
struct TurnScratch { GLuint fbo = 0, tex = 0; uint32_t w = 0, h = 0; };
static TurnScratch sTurnPrimary, sTurnSecondary;
static GLuint sTurnProg = 0;
static GLint sTurnPosLoc = -1, sTurnUvLoc = -1, sTurnTexLoc = -1;

static GLuint drmTurnCompile(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = GL_FALSE;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) { glDeleteShader(sh); return 0; }
    return sh;
}

static bool drmTurnEnsureProgram() {
    if (sTurnProg) return true;
    static const char* kVs =
        "attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv;\n"
        "void main() { gl_Position = vec4(aPos, 0.0, 1.0); vUv = aUv; }\n";
    static const char* kFs =
        "precision mediump float; varying vec2 vUv; uniform sampler2D uTex;\n"
        "void main() { gl_FragColor = texture2D(uTex, vUv); }\n";
    GLuint vs = drmTurnCompile(GL_VERTEX_SHADER, kVs);
    GLuint fs = drmTurnCompile(GL_FRAGMENT_SHADER, kFs);
    if (!vs || !fs) { if (vs) glDeleteShader(vs); if (fs) glDeleteShader(fs); return false; }
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs); glAttachShader(prog, fs);
    glLinkProgram(prog);
    glDeleteShader(vs); glDeleteShader(fs);
    GLint ok = GL_FALSE;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) { glDeleteProgram(prog); ALOGW("NanoMenu DRM turn: blit program failed to link"); return false; }
    sTurnProg = prog;
    sTurnPosLoc = glGetAttribLocation(prog, "aPos");
    sTurnUvLoc  = glGetAttribLocation(prog, "aUv");
    sTurnTexLoc = glGetUniformLocation(prog, "uTex");
    return true;
}

// Redirect a ring slot's rendering into the (shared, lazily created) scratch FBO
// and remember the real scanout FBO for the resolve.
static void drmTurnAttach(AhbRenderTarget* t, TurnScratch* s, const char* label) {
    if (!t->glFbo || t->scanFbo) return;
    if (!drmTurnEnsureProgram()) return;
    if (!s->fbo) {
        GLint prevFbo = 0, prevTex = 0;
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &prevTex);
        glGenTextures(1, &s->tex);
        glBindTexture(GL_TEXTURE_2D, s->tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, (GLsizei)t->w, (GLsizei)t->h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenFramebuffers(1, &s->fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, s->fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, s->tex, 0);
        const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
        glBindTexture(GL_TEXTURE_2D, (GLuint)prevTex);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            ALOGW("NanoMenu DRM turn: scratch FBO(%s) incomplete 0x%x", label, status);
            glDeleteFramebuffers(1, &s->fbo); glDeleteTextures(1, &s->tex);
            s->fbo = s->tex = 0;
            return;
        }
        s->w = t->w; s->h = t->h;
        ALOGW("NanoMenu DRM turn: %s renders into a %ux%u scratch, resolved 180 into scanout", label, s->w, s->h);
    }
    t->scanFbo = t->glFbo;
    t->glFbo = s->fbo;
}

static void drmTurnBlit(AhbRenderTarget& t, const TurnScratch& s) {
    if (!t.scanFbo || !s.tex || !sTurnProg) return;
    GLint prevFbo = 0, prevProg = 0, prevTex = 0, prevActive = 0, prevArray = 0, prevVp[4] = {0, 0, 0, 0};
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    glGetIntegerv(GL_CURRENT_PROGRAM, &prevProg);
    glGetIntegerv(GL_ACTIVE_TEXTURE, &prevActive);
    glActiveTexture(GL_TEXTURE0);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &prevTex);
    glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &prevArray);
    glGetIntegerv(GL_VIEWPORT, prevVp);
    const GLboolean blend = glIsEnabled(GL_BLEND), scissor = glIsEnabled(GL_SCISSOR_TEST);
    const GLboolean depth = glIsEnabled(GL_DEPTH_TEST), cull = glIsEnabled(GL_CULL_FACE);
    GLint posWas = 0, uvWas = 0;
    if (sTurnPosLoc >= 0) glGetVertexAttribiv((GLuint)sTurnPosLoc, GL_VERTEX_ATTRIB_ARRAY_ENABLED, &posWas);
    if (sTurnUvLoc >= 0)  glGetVertexAttribiv((GLuint)sTurnUvLoc,  GL_VERTEX_ATTRIB_ARRAY_ENABLED, &uvWas);

    glBindFramebuffer(GL_FRAMEBUFFER, t.scanFbo);
    glViewport(0, 0, (GLsizei)t.w, (GLsizei)t.h);
    glDisable(GL_BLEND); glDisable(GL_SCISSOR_TEST); glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE);
    glUseProgram(sTurnProg);
    glBindTexture(GL_TEXTURE_2D, s.tex);
    glUniform1i(sTurnTexLoc, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    // Fullscreen strip; texture coordinates turned 180 degrees (u,v) -> (1-u,1-v).
    static const GLfloat kPos[8] = { -1.f, -1.f,  1.f, -1.f,  -1.f, 1.f,  1.f, 1.f };
    static const GLfloat kUv[8]  = {  1.f,  1.f,  0.f,  1.f,   1.f, 0.f,  0.f, 0.f };
    glVertexAttribPointer((GLuint)sTurnPosLoc, 2, GL_FLOAT, GL_FALSE, 0, kPos);
    glEnableVertexAttribArray((GLuint)sTurnPosLoc);
    glVertexAttribPointer((GLuint)sTurnUvLoc, 2, GL_FLOAT, GL_FALSE, 0, kUv);
    glEnableVertexAttribArray((GLuint)sTurnUvLoc);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    if (!posWas) glDisableVertexAttribArray((GLuint)sTurnPosLoc);
    if (!uvWas)  glDisableVertexAttribArray((GLuint)sTurnUvLoc);
    glBindBuffer(GL_ARRAY_BUFFER, (GLuint)prevArray);
    glBindTexture(GL_TEXTURE_2D, (GLuint)prevTex);
    glActiveTexture((GLenum)prevActive);
    glUseProgram((GLuint)prevProg);
    if (blend) glEnable(GL_BLEND);
    if (scissor) glEnable(GL_SCISSOR_TEST);
    if (depth) glEnable(GL_DEPTH_TEST);
    if (cull) glEnable(GL_CULL_FACE);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
}

void drmResolveTurnedTargets(int idx) {
    if (idx < 0 || idx >= AHB_RING_DEPTH) return;
    if (sDrmPrimaryTurned)   drmTurnBlit(sAhbRingPrimary[idx],   sTurnPrimary);
    if (sDrmSecondaryTurned) drmTurnBlit(sAhbRingSecondary[idx], sTurnSecondary);
}

int drmPanelMountRotationDeg(int port) {
    if (port <= 0) return 0;   // port 0 is SurfaceFlinger's primary: ro.surface_flinger.primary_display_orientation
    auto toDeg = [](const char* s) -> int {
        if (!strcmp(s, "ORIENTATION_90")  || !strcmp(s, "90"))  return 90;
        if (!strcmp(s, "ORIENTATION_180") || !strcmp(s, "180")) return 180;
        if (!strcmp(s, "ORIENTATION_270") || !strcmp(s, "270")) return 270;
        return 0;
    };
    char key[PROPERTY_KEY_MAX];
    char val[PROPERTY_VALUE_MAX];
    snprintf(key, sizeof(key), "persist.gsf.rot.%d", port);
    if (property_get(key, val, "") > 0) return toDeg(val);
    if (property_get("persist.gsf.sec_rot", val, "") > 0) return toDeg(val);
    return 0;
}
int64_t sDrmLastVblankUs = 0;   // CLOCK_MONOTONIC us of the latest primary flip-complete
static int64_t sDrmVblankPeriodUs = 16667;   // learned from consecutive primary flip timestamps
static int sDrmLastFenceFd = -1; // in-fence of the last AFBC commit (GPU completion probe)
static int sDrmPrevFenceFd = -1; // in-fence of the commit before that (the one just drained)
// Deferred drain (AFBC low-latency path): the atomic commit returns at once
// and its flip event is collected right before the NEXT commit instead of
// immediately after this one. The presenter then renders frame N+1 while
// frame N's fence and scanout are still pending. When the GPU is fast the
// commit still lands on the very next vblank (age 0 unchanged); when the
// GPU fence is slow (heavy scenes) the pipeline overlaps instead of every
// flip slipping a whole vblank. Prop sys.gammaos.drastic_nano.defer_drain.
// Adaptive: the deferral only engages while the GPU fence is measured to
// complete slowly (heavy scenes); with a fast fence the immediate drain keeps
// the sub-frame latency of light scenes (a permanent deferral there settled
// into a self-perpetuating one-vblank offset, 2.4 frames on Sonic Rush).
static bool sDrmDeferDrain = true;
static bool sDrmDeferActive = false;
static int64_t sDrmLastCommitUs = 0, sDrmPrevCommitUs = 0;
static int64_t sDrmLastEnterUs = 0;   // when drmFlipRingSlot was entered for the last commit (render just submitted)
static int sDrmGpuSlowFrames = 0, sDrmGpuFastFrames = 0;
static bool drmDeferDrainOn() {
    static int sCount = 0;
    if ((sCount++ % 120) == 0)
        sDrmDeferDrain = property_get_bool("sys.gammaos.drastic_nano.defer_drain", true);
    return sDrmDeferDrain && sDrmAfbcMode && sDrmDeferActive;
}
static int64_t drmFenceDoneUs(int fd);
// Called once per presented frame with the GPU completion latency of the
// commit that just landed (fence signal time minus commit time).
// The decision is whether the frame would have reached the first vblank
// after the render was handed over: a fence that signals later than that
// vblank (minus a margin) means the immediate drain would have slipped a
// whole vblank, so the pipelined mode is worth its extra latency; a fence
// that keeps making it means the immediate drain is safe again. This holds
// in both modes (in the deferred mode the GPU runs during the drain wait).
static bool sDrmPacerLocked = true;
void drmSetPacerLocked(bool locked) { sDrmPacerLocked = locked; }
static void drmDeferDrainUpdate(int64_t enterUs, int64_t doneUs) {
    // While the pacer has handed the emulator back to its own timer (heavy
    // scene) the pipelined mode is always right: engage it and never leave
    // it on the phase-dependent "would have made it" test, which flips the
    // mode and costs a second at half rate each time.
    if (!sDrmPacerLocked) {
        sDrmGpuFastFrames = 0;
        if (!sDrmDeferActive) { sDrmDeferActive = true; ALOGW("NanoMenu DRM AFBC: deferred drain ON (pacer bypassed)"); }
        return;
    }
    if (doneUs <= 0 || enterUs <= 0 || sDrmLastVblankUs <= 0) return;
    const int64_t period = sDrmVblankPeriodUs > 0 ? sDrmVblankPeriodUs : 16667;
    const int64_t marginUs = property_get_int32("sys.gammaos.drastic_nano.defer_drain_margin_us", 800);
    int64_t next = sDrmLastVblankUs;
    while (next <= enterUs) next += period;
    while (next - period > enterUs) next -= period;
    const bool slow = doneUs > next - marginUs;
    if (slow) { sDrmGpuSlowFrames++; sDrmGpuFastFrames = 0; }
    else { sDrmGpuFastFrames++; sDrmGpuSlowFrames = 0; }
    const int64_t gpuLatUs = doneUs - enterUs;
    if (!sDrmDeferActive && sDrmGpuSlowFrames >= 2) {
        sDrmDeferActive = true;
        ALOGW("NanoMenu DRM AFBC: deferred drain ON (GPU fence %lld us after commit)", (long long)gpuLatUs);
    } else if (sDrmDeferActive && sDrmGpuFastFrames >= property_get_int32("sys.gammaos.drastic_nano.defer_drain_fast_frames", 120)) {
        sDrmDeferActive = false;
        ALOGW("NanoMenu DRM AFBC: deferred drain OFF (GPU fence %lld us after commit)", (long long)gpuLatUs);
    }
}
bool drmDeferDrainActive() { return drmDeferDrainOn(); }
static uint64_t sDrmCommitSeq = 0; /* per-iteration commit tag, latency probe */
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

EGLConfig getEglConfig(const EGLDisplay& display, bool wantAlpha) {
    EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, wantAlpha ? 8 : 0, EGL_DEPTH_SIZE, 0, EGL_NONE
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

// Atomic ALLOW_MODESET fallback for drivers whose legacy SETCRTC returns
// EINVAL (Qualcomm SDE with cont_splash). Shared by the boot splash
// (drmTryAddDisplay) and the wake-time recommit (drmResumeRecommit) so the
// two paths cannot drift. Do NOT disable the CRTC first - that tears down
// the DSI backlight controller permanently on SDE.
// GPU completion time (CLOCK_MONOTONIC us) of the last AFBC commit's render,
// from the sync file's signal timestamp. 0 if unavailable or not yet signaled.
struct DrmSyncFenceInfo { char obj_name[32]; char driver_name[32]; int32_t status; uint32_t flags; uint64_t timestamp_ns; };
struct DrmSyncFileInfo { char name[32]; int32_t status; uint32_t flags; uint32_t num_fences; uint32_t pad; uint64_t sync_fence_info; };
#define DRM_SYNC_IOC_FILE_INFO _IOWR('>', 4, struct DrmSyncFileInfo)
int64_t drmLastGpuDoneUs() {
    return drmFenceDoneUs(drmDeferDrainOn() ? sDrmPrevFenceFd : sDrmLastFenceFd);
}
static int64_t drmFenceDoneUs(int probeFd) {
    if (probeFd < 0) return 0;
    DrmSyncFenceInfo fi[4] = {};
    DrmSyncFileInfo info = {};
    info.num_fences = 4;
    info.sync_fence_info = (uint64_t)(uintptr_t)fi;
    if (ioctl(probeFd, DRM_SYNC_IOC_FILE_INFO, &info) != 0) return 0;
    if (info.status <= 0) return 0;  // not signaled yet
    uint64_t latest = 0;
    for (uint32_t i = 0; i < info.num_fences && i < 4; i++)
        if (fi[i].timestamp_ns > latest) latest = fi[i].timestamp_ns;
    return latest ? (int64_t)(latest / 1000ULL) : 0;
}

// Refresh rate of the first connected connector's preferred mode, from the
// mode timings (clock / (htotal * vtotal)), before the DRM path is set up.
// Used to lock drastic's frame pacing and audio rate to the panel. 0 if
// nothing is connected or the device cannot be opened.
double drmProbePrimaryRefreshHz() {
    int fd = open("/dev/dri/card0", O_RDWR | O_CLOEXEC);
    if (fd < 0) return 0.0;
    double hz = 0.0;
    struct drm_mode_card_res res = {};
    if (ioctl(fd, DRM_IOCTL_MODE_GETRESOURCES, &res) == 0 && res.count_connectors) {
        uint32_t conns[16] = {};
        struct drm_mode_card_res res2 = {};
        res2.count_connectors = res.count_connectors < 16 ? res.count_connectors : 16;
        res2.connector_id_ptr = (uint64_t)(uintptr_t)conns;
        if (ioctl(fd, DRM_IOCTL_MODE_GETRESOURCES, &res2) == 0) {
            for (uint32_t i = 0; i < res2.count_connectors && hz == 0.0; i++) {
                struct drm_mode_get_connector gc = {};
                gc.connector_id = conns[i];
                if (ioctl(fd, DRM_IOCTL_MODE_GETCONNECTOR, &gc) != 0) continue;
                if (gc.connection != 1 || gc.count_modes == 0) continue;
                struct drm_mode_modeinfo modes[32] = {};
                struct drm_mode_get_connector gc2 = {};
                gc2.connector_id = conns[i];
                gc2.count_modes = gc.count_modes < 32 ? gc.count_modes : 32;
                gc2.modes_ptr = (uint64_t)(uintptr_t)modes;
                if (ioctl(fd, DRM_IOCTL_MODE_GETCONNECTOR, &gc2) != 0) continue;
                const struct drm_mode_modeinfo& m = modes[0];
                if (m.htotal && m.vtotal && m.clock)
                    hz = (double)m.clock * 1000.0 / ((double)m.htotal * (double)m.vtotal);
            }
        }
    }
    close(fd);
    return hz;
}

static int drmAtomicModesetFallback(int fd, uint32_t crtcId, uint32_t connId,
                                    const struct drm_mode_modeinfo& mode,
                                    uint32_t fbId, uint32_t w, uint32_t h) {
    struct drm_set_client_cap cap = {};
    cap.capability = DRM_CLIENT_CAP_UNIVERSAL_PLANES; cap.value = 1;
    ioctl(fd, DRM_IOCTL_SET_CLIENT_CAP, &cap);
    cap.capability = DRM_CLIENT_CAP_ATOMIC; cap.value = 1;
    ioctl(fd, DRM_IOCTL_SET_CLIENT_CAP, &cap);

    struct drm_mode_get_plane_res pr = {};
    ioctl(fd, DRM_IOCTL_MODE_GETPLANERESOURCES, &pr);
    uint32_t planeIds[8] = {};
    struct drm_mode_get_plane_res pr2 = {};
    pr2.count_planes = pr.count_planes < 8 ? pr.count_planes : 8;
    pr2.plane_id_ptr = (uint64_t)(uintptr_t)planeIds;
    ioctl(fd, DRM_IOCTL_MODE_GETPLANERESOURCES, &pr2);
    uint32_t planeId = pr2.count_planes > 0 ? planeIds[0] : 0;

    auto findProp = [&](uint32_t objId, uint32_t objType, const char* name) -> uint32_t {
        struct drm_mode_obj_get_properties p = {};
        p.obj_id = objId; p.obj_type = objType;
        ioctl(fd, DRM_IOCTL_MODE_OBJ_GETPROPERTIES, &p);
        uint32_t pids[64]; uint64_t pvals[64];
        struct drm_mode_obj_get_properties p2x = {};
        p2x.obj_id = objId; p2x.obj_type = objType;
        p2x.count_props = p.count_props < 64 ? p.count_props : 64;
        p2x.props_ptr = (uint64_t)(uintptr_t)pids;
        p2x.prop_values_ptr = (uint64_t)(uintptr_t)pvals;
        ioctl(fd, DRM_IOCTL_MODE_OBJ_GETPROPERTIES, &p2x);
        for (uint32_t i = 0; i < p2x.count_props; i++) {
            struct drm_mode_get_property gp = {};
            gp.prop_id = pids[i];
            ioctl(fd, DRM_IOCTL_MODE_GETPROPERTY, &gp);
            if (strcmp(gp.name, name) == 0) return pids[i];
        }
        return 0;
    };

    struct drm_mode_create_blob blob = {};
    blob.data = (uint64_t)(uintptr_t)&mode;
    blob.length = sizeof(mode);
    ioctl(fd, DRM_IOCTL_MODE_CREATEPROPBLOB, &blob);

    uint32_t objs[] = { crtcId, connId, planeId };
    uint32_t counts[] = { 2, 1, 10 };
    uint32_t aprops[] = {
        findProp(crtcId, DRM_MODE_OBJECT_CRTC, "ACTIVE"),
        findProp(crtcId, DRM_MODE_OBJECT_CRTC, "MODE_ID"),
        findProp(connId, DRM_MODE_OBJECT_CONNECTOR, "CRTC_ID"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "FB_ID"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "CRTC_ID"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "SRC_X"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "SRC_Y"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "SRC_W"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "SRC_H"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "CRTC_X"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "CRTC_Y"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "CRTC_W"),
        findProp(planeId, DRM_MODE_OBJECT_PLANE, "CRTC_H"),
    };
    uint64_t values[] = {
        1, blob.blob_id,
        crtcId,
        fbId, crtcId,
        0, 0, (uint64_t)w << 16, (uint64_t)h << 16,
        0, 0, w, h,
    };

    struct drm_mode_atomic atomic = {};
    atomic.flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
    atomic.count_objs = 3;
    atomic.objs_ptr = (uint64_t)(uintptr_t)objs;
    atomic.count_props_ptr = (uint64_t)(uintptr_t)counts;
    atomic.props_ptr = (uint64_t)(uintptr_t)aprops;
    atomic.prop_values_ptr = (uint64_t)(uintptr_t)values;

    return ioctl(fd, DRM_IOCTL_MODE_ATOMIC, &atomic);
}

// Try to bring up a single DRM CRTC with the given connector. Returns true
// if the CRTC was added to sDrmDisplays. Non-blocking: if the mode is not
// valid (display not ready), returns false immediately -- caller can retry
// later via drmRescanDisplays().
bool drmTryAddDisplay(int fd, uint32_t crtcId, uint32_t connId, const char* stage) {
    struct drm_mode_crtc crtc = {};
    crtc.crtc_id = crtcId;
    ioctl(fd, DRM_IOCTL_MODE_GETCRTC, &crtc);

    // If CRTC has no active mode (e.g. early boot before cont_splash is
    // read by the driver), try getting the preferred mode from the connector.
    if (!crtc.mode_valid && connId != 0) {
        struct drm_mode_get_connector conn = {};
        conn.connector_id = connId;
        if (ioctl(fd, DRM_IOCTL_MODE_GETCONNECTOR, &conn) == 0 &&
            conn.count_modes > 0 && conn.connection == 1 /* connected */) {
            std::vector<struct drm_mode_modeinfo> modes(conn.count_modes);
            struct drm_mode_get_connector conn2 = {};
            conn2.connector_id = connId;
            conn2.count_modes = conn.count_modes;
            conn2.modes_ptr = (uint64_t)(uintptr_t)modes.data();
            if (ioctl(fd, DRM_IOCTL_MODE_GETCONNECTOR, &conn2) == 0 &&
                conn2.count_modes > 0) {
                crtc.mode = modes[0]; // first mode is preferred
                crtc.mode_valid = 1;
                ALOGW("NanoMenu DRM %s: CRTC %u had no mode, using connector %u "
                      "preferred mode %ux%u", stage, crtcId, connId,
                      modes[0].hdisplay, modes[0].vdisplay);
            }
        }
    }

    if (!crtc.mode_valid) return false;

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
    int setcrtc_errno = errno;

    // If SETCRTC fails with EINVAL (Qualcomm SDE cont_splash), use
    // atomic modeset (shared helper, also used by the wake recommit).
    if (ret != 0 && setcrtc_errno == EINVAL) {
        ret = drmAtomicModesetFallback(fd, crtcId, connId, crtc.mode,
                                       buf0.fbId, w, h);
        setcrtc_errno = errno;
        if (ret == 0) {
            ALOGW("NanoMenu DRM %s: atomic modeset OK (legacy SETCRTC was EINVAL)",
                  stage);
        }
    }

    int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    ALOGW("NanoMenu DRM %s: crtc %u (%ux%u) conn %u fb %u → %s at T+%lldms",
          stage, crtcId, w, h, connId, buf0.fbId,
          ret == 0 ? "OK" : strerror(setcrtc_errno), now);

    {
        char buf[PROPERTY_VALUE_MAX];
        snprintf(buf, sizeof(buf), "crtc%u_%ux%u_fb%u_%s_e%d_T%lld",
                 crtcId, w, h, buf0.fbId,
                 ret == 0 ? "OK" : "FAIL", setcrtc_errno, now);
        property_set("sys.gammaos.nano.drm_setcrtc", buf);
    }

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

// Tear down everything drmEarlySplash() set up: RMFB the dumb buffer fb_ids,
// GEM_CLOSE the handles, munmap the mappings, DROP_MASTER, close the DRM fd,
// clear sDrmActive / sDrmDisplays / sDrmZeroCopy, and publish
// sys.gammaos.nano.drm_active=0. Called by readyToRun() to abandon the
// DRM-direct boot path when the headless EGL setup fails (observed on EX8
// Mali-G57 + MTK MT6789 where libEGL Mali only returns a usable EGL config
// after SurfaceFlinger has registered its binder service) so the SF
// window-surface fallback path can take over without leaking master.
void drmReleaseEarly() {
    if (sDrmFd >= 0) {
        for (const auto& d : sDrmDisplays) {
            for (int b = 0; b < 2; b++) {
                if (d.buffers[b].fbId) {
                    uint32_t fbId = d.buffers[b].fbId;
                    ioctl(sDrmFd, DRM_IOCTL_MODE_RMFB, &fbId);
                }
                if (d.buffers[b].mapped && d.buffers[b].size) {
                    munmap(d.buffers[b].mapped, d.buffers[b].size);
                }
                if (d.buffers[b].handle) {
                    struct drm_gem_close gc = {};
                    gc.handle = d.buffers[b].handle;
                    ioctl(sDrmFd, DRM_IOCTL_GEM_CLOSE, &gc);
                }
            }
        }
        ioctl(sDrmFd, DRM_IOCTL_DROP_MASTER, 0);
        close(sDrmFd);
        sDrmFd = -1;
    }
    sDrmDisplays.clear();
    sDrmActive = false;
    sDrmZeroCopy = false;
    sDrmRescanDeadlineNs = 0;
    property_set("sys.gammaos.nano.drm_active", "0");
    ALOGW("NanoMenu DRM: released master and torn down dumb buffers, "
          "falling back to SurfaceFlinger window-surface path");
}

void drmEarlySplash(int existingFd) {
    int64_t t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    int fd;
    if (existingFd >= 0) {
        fd = existingFd;
    } else {
        fd = open("/dev/dri/card0", O_RDWR | O_CLOEXEC);
        if (fd < 0) return;
        ioctl(fd, DRM_IOCTL_SET_MASTER, 0); // try, OK if fails
    }

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
    if (!sDrmActive && sDrmFd >= 0) {
        // No displays added - release DRM master so HWC can use it.
        ioctl(sDrmFd, DRM_IOCTL_DROP_MASTER, 0);
        close(sDrmFd);
        sDrmFd = -1;
        ALOGW("NanoMenu DRM splash: no displays, released DRM master");
    }
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
    // AFBC mode (rk356x + Low Latency): drop CPU_READ_OFTEN so Mali gralloc
    // allocates an AFBC-compressed buffer. On RK3568 the Cluster planes (which,
    // unlike the Smart planes, do NOT carry the per-VP output-pipeline offset
    // that desyncs the two DSI panels) can ONLY scan AFBC. The DRM import below
    // tags the fb with the matching AFBC modifier and the flip drives Cluster.
    // If any of that fails we fall back to linear+Smart (drmFbId stays 0).
    if (sDrmAfbcMode) {
        desc.usage = AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER |
                     AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE |
                     AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY;
    }
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

            // AFBC mode: the linear pitch/offset/modifier are all wrong for a
            // compressed buffer -- the kernel's fb size checks then reject
            // ADDFB2 (EINVAL). Query gralloc for the REAL modifier and plane
            // layout (byte stride + offset) so the import matches Mali's exact
            // AFBC allocation. This is what drm_hwcomposer does for SF.
            unsigned long long afbcMod = 0;
            if (sDrmAfbcMode) {
                using android::GraphicBufferMapper;
                using android::ui::PlaneLayout;
                auto& gm = GraphicBufferMapper::get();
                uint64_t realMod = 0;
                if (gm.getPixelFormatModifier(nh, &realMod) == android::OK &&
                    realMod != 0) {
                    afbcMod = (unsigned long long)realMod;
                }
                std::vector<PlaneLayout> layouts;
                if (gm.getPlaneLayouts(nh, &layouts) == android::OK &&
                    !layouts.empty()) {
                    if (layouts[0].strideInBytes > 0)
                        pitch = (uint32_t)layouts[0].strideInBytes;
                }
                // A prop override still wins, for A/B tuning if gralloc lies.
                unsigned long long ov = (unsigned long long)property_get_int64(
                        "sys.gammaos.drastic_nano.afbc_mod", 0);
                if (ov) afbcMod = ov;
                ALOGW("NanoMenu DRM AFBC: gralloc modifier=0x%llx pitch=%u (%s)",
                      afbcMod, pitch, label);
            }

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
                // AFBC mode: tag the fb with the real gralloc AFBC modifier so
                // the Cluster planes accept it and decode the exact layout.
                if (sDrmAfbcMode && afbcMod) {
                    cmd.flags = DRM_MODE_FB_MODIFIERS;
                    cmd.modifier[0] = afbcMod;
                }
                if (ioctl(sDrmFd, DRM_IOCTL_MODE_ADDFB2, &cmd) == 0
                    && cmd.fb_id != 0) {
                    target->drmFbId = cmd.fb_id;
                    target->drmGemHandle = ph.handle;
                    ALOGW("NanoMenu DRM PRIME: AHB(%s) imported as fb_id=%u "
                          "(gem=%u dmabuf_fd=%d pitch=%u mod=0x%llx)",
                          label, target->drmFbId, target->drmGemHandle,
                          dmabufFd, pitch, afbcMod);
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
static void drmMeasureVblankPhase(int fd);
static int drmCrtcIndex(int fd, uint32_t crtcId);

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

    // AFBC dual-screen sync mode: rk356x + Low Latency only. The RK3568 Cluster
    // planes (which, unlike Smart, don't carry the per-VP output offset that
    // desyncs the two DSI panels) can only scan AFBC buffers, so the ring must
    // be allocated AFBC. Decided at ring-alloc time from the persisted toggle;
    // any downstream failure falls back to the linear+Smart path.
    {
        char plat[PROPERTY_VALUE_MAX] = {};
        property_get("ro.board.platform", plat, "");
        bool isRk356x = (strcmp(plat, "rk356x") == 0);
        // drastic-nano only (sDrmAfbcClient): it renders both DS screens into the
        // one combined buffer. The nano home renders its two passes into separate
        // primary/secondary buffers and must stay on the linear + Smart plane path
        // (the combined buffer would leave its secondary pass unscanned and stretch
        // the primary over both panels). Low Latency itself is still the persisted
        // pref, read here because drastic-nano's prefs load after its DRM setup.
        bool ll = sDrmAfbcClient &&
                  property_get_bool("persist.gammaos.drastic_nano.low_latency", false);
        sDrmAfbcMode = isRk356x && ll && (sDrmDisplays.size() == 2);
        // RG DS: the panel on VOP2 video port 1 (DSI-2, the physical top
        // screen) is driven with its gate scan starting at the hinge, which
        // turns its image 180 degrees; the renderer compensates for that
        // CRTC. Keyed on the port index so it does not depend on CRTC ids.
        // Physical mount of the panel on VOP video port 1 (the second DSI, the
        // physical top screen on the RG DS and RG DS Plus). Its controller scans
        // like the bottom panel so both latch in step; SurfaceFlinger turns it by
        // persist.gsf.rot.1 as that display's physical orientation, and the
        // DRM-direct producers apply the same prop here. Only 180 is supported on
        // this path (a quarter turn would need the panel dimensions swapped).
        sDrmSecondaryRotDeg = drmPanelMountRotationDeg(1);
        if (sDrmSecondaryRotDeg != 0 && sDrmSecondaryRotDeg != 180)
            ALOGW("NanoMenu DRM: port 1 mount rotation %d not supported on the DRM path, ignoring", sDrmSecondaryRotDeg);
        sDrmVp1DisplayIdx = -1;
        for (size_t i = 0; i < sDrmDisplays.size(); i++)
            if (drmCrtcIndex(sDrmFd, sDrmDisplays[i].crtcId) == 1) sDrmVp1DisplayIdx = (int)i;
        sDrmSeamRotCrtc = 0;
        if (sDrmSecondaryRotDeg == 180 && sDrmVp1DisplayIdx >= 0)
            sDrmSeamRotCrtc = sDrmDisplays[sDrmVp1DisplayIdx].crtcId;
        // nano home only (drastic-nano keys its own passes on sDrmSeamRotCrtc): the
        // ring whose panel is the turned one renders into a scratch FBO and is
        // resolved 180 degrees into its scanout AHB (drmResolveTurnedTargets), so
        // no matrix, flip or glyph path changes. Attached after the rings exist.
        {
            const bool primIsVp1 = (sDrmVp1DisplayIdx == sDrmPrimaryIdx);
            const bool rot = (sDrmSecondaryRotDeg == 180 && sDrmVp1DisplayIdx >= 0 && sDrmDisplays.size() > 1);
            sDrmPrimaryTurned   = rot && primIsVp1  && !sDrmAfbcClient;
            sDrmSecondaryTurned = rot && !primIsVp1 && !sDrmAfbcClient;
            if (sDrmSecondaryRotDeg)
                ALOGW("NanoMenu DRM: port 1 mount rotation=%d vp1 display idx %d primary idx %d -> seamRotCrtc %u, turned primary=%d secondary=%d",
                      sDrmSecondaryRotDeg, sDrmVp1DisplayIdx, sDrmPrimaryIdx, sDrmSeamRotCrtc,
                      sDrmPrimaryTurned ? 1 : 0, sDrmSecondaryTurned ? 1 : 0);
        }
        if (property_get_bool("sys.gammaos.drastic_nano.phase_probe", false))
            drmMeasureVblankPhase(sDrmFd);
        ALOGW("NanoMenu DRM: AFBC dual-screen mode %s (platform=%s low_latency=%d displays=%zu)",
              sDrmAfbcMode ? "ON" : "off", plat, ll, sDrmDisplays.size());
    }

    // AHB is always at PANEL NATIVE dimensions of the selected primary display.
    // When the install orientation is non-zero, GL rotation (via uRotation mat2
    // in vertex shaders) maps logical coords to the panel-native AHB -- so the
    // blit is always a fast straight copy with no per-pixel rotation.
    const uint32_t primaryW = sDrmDisplays[sDrmPrimaryIdx].w;
    const uint32_t primaryH = sDrmDisplays[sDrmPrimaryIdx].h;

    // AFBC dual-DSI sync: the ONLY configuration that displays both panels
    // synced at zero added latency is a SINGLE physical buffer scanned by both
    // Cluster planes (proven on device: two distinct buffers desync by ~1 frame
    // from a per-VP output-pipeline offset, one shared buffer does not). So the
    // primary ring buffer holds BOTH DS screens stacked vertically (top screen
    // in rows [0,H), bottom screen in rows [H,2H)); each Cluster plane crops its
    // half via SRC_Y. sDrmAfbcHalfH is one panel's height; the buffer is 2x tall.
    const uint32_t ringH = sDrmAfbcMode ? primaryH * 2 : primaryH;
    if (sDrmAfbcMode) sDrmAfbcHalfH = primaryH;

    // Allocate all AHB_RING_DEPTH primary slots. Slot 0 is the "classic"
    // single-buffered target used by XMB and the default QR path;
    // slots 1..2 only get used when the QR loop opts into the triple-
    // buffer ring path (Step 4). We pay the +30 MB up-front so the ring
    // can be enabled/disabled at runtime via a prop without reallocating.
    for (int i = 0; i < AHB_RING_DEPTH; i++) {
        char label[32];
        snprintf(label, sizeof(label), "primary[%d]", i);
        if (!drmAllocAhbTarget(eglDpy, primaryW, ringH,
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
    // Even in AFBC mode the secondary ring is still allocated: the two panels
    // scan the combined PRIMARY buffer, but main.cpp keys `hasDualDisplay` off
    // sAhbRingSecondary[0] and several paths (OSK, shot) bind secTgt, so keeping
    // it avoids null-FBO binds. It is simply not scanned out in AFBC mode.
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
    // Turned panel: redirect every slot of that ring into the scratch FBO now that
    // the rings exist (the resolve copies it into the real AHB before each flip).
    for (int i = 0; i < AHB_RING_DEPTH; i++) {
        if (sDrmPrimaryTurned)   drmTurnAttach(&sAhbRingPrimary[i],   &sTurnPrimary,   "primary");
        if (sDrmSecondaryTurned) drmTurnAttach(&sAhbRingSecondary[i], &sTurnSecondary, "secondary");
    }

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

// Reproduce the logical->panel "install" matrix that NanoMenu::initShaders()
// computes from the DRM orientation + flip props, for callers that draw
// DRM-direct but never construct a NanoMenu (the drastic single-panel
// composite). Mirrors the rotation cases and flip composition in
// NanoMenuShaders.cpp exactly: the rotated cases bake the GL y-up correction
// into the matrix, the 0-degree case applies the same PRIME Y-flip that
// drmSetupZeroCopy does, and the user flips negate row 0 (H) / row 1 (V).
// Keep this in sync with initShaders() if those props' handling changes.
void drmBuildInstallMatrix(float out[4], int degrees) {
    // degrees < 0 means "use the panel install orientation". A caller can pass
    // an effective rotation (install + a user Display Rotation) so the whole
    // single-panel output can be turned for portrait play.
    const int deg = (degrees < 0) ? sDrmRotationDeg : (((degrees % 360) + 360) % 360);
    switch (deg) {
    case 90:  out[0] =  0.0f; out[1] = -1.0f; out[2] =  1.0f; out[3] =  0.0f; break;
    case 180: out[0] = -1.0f; out[1] =  0.0f; out[2] =  0.0f; out[3] = -1.0f; break;
    case 270: out[0] =  0.0f; out[1] =  1.0f; out[2] = -1.0f; out[3] =  0.0f; break;
    default:  out[0] =  1.0f; out[1] =  0.0f; out[2] =  0.0f; out[3] =  1.0f; break;
    }
    // The PRIME scanout Y-flip is a property of the PHYSICAL panel install, not
    // the logical content rotation. Apply it only on a true 0-degree-install
    // panel. A rotated panel (install 90/180/270) turned to an effective 0 by a
    // user Display Rotation must NOT get it, or that one orientation comes out
    // mirrored/upside-down while every other rotation is correct.
    if (deg == 0 && sDrmRotationDeg == 0) { out[1] = -out[1]; out[3] = -out[3]; }
    if (sDrmFlipH) { out[0] = -out[0]; out[2] = -out[2]; }
    if (sDrmFlipV) { out[1] = -out[1]; out[3] = -out[3]; }
}

// GammaOS hardware rotation key (force-SF home): nano's own SurfaceFlinger layer is NOT turned
// by the WMS display rotation (WMS rotates app windows, not nano's raw layer), and on a square
// panel the logical size never changes so overlayUpdateSurfaceSize's re-land path doesn't run.
// So rotate nano's OWN rendering to follow the logical display rotation. touchMapRaw already
// un-rotates touch by the same mOverlayRotation, so render + touch stay in lockstep. rot is the
// ui::Rotation from the display state: 0..3 = 0/90/180/270. Pure rotation - no PRIME Y-flip (the
// SF window path is not the AHB scanout path). Only meaningful when nano is NOT DRM-direct.
void nanoSetOverlayRenderRotation(int rot) {
    if (sDrmActive) return;   // DRM-direct home bakes rotation into the install matrix already
    switch (rot & 3) {
    case 1:  sDrmRotMat[0] = 0.0f; sDrmRotMat[1] = -1.0f; sDrmRotMat[2] = 1.0f;  sDrmRotMat[3] = 0.0f;  break; // 90
    case 2:  sDrmRotMat[0] = -1.0f; sDrmRotMat[1] = 0.0f; sDrmRotMat[2] = 0.0f;  sDrmRotMat[3] = -1.0f; break; // 180
    case 3:  sDrmRotMat[0] = 0.0f; sDrmRotMat[1] = 1.0f;  sDrmRotMat[2] = -1.0f; sDrmRotMat[3] = 0.0f;  break; // 270
    default: sDrmRotMat[0] = 1.0f; sDrmRotMat[1] = 0.0f;  sDrmRotMat[2] = 0.0f;  sDrmRotMat[3] = 1.0f;  break; // 0
    }
    sDrmGlRotation = (rot & 3) != 0;
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
    //
    // blitRotation is constant for the whole blit, so resolve it ONCE instead of
    // re-running the switch on each of the ~300k pixels. For the orientations
    // whose source row depends only on dy (180, and the default that a
    // non-90/180/270 value would take), the row address and its bounds test
    // hoist out of the inner loop too, and the destination span is clamped up
    // front rather than tested per pixel -- an out-of-range sx used to `continue`
    // and leave that destination pixel untouched, which is exactly what not
    // visiting it does. Pixel mapping, the wrap-around behaviour of the unsigned
    // arithmetic and the leave-untouched semantics are all unchanged.
    if (blitRotation == 90 || blitRotation == 270) {
        // sy varies with dx here, so the source row genuinely has to be
        // recomputed per pixel; only the switch comes out of the loop.
        const bool rot90 = (blitRotation == 90);
        for (uint32_t dy = 0; dy < dstH; dy++) {
            uint32_t* dstRow = (uint32_t*)(dst + dy * dstPitch);
            for (uint32_t dx = 0; dx < dstW; dx++) {
                const uint32_t sx = rot90 ? dy : (srcW - 1 - dy);
                const uint32_t sy = rot90 ? dx : (srcH - 1 - dx);
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
        return;
    }
    const uint32_t rotCopyW = std::min(srcW, dstW);
    for (uint32_t dy = 0; dy < dstH; dy++) {
        const uint32_t sy = (blitRotation == 180) ? dy : (srcH - 1 - dy);
        if (sy >= srcH) continue;
        uint32_t* dstRow = (uint32_t*)(dst + dy * dstPitch);
        const uint32_t* srcRow = (const uint32_t*)((const uint8_t*)ahbPtr
                                 + sy * ahbStride);
        if (blitRotation == 180) {
            for (uint32_t dx = 0; dx < rotCopyW; dx++) {
                uint32_t rgba = srcRow[srcW - 1 - dx];
                dstRow[dx] = 0xFF000000u |
                             ((rgba >> 16) & 0xFFu) |
                             (rgba & 0xFF00u) |
                             ((rgba & 0xFFu) << 16);
            }
        } else {
            // sx == dx: a straight row copy, so this is exactly what the NEON
            // helper above does -- reuse it rather than re-walking pixel by pixel.
            blitRowRgbaToXrgbNeon(srcRow, dstRow, rotCopyW);
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
// Per-display consecutive-EBUSY streak for the dead-display self-heal in
// drmFlipRingSlot. Reset on every successful flip; a long unbroken streak
// means the CRTC lost its primary plane (an external commit stomped it)
// and needs a SETCRTC to come back.
static uint32_t sEbusyStreak[8] = {0};
static int64_t sEbusyRecoverMs[8] = {0};

// --- Low-latency atomic dual-CRTC flip -------------------------------------
// The kernel rockchip,sync-vp-mask phase-locks VP0/VP1 (verified: the two
// panels' scanline counters track to delta 0), so the two DSI panels share a
// vblank instant. The legacy path page-flips each CRTC separately (secondary
// then primary), which lets the bottom panel latch one vblank later than the
// top -- a one-frame CONTENT lag even though scanout is aligned. This commits
// BOTH planes' FB_ID in a single atomic NONBLOCK ioctl so both latch the same
// vblank: synced content, no added latency. Gated to the low-latency dual-DSI
// PRIME path; any failure falls back to the legacy per-CRTC flips.
static uint32_t drmFindPropId(int fd, uint32_t objId, uint32_t objType,
                              const char* name) {
    struct drm_mode_obj_get_properties p = {};
    p.obj_id = objId; p.obj_type = objType;
    if (ioctl(fd, DRM_IOCTL_MODE_OBJ_GETPROPERTIES, &p) != 0) return 0;
    uint32_t pids[128]; uint64_t pvals[128];
    struct drm_mode_obj_get_properties p2 = {};
    p2.obj_id = objId; p2.obj_type = objType;
    p2.count_props = p.count_props < 128 ? p.count_props : 128;
    p2.props_ptr = (uint64_t)(uintptr_t)pids;
    p2.prop_values_ptr = (uint64_t)(uintptr_t)pvals;
    if (ioctl(fd, DRM_IOCTL_MODE_OBJ_GETPROPERTIES, &p2) != 0) return 0;
    for (uint32_t i = 0; i < p2.count_props; i++) {
        struct drm_mode_get_property gp = {};
        gp.prop_id = pids[i];
        if (ioctl(fd, DRM_IOCTL_MODE_GETPROPERTY, &gp) != 0) continue;
        if (strcmp(gp.name, name) == 0) return pids[i];
    }
    return 0;
}

// Value of an enum property's entry by name (0 if absent). Used for the
// Cluster plane's "pixel blend mode" = None, so the panel never blends the
// scanout buffer's alpha channel against the background.
static bool drmFindEnumValue(int fd, uint32_t propId, const char* entry,
                             uint64_t* out) {
    struct drm_mode_get_property gp = {};
    gp.prop_id = propId;
    if (ioctl(fd, DRM_IOCTL_MODE_GETPROPERTY, &gp) != 0) return false;
    struct drm_mode_property_enum ens[32];
    struct drm_mode_get_property gp2 = {};
    gp2.prop_id = propId;
    gp2.count_enum_blobs = gp.count_enum_blobs < 32 ? gp.count_enum_blobs : 32;
    gp2.enum_blob_ptr = (uint64_t)(uintptr_t)ens;
    if (ioctl(fd, DRM_IOCTL_MODE_GETPROPERTY, &gp2) != 0) return false;
    for (uint32_t i = 0; i < gp2.count_enum_blobs; i++)
        if (strcmp(ens[i].name, entry) == 0) { *out = ens[i].value; return true; }
    return false;
}

// Index of crtcId within the DRM crtc resource list -- possible_crtcs is a
// bitmask over that ordering. Returns -1 if not found.
static int drmCrtcIndex(int fd, uint32_t crtcId) {
    struct drm_mode_card_res res = {};
    if (ioctl(fd, DRM_IOCTL_MODE_GETRESOURCES, &res) != 0) return -1;
    uint32_t crtcs[16] = {};
    struct drm_mode_card_res res2 = {};
    res2.count_crtcs = res.count_crtcs < 16 ? res.count_crtcs : 16;
    res2.crtc_id_ptr = (uint64_t)(uintptr_t)crtcs;
    if (ioctl(fd, DRM_IOCTL_MODE_GETRESOURCES, &res2) != 0) return -1;
    for (uint32_t i = 0; i < res2.count_crtcs; i++)
        if (crtcs[i] == crtcId) return (int)i;
    return -1;
}

// Measure the raw vblank phase between the two CRTCs, independent of any
// atomic commit. DRM_IOCTL_WAIT_VBLANK with RELATIVE 1 blocks until that
// pipe's next vblank and reports its timestamp, so waiting on pipe A and then
// pipe B gives (tB - tA) in (0, period]; modulo the refresh period that is the
// hardware phase offset between the two DSI video ports. A phase near 0 means
// the panels tick together and the one-frame content skew is a latch-deadline
// problem; a phase in the middle of the frame means the panels genuinely run
// out of step and one of them must be re-timed to fix the skew at its source.
static void drmMeasureVblankPhase(int fd) {
    if (sDrmDisplays.size() < 2) return;
    int idxA = drmCrtcIndex(fd, sDrmDisplays[0].crtcId);
    int idxB = drmCrtcIndex(fd, sDrmDisplays[1].crtcId);
    if (idxA < 0 || idxB < 0) return;
    // Pipe number goes in the high bits of the request type.
    auto pipeFlag = [](int idx) -> uint32_t {
        return ((uint32_t)idx << 1) & _DRM_VBLANK_HIGH_CRTC_MASK;
    };
    for (int s = 0; s < 8; s++) {
        union drm_wait_vblank va = {}, vb = {};
        va.request.type = (drm_vblank_seq_type)(_DRM_VBLANK_RELATIVE |
                                                pipeFlag(idxA));
        va.request.sequence = 1;
        if (ioctl(fd, DRM_IOCTL_WAIT_VBLANK, &va) != 0) return;
        vb.request.type = (drm_vblank_seq_type)(_DRM_VBLANK_RELATIVE |
                                                pipeFlag(idxB));
        vb.request.sequence = 1;
        if (ioctl(fd, DRM_IOCTL_WAIT_VBLANK, &vb) != 0) return;
        int64_t ta = (int64_t)va.reply.tval_sec * 1000000LL + va.reply.tval_usec;
        int64_t tb = (int64_t)vb.reply.tval_sec * 1000000LL + vb.reply.tval_usec;
        ALOGW("NanoMenu vblank raw phase: crtc%u(pipe%d)=%lld "
              "crtc%u(pipe%d)=%lld phase=%lldus",
              sDrmDisplays[0].crtcId, idxA, (long long)ta,
              sDrmDisplays[1].crtcId, idxB, (long long)tb,
              (long long)(tb - ta));
    }
}

// True if the plane advertises at least one ARM-vendor (AFBC) format modifier
// via its IN_FORMATS blob. On RK3568 only the Cluster planes do; Smart/Esmart
// expose LINEAR only. This is how we tell a Cluster plane apart generically.
static bool drmPlaneHasAfbc(int fd, uint32_t planeId) {
    uint32_t inFmtProp = drmFindPropId(fd, planeId, DRM_MODE_OBJECT_PLANE,
                                       "IN_FORMATS");
    if (!inFmtProp) return false;
    // Read the plane's current IN_FORMATS value (a blob id).
    struct drm_mode_obj_get_properties p = {};
    p.obj_id = planeId; p.obj_type = DRM_MODE_OBJECT_PLANE;
    if (ioctl(fd, DRM_IOCTL_MODE_OBJ_GETPROPERTIES, &p) != 0) return false;
    uint32_t pids[128]; uint64_t pvals[128];
    struct drm_mode_obj_get_properties p2 = {};
    p2.obj_id = planeId; p2.obj_type = DRM_MODE_OBJECT_PLANE;
    p2.count_props = p.count_props < 128 ? p.count_props : 128;
    p2.props_ptr = (uint64_t)(uintptr_t)pids;
    p2.prop_values_ptr = (uint64_t)(uintptr_t)pvals;
    if (ioctl(fd, DRM_IOCTL_MODE_OBJ_GETPROPERTIES, &p2) != 0) return false;
    uint32_t blobId = 0;
    for (uint32_t i = 0; i < p2.count_props; i++)
        if (pids[i] == inFmtProp) { blobId = (uint32_t)pvals[i]; break; }
    if (!blobId) return false;
    // First GETPROPBLOB to size, then read the payload.
    struct drm_mode_get_blob gb = {};
    gb.blob_id = blobId;
    if (ioctl(fd, DRM_IOCTL_MODE_GETPROPBLOB, &gb) != 0 || gb.length == 0)
        return false;
    std::vector<uint8_t> buf(gb.length);
    gb.data = (uint64_t)(uintptr_t)buf.data();
    if (ioctl(fd, DRM_IOCTL_MODE_GETPROPBLOB, &gb) != 0) return false;
    if (gb.length < sizeof(struct drm_format_modifier_blob)) return false;
    const auto* hdr = (const struct drm_format_modifier_blob*)buf.data();
    if ((uint64_t)hdr->modifiers_offset +
        (uint64_t)hdr->count_modifiers * sizeof(struct drm_format_modifier)
        > gb.length) return false;
    const auto* mods = (const struct drm_format_modifier*)
                       (buf.data() + hdr->modifiers_offset);
    for (uint32_t i = 0; i < hdr->count_modifiers; i++) {
        uint64_t vendor = (mods[i].modifier >> 56) & 0xff;
        if (vendor == DRM_FORMAT_MOD_VENDOR_ARM) return true;
    }
    return false;
}

// Discover the AFBC-capable Cluster plane for d.crtcId and cache its full set of
// atomic property ids (FB_ID + CRTC_ID + CRTC_{X,Y,W,H} + SRC_{X,Y,W,H}), plus
// the Smart primary's CRTC_ID prop so the first commit can disable it. The
// caller ensures the Smart primary (planeId/fbIdProp) is already resolved. A
// static claimed-set keeps two displays from grabbing the same Cluster plane;
// displays are processed in ascending crtc order so Cluster0->VP0, Cluster1->VP1
// (matching SF's assignment). Returns false (and the caller keeps the linear
// Smart path) if no suitable plane is found.
static bool drmEnsureClusterPlane(int fd, DrmDisplay& d) {
    if (d.clPlaneId && d.clFbIdProp && d.clCrtcIdProp) return true;
    static uint32_t sClaimed[8] = {0};
    int crtcIdx = drmCrtcIndex(fd, d.crtcId);
    if (crtcIdx < 0) return false;
    uint32_t crtcBit = 1u << crtcIdx;

    struct drm_mode_get_plane_res pr = {};
    if (ioctl(fd, DRM_IOCTL_MODE_GETPLANERESOURCES, &pr) != 0) return false;
    uint32_t ids[32] = {};
    struct drm_mode_get_plane_res pr2 = {};
    pr2.count_planes = pr.count_planes < 32 ? pr.count_planes : 32;
    pr2.plane_id_ptr = (uint64_t)(uintptr_t)ids;
    if (ioctl(fd, DRM_IOCTL_MODE_GETPLANERESOURCES, &pr2) != 0) return false;

    uint32_t chosen = 0;
    for (uint32_t i = 0; i < pr2.count_planes; i++) {
        struct drm_mode_get_plane gp = {};
        gp.plane_id = ids[i];
        if (ioctl(fd, DRM_IOCTL_MODE_GETPLANE, &gp) != 0) continue;
        if (!(gp.possible_crtcs & crtcBit)) continue;
        bool claimed = false;
        for (int c = 0; c < 8 && sClaimed[c]; c++)
            if (sClaimed[c] == ids[i]) { claimed = true; break; }
        if (claimed) continue;
        if (!drmPlaneHasAfbc(fd, ids[i])) continue;
        chosen = ids[i];  // lowest-id AFBC plane => Cluster0 before Cluster1
        break;
    }
    if (!chosen) return false;

    uint32_t fbP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "FB_ID");
    uint32_t crP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "CRTC_ID");
    uint32_t cxP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "CRTC_X");
    uint32_t cyP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "CRTC_Y");
    uint32_t cwP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "CRTC_W");
    uint32_t chP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "CRTC_H");
    uint32_t sxP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "SRC_X");
    uint32_t syP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "SRC_Y");
    uint32_t swP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "SRC_W");
    uint32_t shP  = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "SRC_H");
    if (!fbP || !crP || !cxP || !cyP || !cwP || !chP || !sxP || !syP ||
        !swP || !shP)
        return false;
    // Smart primary CRTC_ID prop, so the first commit can detach it (a VP can
    // scan only one primary; leaving Smart bound would double-composite).
    uint32_t smartCr = d.planeId
        ? drmFindPropId(fd, d.planeId, DRM_MODE_OBJECT_PLANE, "CRTC_ID") : 0;

    d.clPlaneId = chosen;
    d.clFbIdProp = fbP; d.clCrtcIdProp = crP;
    d.clCrtcXProp = cxP; d.clCrtcYProp = cyP; d.clCrtcWProp = cwP; d.clCrtcHProp = chP;
    d.clSrcXProp = sxP; d.clSrcYProp = syP; d.clSrcWProp = swP; d.clSrcHProp = shP;
    d.smartCrtcIdProp = smartCr;
    d.clInFenceProp = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "IN_FENCE_FD");
    d.clBlendProp = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "pixel blend mode");
    d.clAlphaProp = drmFindPropId(fd, chosen, DRM_MODE_OBJECT_PLANE, "alpha");
    d.clBlendNone = 0;
    if (d.clBlendProp && !drmFindEnumValue(fd, d.clBlendProp, "None", &d.clBlendNone))
        d.clBlendProp = 0;
    d.clConfigured = false;
    for (int c = 0; c < 8; c++) if (!sClaimed[c]) { sClaimed[c] = chosen; break; }
    ALOGW("NanoMenu DRM AFBC: crtc=%u -> Cluster plane=%u (smart=%u smartCrtcProp=%u)",
          d.crtcId, chosen, d.planeId, smartCr);
    return true;
}

// Find the plane currently scanning out d.crtcId + its FB_ID prop; cache them.
static bool drmEnsurePlane(int fd, DrmDisplay& d) {
    if (d.planeId && d.fbIdProp) return true;
    // The atomic ioctl and the primary-plane listing both require these client
    // caps on this fd. Legacy page-flip/SETCRTC keep working with them set, so
    // it is safe to enable once for the whole session.
    static bool capsSet = false;
    if (!capsSet) {
        struct drm_set_client_cap cap = {};
        cap.capability = DRM_CLIENT_CAP_UNIVERSAL_PLANES; cap.value = 1;
        ioctl(fd, DRM_IOCTL_SET_CLIENT_CAP, &cap);
        cap.capability = DRM_CLIENT_CAP_ATOMIC; cap.value = 1;
        ioctl(fd, DRM_IOCTL_SET_CLIENT_CAP, &cap);
        capsSet = true;
    }
    struct drm_mode_get_plane_res pr = {};
    if (ioctl(fd, DRM_IOCTL_MODE_GETPLANERESOURCES, &pr) != 0) return false;
    uint32_t ids[16] = {};
    struct drm_mode_get_plane_res pr2 = {};
    pr2.count_planes = pr.count_planes < 16 ? pr.count_planes : 16;
    pr2.plane_id_ptr = (uint64_t)(uintptr_t)ids;
    if (ioctl(fd, DRM_IOCTL_MODE_GETPLANERESOURCES, &pr2) != 0) return false;
    for (uint32_t i = 0; i < pr2.count_planes; i++) {
        struct drm_mode_get_plane gp = {};
        gp.plane_id = ids[i];
        if (ioctl(fd, DRM_IOCTL_MODE_GETPLANE, &gp) != 0) continue;
        if (gp.crtc_id == d.crtcId) {
            uint32_t fb = drmFindPropId(fd, ids[i], DRM_MODE_OBJECT_PLANE, "FB_ID");
            if (fb) { d.planeId = ids[i]; d.fbIdProp = fb; return true; }
        }
    }
    return false;
}

// One atomic commit flipping every display's plane to fbs[i]. 0 on success,
// otherwise errno is set (EBUSY = prior flip still pending).
static int drmAtomicDualFlip(int fd, const uint32_t* fbs) {
    const size_t n = sDrmDisplays.size();
    if (n < 2 || n > 4) return -1;
    uint32_t objs[4]; uint32_t counts[4]; uint32_t props[4]; uint64_t vals[4];
    for (size_t i = 0; i < n; i++) {
        if (!fbs[i] || !drmEnsurePlane(fd, sDrmDisplays[i])) return -1;
        objs[i] = sDrmDisplays[i].planeId;
        counts[i] = 1;
        props[i] = sDrmDisplays[i].fbIdProp;
        vals[i] = fbs[i];
    }
    struct drm_mode_atomic atomic = {};
    // Non-blocking commit with a flip-complete event, then wait on the event via
    // drmDrainPageFlipEvents -- the same model SurfaceFlinger uses, which IS
    // synced on this VOP2. One atomic commit latches both planes on the shared
    // (kernel phase-locked) vblank; the event drain paces to that vblank so the
    // next commit never races (no EBUSY, no tearing).
    atomic.flags = DRM_MODE_PAGE_FLIP_EVENT | DRM_MODE_ATOMIC_NONBLOCK;
    atomic.count_objs = n;
    atomic.objs_ptr = (uint64_t)(uintptr_t)objs;
    atomic.count_props_ptr = (uint64_t)(uintptr_t)counts;
    atomic.props_ptr = (uint64_t)(uintptr_t)props;
    atomic.prop_values_ptr = (uint64_t)(uintptr_t)vals;
    atomic.user_data = sDrmCommitSeq;
    int ret = ioctl(fd, DRM_IOCTL_MODE_ATOMIC, &atomic);
    if (ret == 0) {
        for (size_t i = 0; i < n; i++) {
            int slot = drmCrtcSlot(sDrmDisplays[i].crtcId);
            if (slot >= 0) sCrtcPending[slot]++;
            sPendingFlipEvents++;
        }
        drmDrainPageFlipEvents();
    }
    return ret;
}

// AFBC + Cluster variant of the atomic dual flip (rk356x low-latency sync fix).
// Drives the two AFBC-capable Cluster planes instead of the Smart primaries.
// The FIRST successful commit is a full modeset: it binds each Cluster plane to
// its VP with the correct geometry AND detaches the Smart primary (a VP scans
// one primary; leaving Smart on would double-composite). Every commit after
// that is a plain NONBLOCK|PAGE_FLIP_EVENT FB_ID flip, event-drained exactly
// like drmAtomicDualFlip so pacing/latency are identical. Returns 0 on success;
// nonzero (errno set) makes the caller fall back to the legacy path.
// Flip thread (deferred-drain mode): the atomic commit is issued as a
// BLOCKING call from a SCHED_FIFO helper, so the in-fence wait and the
// programming of both CRTCs run in real-time context. With a nonblocking
// commit that work runs on the kernel's normal-priority commit worker, which
// drastic's real-time emulator threads preempt on heavy scenes: flips landed
// a vblank late with the fence long signalled, and the two panels latched
// one vblank apart 6% of the time. The presenter hands the commit over and
// keeps rendering; before its next commit it waits for the helper to be idle
// (the previous flip has landed) and then collects the flip events.
struct FlipJob {
    uint32_t objs[4]; uint32_t counts[4]; uint32_t props[64]; uint64_t vals[64];
    size_t no; uint64_t userData; int fenceFd;
};
static std::mutex sFlipMu;
static std::condition_variable sFlipCv;
static bool sFlipBusy = false, sFlipThreadStarted = false;
static FlipJob sFlipJob;
static int sFlipResult = 0, sFlipErrno = 0;
static int sFlipGuardHolds = 0;
int drmFlipGuardHolds() { return sFlipGuardHolds; }
static void drmFlipThreadMain() {
    sched_param sp = {}; sp.sched_priority = 80;
    if (sched_setscheduler(0, SCHED_FIFO, &sp) != 0)
        ALOGW("NanoMenu DRM: flip thread SCHED_FIFO failed: %s", strerror(errno));
    pthread_setname_np(pthread_self(), "dn-flip");
    for (;;) {
        FlipJob job;
        {
            std::unique_lock<std::mutex> lk(sFlipMu);
            sFlipCv.wait(lk, [] { return sFlipBusy; });
            job = sFlipJob;
        }
        // Wait for the GPU fence here (not in the kernel) so the commit's
        // CRTC programming happens at a known moment, then keep that moment
        // clear of the vblank edge: the driver flushes the two CRTCs one after
        // the other, and a vblank falling between them latches the panels one
        // frame apart. If the edge is within flip_guard_us, wait until just
        // past it (the frame lands on the following vblank either way).
        if (job.fenceFd >= 0) {
            struct pollfd pfd = { job.fenceFd, POLLIN, 0 };
            for (int t = 0; t < 100; t++) { if (poll(&pfd, 1, 20) > 0) break; }
        }
        {
            const int64_t guardUs = property_get_int32("sys.gammaos.drastic_nano.flip_guard_us", 1000);
            const int64_t period = sDrmVblankPeriodUs > 0 ? sDrmVblankPeriodUs : 16667;
            const int64_t last = sDrmLastVblankUs;
            if (guardUs > 0 && last > 0) {
                int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
                int64_t next = last + period;
                while (next <= now) next += period;
                if (next - now < guardUs) {
                    usleep((useconds_t)(next - now + 300));
                    sFlipGuardHolds++;
                }
            }
        }
        struct drm_mode_atomic atomic = {};
        atomic.flags = DRM_MODE_PAGE_FLIP_EVENT;   // blocking: returns once the flip has landed
        atomic.count_objs = (uint32_t)job.no;
        atomic.objs_ptr = (uint64_t)(uintptr_t)job.objs;
        atomic.count_props_ptr = (uint64_t)(uintptr_t)job.counts;
        atomic.props_ptr = (uint64_t)(uintptr_t)job.props;
        atomic.prop_values_ptr = (uint64_t)(uintptr_t)job.vals;
        atomic.user_data = job.userData;
        errno = 0;
        const int ret = ioctl(sDrmFd, DRM_IOCTL_MODE_ATOMIC, &atomic);
        {
            std::lock_guard<std::mutex> lk(sFlipMu);
            sFlipResult = ret; sFlipErrno = errno; sFlipBusy = false;
        }
        sFlipCv.notify_all();
    }
}
static void drmFlipThreadWaitIdle() {
    std::unique_lock<std::mutex> lk(sFlipMu);
    sFlipCv.wait(lk, [] { return !sFlipBusy; });
}
static bool drmFlipThreadOn() {
    static int sCount = 0; static bool sOn = true;
    if ((sCount++ % 120) == 0) sOn = property_get_bool("sys.gammaos.drastic_nano.flip_thread", true);
    return sOn;
}
bool drmFlipThreadBusy() { std::lock_guard<std::mutex> lk(sFlipMu); return sFlipBusy; }

static int drmAtomicDualFlipCluster(int fd, const uint32_t* fbs, int inFenceFd) {
    const size_t n = sDrmDisplays.size();
    if (n != 2) return -1;
    for (size_t i = 0; i < n; i++) {
        DrmDisplay& d = sDrmDisplays[i];
        if (!fbs[i]) return -1;
        if (!d.planeId && !drmEnsurePlane(fd, d)) return -1;  // smart id
        if (!drmEnsureClusterPlane(fd, d)) return -1;
    }
    bool needConfig = false;
    for (size_t i = 0; i < n; i++)
        if (!sDrmDisplays[i].clConfigured) needConfig = true;

    uint32_t objs[4]; uint32_t counts[4];
    uint32_t props[64]; uint64_t vals[64];
    size_t no = 0, np = 0;
    // Region assignment for the combined buffer: the primary display (VP that
    // shows the DS TOP screen) crops rows [0,H); the secondary crops [H,2H).
    // A prop can swap it if the panels come out with the wrong half.
    bool regionSwap = property_get_bool(
            "sys.gammaos.drastic_nano.afbc_region_swap", false);
    for (size_t i = 0; i < n; i++) {
        DrmDisplay& d = sDrmDisplays[i];
        uint32_t w = d.w, h = d.h;
        // srcY selects which half of the combined buffer this panel scans.
        bool topRegion = ((int)i == sDrmPrimaryIdx);
        if (regionSwap) topRegion = !topRegion;
        uint32_t srcY = topRegion ? 0u : sDrmAfbcHalfH;
        // DEBUG afbc_both_top: force BOTH planes to crop the SAME (top) half, so
        // both panels show identical content through this exact code path. If it
        // then looks synced the offset is content-per-region (render race /
        // per-half frame mismatch); if it STILL desyncs it is a hardware per-VP
        // output offset (only fixable by delaying the lead panel or in-kernel).
        if (property_get_bool("sys.gammaos.drastic_nano.afbc_both_top", false))
            srcY = 0u;
        objs[no] = d.clPlaneId; size_t start = np;
        props[np] = d.clFbIdProp;  vals[np++] = fbs[i];
        // GPU completion handed to the kernel: the commit is queued now and
        // latches on the first vblank after the fence signals, so the CPU
        // does not sit in a fence wait before every flip.
        if (inFenceFd >= 0 && d.clInFenceProp && !needConfig) {
            props[np] = d.clInFenceProp; vals[np++] = (uint64_t)(uint32_t)inFenceFd;
        }
        if (needConfig) {
            props[np] = d.clCrtcIdProp; vals[np++] = d.crtcId;
            props[np] = d.clCrtcXProp;  vals[np++] = 0;
            props[np] = d.clCrtcYProp;  vals[np++] = 0;
            props[np] = d.clCrtcWProp;  vals[np++] = w;
            props[np] = d.clCrtcHProp;  vals[np++] = h;
            // Opaque scanout: no per-pixel alpha blending against the VP
            // background, full global alpha. Without this the planes come up
            // in whatever blend mode the previous owner left (Cluster0 was
            // seen in mode 2), and any region the shader leaves with alpha
            // below 1 shows through as a flickering translucent patch.
            if (d.clBlendProp) { props[np] = d.clBlendProp; vals[np++] = d.clBlendNone; }
            if (d.clAlphaProp) { props[np] = d.clAlphaProp; vals[np++] = 0xffff; }
            props[np] = d.clSrcXProp;   vals[np++] = 0;
            props[np] = d.clSrcYProp;   vals[np++] = (uint64_t)srcY << 16;
            props[np] = d.clSrcWProp;   vals[np++] = (uint64_t)w << 16;
            props[np] = d.clSrcHProp;   vals[np++] = (uint64_t)h << 16;
        }
        counts[no] = (uint32_t)(np - start); no++;
        if (needConfig && d.smartCrtcIdProp) {
            objs[no] = d.planeId; start = np;
            props[np] = d.smartCrtcIdProp; vals[np++] = 0;
            if (d.fbIdProp) { props[np] = d.fbIdProp; vals[np++] = 0; }
            counts[no] = (uint32_t)(np - start); no++;
        }
    }

    // Never overlap with a commit the flip thread still has in flight.
    drmFlipThreadWaitIdle();
    if (!needConfig && drmDeferDrainOn() && drmFlipThreadOn()) {
        if (!sFlipThreadStarted) {
            sFlipThreadStarted = true;
            std::thread(drmFlipThreadMain).detach();
            ALOGW("NanoMenu DRM AFBC: flip thread started (blocking commits in FIFO context)");
        }
        {
            std::lock_guard<std::mutex> lk(sFlipMu);
            memcpy(sFlipJob.objs, objs, sizeof(objs)); memcpy(sFlipJob.counts, counts, sizeof(counts));
            memcpy(sFlipJob.props, props, sizeof(props)); memcpy(sFlipJob.vals, vals, sizeof(vals));
            sFlipJob.no = no; sFlipJob.userData = sDrmCommitSeq; sFlipJob.fenceFd = inFenceFd; sFlipBusy = true;
        }
        sFlipCv.notify_all();
        for (size_t i = 0; i < n; i++) {
            int slot = drmCrtcSlot(sDrmDisplays[i].crtcId);
            if (slot >= 0) sCrtcPending[slot]++;
            sPendingFlipEvents++;
        }
        return 0;
    }
    struct drm_mode_atomic atomic = {};
    atomic.flags = needConfig ? DRM_MODE_ATOMIC_ALLOW_MODESET
                              : (DRM_MODE_PAGE_FLIP_EVENT |
                                 DRM_MODE_ATOMIC_NONBLOCK);
    atomic.count_objs = (uint32_t)no;
    atomic.objs_ptr = (uint64_t)(uintptr_t)objs;
    atomic.count_props_ptr = (uint64_t)(uintptr_t)counts;
    atomic.props_ptr = (uint64_t)(uintptr_t)props;
    atomic.prop_values_ptr = (uint64_t)(uintptr_t)vals;
    // Per-commit tag so the drain can pair BOTH CRTCs' flip-complete events to
    // the same commit (diagnostic pairing, see FLIPP log in the drain).
    atomic.user_data = sDrmCommitSeq;
    int ret = ioctl(fd, DRM_IOCTL_MODE_ATOMIC, &atomic);
    if (ret != 0) {
        static int64_t sLastErrMs = 0;
        const int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        if (nowMs - sLastErrMs >= 1000) {
            sLastErrMs = nowMs;
            ALOGW("NanoMenu DRM AFBC: atomic commit failed: %s (fence fd %d, config %d)",
                  strerror(errno), inFenceFd, needConfig ? 1 : 0);
        }
    }
    if (ret == 0) {
        if (needConfig) {
            for (size_t i = 0; i < n; i++) sDrmDisplays[i].clConfigured = true;
            ALOGW("NanoMenu DRM AFBC: Cluster modeset committed (both VPs)");
        } else {
            for (size_t i = 0; i < n; i++) {
                int slot = drmCrtcSlot(sDrmDisplays[i].crtcId);
                if (slot >= 0) sCrtcPending[slot]++;
                sPendingFlipEvents++;
            }
            if (!drmDeferDrainOn()) drmDrainPageFlipEvents();
        }
    }
    return ret;
}

void drmFlipRingSlot(int idx, bool skipNonPrimary) {
    if (idx < 0 || idx >= AHB_RING_DEPTH) return;
    AhbRenderTarget& prim = sAhbRingPrimary[idx];
    AhbRenderTarget& sec  = sAhbRingSecondary[idx];
    if (!sDrmZeroCopy || !prim.ahb) return;
    // Deferred drain: the previous commit's flip is collected here, after the
    // caller has already rendered this frame, so its GPU work overlapped the
    // previous scanout wait. A second nonblocking commit while one is pending
    // would be EBUSY, so this must precede the commit below.
    // GPU latency is measured from the moment the caller handed us the
    // rendered frame (entry here), not from the commit: in deferred mode the
    // commit itself waits for the previous flip, which would make the fence
    // look fast and flap the mode.
    const int64_t enterUs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
    if (sPendingFlipEvents > 0 || drmAnyCrtcPending()) {
        drmFlipThreadWaitIdle();   // blocking commit returned: the flip has landed
        drmDrainPageFlipEvents();
        if (sDrmAfbcMode && sDrmLastFenceFd >= 0 && sDrmLastEnterUs > 0) {
            const int64_t done = drmFenceDoneUs(sDrmLastFenceFd);
            if (done > 0) drmDeferDrainUpdate(sDrmLastEnterUs, done);
        }
    }

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
    int secSrcForSecondaryCrtcIdx = idx;
    if (sDrmFrameSync && !skipNonPrimary && sDrmDisplays.size() > 1 &&
        sec.ahb) {
        if (sFrameSyncHoldSlot >= 0 &&
            sFrameSyncHoldSlot < AHB_RING_DEPTH &&
            sAhbRingSecondary[sFrameSyncHoldSlot].ahb) {
            secSrcForSecondaryCrtc = &sAhbRingSecondary[sFrameSyncHoldSlot];
            frameSyncUsingHoldSlot = true;
            secSrcForSecondaryCrtcIdx = sFrameSyncHoldSlot;
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
    sDrmCommitSeq++;
    if (property_get_bool("sys.gammaos.drastic_nano.flip_pair_log", false)) {
        static int sPresLog = 0;
        if (sPresLog < 600) {
            ALOGW("PRES ud=%llu idx=%d secidx=%d t=%lld ll=%d fs=%d afbc=%d",
                  (unsigned long long)sDrmCommitSeq, idx,
                  frameSyncUsingHoldSlot ? secSrcForSecondaryCrtcIdx : idx,
                  (long long)t0, sDrmLowLatency ? 1 : 0, sDrmFrameSync ? 1 : 0,
                  sDrmAfbcMode ? 1 : 0);
            sPresLog++;
        }
    }

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
    int afbcInFenceFd = -1;
    bool fenceUsed = false;
    if (sAhbRingSyncPrimary[idx] != EGL_NO_SYNC_KHR &&
        sEglDupNativeFenceFDANDROID && sEglDestroySyncKHR &&
        sRingEglDpy != EGL_NO_DISPLAY) {
        const bool kernelFence = sDrmAfbcMode && primeActive &&
                sDrmDisplays.size() == 2 &&
                sDrmDisplays[0].clInFenceProp && sDrmDisplays[1].clInFenceProp &&
                property_get_bool("sys.gammaos.drastic_nano.afbc_in_fence", true);
        if (kernelFence) {
            // AFBC path: pass the slot's fence to the atomic commit as the
            // planes' IN_FENCE_FD; no userspace wait.
            primaryFenceFd = sEglDupNativeFenceFDANDROID(
                    sRingEglDpy, sAhbRingSyncPrimary[idx]);
            if (primaryFenceFd >= 0) {
                fenceUsed = true; afbcInFenceFd = primaryFenceFd;
                // GPU completion probe: a duplicate of the fence polled on a
                // helper thread gives the instant the GPU finished this
                // slot, which against the flip event gives the margin the
                // adaptive pacing lead is leaving before the vblank.
                if (property_get_bool("sys.gammaos.drastic_nano.gpu_done_log", false)) {
                    const int pfd = dup(primaryFenceFd);
                    const uint64_t ud = sDrmCommitSeq;
                    if (pfd >= 0) std::thread([pfd, ud] {
                        struct pollfd pf = { pfd, POLLIN, 0 };
                        poll(&pf, 1, 200);
                        const int64_t t = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
                        close(pfd);
                        ALOGW("GPUDONE ud=%llu t=%lld", (unsigned long long)ud, (long long)t);
                    }).detach();
                }
            }
            else {
                sEglClientWaitSyncKHR(sRingEglDpy, sAhbRingSyncPrimary[idx],
                                      EGL_SYNC_FLUSH_COMMANDS_BIT_KHR, 100000000);
                fenceUsed = true;
            }
        } else if (primeActive && sEglClientWaitSyncKHR) {
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
    } else if (primaryFenceFd >= 0 && afbcInFenceFd < 0) {
        // PRIME path already waited via eglClientWaitSyncKHR, but we
        // never actually used the fence_fd -- close it to avoid a fd leak.
        // (When the fd is the AFBC commit's IN_FENCE_FD it stays open until
        // the atomic ioctl has taken its reference.)
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

    // Presentation-latency reference point: the frame's GPU work is complete
    // here (the fence has been waited on) and nothing has been submitted yet,
    // so this is the earliest instant the frame COULD be shown. Subtracting it
    // from the primary panel's flip-complete vblank timestamp gives the true
    // ready-to-visible latency, directly comparable between Low Latency and
    // Frame Sync. Same clock base as drm_event_vblank's tv (CLOCK_MONOTONIC).
    sDrmReadyUs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;

    // Low Latency Mode: flip both CRTCs in ONE atomic commit so they latch
    // the same (kernel-phase-locked) vblank -- kills the inter-panel content
    // lag without Frame Sync's added frame of latency. Only the dual-DSI PRIME
    // path; falls back to the legacy per-CRTC loop below on any failure.
    bool atomicFlipDone = false;
    if (android::sDrmLowLatency && primeActive && !skipNonPrimary &&
        sDrmDisplays.size() == 2 && sDrmFd >= 0 && prim.drmFbId != 0) {
        // The secondary (bottom) VP latches/scans one frame AHEAD of the
        // primary on this VOP2, so putting the same-slot buffer on both makes
        // the bottom screen run a frame early. Present the secondary from a
        // slot that is sec_delay frames OLDER to cancel that lead, while the
        // primary stays at the caller's low-latency slot. Net: content synced,
        // primary latency unchanged. Prop-tunable for A/B (default 1).
        // AFBC/Cluster is the true fix: each panel shows its OWN current buffer
        // with no offset, so sec_delay must be 0 there (any delay re-adds the
        // very latency we are removing). Only the legacy Smart atomic path uses
        // the sec_delay compensation (default 1).
        int secDelay = sDrmAfbcMode ? 0 : property_get_int32(
                "sys.gammaos.drastic_nano.sec_delay", 1);
        if (secDelay < 0) secDelay = 0;
        if (secDelay >= AHB_RING_DEPTH) secDelay = AHB_RING_DEPTH - 1;
        int secIdx = (idx - secDelay + 2 * AHB_RING_DEPTH) % AHB_RING_DEPTH;
        uint32_t secDelayedFb = sAhbRingSecondary[secIdx].drmFbId;
        // DEBUG shared_fb: point BOTH planes at the SAME buffer (prim). If the
        // panels are then synced (vs the two-buffer case which desyncs), it
        // confirms the offset is per-VP post-latch pipeline and the fix is a
        // single shared scanout buffer for both panels (SF's model).
        // shared_fb is a diagnostic: point BOTH planes at the primary's single
        // buffer. Works for the Cluster (AFBC) path too so we can tell a per-VP
        // OUTPUT-pipeline offset (still desyncs with one shared buffer) apart
        // from a per-buffer latch race (syncs with one shared buffer).
        bool sharedFb = property_get_bool(
                "sys.gammaos.drastic_nano.shared_fb", false);
        uint32_t fbs[2] = {0, 0};
        for (size_t i = 0; i < 2; i++) {
            bool isPrim = ((int)i == sDrmPrimaryIdx);
            uint32_t secFb = (haveSecondary && secDelayedFb) ? secDelayedFb
                             : ((haveSecondary && sec.drmFbId) ? sec.drmFbId
                                                               : prim.drmFbId);
            fbs[i] = isPrim ? prim.drmFbId : secFb;
            if (sharedFb) fbs[i] = prim.drmFbId;
        }
        // AFBC: one combined buffer feeds BOTH panels; each Cluster plane crops
        // its half (drmAtomicDualFlipCluster sets SRC_Y). Always the primary
        // buffer for both -- this is what makes the panels sync at zero latency.
        if (sDrmAfbcMode) { fbs[0] = prim.drmFbId; fbs[1] = prim.drmFbId; }
        errno = 0;
        // AFBC mode routes to the Cluster planes (the root-cause sync fix);
        // otherwise the original Smart-plane atomic flip. On this VOP2 only the
        // Cluster planes avoid the per-VP Smart output offset, so shared_fb/
        // sec_delay are irrelevant there (both panels share one AFBC pipeline).
        const bool deferNow = drmDeferDrainOn();
        const int64_t commitUs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
        int ar = sDrmAfbcMode ? drmAtomicDualFlipCluster(sDrmFd, fbs, afbcInFenceFd)
                              : drmAtomicDualFlip(sDrmFd, fbs);
        if (afbcInFenceFd >= 0) {
            if (sDrmPrevFenceFd >= 0) close(sDrmPrevFenceFd);
            sDrmPrevFenceFd = sDrmLastFenceFd; // the commit just drained
            sDrmPrevCommitUs = sDrmLastCommitUs;
            sDrmLastFenceFd = afbcInFenceFd;   // queried by drmLastGpuDoneUs()
            sDrmLastCommitUs = commitUs;
            sDrmLastEnterUs = enterUs;
            if (ar == 0 && !deferNow) {
                // Immediate mode: the cluster commit drained the flip already.
                const int64_t done = drmFenceDoneUs(sDrmLastFenceFd);
                if (done > 0) drmDeferDrainUpdate(enterUs, done);
            }
            afbcInFenceFd = -1; primaryFenceFd = -1;
        }
        {
            static int sAtomicLog = 0;
            if (sAtomicLog < 40)
                ALOGW("NanoMenu atomic dual-flip #%d: afbc=%d ar=%d errno=%d idx=%d primIdx=%d "
                      "primFb=%u secFb=%u fbs[0]=%u fbs[1]=%u",
                      sAtomicLog, sDrmAfbcMode ? 1 : 0, ar, errno, idx, sDrmPrimaryIdx,
                      prim.drmFbId, sec.drmFbId, fbs[0], fbs[1]);
            sAtomicLog++;
        }
        if (ar == 0) {
            atomicFlipDone = true;
            for (size_t i = 0; i < sDrmDisplays.size(); i++) sEbusyStreak[i] = 0;
        } else if (errno == EBUSY) {
            // Prior atomic flip still pending: drop this frame. Both panels
            // keep their current buffer, so they stay in sync. Skip legacy.
            atomicFlipDone = true;
        } else if (sDrmAfbcMode) {
            // AFBC mode has no valid legacy fallback: the buffers are AFBC and
            // the combined layout can only be scanned by the cropped Cluster
            // planes. Drop the frame (keep last good) rather than blit garbage.
            atomicFlipDone = true;
        }
        // else: fall through to the legacy per-CRTC flips.
    }

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
    for (size_t outer = 0; !atomicFlipDone && outer < sDrmDisplays.size(); outer++) {
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
        flip.user_data = sDrmCommitSeq;
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
        if (flipRc == 0) {
            if (i < 8) sEbusyStreak[i] = 0;
            if (wantEvent) {
                sPendingFlipEvents++;
                if (crtcSlot >= 0) sCrtcPending[crtcSlot]++;
            }
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
                // Self-heal a DEAD display. A transient EBUSY (cross-CRTC
                // vblank drift) clears within a frame or two; ~90
                // consecutive EBUSYs (about 1.5 s) means the CRTC's
                // primary plane is gone - something committed over us
                // (a framework overlay window through the hardware
                // composer, e.g. the old volume indicator) and disabled
                // the plane on dismiss, and a plane-less CRTC EBUSYs
                // every legacy page flip forever. One synchronous
                // SETCRTC re-attaches the plane; the 13-32 ms it costs
                // is irrelevant on a display that has shown nothing for
                // 1.5 s. Rate-limited per display so a genuinely wedged
                // driver cannot turn this into a modeset storm.
                if (i < 8) {
                    sEbusyStreak[i]++;
                    if (sEbusyStreak[i] >= 90 &&
                        nowMs - sEbusyRecoverMs[i] >= 2000) {
                        sEbusyRecoverMs[i] = nowMs;
                        sEbusyStreak[i] = 0;
                        struct drm_mode_crtc rec = {};
                        rec.crtc_id = d.crtcId;
                        rec.fb_id = targetFbId;
                        rec.set_connectors_ptr = (uint64_t)(uintptr_t)&d.connId;
                        rec.count_connectors = 1;
                        rec.mode = d.mode;
                        rec.mode_valid = 1;
                        int rrc = ioctl(sDrmFd, DRM_IOCTL_MODE_SETCRTC, &rec);
                        ALOGW("NanoMenu DRM: display=%zu EBUSY-dead, "
                              "SETCRTC recover fb %u -> %s",
                              i, targetFbId, rrc == 0 ? "OK" : strerror(errno));
                    }
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
        // Derived from tEnd (already microseconds) instead of a second
        // systemTime() read: this ran on every flip purely to rate-limit a
        // log line, and the two reads are the same instant for that purpose.
        int64_t nowMs = tEnd / 1000LL;
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
    // Async-secondary pacing (perf loop, opt-in, default OFF): on dual-DSI the
    // secondary CRTC's vblank is phase-offset and its flip lands ~18 ms late every
    // frame, gating this blocking drain to ~2 vblanks (~28 ms -> ~50 fps). When
    // sys.gammaos.drastic_nano.async_secondary_flip is set we return as soon as the
    // PRIMARY CRTC's flip has landed and let the secondary complete asynchronously
    // (the submit side at ~L1426 skips a CRTC that is still pending, so no EBUSY;
    // the secondary just holds its previous frame until its own vblank arrives).
    const bool asyncSec =
            (sDrmDisplays.size() > 1) &&
            property_get_bool("sys.gammaos.drastic_nano.async_secondary_flip", false);
    int primSlot = -1;
    if (asyncSec && sDrmPrimaryIdx >= 0 &&
        (size_t)sDrmPrimaryIdx < sDrmDisplays.size()) {
        primSlot = drmCrtcSlot(sDrmDisplays[sDrmPrimaryIdx].crtcId);
    }
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
    while (true) {
        // Stop condition. async-secondary: return once the PRIMARY CRTC's flip has
        // landed (leave the secondary pending; it drains opportunistically on a
        // later poll). Otherwise: wait for every submitted CRTC as before.
        if (asyncSec && primSlot >= 0) {
            if (primSlot >= sCrtcTrackCount || sCrtcPending[primSlot] <= 0) break;
        } else if (!(sPendingFlipEvents > 0 || drmAnyCrtcPending())) {
            break;
        }
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
                    if (sDrmPrimaryIdx >= 0 && (size_t)sDrmPrimaryIdx < sDrmDisplays.size() &&
                        vb->crtc_id == sDrmDisplays[sDrmPrimaryIdx].crtcId)
                        {
                            const int64_t nv = (int64_t)vb->tv_sec * 1000000LL + vb->tv_usec;
                            const int64_t d = nv - sDrmLastVblankUs;
                            if (sDrmLastVblankUs > 0 && d > 15000 && d < 18000)
                                sDrmVblankPeriodUs = (sDrmVblankPeriodUs * 15 + d + 8) / 16;
                        }
                        sDrmLastVblankUs = (int64_t)vb->tv_sec * 1000000LL + vb->tv_usec;
                    if (property_get_bool(
                                "sys.gammaos.drastic_nano.flip_pair_log", false)) {
                        static int sPairLog = 0;
                        if (sPairLog < 3000) {
                            ALOGW("FLIPP ud=%llu crtc=%u seq=%u tv=%lld",
                                  (unsigned long long)vb->user_data, vb->crtc_id,
                                  vb->sequence,
                                  (long long)vb->tv_sec * 1000000LL + vb->tv_usec);
                            sPairLog++;
                        }
                    }
                    {
                        static int sSeqLog = 0;
                        if (sSeqLog < 40) {
                            ALOGW("NanoMenu flipdone: crtc=%u seq=%u tv=%u.%06u",
                                  vb->crtc_id, vb->sequence,
                                  vb->tv_sec, vb->tv_usec);
                            sSeqLog++;
                        }
                    }
                    // Rolling VP0/VP1 vblank phase measurement. The two DSI
                    // video ports free-run on independent pixel clocks; the
                    // delta between their flip-complete vblank timestamps is
                    // the phase offset that can push one panel's latch a whole
                    // refresh behind the other even from a single atomic
                    // commit. Prop-gated, logged once per second.
                    if (slot >= 0 && slot < 4 &&
                        property_get_bool(
                                "sys.gammaos.drastic_nano.phase_log", false)) {
                        static int64_t sLastVblUs[4] = {0, 0, 0, 0};
                        static uint32_t sVblCrtc[4] = {0, 0, 0, 0};
                        static uint32_t sVblSeq[4] = {0, 0, 0, 0};
                        static int64_t sLastPhaseMs = 0;
                        sLastVblUs[slot] = (int64_t)vb->tv_sec * 1000000LL +
                                           (int64_t)vb->tv_usec;
                        sVblCrtc[slot] = vb->crtc_id;
                        sVblSeq[slot] = vb->sequence;
                        int64_t nowMs =
                                systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
                        if (sLastVblUs[0] && sLastVblUs[1] &&
                            nowMs - sLastPhaseMs >= 1000) {
                            sLastPhaseMs = nowMs;
                            // Ready-to-visible latency on each panel: how long
                            // after the frame was renderable it actually hit
                            // the glass. This is the number that matters when
                            // comparing Low Latency against Frame Sync.
                            ALOGW("NanoMenu present latency: crtc%u=%lldus "
                                  "crtc%u=%lldus",
                                  sVblCrtc[0],
                                  (long long)(sLastVblUs[0] - sDrmReadyUs),
                                  sVblCrtc[1],
                                  (long long)(sLastVblUs[1] - sDrmReadyUs));
                            ALOGW("NanoMenu vblank phase: crtc%u=%lld seq=%u "
                                  "crtc%u=%lld seq=%u delta=%lldus seqdiff=%d",
                                  sVblCrtc[0], (long long)sLastVblUs[0],
                                  sVblSeq[0],
                                  sVblCrtc[1], (long long)sLastVblUs[1],
                                  sVblSeq[1],
                                  (long long)(sLastVblUs[1] - sLastVblUs[0]),
                                  (int)((int32_t)sVblSeq[1] -
                                        (int32_t)sVblSeq[0]));
                        }
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
    // Reset GL rotation to identity for the SF EGL path. SurfaceFlinger applies the
    // port 1 panel's physical orientation itself, so drop the DRM-only fold too.
    sDrmPrimaryTurned = sDrmSecondaryTurned = false;
    for (TurnScratch* s : { &sTurnPrimary, &sTurnSecondary }) {
        if (s->fbo) glDeleteFramebuffers(1, &s->fbo);
        if (s->tex) glDeleteTextures(1, &s->tex);
        s->fbo = s->tex = 0; s->w = s->h = 0;
    }
    if (sTurnProg) { glDeleteProgram(sTurnProg); sTurnProg = 0; }
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
        if (t.scanFbo) { t.glFbo = t.scanFbo; t.scanFbo = 0; }   // the shared scratch was freed above
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
    if (sDrmFd >= 0) {
        ioctl(sDrmFd, DRM_IOCTL_DROP_MASTER, 0);
        close(sDrmFd);
        sDrmFd = -1;
    }
}

// Re-commit the DRM output state after a kernel suspend/resume cycle.
//
// Suspend runs vop2_crtc_atomic_disable on every CRTC; resume re-enables the
// CRTCs with their mode but with NO planes attached (verified on RG DS via
// /sys/kernel/debug/dri/0/summary: both VPs ACTIVE, zero window sections).
// A CRTC without a primary-plane framebuffer makes every subsequent legacy
// DRM_IOCTL_MODE_PAGE_FLIP return EBUSY, and the flip path deliberately
// DROPS EBUSY flips (re-routing them into a synchronous SETCRTC caused
// 13-32 ms stalls on normal microhitch frames, see the 2026-04-13 note in
// drmFlipRingSlot), so nothing ever re-attaches a plane: backlight on,
// panels black, endless "DRM flip EBUSY" log storm.
//
// Called once at the wake point (the render thread, which owns all flip
// state), with the backlight still off so the modeset is invisible:
//  1. Defensive SET_MASTER (no-op when we already are).
//  2. Discard stale page-flip events non-blocking. The pre-sleep flip's
//     event may have been lost across suspend, so the blocking drain
//     (100 ms/event) is wrong here.
//  3. Zero the flip bookkeeping. A lost event otherwise leaves
//     sCrtcPending > 0 and the pre-submit gate skips that CRTC forever.
//  4. Reset the AHB ring cursors; both ring consumers re-prime cleanly.
//  5. Full SETCRTC per display with the same fb the flip path would use
//     (PRIME ring slot 0, or the dumb buffer on the legacy blit path),
//     with the atomic ALLOW_MODESET fallback for SDE-class drivers.
//
// Compiled into both gammaos-nano and drastic-nano (shared filegroup); each
// process recommits its own master after its own sleep block.
static long drmReadSuspendCount() {
    FILE* f = fopen("/sys/power/suspend_stats/success", "r");
    if (!f) return -1;
    long v = -1;
    if (fscanf(f, "%ld", &v) != 1) v = -1;
    fclose(f);
    return v;
}
static long sSuspendSeen = -1;
static int64_t sSuspendCheckUs = 0;

bool drmSuspendCycleDetected() {
    const int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
    if (now - sSuspendCheckUs < 500000) return false;
    sSuspendCheckUs = now;
    const long c = drmReadSuspendCount();
    if (c < 0) return false;
    if (sSuspendSeen < 0) { sSuspendSeen = c; return false; }
    if (c == sSuspendSeen) return false;
    sSuspendSeen = c;
    return true;
}

void drmSuspendMarkSeen() {
    const long c = drmReadSuspendCount();
    if (c >= 0) sSuspendSeen = c;
    sSuspendCheckUs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
}

void drmResumeRecommit() {
    if (sDrmFd < 0 || !sDrmActive || sDrmDisplays.empty()) return;

    ioctl(sDrmFd, DRM_IOCTL_SET_MASTER, 0);

    // Discard whatever flip events accumulated across the suspend without
    // blocking the wake path.
    for (;;) {
        struct pollfd pfd = { sDrmFd, POLLIN, 0 };
        if (poll(&pfd, 1, 0) <= 0 || !(pfd.revents & POLLIN)) break;
        char buf[4096];
        if (read(sDrmFd, buf, sizeof(buf)) <= 0) break;
    }
    sPendingFlipEvents = 0;
    for (int i = 0; i < sCrtcTrackCount; i++) sCrtcPending[i] = 0;
    sRingRenderIdx = 0;
    sRingPresentIdx = 0;
    sRingPrimedCount = 0;

    for (size_t i = 0; i < sDrmDisplays.size(); i++) {
        auto& d = sDrmDisplays[i];
        const bool isPrimary = ((int)i == sDrmPrimaryIdx);
        // Same source routing as drmFlipRingSlot: primary CRTC scans the
        // primary AHB, every other CRTC the secondary AHB (primary as the
        // mirror fallback), dumb buffer when PRIME import is unavailable.
        uint32_t fbId = 0;
        // AFBC mode: the ring buffers are AFBC and only the Cluster planes can
        // scan them, so the legacy SETCRTC through the Smart primary would be
        // rejected and leave the panels blank after wake. Bring the CRTC up on
        // its linear dumb buffer instead and force the next flip to redo the
        // Cluster binding modeset (the kernel dropped the plane state).
        if (sDrmZeroCopy && !sDrmAfbcMode) {
            if (!isPrimary && sAhbRingSecondary[0].drmFbId != 0)
                fbId = sAhbRingSecondary[0].drmFbId;
            else
                fbId = sAhbRingPrimary[0].drmFbId;
        }
        if (fbId == 0) fbId = d.buffers[d.activeBuffer].fbId;
        if (sDrmAfbcMode) d.clConfigured = false;

        struct drm_mode_crtc crtc = {};
        crtc.crtc_id = d.crtcId;
        crtc.fb_id = fbId;
        crtc.set_connectors_ptr = (uint64_t)(uintptr_t)&d.connId;
        crtc.count_connectors = 1;
        crtc.mode = d.mode;
        crtc.mode_valid = 1;
        int rc = ioctl(sDrmFd, DRM_IOCTL_MODE_SETCRTC, &crtc);
        if (rc != 0 && errno == EINVAL) {
            rc = drmAtomicModesetFallback(sDrmFd, d.crtcId, d.connId, d.mode,
                                          fbId, d.w, d.h);
        }
        ALOGW("NanoMenu DRM resume recommit: crtc %u conn %u fb %u -> %s",
              d.crtcId, d.connId, fbId, rc == 0 ? "OK" : strerror(errno));
    }
}

} // namespace android
