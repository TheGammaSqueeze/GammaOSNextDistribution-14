// PSP Go XMB slide clock - 1:1 port of the web xmb PSP clock
// (/work/ps3/xmb-app psp_clock.js + index.html sections 5.x) into nano GLES2.
//
// Full-screen procedural analog clock: a refractive glass disc over a blurred,
// darkened background, a procedural entrance explosion, traced-vector numerals,
// spring-snapped hands, a comet trail, a frosted date label and a dynamic glow
// sampled from the wallpaper. Gated by persist.gammaos.nano.pspclock; shown while
// KEY_F12 (the swivel) is DOWN, exited on UP. Everything is a pure function of
// mPspClockReveal so an F12 flip mid-transition just reverses the scalar.
//
// This is built up in stages; see /work/ps3/xmb-app/PSP_CLOCK_SPEC.md.

#define LOG_TAG "GammaOSNano"
#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Bg.h"
#include "NanoMenuDrm.h"      // sDrmRotMat (auto-rotation) if ever needed directly
#include "NanoMenuShaders.h"  // compileShader / linkProgram (namespace android)
#include "NanoMenuPS3ClockGlyphs.h"  // baked numeral outline contours
#include <cutils/properties.h>
#include <sys/system_properties.h>
#include <vector>
#include <utils/Log.h>
#include <GLES2/gl2.h>
#include <math.h>
#include <time.h>
#include <algorithm>
// --- live-app capture (#5): binder screen-capture + worker/render-thread channel ---
#include <mutex>
#include <atomic>
#include <thread>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <optional>
#include <unistd.h>
#include <android/gui/BnScreenCaptureListener.h>
#include <gui/DisplayCaptureArgs.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/BufferItemConsumer.h>       // continuous mirror consumer
#include <gui/BufferItem.h>
#include <gui/BufferQueue.h>
#include <gui/LayerState.h>               // layer_state_t::eLayerSkipScreenshot
#include <ui/DisplayState.h>              // ui::DisplayState (layerStack)
#include <ui/GraphicBuffer.h>
#include <ui/Fence.h>
#include <ui/Rect.h>
#include <ui/PixelFormat.h>
#include <utils/String8.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2ext.h>

namespace android {

// ============================================================================
// PSP live-app capture (#5) - file-static worker <-> render-thread channel.
//
// The detached worker touches ONLY the state in this anonymous namespace plus
// its own local sp<> copies of the display token / exclude handles. It NEVER
// dereferences a NanoMenu* (safe across overlay teardown / process restart).
// The render thread does the tiny GL upload from gPspCapStaging under a
// microsecond-scale mutex. Everything here is dead unless the liveapp gate is on.
// ============================================================================
namespace {

// ---- Bounded capture listener --------------------------------------------
// SyncScreenCaptureListener::waitForResults() does resultsFuture.get() +
// fence->waitForever() - BOTH unbounded. On a wedged SF/GPU (the MediaTek/Mali
// force-SF failure modes) that hangs the worker forever, so it can never observe
// the stop flag and the process-exit wait becomes a no-op. This listener instead
// signals a condition_variable and the worker waits with a bounded timeout, and
// waits the returned fence itself with a bounded fence->wait(ms). This is the
// prerequisite that makes every "bounded / promptly-stoppable" property real.
class NanoPspCaptureListener : public gui::BnScreenCaptureListener {
public:
    binder::Status onScreenCaptureCompleted(
            const gui::ScreenCaptureResults& results) override {
        {
            std::lock_guard<std::mutex> lk(mMutex);
            mResults = results;
            mDone = true;
        }
        mCv.notify_one();
        return binder::Status::ok();
    }
    // Returns true if the callback arrived within timeoutMs (out = results).
    bool wait(int timeoutMs, gui::ScreenCaptureResults& out) {
        std::unique_lock<std::mutex> lk(mMutex);
        if (!mCv.wait_for(lk, std::chrono::milliseconds(timeoutMs),
                          [this] { return mDone; }))
            return false;
        out = mResults;
        return true;
    }
private:
    std::mutex                 mMutex;
    std::condition_variable    mCv;
    bool                       mDone = false;
    gui::ScreenCaptureResults  mResults;
};

std::mutex               gPspCapMutex;         // guards the staging vector + dims + ready flag
std::vector<uint8_t>     gPspCapStaging;       // tightly-packed RGBA of the latest frame
int                      gPspCapW = 0, gPspCapH = 0;
bool                     gPspCapFrameReady = false;   // producer set, consumer clears
std::atomic<bool>        gPspCapRun{false};    // worker run flag; render thread clears to stop
std::atomic<bool>        gPspCapAlive{false};  // true while a worker thread exists (join-free guard)
// Binder inputs, published by the render thread under gPspCapMutex before launch.
sp<IBinder>              gPspCapDisplayToken;               // physical display token
std::vector<sp<IBinder>> gPspCapExcludeHandles;            // ALL nano-owned layer handles
uint32_t                 gPspCapWantW = 0, gPspCapWantH = 0; // capture size (0 = full display)

// Capture cadence + timeouts. The default cadence is live-tunable so the true SF
// ceiling (fastest capture that keeps the app at a full 60fps) can be dialled in per
// device WITHOUT a rebuild via persist.gammaos.nano.pspclock.capms. There is no point
// capturing faster than the overlay composites (the backdrop is only shown on a nano
// render), so the sweet spot is roughly the overlay's own frame rate. Timeouts are
// bounded so a wedged SF/GPU skips a frame instead of pinning the worker.
constexpr int kCapPeriodDefaultMs = 33;    // ~30Hz default (matches the overlay render rate)
constexpr int kCapSliceMs    = 5;     // wake this often to notice a stop request
constexpr int kCapCbTimeout  = 700;   // max wait for onScreenCaptureCompleted
constexpr int kCapFenceMs    = 500;   // max wait on the capture fence

// Live-tunable capture period (ms), clamped to a sane range. Read once per iteration.
static inline int pspCapPeriodMs() {
    int ms = (int)property_get_int32("persist.gammaos.nano.pspclock.capms", kCapPeriodDefaultMs);
    if (ms < 8)   ms = 8;      // 125Hz hard ceiling - never busy-spin SF
    if (ms > 500) ms = 500;    // 2Hz floor
    return ms;
}

// ---------------------------------------------------------------------------
// Continuous display mirror (zero-copy, render-thread only)
// ---------------------------------------------------------------------------
// The per-frame captureDisplay path (above) tops out at ~25Hz because each frame
// is a synchronous SF screenshot + a CPU readback + a GL re-upload, so the live
// app BEHIND the clock stutters. Instead, stand up a SurfaceFlinger VIRTUAL DISPLAY
// that mirrors the primary's layer stack into a BufferQueue; SF fills it every
// composite (a live 60fps feed) at ~zero extra cost. We consume the newest buffer
// each render frame, import it straight to a GL texture as an EGLImage (NO copy),
// and V-flip-blit it into mPspClockAppTex so the WHOLE downstream pipeline (blur,
// lens refraction, parallax, darken) is byte-identical to the old capture path.
// nano's own overlay layer is flagged skip-screenshot so the mirror excludes it
// (no feedback) - this replaces the captureDisplay excludeHandles mechanism.
// Everything here runs on the render thread (the only GL thread), so no mutex.
sp<IBinder>            gMirDisplay;                 // virtual display token
sp<SurfaceControl>     gMirLayer;                   // mirrorDisplay() clone (excludes skip-screenshot)
sp<BufferItemConsumer> gMirConsumer;               // consumes the mirrored frames
bool                   gMirActive = false;          // pipeline up
// Dedicated layer stack for the mirror clone + virtual display (kept off the primary's
// stack 0 so the clone is NEVER composited onto the real panel - no on-screen artifact).
constexpr uint32_t     kMirLayerStack = 0x6E414D6F;  // 'nAMo' - unlikely to collide
bool                   gMirFailed = false;          // start failed once -> fall back to worker
GLuint                 gMirFlipFbo = 0;             // FBO wrapping mPspClockAppTex for the flip blit
// Per-buffer EGLImage+texture cache. The mirror BufferQueue only cycles a handful of
// gralloc buffers, so import each ONCE (keyed by GraphicBuffer id) and reuse - recreating
// the EGLImage + rebinding the texture every frame was ~0.4ms of pure overhead.
struct MirTexEntry { uint64_t id; EGLImageKHR img; GLuint tex; };
MirTexEntry            gMirCache[8];
int                    gMirCacheN = 0;
// Dedicated persistent copy of the app backdrop blur. The heavy blurGlassChain that
// defocuses the surround is recomputed only every Nth frame (the surround is blurred +
// darkened and has NO parallax, so 30Hz is visually identical to 60Hz) and copied here;
// the frost blit then reads THIS every frame, immune to the chrome-glow pass clobbering
// the shared mGlassBlurTex. gBdValid gates the first-frame recompute.
GLuint                 gBdBlurTex = 0, gBdBlurFbo = 0;
int                    gBdBlurW = 0, gBdBlurH = 0;
bool                   gBdValid = false;
int                    gBdTick = 0;
BufferItem             gMirHeld;                    // buffer held for release next frame (GPU-read safety)
bool                   gMirHasHeld = false;
// Minimal V-flip copy program (mirror src -> mPspClockAppTex).
GLuint gMirProg = 0; GLint gMirPos = -1, gMirUV = -1, gMirSamp = -1, gMirFlip = -1;
// EGLImage entry points (loaded lazily; the overlay instance does not run the DRM init).
PFNEGLCREATEIMAGEKHRPROC            pEglCreateImageKHR = nullptr;
PFNEGLDESTROYIMAGEKHRPROC           pEglDestroyImageKHR = nullptr;
PFNGLEGLIMAGETARGETTEXTURE2DOESPROC pGlEGLImageTargetTexture2DOES = nullptr;

// ---- MSAA render-to-texture for the crisp FACE (perf, arm64/tile-based GPUs) ---------
// The face (ticks/hands/hub/numerals/comet trail) is anti-aliased by rendering it into an
// offscreen buffer and downsampling. The historic path SUPERSAMPLES 2x2 = shades 4x the
// screen pixel area, which on-device (TrimUI Brick, PowerVR Rogue GE8300) is the single
// biggest GPU cost of the clock (the whole clock is GPU-bound, ~95% util). GL_EXT/IMG_
// multisampled_render_to_texture lets a tile-based GPU take 4 coverage samples per pixel
// and resolve them INSIDE tile memory - equal-or-better edge AA at ~1x fragment cost
// instead of 4x. Loaded lazily; when the extension is absent (or the tuning prop is off)
// pspClockFace falls back to the 2x2 supersampled path, so nothing regresses elsewhere.
PFNGLFRAMEBUFFERTEXTURE2DMULTISAMPLEEXTPROC pGlFramebufferTexture2DMultisampleEXT = nullptr;
static int  gPspFaceMsaaSamples = 0;    // 0 = unprobed, -1 = unavailable, >0 = sample count
static bool gPspFaceMsaaProbed  = false;

// True once the driver is confirmed to support MSAA-render-to-texture; caches the entry
// point and the sample count (capped at 4 - GE8300 tops out there and 4x is plenty for
// these thin polygons). Must run with a current GL context (called from pspClockFace).
static bool pspFaceMsaaAvailable() {
    if (!gPspFaceMsaaProbed) {
        gPspFaceMsaaProbed = true;
        const char* exts = (const char*)glGetString(GL_EXTENSIONS);
        const bool has = exts && (strstr(exts, "GL_EXT_multisampled_render_to_texture") ||
                                  strstr(exts, "GL_IMG_multisampled_render_to_texture"));
        if (has) {
            pGlFramebufferTexture2DMultisampleEXT =
                (PFNGLFRAMEBUFFERTEXTURE2DMULTISAMPLEEXTPROC)
                eglGetProcAddress("glFramebufferTexture2DMultisampleEXT");
        }
        if (pGlFramebufferTexture2DMultisampleEXT) {
            GLint maxs = 0; glGetIntegerv(GL_MAX_SAMPLES_EXT, &maxs);
            gPspFaceMsaaSamples = maxs >= 4 ? 4 : (maxs >= 2 ? 2 : -1);
        } else {
            gPspFaceMsaaSamples = -1;
        }
    }
    return gPspFaceMsaaSamples > 0;
}

// Tuning gate for the MSAA face path (default ON). Serial-cached so a live toggle
// (persist.gammaos.nano.pspclock.facemsaa 0/1) takes effect next frame for A/B compares
// without a per-frame name lookup.
static bool pspFaceMsaaEnabled() {
    static const prop_info* pi = nullptr; static uint32_t ser = 0; static bool val = true;
    if (!pi) pi = __system_property_find("persist.gammaos.nano.pspclock.facemsaa");
    if (pi) { uint32_t s = __system_property_serial(pi); if (s != ser) { ser = s; val = property_get_bool("persist.gammaos.nano.pspclock.facemsaa", true); } }
    return val;
}

// Per-pass GPU profiler gate (persist.gammaos.nano.pspclock.prof=1, default OFF). Serial-
// cached; when off, drawPspClock issues no glFinish/timestamps so it is truly zero-cost.
static bool pspClockProfEnabled() {
    static const prop_info* pi = nullptr; static uint32_t ser = 0; static bool val = false;
    if (!pi) pi = __system_property_find("persist.gammaos.nano.pspclock.prof");
    if (pi) { uint32_t s = __system_property_serial(pi); if (s != ser) { ser = s; val = property_get_bool("persist.gammaos.nano.pspclock.prof", false); } }
    return val;
}

static const char MIR_BLIT_VS[] = R"(
    attribute vec2 aPos; attribute vec2 aUV; varying vec2 vUV;
    void main() { vUV = aUV; gl_Position = vec4(aPos, 0.0, 1.0); }
)";
static const char MIR_BLIT_FS[] = R"(
    precision mediump float; varying vec2 vUV; uniform sampler2D uT; uniform float uFlip;
    void main() { vec2 uv = vec2(vUV.x, mix(vUV.y, 1.0 - vUV.y, uFlip)); gl_FragColor = texture2D(uT, uv); }
)";

} // namespace

// ---- constants (PSP native, from psp_clock.js / spec section 4.1) -----------
static const float PSP_W = 480.0f, PSP_H = 272.0f, CXf = 240.0f, CYf = 136.0f;
static const float R_DISC = 141.0f;

static inline float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

// Current time: live localtime with sub-second precision (drives the second-hand
// spring + comet trail).
static void pspNow(int& h, int& m, int& s, float& sub) {
    time_t now = time(nullptr); struct tm lt; localtime_r(&now, &lt);
    struct timespec tsp; clock_gettime(CLOCK_REALTIME, &tsp);
    h = lt.tm_hour; m = lt.tm_min; s = lt.tm_sec; sub = tsp.tv_nsec / 1e9f;
}
static const float PSP_DECAY = 0.98f, PSP_FRAME_MS = 50.0f;

// Read the gate prop (cheap; per-frame is fine, it is a shared-memory read).
// 0/unset = off; 1 = enabled (F12 down/up drives the open/close via NanoMenuInput).
void NanoMenu::pspClockPollInput() {
    mPspClockEnabled = property_get_bool("persist.gammaos.nano.pspclock", false);
    // Standalone summon: the framework raised the overlay + set pspclock_summon to invoke
    // the clock over a running app (the swivel does not reach nano's evdev while it is parked
    // behind an app). While standalone, mirror the summon prop into mPspClockOn so releasing
    // the slide (framework clears the prop) ramps the clock closed; the fully-closed teardown
    // in drawPspClock then lowers the overlay we raised.
    if (mPspClockStandalone && mPspClockOn
        && !property_get_bool("sys.gammaos.nano.pspclock_summon", false)) {
        mPspClockOn = false;
    }
}

// Gyro/accel parallax (user request): tilting the device shifts the background sampled
// through the glass disc so you can "peek behind" it at the corners. Reads the
// accelerometer via the ASensor NDK while the clock is up, low-passes the gravity vector
// into a small UV offset (mPspTilt*), and disables the sensor when the clock closes so it
// costs nothing at rest. persist.gammaos.nano.pspclock.tilt="x,y" overrides it for testing.
void NanoMenu::pspClockPollTilt(bool active) {
    // Debug override (UV units, already the final offset): bypasses the sensor entirely.
    {
        char buf[PROPERTY_VALUE_MAX] = {};
        if (property_get("persist.gammaos.nano.pspclock.tilt", buf, "") > 0 && buf[0]) {
            float tx = 0.0f, ty = 0.0f;
            if (sscanf(buf, "%f,%f", &tx, &ty) == 2) {
                mPspTiltX += (tx - mPspTiltX) * 0.15f;   // low-pass so externally-stepped test
                mPspTiltY += (ty - mPspTiltY) * 0.15f;   // values glide (smooth parallax capture)
                return;
            }
        }
    }
    // Diagnostic kill-switch: force the accelerometer fully OFF (disable the HAL sensor and
    // skip all polling) so we can A/B whether sensor activity is what stalls the game behind
    // the clock. persist.gammaos.nano.pspclock.noaccel=1.
    if (!active || property_get_bool("persist.gammaos.nano.pspclock.noaccel", false)) {
        if (mPspSensorEnabled && mPspSensorQueue && mPspAccelSensor) {
            ASensorEventQueue_disableSensor(mPspSensorQueue, mPspAccelSensor);
            mPspSensorEnabled = false;
        }
        mPspTiltX *= 0.90f; mPspTiltY *= 0.90f;   // ease back to centre
        return;
    }
    if (mPspSensorMgr == nullptr) {
        mPspSensorMgr = ASensorManager_getInstanceForPackage("com.gammaos.nano");
        if (mPspSensorMgr == nullptr) mPspSensorMgr = ASensorManager_getInstance();
        if (mPspSensorMgr) {
            mPspAccelSensor = ASensorManager_getDefaultSensor(mPspSensorMgr, ASENSOR_TYPE_ACCELEROMETER);
            ALooper* looper = ALooper_forThread();
            if (looper == nullptr) looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
            if (looper && mPspAccelSensor)
                mPspSensorQueue = ASensorManager_createEventQueue(mPspSensorMgr, looper, 3, nullptr, nullptr);
        }
    }
    if (mPspSensorQueue && mPspAccelSensor && !mPspSensorEnabled) {
        ASensorEventQueue_enableSensor(mPspSensorQueue, mPspAccelSensor);
        ASensorEventQueue_setEventRate(mPspSensorQueue, mPspAccelSensor, 16666);   // ~60Hz
        mPspSensorEnabled = true;
    }
    if (!mPspSensorQueue) return;
    ASensorEvent ev; float ax = 0.0f, ay = 0.0f; bool got = false;
    while (ASensorEventQueue_getEvents(mPspSensorQueue, &ev, 1) > 0) {
        ax = ev.acceleration.x; ay = ev.acceleration.y; got = true;   // keep the latest
    }
    if (got) {
        // Gravity along the panel axes (~9.81 m/s^2 at full tilt) -> small UV parallax.
        // Shift the sampled bg TOWARD the tilt so the far edge of the disc reveals more.
        //
        // Device calibration, live-tunable so the parallax ORIENTATION + throw can be
        // dialled in per device WITHOUT a rebuild:
        //   persist.gammaos.nano.pspclock.tilt.cal = "gain,rot,sx,sy"
        //     gain  parallax throw in UV units (default 0.12)
        //     rot   base orientation: rotate the accel->UV axes by rot*90 deg (0/1/2/3).
        //           The Sprd accel on the RG Rotate is mounted 90 deg vs the panel, so the
        //           default is 1.
        //     sx,sy per-axis sign (+1/-1) to flip either axis. The default is -1,-1: the
        //           background pans OPPOSITE the tilt, which is the natural "peek behind the
        //           glass" parallax (shift your viewpoint one way, the bg behind slides the
        //           other way). Set +1 to move the bg toward the tilt instead.
        // The sDrmRotMat transform in pspClockLens then rotates this panel-native tilt into
        // the CURRENT screen-rotation frame (composes on top of this base orientation).
        static float sCalGain = 0.12f; static int sCalRot = 1;
        static float sCalSx = -1.0f, sCalSy = -1.0f; static bool sCalInit = false;
        static int sCalTick = 0;
        if (!sCalInit || (++sCalTick % 60) == 0) {   // re-read ~1s so a live retune applies
            sCalInit = true;
            sCalGain = 0.12f; sCalRot = 1; sCalSx = -1.0f; sCalSy = -1.0f;   // -1,-1 = peek OPPOSITE the tilt; wider default range
            char cb[PROPERTY_VALUE_MAX] = {};
            if (property_get("persist.gammaos.nano.pspclock.tilt.cal", cb, "") > 0 && cb[0]) {
                float g = sCalGain, sx = sCalSx, sy = sCalSy; int rt = sCalRot;
                int n = sscanf(cb, "%f,%d,%f,%f", &g, &rt, &sx, &sy);
                if (n >= 1 && g >= 0.0f && g <= 1.0f) sCalGain = g;
                if (n >= 2) sCalRot = ((rt % 4) + 4) % 4;
                if (n >= 3) sCalSx = sx < 0.0f ? -1.0f : 1.0f;
                if (n >= 4) sCalSy = sy < 0.0f ? -1.0f : 1.0f;
            }
        }
        const float G = 9.81f;
        auto cl = [](float v){ return v < -1.0f ? -1.0f : (v > 1.0f ? 1.0f : v); };
        // Base accel -> panel-native UV, rotated by rot*90 deg to correct the sensor mount.
        float ux = ax / G, uy = ay / G, rx, ry;
        switch (sCalRot) {
            case 1:  rx = -uy; ry =  ux; break;   // 90
            case 2:  rx = -ux; ry = -uy; break;   // 180
            case 3:  rx =  uy; ry = -ux; break;   // 270
            default: rx =  ux; ry =  uy; break;   // 0
        }
        float tgtX = cl(rx) * sCalSx * sCalGain;
        float tgtY = cl(ry) * sCalSy * sCalGain;
        mPspTiltX += (tgtX - mPspTiltX) * 0.15f;   // low-pass (glide, kill jitter)
        mPspTiltY += (tgtY - mPspTiltY) * 0.15f;
    }
}

// Smoothed XMB text-fade multiplier (spec 5.4). Consumers multiply their alpha.
float NanoMenu::pspClockTextFade() const {
    if (mPspClockStandalone) return 0.0f;   // summoned over an app: no XMB text shows at all
    return mPspClockReveal <= 0.0f ? 1.0f : mPspTextFadeSmooth;
}

// Whole-canvas opacity backstop (spec 5.5, applyClockTransition): clears any
// remaining XMB chrome (descriptions and stray UI) AFTER the moving items have
// left, over the window reveal 0.58..0.72 with a smoothstep. 1.0 = fully opaque.
float NanoMenu::pspClockChromeFade() const {
    if (mPspClockStandalone) return 0.0f;   // summoned over an app: clear all XMB chrome (none should show)
    if (mPspClockReveal <= 0.0f) return 1.0f;
    float f = clamp01((mPspClockReveal - 0.58f) / 0.14f);
    return 1.0f - f * f * (3.0f - 2.0f * f);
}

// Category icon blow-away (spec 5.5): each icon flies off the RIGHT edge with an
// upward gust + tumble, near-linear over a wide window, no fade. Offsets are in
// device px (scaled to the panel; the web values were tuned at a 720 canvas).
bool NanoMenu::pspClockBlowCat(int i, float& bx, float& by, float& brot) const {
    // Standalone (summoned over an app): there is no XMB category bar to blow away.
    if (mPspClockStandalone || mPspClockReveal <= 0.0f) { bx = by = brot = 0.0f; return false; }
    float seed = ((i*53 + 7) % 17) / 17.0f;
    float st = clamp01((mPspClockReveal - seed*0.03f) / 0.65f);
    float bl = st * (1.15f - 0.15f*st);
    float bsc = mWidth / 720.0f;
    bx = bl * (1500.0f + i*240.0f + seed*500.0f) * bsc;
    by = -bl * (90.0f + seed*220.0f) * bsc;
    brot = bl * (seed - 0.5f) * 3.4f;
    return true;
}

// Item icon blow-away (spec 5.5): icons slide off ~50% slower than the category
// bar with an upward arc + tumble; the text fades separately (pspClockTextFade).
bool NanoMenu::pspClockBlowItem(int i, float& xShift, float& yLift, float& rot) const {
    // Standalone (summoned over an app): there is no XMB item list to blow away.
    if (mPspClockStandalone || mPspClockReveal <= 0.0f) { xShift = yLift = rot = 0.0f; return false; }
    float seed = ((i*61 + 13) % 23) / 23.0f;
    float st = clamp01((mPspClockReveal - i*0.02f - seed*0.03f) / 0.65f);
    float e = st * (1.15f - 0.15f*st);
    float bsc = mWidth / 720.0f;
    xShift = e * (1950.0f + seed*1200.0f) * bsc;
    yLift  = e * (70.0f + seed*240.0f) * bsc;
    rot    = e * (seed - 0.5f) * 2.6f;
    return true;
}

// =============================================================================
// PSP clock live-app capture (#5) - gate helpers, detached worker, render tick.
// =============================================================================

// Debug/force prop: capture the live app even in the home (non-overlay) instance.
// Lets the feature be exercised on a device with no scrim overlay by opting in.
static bool pspClockAppSrcDebug() {
    return property_get_bool("persist.gammaos.nano.pspclock.appsrc", false)
        || property_get_bool("sys.gammaos.nano.pspclock.appsrc", false);
}

// Master enable for live-app capture. Default OFF -> zero behaviour change: with
// this false, want is always false, the worker never launches, and the lens uses
// ps3bg::workTex() exactly as before.
bool NanoMenu::pspClockLiveAppEnabled() const {
    return property_get_bool("persist.gammaos.nano.pspclock.liveapp", false)
        || pspClockAppSrcDebug();
}

// The lens should sample the captured app (not the wave) when there is a live app
// behind us: overlay-instance scrim mode (mOverlayMode && !mOverlayWallpaper), OR
// the debug appsrc prop is forcing it. Only once we actually hold a frame.
bool NanoMenu::pspClockUseAppSource() const {
    if (!pspClockLiveAppEnabled()) return false;
    if (!mPspClockAppTexValid || mPspClockAppTex == 0) return false;
    return (mOverlayMode && !mOverlayWallpaper) || pspClockAppSrcDebug();
}

// Detached capture worker. Runs entirely on file-static state + local sp<> copies.
// Each iteration: ScreenshotClient::captureDisplay(args, listener) with a BOUNDED
// listener wait -> bounded fence->wait -> lock(SW_READ) -> memcpy tightly into
// gPspCapStaging under gPspCapMutex -> unlock -> DROP the sp<GraphicBuffer>.
// The buffer never outlives one iteration, so gralloc reclaims it every frame
// (kills the accumulation OOM). Exits promptly when gPspCapRun clears.
void NanoMenu::pspClockCaptureWorker() {
    gPspCapAlive.store(true, std::memory_order_release);

    // Snapshot the binder inputs locally (sp<> copies keep the binders alive for
    // the worker's lifetime independent of overlay/process teardown). Re-read each
    // iteration under the mutex so a mid-run refresh (rotation/relaunch) is picked
    // up and a stale exclude handle cannot silently start self-capturing.
    while (gPspCapRun.load(std::memory_order_acquire)) {
        // Live-tunable throttle; wake every kCapSliceMs to notice a stop request quickly.
        const int periodMs = pspCapPeriodMs();
        for (int i = 0; i < periodMs / kCapSliceMs &&
                        gPspCapRun.load(std::memory_order_acquire); i++)
            usleep(kCapSliceMs * 1000);
        if (!gPspCapRun.load(std::memory_order_acquire)) break;

        sp<IBinder>              token;
        std::vector<sp<IBinder>> excludes;
        uint32_t                 wantW = 0, wantH = 0;
        {
            std::lock_guard<std::mutex> lk(gPspCapMutex);
            token    = gPspCapDisplayToken;
            excludes = gPspCapExcludeHandles;
            wantW    = gPspCapWantW;
            wantH    = gPspCapWantH;
        }
        if (token == nullptr) continue;

        gui::DisplayCaptureArgs args;
        args.displayToken   = token;
        args.width          = wantW;   // 0 -> full display resolution
        args.height         = wantH;
        args.pixelFormat    = ui::PixelFormat::RGBA_8888;
        args.captureSecureLayers = false;   // secure surfaces render black - safe
        args.allowProtected      = false;   // no protected content sampling; also skips
                                            // SF's extra protected-layer main-thread round-trip
        // Exclude EVERY nano-owned layer (primary overlay + secondary wallpapers) so
        // the capture is the app underneath only - no self-recursion / white feedback,
        // and SF never has to wait on our own (potentially blocked) layer.
        for (const sp<IBinder>& h : excludes)
            if (h != nullptr) args.excludeHandles.insert(h);

        sp<NanoPspCaptureListener> listener = new NanoPspCaptureListener();
        if (ScreenshotClient::captureDisplay(args, listener) != NO_ERROR)
            continue;   // binder call failed (bad handle etc.) - no callback owed, skip

        // BOUNDED wait for the callback. If SF never delivers (service death, drop),
        // we skip rather than block forever, and stay responsive to gPspCapRun.
        gui::ScreenCaptureResults res;
        if (!listener->wait(kCapCbTimeout, res))
            continue;
        if (!res.fenceResult.ok() || res.buffer == nullptr)
            continue;

        // Bounded fence wait on the CPU before locking (our listener does NOT
        // waitForever). A wedged fence just skips this frame.
        {
            sp<Fence> f = res.fenceResult.value_or(Fence::NO_FENCE);
            if (f != nullptr && f != Fence::NO_FENCE) f->wait(kCapFenceMs);
        }

        sp<GraphicBuffer> buf = res.buffer;   // LOCAL sp<> - dropped at loop end.
        void* base = nullptr;
        if (buf->lock(GraphicBuffer::USAGE_SW_READ_OFTEN, &base) == NO_ERROR && base) {
            const int w = (int)buf->getWidth();
            const int h = (int)buf->getHeight();
            const int stride = (int)buf->getStride();
            if (w > 0 && h > 0 && w <= 8192 && h <= 8192) {
                const size_t need = (size_t)w * h * 4;
                std::lock_guard<std::mutex> lk(gPspCapMutex);
                if (gPspCapStaging.size() != need) gPspCapStaging.resize(need);
                const uint8_t* src = (const uint8_t*)base;
                // Row-REVERSE (vertical flip) as we repack: the captured buffer's TOP row
                // (display top) is written to the staging BOTTOM row, so after glTexImage2D
                // the texture is UPRIGHT (display top at v=1). Then the lens disc AND the
                // blurred backdrop both sample the app right-side up with no shader flip.
                // (GLES2 has no GL_UNPACK_ROW_LENGTH so we repack tightly anyway.)
                for (int y = 0; y < h; y++)
                    memcpy(&gPspCapStaging[(size_t)(h - 1 - y) * w * 4],
                           src + (size_t)y * stride * 4, (size_t)w * 4);
                gPspCapW = w; gPspCapH = h;
                gPspCapFrameReady = true;
            }
            buf->unlock();
        }
        // buf (and res.buffer's ref) drops here -> gralloc reclaims the buffer.
        res = gui::ScreenCaptureResults();   // drop the results' buffer ref too
    }
    gPspCapAlive.store(false, std::memory_order_release);
    // Detached: no join. The render thread waits on gPspCapAlive before relaunch.
}

// Stop the detached worker and give it a bounded chance to exit its loop. Called
// from process teardown (~NanoMenu and the render threadLoop before stopProcess())
// so the worker is provably out of its binder call before the binder threadpool is
// torn down. This is only correct because NanoPspCaptureListener::wait() is bounded
// (<= kCapCbTimeout) - the worker can never be parked forever, so drainMs of the
// same order reliably lets it finish. Bounded busy-wait, not a join: cannot deadlock.
void NanoMenu::pspClockStopCaptureWorker(int drainMs) {
    gPspCapRun.store(false, std::memory_order_release);
    const int slices = drainMs / 5;
    for (int i = 0; i < slices && gPspCapAlive.load(std::memory_order_acquire); i++)
        usleep(5000);
}

// Whether the continuous mirror should be used (default) vs the legacy captureDisplay
// worker. persist.gammaos.nano.pspclock.nomirror=1 forces the worker (fallback / A-B).
static inline bool pspClockMirrorEnabled() {
    return !property_get_bool("persist.gammaos.nano.pspclock.nomirror", false);
}

// Render-thread. Stand up the virtual-display mirror pipeline. Returns false (and leaves
// nothing allocated) on any failure, so the caller can fall back to the capture worker.
bool NanoMenu::pspClockMirrorStart() {
    if (gMirActive) return true;
    if (mDisplayToken == nullptr || mFlingerSurfaceControl == nullptr) return false;
    int w = mWidth, h = mHeight;
    if (w < 8 || h < 8) return false;

    // Lazy-load the EGLImage entry points (the overlay instance never runs the DRM init).
    if (!pEglCreateImageKHR) {
        pEglCreateImageKHR  = (PFNEGLCREATEIMAGEKHRPROC) eglGetProcAddress("eglCreateImageKHR");
        pEglDestroyImageKHR = (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
        pGlEGLImageTargetTexture2DOES =
            (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
    }
    if (!pEglCreateImageKHR || !pEglDestroyImageKHR || !pGlEGLImageTargetTexture2DOES) {
        ALOGW("psp mirror: EGLImage entry points unavailable");
        return false;
    }

    // Producer/consumer pair. SF (the virtual display) is the producer; we consume as GPU texture.
    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferConsumer> consumer;
    BufferQueue::createBufferQueue(&producer, &consumer);
    sp<BufferItemConsumer> bic = new BufferItemConsumer(
        consumer, GraphicBuffer::USAGE_HW_TEXTURE, /*bufferCount=*/4, /*controlledByApp=*/false);
    if (bic == nullptr) return false;
    bic->setName(String8("nano-clock-mirror"));
    bic->setDefaultBufferSize((uint32_t)w, (uint32_t)h);
    bic->setDefaultBufferFormat(PIXEL_FORMAT_RGBA_8888);

    // Flag OUR overlay skip-screenshot FIRST and apply, so the mirror clone built below
    // excludes it (mirrorDisplay's clone loop skips isInternalDisplayOverlay layers). This
    // is what prevents a recursive-clock feedback loop in the backdrop.
    {
        SurfaceComposerClient::Transaction sk;
        sk.setFlags(mFlingerSurfaceControl, layer_state_t::eLayerSkipScreenshot,
                    layer_state_t::eLayerSkipScreenshot);
        sk.apply(/*synchronous=*/true);
    }

    // Resolve the physical display id backing our token (mirrorDisplay wants a DisplayId).
    std::optional<PhysicalDisplayId> physId;
    for (PhysicalDisplayId id : SurfaceComposerClient::getPhysicalDisplayIds()) {
        if (SurfaceComposerClient::getPhysicalDisplayToken(id) == mDisplayToken) { physId = id; break; }
    }
    if (!physId) {
        auto ids = SurfaceComposerClient::getPhysicalDisplayIds();
        if (!ids.empty()) physId = ids.front();
    }
    if (!physId) { ALOGW("psp mirror: no physical display id"); return false; }

    // A cloned mirror of the primary display's layers (EXCLUDES our skip-screenshot overlay
    // and any secure layers) - this is the feed we route to the virtual display.
    sp<SurfaceComposerClient> sess = session();
    sp<SurfaceControl> mirror = (sess != nullptr) ? sess->mirrorDisplay(*physId) : nullptr;
    if (mirror == nullptr) { ALOGW("psp mirror: mirrorDisplay failed"); return false; }

    // Virtual display whose OWN dedicated layer stack holds only the mirror clone, so the
    // clone is never composited onto the real panel.
    sp<IBinder> disp = SurfaceComposerClient::createDisplay(String8("nano-clock-mirror"),
                                                            /*secure=*/false);
    if (disp == nullptr) {
        SurfaceComposerClient::Transaction c; c.reparent(mirror, nullptr); c.apply();
        ALOGW("psp mirror: createDisplay failed");
        return false;
    }

    ui::LayerStack stack = ui::LayerStack::fromValue(kMirLayerStack);
    SurfaceComposerClient::Transaction t;
    t.setDisplaySurface(disp, producer);
    t.setDisplayLayerStack(disp, stack);
    t.setDisplayProjection(disp, ui::ROTATION_0, Rect(w, h), Rect(w, h));
    t.setLayerStack(mirror, stack);      // move the clone onto the vdisplay-only stack
    t.setLayer(mirror, 0x7fffffff);      // top of that stack
    t.show(mirror);
    t.apply();
    gMirLayer = mirror;

    // Minimal V-flip blit program (mirror src -> mPspClockAppTex, orientation-corrected).
    if (gMirProg == 0) {
        GLuint vs = compileShader(GL_VERTEX_SHADER, MIR_BLIT_VS);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, MIR_BLIT_FS);
        gMirProg = linkProgram(vs, fs);
        if (vs) glDeleteShader(vs);
        if (fs) glDeleteShader(fs);
        if (gMirProg) {
            gMirPos  = glGetAttribLocation(gMirProg, "aPos");
            gMirUV   = glGetAttribLocation(gMirProg, "aUV");
            gMirSamp = glGetUniformLocation(gMirProg, "uT");
            gMirFlip = glGetUniformLocation(gMirProg, "uFlip");
        }
    }
    if (gMirProg == 0) {
        SurfaceComposerClient::Transaction c;
        c.reparent(mirror, nullptr);
        c.setFlags(mFlingerSurfaceControl, 0, layer_state_t::eLayerSkipScreenshot);
        c.apply();
        SurfaceComposerClient::destroyDisplay(disp);
        ALOGW("psp mirror: blit program link failed");
        return false;
    }

    gMirDisplay  = disp;
    gMirConsumer = bic;
    gMirActive   = true;
    ALOGW("psp mirror: started (%dx%d, stack=0x%x)", w, h, kMirLayerStack);
    return true;
}

// Render-thread. Tear the mirror down and un-flag the overlay layer. Safe to call twice.
void NanoMenu::pspClockMirrorStop() {
    if (gMirHasHeld && gMirConsumer != nullptr) {
        gMirConsumer->releaseBuffer(gMirHeld, Fence::NO_FENCE);
        gMirHasHeld = false;
    }
    if (gMirCacheN > 0) {
        EGLDisplay dpy = eglGetCurrentDisplay();
        for (int i = 0; i < gMirCacheN; i++) {
            if (gMirCache[i].tex) glDeleteTextures(1, &gMirCache[i].tex);
            if (gMirCache[i].img != EGL_NO_IMAGE_KHR && pEglDestroyImageKHR)
                pEglDestroyImageKHR(dpy, gMirCache[i].img);
        }
        gMirCacheN = 0;
    }
    if (gMirDisplay != nullptr || gMirLayer != nullptr) {
        SurfaceComposerClient::Transaction t;
        if (gMirLayer != nullptr) t.reparent(gMirLayer, nullptr);   // drop the mirror clone
        if (mFlingerSurfaceControl != nullptr)
            t.setFlags(mFlingerSurfaceControl, 0, layer_state_t::eLayerSkipScreenshot);
        t.apply();
        if (gMirDisplay != nullptr) { SurfaceComposerClient::destroyDisplay(gMirDisplay); gMirDisplay = nullptr; }
        gMirLayer = nullptr;
    }
    if (gMirConsumer != nullptr) { gMirConsumer->abandon(); gMirConsumer = nullptr; }
    if (gMirFlipFbo) { glDeleteFramebuffers(1, &gMirFlipFbo); gMirFlipFbo = 0; }
    // Throttled backdrop-blur copy: drop it so the next summon recomputes from frame 0.
    if (gBdBlurTex) { glDeleteTextures(1, &gBdBlurTex); gBdBlurTex = 0; }
    if (gBdBlurFbo) { glDeleteFramebuffers(1, &gBdBlurFbo); gBdBlurFbo = 0; }
    gBdBlurW = gBdBlurH = 0; gBdValid = false; gBdTick = 0;
    gMirActive = false;
}

// Render-thread. Import a mirror GraphicBuffer straight to a GL texture (no copy) and
// V-flip-blit it into mPspClockAppTex, which the rest of the clock samples unchanged.
void NanoMenu::pspClockMirrorImportAndBlit(const sp<GraphicBuffer>& buf) {
    if (buf == nullptr) return;
    const int w = (int)buf->getWidth(), h = (int)buf->getHeight();
    if (w < 8 || h < 8) return;
    EGLDisplay dpy = eglGetCurrentDisplay();

    // Import this buffer ONCE (keyed by its id) and reuse the texture on later frames.
    const uint64_t id = buf->getId();
    GLuint srcTex = 0;
    for (int i = 0; i < gMirCacheN; i++) if (gMirCache[i].id == id) { srcTex = gMirCache[i].tex; break; }
    if (srcTex == 0) {
        EGLint attrs[] = { EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE };
        EGLImageKHR img = pEglCreateImageKHR(dpy, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
                                             (EGLClientBuffer)buf->getNativeBuffer(), attrs);
        if (img == EGL_NO_IMAGE_KHR) return;
        glGenTextures(1, &srcTex);
        glBindTexture(GL_TEXTURE_2D, srcTex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        pGlEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)img);
        if (glGetError() != GL_NO_ERROR) {
            glDeleteTextures(1, &srcTex);
            pEglDestroyImageKHR(dpy, img);
            return;
        }
        if (gMirCacheN >= (int)(sizeof(gMirCache) / sizeof(gMirCache[0]))) {
            // Full (buffer set churned): evict the oldest entry.
            glDeleteTextures(1, &gMirCache[0].tex);
            pEglDestroyImageKHR(dpy, gMirCache[0].img);
            for (int i = 1; i < gMirCacheN; i++) gMirCache[i - 1] = gMirCache[i];
            gMirCacheN--;
        }
        gMirCache[gMirCacheN++] = { id, img, srcTex };
    } else {
        glBindTexture(GL_TEXTURE_2D, srcTex);
    }

    // (Re)allocate mPspClockAppTex to the buffer size and attach it to the flip FBO.
    if (mPspClockAppTex == 0) glGenTextures(1, &mPspClockAppTex);
    if (w != mPspClockAppTexW || h != mPspClockAppTexH) {
        glBindTexture(GL_TEXTURE_2D, mPspClockAppTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        mPspClockAppTexW = w; mPspClockAppTexH = h;
    }
    // Snapshot the GL state this helper changes so it leaves it EXACTLY as found. The blit
    // runs mid-frame before the backdrop/entrance passes; leaking a disabled blend makes the
    // entrance icons/glyphs render opaque (white squares), and leaking a logical-sized viewport
    // blanks the rotated surround. Restore all three on exit.
    GLint prevFbo = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint prevVp[4]; glGetIntegerv(GL_VIEWPORT, prevVp);
    GLboolean prevBlend = glIsEnabled(GL_BLEND);
    if (gMirFlipFbo == 0) glGenFramebuffers(1, &gMirFlipFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, gMirFlipFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mPspClockAppTex, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
        return;
    }
    glViewport(0, 0, w, h);
    glDisable(GL_BLEND);
    glUseProgram(gMirProg);
    // Fullscreen quad (triangle strip). Default flip=1 (SF buffer is display-top-first;
    // downstream expects upright). Live-tunable so orientation can be corrected on-device.
    static const GLfloat pos[] = { -1,-1,  1,-1, -1, 1,  1, 1 };
    static const GLfloat uv[]  = {  0, 0,  1, 0,  0, 1,  1, 1 };
    glVertexAttribPointer(gMirPos, 2, GL_FLOAT, GL_FALSE, 0, pos);
    glEnableVertexAttribArray(gMirPos);
    glVertexAttribPointer(gMirUV, 2, GL_FLOAT, GL_FALSE, 0, uv);
    glEnableVertexAttribArray(gMirUV);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, srcTex);
    glUniform1i(gMirSamp, 0);
    glUniform1f(gMirFlip, property_get_bool("persist.gammaos.nano.pspclock.mirrorflip", true) ? 1.0f : 0.0f);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(gMirPos);
    glDisableVertexAttribArray(gMirUV);
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    if (prevBlend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
    mPspClockAppTexValid = true;
}

// Render-thread. Lifecycle + per-frame pump for the continuous mirror. Drains to the
// NEWEST queued frame (dropping older ones), imports it zero-copy, holds it one frame for
// GPU-read safety, then releases it on the next pump.
void NanoMenu::pspClockMirrorTick(bool want) {
    if (want && !gMirActive) {
        if (!pspClockMirrorStart()) { gMirFailed = true; return; }
        gMirFailed = false;
    } else if (!want && gMirActive) {
        pspClockMirrorStop();
        return;
    }
    if (!gMirActive || gMirConsumer == nullptr) return;

    // Release the buffer we held last frame (its GPU read has completed by now).
    if (gMirHasHeld) {
        gMirConsumer->releaseBuffer(gMirHeld, Fence::NO_FENCE);
        gMirHasHeld = false;
    }
    // Drain to the newest available buffer (mirror runs at SF's 60fps; keep only the latest).
    BufferItem item, newest; bool have = false;
    while (gMirConsumer->acquireBuffer(&item, 0, /*waitForFence=*/false) == NO_ERROR) {
        if (have) gMirConsumer->releaseBuffer(newest, Fence::NO_FENCE);
        newest = item; have = true;
    }
    if (!have) return;                       // no new frame this tick; keep the last texture
    if (newest.mFence != nullptr && newest.mFence != Fence::NO_FENCE)
        newest.mFence->wait(kCapFenceMs);
    pspClockMirrorImportAndBlit(newest.mGraphicBuffer);
    gMirHeld = newest; gMirHasHeld = true;   // hold one frame for GPU-read safety
}

// Render-thread ONLY. Starts/stops the detached worker to match the gate, refreshes
// the worker's binder inputs each frame (so a rotation/relaunch cannot leave a stale
// exclude handle -> self-capture, or a stale display token), and uploads the latest
// CPU frame into mPspClockAppTex. The mutex is held only for a pointer swap + scalars
// (microseconds) - NEVER across a binder call, a GL call, or a SW buffer lock, so it
// cannot stall the render thread into the ~8s watchdog.
void NanoMenu::pspClockAppCaptureTick() {
    // Diagnostic kill-switch: force the live-app SF capture OFF to A/B whether the
    // full-display captureDisplay is what stalls the game. persist.gammaos.nano.pspclock.nocap=1.
    const bool want = pspClockLiveAppEnabled()
                   && !property_get_bool("persist.gammaos.nano.pspclock.nocap", false)
                   && ((mOverlayMode && !mOverlayWallpaper) || pspClockAppSrcDebug());

    // Preferred path: the continuous virtual-display mirror (live 60fps, zero-copy). It
    // supersedes the captureDisplay worker below. If the mirror fails to start once we
    // fall through to the worker (never a hard black), and nomirror=1 forces the worker.
    if (pspClockMirrorEnabled() && !gMirFailed) {
        // The mirror owns live-app capture; make sure the legacy worker is fully stopped.
        if (mPspClockCaptureRunning) {
            gPspCapRun.store(false, std::memory_order_release);
            mPspClockCaptureRunning = false;
        }
        pspClockMirrorTick(want);
        return;   // mirror path handles the whole lifecycle for this frame
    }
    // Mirror disabled or failed to start: tear it down (if up) and run the worker path.
    if (gMirActive) pspClockMirrorStop();

    // ---- collect the current nano-owned layer handles (render thread) ----
    // Exclude the primary overlay layer AND every secondary wallpaper control that
    // may be on the captured display, so the capture is strictly the app underneath.
    // Recomputed every frame -> survives layer recreation on rotation/display change.
    auto collectExcludes = [&]() {
        std::vector<sp<IBinder>> h;
        if (mFlingerSurfaceControl != nullptr) {
            sp<IBinder> ph = mFlingerSurfaceControl->getHandle();
            if (ph != nullptr) h.push_back(ph);
        }
        for (const sp<SurfaceControl>& sc : mSecondaryWallpaperControls) {
            if (sc != nullptr) {
                sp<IBinder> sh = sc->getHandle();
                if (sh != nullptr) h.push_back(sh);
            }
        }
        return h;
    };

    // ---- lifecycle: start ----
    if (want && !mPspClockCaptureRunning) {
        // Only launch if no prior worker is still alive (join-free single-owner guard).
        if (!gPspCapAlive.load(std::memory_order_acquire) && mDisplayToken != nullptr) {
            std::vector<sp<IBinder>> excl = collectExcludes();
            {
                std::lock_guard<std::mutex> lk(gPspCapMutex);
                gPspCapFrameReady    = false;          // discard any stale frame
                gPspCapDisplayToken  = mDisplayToken;  // already the primary display token
                gPspCapExcludeHandles = std::move(excl);
                gPspCapWantW = 0; gPspCapWantH = 0;    // full display; lens samples a disc from it
            }
            gPspCapRun.store(true, std::memory_order_release);
            std::thread(&NanoMenu::pspClockCaptureWorker).detach();
            mPspClockCaptureRunning = true;
        }
    } else if (want && mPspClockCaptureRunning) {
        // Running: refresh the exclude set + token in case the layer/display changed.
        std::vector<sp<IBinder>> excl = collectExcludes();
        std::lock_guard<std::mutex> lk(gPspCapMutex);
        gPspCapDisplayToken   = mDisplayToken;
        gPspCapExcludeHandles = std::move(excl);
    }

    // ---- lifecycle: stop (gate dropped) ----
    if (!want && mPspClockCaptureRunning) {
        gPspCapRun.store(false, std::memory_order_release);   // worker exits on its own
        mPspClockCaptureRunning = false;
        // Keep mPspClockAppTex (last frame) so a brief gate flicker does not flash the
        // disc back to the wave; freed in pspClockReleaseGL / on process exit.
    }

    // ---- upload the newest CPU frame (if any) ----
    // Retained render-thread scratch: we swap it with the staging vector under the
    // mutex (O(1), no copy) and upload OUTSIDE the lock so the worker can produce the
    // next frame in parallel. Swapping (not clearing) hands the worker back a buffer
    // with capacity, avoiding a per-frame realloc on its side.
    static std::vector<uint8_t> sUpload;
    bool haveNew = false; int uw = 0, uh = 0;
    {
        std::lock_guard<std::mutex> lk(gPspCapMutex);
        if (gPspCapFrameReady && gPspCapW > 0 && gPspCapH > 0) {
            uw = gPspCapW; uh = gPspCapH;
            sUpload.swap(gPspCapStaging);   // O(1) hand-off (staging keeps sUpload's capacity)
            gPspCapFrameReady = false;
            haveNew = true;
        }
    }   // mutex released BEFORE the GL upload

    if (haveNew && (size_t)uw * uh * 4 <= sUpload.size()) {
        if (mPspClockAppTex == 0) glGenTextures(1, &mPspClockAppTex);
        glBindTexture(GL_TEXTURE_2D, mPspClockAppTex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        if (uw != mPspClockAppTexW || uh != mPspClockAppTexH) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, uw, uh, 0,
                         GL_RGBA, GL_UNSIGNED_BYTE, sUpload.data());
            mPspClockAppTexW = uw; mPspClockAppTexH = uh;
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } else {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, uw, uh,
                            GL_RGBA, GL_UNSIGNED_BYTE, sUpload.data());
        }
        mPspClockAppTexValid = true;
    }
}

// -----------------------------------------------------------------------------
// Per-frame orchestrator. Called at the tail of renderPs3Xmb() with dt in ms.
// Advances the reveal scalar and runs the enabled passes.
// -----------------------------------------------------------------------------
void NanoMenu::drawPspClock(float dtMs) {
    if (!mPs3Xmb) return;                    // PS3 XMB mode only; never touch DSi
    pspClockPollInput();
    // Gyro/accel parallax: sample the tilt while the clock is up (disables the sensor and
    // eases the offset back to centre once it is fully closed).
    pspClockPollTilt(mPspClockReveal > 0.001f || mPspClockOn);
    // Feed the reveal to the shared wave renderer for the transition surge (spec
    // 5.6). Always set (0 when closed) so the surge is a clean no-op off-clock.
    ps3bg::setClockWaveSurge(mPspClockReveal);

    // 60fps present mode. On the force-SF present path (SF window surface, NOT the
    // overlay instance, which manages its own swap mode) eglSwapBuffers defaults to
    // interval 1: a frame a hair over 16.7ms stalls to the next vsync = 33ms = 30fps
    // (see the surface-setup pacing comment). The clock's passes exceed 16.7ms, so
    // switch to interval 0 while it is open - SurfaceFlinger still latches at its own
    // vsync, so no tearing - and restore interval 1 for the light static home. Only
    // on transitions. Harmless on DRM-direct (present is the page flip, not the swap).
    if (!mOverlayMode) {
        int want = (mPspClockEnabled && (mPspClockOn || mPspClockReveal > 0.0f)) ? 0 : 1;
        if (want != mPspPresentBoosted) {
            eglSwapInterval(mDisplay, want);
            mPspPresentBoosted = want;
        }
    }

    if (!mPspClockEnabled) {                 // feature off: park and bail
        if (mPspClockReveal != 0.0f || mPspClockOn) {
            mPspClockOn = false; mPspClockReveal = 0.0f;
            mPspDescent = -1.0f; mPspTextFadeSmooth = 1.0f; mPspDetailFade = 0.0f;
        }
        // Clock disabled entirely: make sure the live-app capture worker is stopped.
        if (mPspClockCaptureRunning) {
            gPspCapRun.store(false, std::memory_order_release);
            mPspClockCaptureRunning = false;
        }
        if (gMirActive) pspClockMirrorStop();   // and tear the mirror down
        return;
    }

    if (!mPspClockOn && mPspClockReveal <= 0.0f) {
        // Parked (closed): reset the smoothed followers for a clean next open.
        mPspDescent = -1.0f; mPspTextFadeSmooth = 1.0f; mPspDetailFade = 0.0f;
        // Fully closed: stop the live-app capture worker (we early-return here, so
        // pspClockAppCaptureTick's gate-drop branch is never reached on the close).
        if (mPspClockCaptureRunning) {
            gPspCapRun.store(false, std::memory_order_release);
            mPspClockCaptureRunning = false;
        }
        if (gMirActive) pspClockMirrorStop();   // and tear the mirror down (release the vdisplay)
        // Standalone summon fully retracted: lower the overlay WE raised (the framework
        // left show_overlay=1 through the retract so this animates fully first). Clearing
        // standalone first lets overlayPoll's hide guard pass; it does the actual overlayHide.
        if (mPspClockStandalone) {
            mPspClockStandalone = false;
            if (mPspClockRaisedOverlay) {
                mPspClockRaisedOverlay = false;
                property_set("sys.gammaos.nano.pspclock_summon", "0");
                property_set("sys.gammaos.nano.show_overlay", "0");
            }
        } else if (!mOverlayMode) {
            // Non-overlay home that serviced the summon IN-PLACE (no resident --overlay
            // instance, e.g. the TrimUI Brick's SF-composited home - see the park-gate
            // comment in NanoMenu.cpp threadLoop). The framework raised show_overlay for
            // the summon and cleared pspclock_summon on release; lower show_overlay now
            // that the clock has fully retracted so WindowManager stops treating this home
            // as an active overlay and the park gate returns to its normal state. Idempotent
            // and harmless on the DRM-direct home (show_overlay was never raised there).
            property_set("sys.gammaos.nano.show_overlay", "0");
        }
        return;
    }

    if (dtMs <= 0.0f) dtMs = 16.0f;

    // Reveal advance: open 5000 ms, close 2700 ms (spec deviation D2). F12-down sets
    // mPspClockOn (ramp up), F12-up clears it (ramp down); everything downstream is a
    // pure function of this scalar so a mid-transition flip just reverses it.
    {
        const float dur = mPspClockOn ? 5000.0f : 2700.0f;
        const float step = dtMs / dur;
        mPspClockReveal += mPspClockOn ? step : -step;
        mPspClockReveal = clamp01(mPspClockReveal);
    }

    // Disc-drop window: reveal 0.30 .. 1.0 (spec deviation D3).
    const float clockReveal = clamp01((mPspClockReveal - 0.3f) / 0.7f);

    // Smoothed text-fade follower (spec 5.4). Target: on open gone by 0.07, on
    // close back in over 0.16..0. 45 ms time constant so an F12 flip eases.
    float textTarget;
    if (mPspClockReveal <= 0.0f) textTarget = 1.0f;
    else if (mPspClockOn) textTarget = std::max(0.0f, 1.0f - mPspClockReveal / 0.07f);
    else textTarget = clamp01((0.16f - mPspClockReveal) / 0.16f);
    const float kSmooth = 1.0f - expf(-dtMs / 45.0f);
    mPspTextFadeSmooth += (textTarget - mPspTextFadeSmooth) * kSmooth;

    // Idle float phase (amplitude 3 px, period 3.6 s; spec 5.13).
    mPspFloatT += dtMs;

    if (mPspClockReveal <= 0.0f) return;     // fully closed after this frame

    // Disc geometry from the scale rule (spec 5.13/8): sc2 = min(H/272, W/288),
    // W/288 = 2*141+6. Same rule the face uses so the glass and face land together.
    const float W = (float)mWidth, H = (float)mHeight;
    const float sc2 = std::min(H / 272.0f, W / 288.0f);
    const float ox = (W - 480.0f * sc2) * 0.5f, oy = (H - 272.0f * sc2) * 0.5f;
    const float floatY = 3.0f * sinf(mPspFloatT / 1000.0f * 2.0f * (float)M_PI / 3.6f);
    // Descent: OPEN = buoyant easeOutBack-1 (c=1.4, sinks ~23px then bobs up),
    // CLOSE = linear lift. Smoothed 45ms so an F12 flip eases (spec 5.12/5.13).
    float descentTarget;
    if (mPspClockOn) {
        const float r = clockReveal;
        if (r <= 0.0f) descentTarget = -1.0f;
        else if (r >= 1.0f) descentTarget = 0.0f;
        else { const float c = 1.4f, u = r - 1.0f; descentTarget = (c + 1.0f)*u*u*u + c*u*u; }
    } else {
        descentTarget = -(1.0f - clockReveal);
    }
    mPspDescent += (descentTarget - mPspDescent) * kSmooth;
    const float descentPx = mPspDescent * (272.0f + 50.0f) * sc2;
    mPspLensCx = ox + 240.0f * sc2;
    mPspLensCy = oy + (136.0f + floatY) * sc2 + descentPx;
    mPspLensR  = 141.0f * sc2;
    mPspLensValid = (clockReveal > 0.0f);

    // Detail fade: trail/ticks/ambient glyphs only once settled (in 320ms / out 140ms).
    {
        const bool settled = mPspClockOn && clockReveal > 0.9f;
        const float rate = dtMs / (settled ? 320.0f : 140.0f);
        mPspDetailFade = clamp01(mPspDetailFade + (settled ? rate : -rate));
    }

    // Second-hand comet trail (spec 1.3/4.5): decay 0.98 per 50ms; element sec*2
    // forced 1.0, sec*2+1 forced 1.0 once >50ms in.
    {
        int h, m, s; float sub; pspNow(h, m, s, sub);
        int i0 = s*2, i1 = s*2+1;
        float f = powf(PSP_DECAY, dtMs / PSP_FRAME_MS);
        float usf = sub * 1e6f;
        for (int i = 0; i < 120; i++) {
            if (i == i0) mPspTrail[i] = 1.0f;
            else if (i == i1) { if (usf > 50000.0f) mPspTrail[i] = 1.0f; else mPspTrail[i] *= f; }
            else mPspTrail[i] *= f;
        }
    }

    // --- passes (built up stage by stage) ---
    // Bake the numeral textures BEFORE the render passes (mid-frame texture
    // allocation flushes/loses the Mali tile, which was erasing the blur+disc).
    if (!mPspGlyphBaked) pspClockBakeGlyphs();
    // Sample the dominant wallpaper colour for the glow (throttled ~7Hz). Done
    // before the visible passes so its FBO switch cannot flush the clock's tile.
    { static int sG = 0; if ((sG++ % 8) == 0) pspClockSampleGlow(); }
    pspClockAppCaptureTick();                 // #5: pump live-app capture -> mPspClockAppTex
    // #5 dynamic darkening: sample the live-app mean brightness (~7Hz) so a bright
    // game dims the disc + darkens the surround. Before the visible passes (FBO switch).
    if (pspClockUseAppSource()) { static int sD = 0; if ((sD++ % 8) == 0) pspClockSampleAppDim(); }
    // Per-pass GPU profiling (persist.gammaos.nano.pspclock.prof=1). glFinish-brackets each
    // stage so the accumulated us pinpoint where the GPU time goes; glFinish serializes the
    // pipeline so absolute totals read a little high, but the RELATIVE split is what guides
    // optimization. Averaged and logged ~once a second. No-op (no glFinish) when the prop is off.
    const bool prof = pspClockProfEnabled();
    auto profNs = []() -> int64_t {
        struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
        return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
    };
    static double sPBd = 0, sPLens = 0, sPEnt = 0, sPFace = 0; static int sPN = 0;
    static int64_t sPLastLog = 0;
    int64_t tBd0 = prof ? (glFinish(), profNs()) : 0;

    pspClockBackdropBlur(clockReveal);       // stage 1
    int64_t tLens0 = prof ? (glFinish(), profNs()) : 0;
    pspClockLens(clockReveal);               // stage 2
    int64_t tEnt0 = prof ? (glFinish(), profNs()) : 0;

    // Stage 5: entrance explosion (after the lens so glyphs behind the disc get the
    // lens bow; before the face). THREE staggered burst copies + TWO icon streams +
    // ambient, all additive (spec 5.13). Envelope ends midway through the drop (0.65).
    // glEnable is REQUIRED, not just glBlendFunc: on the mirror path pspClockLens early-returns
    // during the entrance (before its setUiBlend) so blend can still be OFF here, which made
    // the icon/glyph quads render opaque (white squares). Own the full blend state.
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE);
    {
        const float BURST_P = 0.7f;
        float burstEnv = clamp01((0.65f - mPspClockReveal) / 0.16f);
        auto burstFed = [&](float delay){ float r = mPspClockReveal - delay; return r > 0 ? fmodf(r/BURST_P, 1.0f)*0.87f : 0.0f; };
        pspClockEntrance(sc2, ox, oy, std::min(1.0f, burstFed(0.0f)),            0.0f,  burstEnv);
        pspClockEntrance(sc2, ox, oy, std::min(1.0f, burstFed(BURST_P/3.0f)),    0.42f, burstEnv);
        pspClockEntrance(sc2, ox, oy, std::min(1.0f, burstFed(2.0f*BURST_P/3.0f)),0.84f, burstEnv);
        const float ICON_P = 0.62f;
        float iconFedA = fmodf(mPspClockReveal, ICON_P) * 0.87f;
        float rIB = mPspClockReveal - ICON_P/2.0f;
        float iconFedB = rIB > 0 ? fmodf(rIB, ICON_P)*0.87f : 0.0f;
        int iconCycleA = (int)(mPspClockReveal / ICON_P);
        int iconCycleB = rIB > 0 ? (int)(rIB / ICON_P) : 0;
        pspClockEntranceIcons(sc2, ox, oy, iconFedA, burstEnv, mPspIconSeed + iconCycleA*2);
        pspClockEntranceIcons(sc2, ox, oy, iconFedB, burstEnv, mPspIconSeed + iconCycleB*2 + 1);
        pspClockAmbientGlyphs(dtMs);
    }
    setUiBlend();
    int64_t tFace0 = prof ? (glFinish(), profNs()) : 0;

    pspClockFace(clockReveal, floatY, descentPx);  // stage 3/4

    if (prof) {
        int64_t tEnd = (glFinish(), profNs());
        sPBd   += (tLens0 - tBd0)   / 1000.0;
        sPLens += (tEnt0  - tLens0) / 1000.0;
        sPEnt  += (tFace0 - tEnt0)  / 1000.0;
        sPFace += (tEnd   - tFace0) / 1000.0;
        sPN++;
        if (sPLastLog == 0) sPLastLog = tEnd;
        if (tEnd - sPLastLog > 1000000000LL && sPN > 0) {
            double n = (double)sPN;
            double bd = sPBd/n, ln = sPLens/n, en = sPEnt/n, fc = sPFace/n;
            double tot = bd + ln + en + fc;
            ALOGI("pspclock prof (us/frame, %d frm, reveal=%.2f): backdrop=%.0f lens=%.0f entrance=%.0f FACE=%.0f | sum=%.0f (%.1ffps if GPU-bound)",
                  sPN, mPspClockReveal, bd, ln, en, fc, tot, tot > 0 ? 1e6/tot : 0.0);
            sPBd = sPLens = sPEnt = sPFace = 0; sPN = 0; sPLastLog = tEnd;
        }
    }
}

// -----------------------------------------------------------------------------
// Stage 1: backdrop blur + darken outside the disc (spec 5.10 / deviation D9).
// The SAME defocus as Settings submenu dialogs: blur the composited bg+wave and
// blit it full-screen, then a mild darken. (The disc will be drawn as a crisp
// opaque stamp on top in a later stage, which masks the hole - no stencil.)
// -----------------------------------------------------------------------------
// Render-thread. Copy the freshly-computed mGlassBlurTex into the persistent gBdBlurTex
// (reusing the mirror's straight-copy program, uFlip=0) so the throttled frost blit can
// re-read it on skipped frames without the chrome glow having clobbered mGlassBlurTex.
void NanoMenu::pspClockCopyBlurToBackdrop() {
    if (gMirProg == 0 || mGlassBlurTex == 0 || mGlassBlurW <= 0 || mGlassBlurH <= 0) return;
    if (gBdBlurTex == 0 || gBdBlurW != mGlassBlurW || gBdBlurH != mGlassBlurH) {
        if (gBdBlurTex == 0) glGenTextures(1, &gBdBlurTex);
        glBindTexture(GL_TEXTURE_2D, gBdBlurTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mGlassBlurW, mGlassBlurH, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        gBdBlurW = mGlassBlurW; gBdBlurH = mGlassBlurH;
    }
    if (gBdBlurFbo == 0) glGenFramebuffers(1, &gBdBlurFbo);
    // Snapshot the state this helper changes and restore it exactly. Leaking a disabled blend
    // makes the later darken drawQuad write OPAQUE black (blanking the surround); leaking a
    // logical-sized viewport miscovers the rotated surround. Both showed as the backdrop flicker.
    GLint prevFbo = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint prevVp[4]; glGetIntegerv(GL_VIEWPORT, prevVp);
    GLboolean prevBlend = glIsEnabled(GL_BLEND);
    glBindFramebuffer(GL_FRAMEBUFFER, gBdBlurFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, gBdBlurTex, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
        glViewport(0, 0, mGlassBlurW, mGlassBlurH);
        glDisable(GL_BLEND);
        glUseProgram(gMirProg);
        static const GLfloat pos[] = { -1,-1,  1,-1, -1, 1,  1, 1 };
        static const GLfloat uv[]  = {  0, 0,  1, 0,  0, 1,  1, 1 };
        glVertexAttribPointer(gMirPos, 2, GL_FLOAT, GL_FALSE, 0, pos);
        glEnableVertexAttribArray(gMirPos);
        glVertexAttribPointer(gMirUV, 2, GL_FLOAT, GL_FALSE, 0, uv);
        glEnableVertexAttribArray(gMirUV);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mGlassBlurTex);
        glUniform1i(gMirSamp, 0);
        glUniform1f(gMirFlip, 0.0f);   // straight copy, no V-flip
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glDisableVertexAttribArray(gMirPos);
        glDisableVertexAttribArray(gMirUV);
    }
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    if (prevBlend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
}

void NanoMenu::pspClockBackdropBlur(float amt) {
    if (amt <= 0.0f) return;
    // #5 live-app backdrop: when the disc is refracting the running app, the SURROUND
    // (outside the disc) shows a BLURRED + DARKENED view of that same live app instead of
    // the flat black scrim - a defocused peek at what is behind the glass. Blur the captured
    // app (already stored UPRIGHT) and blit it full-screen untonemapped (it is display sRGB,
    // not the LINEAR wave), then a dynamic darken over it (heavier when the game is bright).
    if (pspClockUseAppSource() && mPspClockAppTex != 0
        && mPspClockAppTexW >= 8 && mPspClockAppTexH >= 8) {
        // The surround defocus is the single most expensive pass, yet it is heavily blurred
        // AND darkened AND has NO parallax (only the disc, which samples the app texture
        // directly, moves with tilt). So recompute blurGlassChain only every Nth frame and
        // copy it into a dedicated texture the frost blit reads every frame - 30Hz here is
        // visually identical to 60Hz and frees the budget to keep the whole overlay at 60fps.
        // The dedicated copy is what makes this safe: the chrome-glow pass later this frame
        // reuses blurGlassChain and clobbers the shared mGlassBlurTex, so a skipped frame that
        // re-read mGlassBlurTex would flash the glow halo (the old throttle's bug). div<=1
        // disables it. Only throttle when the mirror is up (its blit program does the copy).
        int div = (int)property_get_int32("persist.gammaos.nano.pspclock.blurdiv", 2);
        if (div < 1) div = 1; if (div > 4) div = 4;
        const bool canThrottle = (div > 1) && (gMirProg != 0);
        bool useBackdropTex = false;
        if (!canThrottle) {
            blurGlassChain(mPspClockAppTex, mPspClockAppTexW, mPspClockAppTexH, 3, 2);
        } else {
            const bool recompute = !gBdValid || (gBdTick % div) == 0;
            gBdTick++;
            if (recompute) {
                blurGlassChain(mPspClockAppTex, mPspClockAppTexW, mPspClockAppTexH, 3, 2);
                if (mGlassBlurTex != 0 && mGlassBlurW > 0 && mGlassBlurH > 0) {
                    pspClockCopyBlurToBackdrop();   // mGlassBlurTex -> gBdBlurTex (persistent)
                    gBdValid = true;
                }
            }
            useBackdropTex = gBdValid;
        }
        // tintA = 1 (OPAQUE), NOT 0 like the home-clock wave path. In the overlay
        // in-game path the framebuffer is cleared to a translucent scrim and blending
        // is ON (NanoMenuRender ~L4401/4411), and SurfaceFlinger composites this layer
        // over the live app by its ALPHA channel. A tintA=0 frost outputs alpha 0, so
        // under blend it writes NOTHING (the app surround stayed black). tintA=1 makes
        // the blurred app opaque (alpha = fade = amt), so SF shows OUR blurred+darkened
        // app in the surround, not the raw live app / black. (The home wave path draws
        // into an opaque FB with blend off, where the alpha is ignored - it keeps 0.)
        drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f,
                         1.0f, 1.0f, 1.0f, 1.0f, amt, /*waveSpace=*/true, /*tonemapOverride=*/0.0f,
                         useBackdropTex ? gBdBlurTex : 0, gBdBlurW, gBdBlurH);
        drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f,
                 mPspAppBackdropDark * amt);
        return;
    }
    // Capture the pre-composited gradient+wave (LINEAR) and blur it. This is the
    // same source the submenu frost uses; captureGlassFromWave leaves the result
    // in mGlassBlurTex for drawFrostedGlass to tent-upsample. THROTTLE the blur
    // recompute to ~15Hz (every 4th frame); mGlassBlurTex persists, so the blit
    // still runs every frame - the defocus does not need per-frame freshness and
    // the full-screen capture+downsample is the main cost (spec 8 perf note).
    {
        // Recompute the wave defocus EVERY frame while the clock is open. It used to be
        // throttled to ~15Hz relying on mGlassBlurTex persisting, but the clock-chrome
        // soft glow (pspClockChromeGlowPass, later this frame) now reuses blurGlassChain
        // and clobbers that shared blur state, so a stale throttle would blit the glow
        // halo as the backdrop. The wave FBO downsample is cheap (foreground-only mode).
        // Heavier blur than the shared submenu frost (captureGlassFromWave uses 2 down-
        // levels/no Gaussian): the clock backdrop wants the wave collapsed into a soft
        // defocus, so blur the wave FBO through 3 down-levels (1/8 res) + 2 separable
        // Gaussian passes directly (user: "increase the blur further").
        GLuint wt = ps3bg::workTex();
        if (wt == 0) return;
        int fw = (int)(ps3::gFrameW + 0.5f), fh = (int)(ps3::gFrameH + 0.5f);
        if (fw < 8 || fh < 8) return;
        blurGlassChain(wt, fw, fh, 3, 2);
    }
    // Full-screen frosted blit (waveSpace maps texcoords to the wave FBO). radius
    // 0 = plain rect, neutral tint, fade = amt so the defocus ramps in on open.
    drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f,
                     1.0f, 1.0f, 1.0f, 0.0f, amt, /*waveSpace=*/true);
    // Defocus darken. The web uses rgba(0,0,0,0.30*amt); lifted to 0.55 (user: "darken
    // outside the clock face further") so the blurred surround reads clearly darker
    // than the crisp zoomed disc. Ramps with amt=clockReveal (0..1) over the drop, same
    // envelope as the blur and the lens.
    drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, 0.55f * amt);
}

// -----------------------------------------------------------------------------
// Stage 2: the glass refraction disc (spec 5.9). The web precomputes a radial
// displacement map and JS-bilinear-refracts the bg+wave through it; here it is an
// ANALYTIC fragment shader sampling ps3bg::workTex. The face is a zoom-IN
// (FACE_ZOOM 1.35, deeper than the web's 1.1 so the disc reads as a clearly
// magnified crisp window vs the blurred surround) with an identity interior and
// only the outer ~2% bevel band
// bending outward (M=1, BEVEL=0.98, EDGE=1.10, P=1.8). Drawn as a crisp opaque
// stamp over the punched blur (no stencil needed, EGL has none). Plus a thin
// bright bevel rim + a faint facet line.
static const char PSP_LENS_VS[] = R"(
    attribute vec2 aPosition;
    attribute vec2 aLocal;
    attribute vec2 aTexCoord;
    uniform mat2 uRotation;
    varying vec2 vLocal;
    varying vec2 vTex;
    void main() {
        gl_Position = vec4(uRotation * aPosition, 0.0, 1.0);
        vLocal = aLocal;
        vTex = aTexCoord;
    }
)";
static const char PSP_LENS_FS[] = R"(
    precision mediump float;
    varying vec2 vLocal;
    varying vec2 vTex;
    uniform vec2  uHalf;      // disc half-size in local px (R,R)
    uniform vec2  uCenter;    // disc centre in workTex UV
    uniform float uZoom;      // FACE_ZOOM (>1 = magnify in; 1.35 here, deeper than web 1.1)
    uniform float uTonemap;   // exp2 tonemap of the LINEAR workTex
    uniform float uAlpha;     // lens opacity (fades in over the drop)
    uniform vec2  uTilt;      // gyro/accel parallax: UV shift of the sampled bg (peek behind)
    uniform float uAppSrc;    // #5: >0 = source is the captured LIVE app; carries the dim factor
    uniform sampler2D uTex;
    void main() {
        vec2 n = vLocal / uHalf;          // normalized disc coords, |n|=1 at rim
        float t = length(n);
        if (t > 1.0) discard;             // outside the disc -> blur shows
        const float BEVEL = 0.98, EDGE = 1.10, P = 1.8;
        const float baseSrc = 0.98;       // BEVEL/M, M=1
        float srcT;
        if (t <= BEVEL) srcT = t;
        else { float u = (t - BEVEL) / (1.0 - BEVEL); srcT = baseSrc + pow(u, P) * (EDGE - baseSrc); }
        float scale = (srcT / max(t, 1e-4)) / uZoom;
        // Refraction is radial, so scale the actual UV vector to this pixel.
        vec2 uv = uCenter + (vTex - uCenter) * scale;
        // Gyro/accel parallax: tilt PANS the whole sampled background as one, so the glass
        // reads as a window over a bg plane behind it and the view slides toward the tilt.
        // The shift MUST be uniform (same offset at every t): a t-varying weight moves the
        // rim more than the centre, which distorts/WARPS the view instead of moving it. So
        // add uTilt flat - the interior translates cleanly, no warp.
        uv += uTilt;
        uv = clamp(uv, 0.0, 1.0);
        vec3 c = texture2D(uTex, uv).rgb;
        if (uTonemap > 0.0) c = vec3(1.0) - exp2(-c * uTonemap);
        // Dynamically dim the live app so the white clock face/hands stay readable over a
        // bright game. uAppSrc carries the dim FACTOR: 0 = wave source (no dim), >0 = app
        // shown at that fraction (smaller when the game is brighter). The app texture is
        // stored upright (row-reversed at upload), so no flip here. The wave needs no dim.
        if (uAppSrc > 0.001) c *= uAppSrc;
        // Frosted glass sheen (web body radial gradient, psp_clock.js draw sect 1):
        // the centre stays clear so the bg reads through, a soft WHITE haze builds
        // from ~0.5 outward to a light frost just inside the rim - the "frosted white
        // sheen" the glass face has. Additive so the wallpaper colour tints it.
        float haze = smoothstep(0.5, 0.95, t) * 0.09;
        c += vec3(1.0) * haze;
        // Bevel rim: thin bright specular right at the edge (web body stops at
        // (R-3)/R white 0.20). A broad faint inner highlight + a crisp edge glint.
        float rim = smoothstep(0.955, 0.992, t) * (1.0 - smoothstep(0.992, 1.0, t));
        c += vec3(1.0) * (0.18 * rim);
        gl_FragColor = vec4(c, uAlpha);
    }
)";

void NanoMenu::pspClockLens(float cr) {
    if (cr <= 0.0f || !mPspLensValid) return;
    // Source select (#5): sample the captured LIVE app when there is one behind us
    // (overlay scrim / debug appsrc) and we hold a frame; otherwise the XMB wave.
    const bool useApp = pspClockUseAppSource();
    const GLuint srcTex = useApp ? mPspClockAppTex : ps3bg::workTex();
    if (srcTex == 0) return;
    if (mPspLensProgram == 0) {
        GLuint vs = compileShader(GL_VERTEX_SHADER, PSP_LENS_VS);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, PSP_LENS_FS);
        mPspLensProgram = linkProgram(vs, fs);
        if (mPspLensProgram == 0) return;
        mPspLensLocPos     = glGetAttribLocation(mPspLensProgram, "aPosition");
        mPspLensLocLocal   = glGetAttribLocation(mPspLensProgram, "aLocal");
        mPspLensLocTex     = glGetAttribLocation(mPspLensProgram, "aTexCoord");
        mPspLensLocRot     = glGetUniformLocation(mPspLensProgram, "uRotation");
        mPspLensLocHalf    = glGetUniformLocation(mPspLensProgram, "uHalf");
        mPspLensLocCenter  = glGetUniformLocation(mPspLensProgram, "uCenter");
        mPspLensLocZoom    = glGetUniformLocation(mPspLensProgram, "uZoom");
        mPspLensLocTonemap = glGetUniformLocation(mPspLensProgram, "uTonemap");
        mPspLensLocAlpha   = glGetUniformLocation(mPspLensProgram, "uAlpha");
        mPspLensLocTexture = glGetUniformLocation(mPspLensProgram, "uTex");
        mPspLensLocTilt    = glGetUniformLocation(mPspLensProgram, "uTilt");
        mPspLensLocAppSrc  = glGetUniformLocation(mPspLensProgram, "uAppSrc");
    }
    const float cx = mPspLensCx, cy = mPspLensCy, R = mPspLensR;
    if (R < 4.0f) return;
    // Quad over the disc bbox (device px -> NDC), matching drawFrostedGlass.
    const float x = cx - R, y = cy - R, w = 2.0f * R, h = 2.0f * R;
    float x0 = (x / mWidth) * 2.0f - 1.0f;
    float y0 = 1.0f - ((y + h) / mHeight) * 2.0f;
    float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;
    float y1 = 1.0f - (y / mHeight) * 2.0f;
    GLfloat verts[] = { x0,y0, x1,y0, x1,y1, x1,y1, x0,y1, x0,y0 };
    GLfloat local[] = { -R,R, R,R, R,-R, R,-R, -R,-R, -R,R };
    // waveSpace texcoords: logical NDC straight to [0,1] (no rotation on the tex).
    GLfloat tex[12] = { x0*0.5f+0.5f, y0*0.5f+0.5f,  x1*0.5f+0.5f, y0*0.5f+0.5f,
                        x1*0.5f+0.5f, y1*0.5f+0.5f,  x1*0.5f+0.5f, y1*0.5f+0.5f,
                        x0*0.5f+0.5f, y1*0.5f+0.5f,  x0*0.5f+0.5f, y0*0.5f+0.5f };
    // Disc centre in workTex UV (waveSpace, logical, GL y-up).
    float cx0 = (cx / mWidth) * 2.0f - 1.0f;
    float cy0 = 1.0f - (cy / mHeight) * 2.0f;
    float uCx = cx0 * 0.5f + 0.5f, uCy = cy0 * 0.5f + 0.5f;
    const float op = std::min(1.0f, cr * 5.0f);   // lens fades in over the first 20% of the drop

    setUiBlend();
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(mPspLensProgram);
    glUniformMatrix2fv(mPspLensLocRot, 1, GL_FALSE, sDrmRotMat);
    glUniform2f(mPspLensLocHalf, R, R);
    glUniform2f(mPspLensLocCenter, uCx, uCy);
    // FACE_ZOOM: the web uses 1.1 (a very gentle magnify). At 1.1 the disc interior
    // is nearly 1:1 with the surround, so against the mildly-blurred backdrop the
    // crisp face barely reads as a distinct refraction. Push the zoom well past the
    // web so the disc is an obviously magnified window: it samples a ~74%-width patch
    // of ps3bg::workTex (which already has the wave glitter particles baked in) and
    // enlarges it into the disc, so the particles stay visible - just larger - and
    // the crisp/zoomed face clearly contrasts the blurred, darkened surround. 1.35 is
    // the sweet spot: markedly more zoom than 1.1 without over-magnifying to a
    // near-uniform patch (too few distinct particles) or ballooning the glitter blurry.
    glUniform1f(mPspLensLocZoom, 1.35f);
    // The wave (workTex) is LINEAR and needs the exp2 tonemap; the captured app
    // frame is already display-space sRGB, so sample it with uTonemap 0 (the shader
    // skips the exp2 when uTonemap <= 0).
    glUniform1f(mPspLensLocTonemap, useApp ? 0.0f : 1.6846f);
    if (mPspLensLocAppSrc >= 0) glUniform1f(mPspLensLocAppSrc, useApp ? mPspAppDim : 0.0f);
    glUniform1f(mPspLensLocAlpha, op);
    // Gyro/accel parallax offset (smoothed device tilt -> UV shift). Fades in with the
    // lens so the peek-behind only kicks in once the disc is present.
    // Orientation-aware: the accelerometer reports gravity in the device's PHYSICAL
    // frame, but the tilt is added in the LOGICAL (unrotated) UV frame the disc samples
    // - the geometry is rotated to physical by sDrmRotMat (uRotation) while the UV is
    // not (line ~826). So map the physical tilt into the logical frame with the INVERSE
    // of sDrmRotMat (= its transpose, it is orthonormal). This keeps "tilt toward a
    // screen edge peeks that way" correct under every panel rotation, matching what the
    // user sees on the rotated screen. At 0deg (overlay = identity) it is a no-op.
    if (mPspLensLocTilt >= 0) {
        // Map by the CONTENT rotation only. sDrmRotMat also carries the DRM PRIME scanout
        // Y-flip (row 1 negated) on the DRM-direct HOME instance; that flip is a physical
        // panel-scanout property, NOT a content rotation, so it must not enter the accel->UV
        // parallax mapping or it inverts the vertical pan (the "wrong in wallpaper mode, fine
        // in app mode" bug - the force-SF overlay has no flip so this strip is a no-op there).
        float r0 = sDrmRotMat[0], r1 = sDrmRotMat[1];
        float r2 = sDrmRotMat[2], r3 = sDrmRotMat[3];
        if (sDrmYFlipForPrime) { r1 = -r1; r3 = -r3; }   // undo the PRIME Y-flip for the tilt only
        float tlx = r0 * mPspTiltX + r1 * mPspTiltY;
        float tly = r2 * mPspTiltX + r3 * mPspTiltY;
        glUniform2f(mPspLensLocTilt, tlx * op, tly * op);
    }
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, srcTex);
    glUniform1i(mPspLensLocTexture, 0);
    glVertexAttribPointer(mPspLensLocPos, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mPspLensLocPos);
    glVertexAttribPointer(mPspLensLocLocal, 2, GL_FLOAT, GL_FALSE, 0, local);
    glEnableVertexAttribArray(mPspLensLocLocal);
    glVertexAttribPointer(mPspLensLocTex, 2, GL_FLOAT, GL_FALSE, 0, tex);
    glEnableVertexAttribArray(mPspLensLocTex);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mPspLensLocPos);
    glDisableVertexAttribArray(mPspLensLocLocal);
    glDisableVertexAttribArray(mPspLensLocTex);
}

// ---- entrance explosion (spec 5.11, index.html) ----------------------------
struct PspEGlyph { float ang, Rmax, size1, peak, start, rot, wob, wobA; int type; };
static PspEGlyph sEGlyphs[32];
static bool sEGlyphInit = false;
static void pspInitEGlyphs() {
    if (sEGlyphInit) return;
    // Fixed-seed RNG with the same DISTRIBUTION as ENTRANCE_GLYPHS (the web set is
    // itself Math.random at load, i.e. varies per page load - a fixed seed here is
    // the faithful port: a burst with the same weighting, ranges and hero/outlier mix).
    unsigned int st = 0x1a2b3c4du;
    auto R = [&](){ st ^= st<<13; st ^= st>>17; st ^= st<<5; return (st & 0xffffff) / (float)0x1000000; };
    static const int typeW[11] = {0,0,0,0,1,1,1,3,3,2,4};
    for (int i = 0; i < 32; i++) {
        bool hero = i < 4, outlier = i >= 32 - 7;
        PspEGlyph& g = sEGlyphs[i];
        g.ang  = (float)(-M_PI/2) + (R()-0.5f)*(float)M_PI*1.9f;
        g.Rmax = outlier ? (135.0f + R()*55.0f) : (55.0f + R()*75.0f);
        g.type = typeW[(int)(R()*11.0f) % 11];
        g.size1= hero ? (22.0f + R()*10.0f) : (11.0f + R()*7.0f);
        g.peak = hero ? (0.55f + R()*0.28f) : (0.3f + R()*0.25f);
        g.start= 0.02f + R()*0.12f;
        g.rot  = (R()-0.5f)*1.1f;
        g.wob  = R()*6.28f;
        g.wobA = 3.0f + R()*6.0f;
    }
    sEGlyphInit = true;
}
static inline float e3(float t){ float u = 1.0f - t; return 1.0f - u*u*u; }

// Current glow colour, 0..1 (dominant background, spec 4.2/5.9.4; fallback cyan).
void NanoMenu::pspClockGlow(float& r, float& g, float& b) const {
    r = mPspGlow[0] / 255.0f; g = mPspGlow[1] / 255.0f; b = mPspGlow[2] / 255.0f;
}

// Sample the composited wallpaper (ps3bg::workTex) down to 8x8 and take the
// dominant colour, brightened to mid level with a small lift toward white, so all
// the glows harmonize with the wallpaper (spec 5.9.4). Throttled by the caller.
void NanoMenu::pspClockSampleGlow() {
    GLuint wt = ps3bg::workTex();
    if (wt == 0) return;
    const int GS = 8;
    if (mPspGlowFbo == 0) {
        glGenTextures(1, &mPspGlowTex);
        glBindTexture(GL_TEXTURE_2D, mPspGlowTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, GS, GS, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenFramebuffers(1, &mPspGlowFbo);
        glBindFramebuffer(GL_FRAMEBUFFER, mPspGlowFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mPspGlowTex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    GLint prevFbo = 0, prevVp[4];
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    glGetIntegerv(GL_VIEWPORT, prevVp);
    glBindFramebuffer(GL_FRAMEBUFFER, mPspGlowFbo);
    glViewport(0, 0, GS, GS);
    glDisable(GL_BLEND);
    // Fullscreen quad sampling the whole wallpaper (mTextProgram; identity uv 0..1).
    static const float ident[4] = {1,0,0,1};
    glUseProgram(mTextProgram);
    if (mTextLocRotation >= 0) glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, ident);
    if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, wt);
    glUniform1i(mTextLocTexture, 0); glBindBuffer(GL_ARRAY_BUFFER, 0);
    GLfloat v[] = {-1,-1, 1,-1, 1,1, -1,-1, 1,1, -1,1};
    GLfloat uv[] = {0,0, 1,0, 1,1, 0,0, 1,1, 0,1};
    GLfloat col[6*4]; for (int k=0;k<6;k++){col[k*4]=1;col[k*4+1]=1;col[k*4+2]=1;col[k*4+3]=1;}
    glVertexAttribPointer(mTextLocPosition,2,GL_FLOAT,GL_FALSE,0,v); glEnableVertexAttribArray(mTextLocPosition);
    glVertexAttribPointer(mTextLocTexCoord,2,GL_FLOAT,GL_FALSE,0,uv); glEnableVertexAttribArray(mTextLocTexCoord);
    glVertexAttribPointer(mTextLocColor,4,GL_FLOAT,GL_FALSE,0,col); glEnableVertexAttribArray(mTextLocColor);
    glDrawArrays(GL_TRIANGLES,0,6);
    glDisableVertexAttribArray(mTextLocPosition); glDisableVertexAttribArray(mTextLocTexCoord); glDisableVertexAttribArray(mTextLocColor);
    unsigned char px[GS*GS*4];
    glReadPixels(0, 0, GS, GS, GL_RGBA, GL_UNSIGNED_BYTE, px);
    // restore
    if (mTextLocRotation >= 0) glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, sDrmRotMat);
    glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    glEnable(GL_BLEND);
    // average non-dark, tonemap linear->display, brighten to 185, lift 12% toward white
    float rS=0,gS=0,bS=0; int cnt=0;
    for (int i=0;i<GS*GS;i++){ int r=px[i*4],g=px[i*4+1],b=px[i*4+2];
        if (r+g+b < 24) continue; rS+=r; gS+=g; bS+=b; cnt++; }
    if (cnt == 0) return;
    float ar=rS/cnt, ag=gS/cnt, ab=bS/cnt;
    // tonemap (workTex is LINEAR) to display space, matching the lens/frost
    ar = (1.0f - exp2f(-ar/255.0f*1.6846f))*255.0f;
    ag = (1.0f - exp2f(-ag/255.0f*1.6846f))*255.0f;
    ab = (1.0f - exp2f(-ab/255.0f*1.6846f))*255.0f;
    float mxc = std::max(std::max(ar, ag), std::max(ab, 1.0f)), k = 185.0f/mxc;
    ar*=k; ag*=k; ab*=k;
    const float lift=0.12f; ar+=(255-ar)*lift; ag+=(255-ag)*lift; ab+=(255-ab)*lift;
    if (!mPspGlowValid) { mPspGlow[0]=ar; mPspGlow[1]=ag; mPspGlow[2]=ab; mPspGlowValid=true; }
    else { const float sm=0.06f; mPspGlow[0]+=(ar-mPspGlow[0])*sm; mPspGlow[1]+=(ag-mPspGlow[1])*sm; mPspGlow[2]+=(ab-mPspGlow[2])*sm; }
}

// -----------------------------------------------------------------------------
// #5 dynamic darkening (user: "have the clock face dynamically darken the
// background if needed if the content is too bright"). Sample the captured LIVE
// app frame down to 8x8 and take its mean luminance, then ease two factors so a
// bright game never washes out the clock: mPspAppDim (the disc-interior dim in the
// lens shader) shrinks as the game gets brighter, and mPspAppBackdropDark (the
// surround darken over the blurred app) grows. The app frame is display-space
// sRGB, so NO tonemap (unlike the LINEAR wallpaper in pspClockSampleGlow). The FBO
// switch happens before the visible passes so it cannot flush the clock's tile.
// Throttled ~7Hz by the caller; both factors are low-passed for smooth tracking.
// -----------------------------------------------------------------------------
void NanoMenu::pspClockSampleAppDim() {
    if (mPspClockAppTex == 0 || mPspClockAppTexW < 8 || mPspClockAppTexH < 8) return;
    const int GS = 32;   // 32x32 = 1024 well-spread taps -> a low-variance full-frame mean
    if (mPspAppLumFbo == 0) {
        glGenTextures(1, &mPspAppLumTex);
        glBindTexture(GL_TEXTURE_2D, mPspAppLumTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, GS, GS, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenFramebuffers(1, &mPspAppLumFbo);
        glBindFramebuffer(GL_FRAMEBUFFER, mPspAppLumFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mPspAppLumTex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    GLint prevFbo = 0, prevVp[4];
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    glGetIntegerv(GL_VIEWPORT, prevVp);
    glBindFramebuffer(GL_FRAMEBUFFER, mPspAppLumFbo);
    glViewport(0, 0, GS, GS);
    glDisable(GL_BLEND);
    // Fullscreen blit of the captured app into GSxGS (mTextProgram, identity uv). GL_LINEAR
    // minification only box-filters a 2x2 footprint per output texel, NOT the whole block,
    // so GS=32 gives 1024 well-spread stratified taps across the frame; the CPU mean below
    // then averages them into an unbiased, low-variance full-frame luminance. Identity
    // rotation - we only want the mean, orientation is irrelevant.
    static const float ident[4] = {1,0,0,1};
    glUseProgram(mTextProgram);
    if (mTextLocRotation >= 0) glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, ident);
    if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, mPspClockAppTex);
    glUniform1i(mTextLocTexture, 0); glBindBuffer(GL_ARRAY_BUFFER, 0);
    GLfloat v[] = {-1,-1, 1,-1, 1,1, -1,-1, 1,1, -1,1};
    GLfloat uv[] = {0,0, 1,0, 1,1, 0,0, 1,1, 0,1};
    GLfloat col[6*4]; for (int k=0;k<6;k++){col[k*4]=1;col[k*4+1]=1;col[k*4+2]=1;col[k*4+3]=1;}
    glVertexAttribPointer(mTextLocPosition,2,GL_FLOAT,GL_FALSE,0,v); glEnableVertexAttribArray(mTextLocPosition);
    glVertexAttribPointer(mTextLocTexCoord,2,GL_FLOAT,GL_FALSE,0,uv); glEnableVertexAttribArray(mTextLocTexCoord);
    glVertexAttribPointer(mTextLocColor,4,GL_FLOAT,GL_FALSE,0,col); glEnableVertexAttribArray(mTextLocColor);
    glDrawArrays(GL_TRIANGLES,0,6);
    glDisableVertexAttribArray(mTextLocPosition); glDisableVertexAttribArray(mTextLocTexCoord); glDisableVertexAttribArray(mTextLocColor);
    unsigned char px[GS*GS*4];
    glReadPixels(0, 0, GS, GS, GL_RGBA, GL_UNSIGNED_BYTE, px);
    // restore
    if (mTextLocRotation >= 0) glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, sDrmRotMat);
    glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    glEnable(GL_BLEND);
    // Mean Rec.601 luminance over all 64 texels (the app frame fills the whole texture,
    // so unlike the glow sampler there is no dark background to skip). 0..1.
    float lum = 0.0f;
    for (int i = 0; i < GS*GS; i++)
        lum += 0.299f*px[i*4] + 0.587f*px[i*4+1] + 0.114f*px[i*4+2];
    lum /= (float)(GS*GS) * 255.0f;
    // Brightness -> targets. A dark game barely dims the disc (keeps the refraction
    // vivid) and darkens the surround only mildly; a bright game dims the disc hard so
    // the white face + glow stay legible, and darkens the surround in step so the
    // blurred backdrop never overpowers the face. smoothstep so the tracking has no knee.
    auto sstep = [](float e0, float e1, float x){ float t = clamp01((x - e0) / (e1 - e0)); return t*t*(3.0f - 2.0f*t); };
    float b = sstep(0.16f, 0.62f, lum);      // lower/earlier ramp: normal bright gameplay pushes b -> 1, not ~0
    float dimTarget  = 0.92f - 0.60f * b;    // 0.92 (dark game, near-no-dim) .. 0.32 (bright game, disc clearly dimmed)
    float darkTarget = 0.46f + 0.34f * b;    // 0.46 (dark game) .. 0.80 (bright game) surround follows in step
    const float sm = 0.12f;
    mPspAppDim          += (dimTarget  - mPspAppDim)          * sm;
    mPspAppBackdropDark += (darkTarget - mPspAppBackdropDark) * sm;
}

// -----------------------------------------------------------------------------
// Bake the four numeral outlines (12/3/6/9) into white alpha textures (a=coverage)
// once, via an even-odd scanline fill (handles the 6/9 counters). Drawn later with
// the expanded-fill glow. The bumpy trace edges are hidden by that soft halo.
// -----------------------------------------------------------------------------
void NanoMenu::pspClockBakeGlyphs() {
    if (mPspGlyphBaked) return;
    const char* keys[4] = { "1", "3", "6", "9" };   // glyphFor keys on first char (1 -> "12")
    for (int gi = 0; gi < 4; gi++) {
        const pspglyph::GlyphMesh* gm = pspglyph::glyphFor(keys[gi]);
        if (!gm) continue;
        float minx = 1e9f, maxx = -1e9f, miny = 1e9f, maxy = -1e9f;
        { int o = 0; for (int c = 0; c < gm->nc; c++) { int n = gm->c[c];
            for (int k = 0; k < n; k++) { float x = gm->v[(o+k)*2], y = gm->v[(o+k)*2+1];
                minx = std::min(minx, x); maxx = std::max(maxx, x);
                miny = std::min(miny, y); maxy = std::max(maxy, y); } o += n; } }
        const float PAD = 9.0f;
        const float cx = (minx + maxx) * 0.5f, cy = (miny + maxy) * 0.5f;
        const float hx = (maxx - minx) * 0.5f + PAD, hy = (maxy - miny) * 0.5f + PAD;
        mPspGlyphHXu[gi] = hx; mPspGlyphHYu[gi] = hy;
        const float PXU = 1.6f;              // final px per glyph unit
        const int SS = 4;                    // supersample for AA (crisper glyph edges)
        int W = (int)(2*hx*PXU + 0.5f), H = (int)(2*hy*PXU + 0.5f);
        if (W < 4) W = 4; if (H < 4) H = 4;
        int rw = W*SS, rh = H*SS;
        std::vector<unsigned char> cov((size_t)rw*rh, 0);
        std::vector<float> xs;
        for (int ry = 0; ry < rh; ry++) {
            float gy = (cy - hy) + ((ry + 0.5f) / rh) * 2*hy;
            xs.clear();
            int o = 0;
            for (int c = 0; c < gm->nc; c++) { int n = gm->c[c];
                for (int k = 0; k < n; k++) { int k2 = (k+1) % n;
                    float ay = gm->v[(o+k)*2+1], by = gm->v[(o+k2)*2+1];
                    if ((ay <= gy && by > gy) || (by <= gy && ay > gy)) {
                        float ax = gm->v[(o+k)*2], bx = gm->v[(o+k2)*2];
                        float t = (gy - ay) / (by - ay); xs.push_back(ax + t*(bx-ax)); } }
                o += n; }
            std::sort(xs.begin(), xs.end());
            for (size_t p = 0; p + 1 < xs.size(); p += 2) {
                int ia = (int)(((xs[p]   - (cx-hx)) / (2*hx)) * rw + 0.5f);
                int ib = (int)(((xs[p+1] - (cx-hx)) / (2*hx)) * rw + 0.5f);
                if (ia < 0) ia = 0; if (ib > rw) ib = rw;
                for (int rx = ia; rx < ib; rx++) cov[(size_t)ry*rw + rx] = 255; }
        }
        // Soft-glow copy: separable box blur of the SS coverage (2 iterations ~=
        // Gaussian), radius BR SS-px (= BR/SS final px). This is the diffuse halo the
        // web gets from ctx.shadowBlur; a dilated copy of the SHARP tex only ever reads
        // as an outline. PAD=9 glyph units of transparent margin holds the spread.
        {
            const int BR = SS * 3;                 // 9 SS px ~ 3 final px halo
            std::vector<unsigned char> gcov = cov;
            std::vector<unsigned char> tmp((size_t)rw*rh);
            auto boxH = [&](std::vector<unsigned char>& src, std::vector<unsigned char>& dst){
                int win = 2*BR + 1;
                for (int y = 0; y < rh; y++) {
                    const unsigned char* s = &src[(size_t)y*rw];
                    unsigned char* d = &dst[(size_t)y*rw];
                    int acc = 0;
                    for (int x = -BR; x <= BR; x++) acc += s[x < 0 ? 0 : (x >= rw ? rw-1 : x)];
                    for (int x = 0; x < rw; x++) {
                        d[x] = (unsigned char)(acc / win);
                        int xo = x - BR, xn = x + BR + 1;
                        acc -= s[xo < 0 ? 0 : (xo >= rw ? rw-1 : xo)];
                        acc += s[xn < 0 ? 0 : (xn >= rw ? rw-1 : xn)];
                    }
                }
            };
            auto boxV = [&](std::vector<unsigned char>& src, std::vector<unsigned char>& dst){
                int win = 2*BR + 1;
                for (int x = 0; x < rw; x++) {
                    int acc = 0;
                    for (int y = -BR; y <= BR; y++) { int yy = y<0?0:(y>=rh?rh-1:y); acc += src[(size_t)yy*rw + x]; }
                    for (int y = 0; y < rh; y++) {
                        dst[(size_t)y*rw + x] = (unsigned char)(acc / win);
                        int yo = y - BR, yn = y + BR + 1;
                        int ao = yo<0?0:(yo>=rh?rh-1:yo), an = yn<0?0:(yn>=rh?rh-1:yn);
                        acc -= src[(size_t)ao*rw + x]; acc += src[(size_t)an*rw + x];
                    }
                }
            };
            boxH(gcov, tmp); boxV(tmp, gcov);   // pass 1
            boxH(gcov, tmp); boxV(tmp, gcov);   // pass 2 (smoother falloff)
            std::vector<unsigned char> gtex((size_t)W*H*4);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int sum = 0;
                for (int sy = 0; sy < SS; sy++) for (int sx = 0; sx < SS; sx++)
                    sum += gcov[(size_t)(y*SS+sy)*rw + (x*SS+sx)];
                int a = sum / (SS*SS);
                size_t idx = ((size_t)y*W + x)*4; gtex[idx]=255; gtex[idx+1]=255; gtex[idx+2]=255; gtex[idx+3]=(unsigned char)a; }
            GLuint gt = 0; glGenTextures(1, &gt); glBindTexture(GL_TEXTURE_2D, gt);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, gtex.data());
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            mPspGlyphGlowTex[gi] = gt;
        }
        std::vector<unsigned char> tex((size_t)W*H*4);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int sum = 0;
            for (int sy = 0; sy < SS; sy++) for (int sx = 0; sx < SS; sx++)
                sum += cov[(size_t)(y*SS+sy)*rw + (x*SS+sx)];
            int a = sum / (SS*SS);
            size_t idx = ((size_t)y*W + x)*4; tex[idx]=255; tex[idx+1]=255; tex[idx+2]=255; tex[idx+3]=(unsigned char)a; }
        GLuint t = 0; glGenTextures(1, &t); glBindTexture(GL_TEXTURE_2D, t);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, tex.data());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        mPspGlyphTex[gi] = t;
    }
    mPspGlyphBaked = true;
}

// -----------------------------------------------------------------------------
// Soft Gaussian glow for the clock chrome - the faithful GLES2 equivalent of the
// web's canvas shadowBlur. drawShapes renders the elements (in the colour/alpha it
// is handed) into an offscreen buffer; a real separable Gaussian (blurGlassChain)
// turns that coverage into a soft halo, composited ADDITIVELY in the glow colour.
// This replaces the old stacked expanding copies, which banded into a hard stroke.
// downLevels/gaussIters set the halo width (more = wider/softer).
// -----------------------------------------------------------------------------
void NanoMenu::pspClockChromeGlowPass(
        const std::function<void(float,float,float,float)>& drawShapes,
        int downLevels, int gaussIters,
        float gr, float gg, float gb, float alpha) {
    if (alpha <= 0.002f) return;
    if (mGlassDownProgram == 0) return;   // blur pipeline not ready -> skip (never a hard fill)

    // Size the glow buffer to the CURRENT viewport (panel-native under rotation) so the
    // shapes land at exactly the same pixels as the crisp pass, and the composite is a
    // straight 1:1 copy that needs no rotation of its own.
    GLint vp[4]; glGetIntegerv(GL_VIEWPORT, vp);
    const int vw = vp[2], vh = vp[3];
    if (vw < 16 || vh < 16) return;
    GLint prevFbo = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);

    if (mPspChromeGlowTex == 0 || mPspChromeGlowW != vw || mPspChromeGlowH != vh) {
        if (mPspChromeGlowTex == 0) glGenTextures(1, &mPspChromeGlowTex);
        glBindTexture(GL_TEXTURE_2D, mPspChromeGlowTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, vw, vh, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        mPspChromeGlowW = vw; mPspChromeGlowH = vh;
    }
    if (mPspChromeGlowFbo == 0) glGenFramebuffers(1, &mPspChromeGlowFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, mPspChromeGlowFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, mPspChromeGlowTex, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
        return;
    }
    glViewport(0, 0, vw, vh);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    // Render the shapes in WHITE with normal alpha blending: over the cleared black the
    // RGB channel holds shape coverage (1 in the solid body, the AA ramp at the edges),
    // which is exactly what we blur into a halo.
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    drawShapes(1.0f, 1.0f, 1.0f, 1.0f);

    // Blur the coverage into a soft Gaussian halo (restores the previous FBO+viewport).
    blurGlassChain(mPspChromeGlowTex, vw, vh, downLevels, gaussIters);
    GLuint blur = mGlassBlurTex;
    if (blur == 0) { glBindFramebuffer(GL_FRAMEBUFFER, prevFbo); glViewport(vp[0],vp[1],vp[2],vp[3]); setUiBlend(); return; }

    // Composite the blurred coverage additively in the glow colour, as a 1:1 full-screen
    // copy with IDENTITY rotation: the buffer already carries the scene rotation (the
    // shapes were drawn with the live uRotation), so re-applying it would double-rotate.
    glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
    glViewport(vp[0], vp[1], vp[2], vp[3]);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE);   // additive; colour * texel.rgb(=coverage) is the intensity
    static const GLfloat q[]  = { -1,-1,  1,-1,  1,1,  1,1, -1,1, -1,-1 };
    static const GLfloat qt[] = {  0, 0,  1, 0,  1,1,  1,1,  0,1,  0, 0 };
    GLfloat cols[6*4];
    for (int i = 0; i < 6; i++) { cols[i*4]=gr*alpha; cols[i*4+1]=gg*alpha; cols[i*4+2]=gb*alpha; cols[i*4+3]=1.0f; }
    static const GLfloat ident[4] = { 1.0f, 0.0f, 0.0f, 1.0f };
    glUseProgram(mTextProgram);
    if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);
    glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, ident);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, blur);
    glUniform1i(mTextLocTexture, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, q);
    glEnableVertexAttribArray(mTextLocPosition);
    glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, qt);
    glEnableVertexAttribArray(mTextLocTexCoord);
    glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 0, cols);
    glEnableVertexAttribArray(mTextLocColor);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mTextLocPosition);
    glDisableVertexAttribArray(mTextLocTexCoord);
    glDisableVertexAttribArray(mTextLocColor);
    glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, sDrmRotMat);   // restore scene rotation
    setUiBlend();
}

// -----------------------------------------------------------------------------
// Stage 3/4: the clock face (psp_clock.js draw(), sections 1-7). Positions are in
// PSP coords (480x272, centre 240,136) transformed to device px relative to the
// disc centre (mPspLensCx/Cy, which already carries the float + drop descent), so
// the face rides the glass exactly. sc = mPspLensR / 141.
// -----------------------------------------------------------------------------
void NanoMenu::pspClockFace(float reveal, float /*floatY*/, float /*descentFrac*/) {
    float sc = mPspLensR / 141.0f;   // NON-const: the AA supersample pass doubles it (with the
                                     // lens centre) so dx()/dy() land the face on the 2x FBO.
    const float CX = 240.0f, CY = 136.0f;
    auto dx = [&](float px){ return mPspLensCx + (px - CX) * sc; };
    auto dy = [&](float py){ return mPspLensCy + (py - CY) * sc; };
    auto polar = [&](float frac, float R, float& ox, float& oy){
        float th = (float)M_PI_2 - frac * 2.0f * (float)M_PI;
        ox = CX + R * cosf(th); oy = CY - R * sinf(th);
    };
    float gr, gg, gb; pspClockGlow(gr, gg, gb);
    const float detail = mPspDetailFade;

    // Breathing glow pulse (spec: same slow in/out as the highlighted menu item,
    // PULSE_PERIOD_MS): the face glow never fully fades, it just varies, so the
    // numerals/ticks/hands read as a LIVING glow, not a static stroke. Multiplies
    // every glow-halo alpha below (the crisp white cores are left steady).
    float glowPhase = fmodf(mEffectTime, ps3::PULSE_PERIOD_MS / 1000.0f)
                    / (ps3::PULSE_PERIOD_MS / 1000.0f);
    float glowPulse = 0.85f + 0.30f * (0.5f * (1.0f - cosf(glowPhase * 2.0f * (float)M_PI)));  // 0.85..1.15 (gentle)

    // ---- shape lambdas: draw each element in a given colour/alpha. Shared by the
    // soft-glow pass (rendered white, then blurred) and the crisp cores on top. ----
    // Hour ticks: 8 chunky round-capped bars (web lineWidth 6, lineCap 'round').
    auto drawTicksShapes = [&](float r, float g, float b, float a){
        if (a <= 0.002f) return;
        static const int HT[8] = {1,2,4,5,7,8,10,11};
        const float w = 3.0f * sc;                 // half-width (bar width 6)
        for (int k = 0; k < 8; k++) {
            float fr = HT[k] / 12.0f;
            float ax, ay, bx, by; polar(fr, 131.0f + 6.0f, ax, ay); polar(fr, 131.0f - 24.0f, bx, by);
            float axd = dx(ax), ayd = dy(ay), bxd = dx(bx), byd = dy(by);
            float ux = bxd-axd, uy = byd-ayd, L = sqrtf(ux*ux+uy*uy); if (L < 1e-3f) continue;
            float nx = -uy/L, ny = ux/L;
            float p0x=axd+nx*w, p0y=ayd+ny*w, p1x=bxd+nx*w, p1y=byd+ny*w;
            float p2x=bxd-nx*w, p2y=byd-ny*w, p3x=axd-nx*w, p3y=ayd-ny*w;
            drawTriangle(p0x,p0y,p1x,p1y,p2x,p2y, r,g,b,a);
            drawTriangle(p0x,p0y,p2x,p2y,p3x,p3y, r,g,b,a);
            const int SEG = 8;
            auto cap = [&](float cxc, float cyc){
                float px = cxc + w, py = cyc;
                for (int i = 1; i <= SEG; i++) {
                    float ang = (float)i/SEG * 2.0f*(float)M_PI;
                    float qx = cxc + w*cosf(ang), qy = cyc + w*sinf(ang);
                    drawTriangle(cxc,cyc, px,py, qx,qy, r,g,b,a); px=qx; py=qy;
                }
            };
            cap(axd,ayd); cap(bxd,byd);
        }
    };
    // Numerals 12/3/6/9 from the baked sharp glyph textures.
    struct NmR { float frac; float R; int gi; };
    static const NmR NUMS[4] = {
        {0.0f/12.0f, 116.0f, 0}, {3.0f/12.0f, 120.0f, 1},
        {6.0f/12.0f, 113.0f, 2}, {9.0f/12.0f, 118.0f, 3} };
    const float NUM_H = 41.0f;
    auto drawNumeralGlyphs = [&](float r, float g, float b, float a){
        for (int n = 0; n < 4; n++) {
            const NmR& nm = NUMS[n];
            GLuint tex = mPspGlyphTex[nm.gi]; if (tex == 0) continue;
            float px, py; polar(nm.frac, nm.R, px, py);
            float cxd = dx(px), cyd = dy(py);
            float s = (NUM_H / 100.0f) * sc;
            float hwn = mPspGlyphHXu[nm.gi] * s, hhn = mPspGlyphHYu[nm.gi] * s;
            drawIconTex(tex, cxd - hwn, cyd - hhn, 2*hwn, 2*hhn, r, g, b, a);
        }
    };
    // Hands (hour/minute/second) - the second hand spring-snaps (spec 1.2).
    int hh, mm, ss; float subsec; pspNow(hh, mm, ss, subsec);
    const int h12 = hh % 12;
    const float hourFrac = h12/12.0f + mm/720.0f;
    const float minFrac  = mm/60.0f + ss/3600.0f;
    float secFrac;
    {
        float tsp = subsec; if (tsp<0) tsp=0; if (tsp>1) tsp=1;
        float esp = sqrtf(1.0f - (tsp-1.0f)*(tsp-1.0f));
        float Esp = esp + 1.2f*esp*(1.0f-esp); float frsp = Esp*(2.0f-Esp);
        secFrac = (ss + frsp) / 60.0f;
    }
    auto handShape = [&](float frac, float len, float back, float wHub, float wTip,
                         float r, float g, float b, float a){
        float th = (float)M_PI_2 - frac*2.0f*(float)M_PI;
        float ddx = cosf(th), ddy = -sinf(th);
        float pxp = -ddy, pyp = ddx;
        float tipX = CX + ddx*len, tipY = CY + ddy*len;
        float backX = CX - ddx*back, backY = CY - ddy*back;
        float ax=dx(tipX+pxp*wTip), ay=dy(tipY+pyp*wTip);
        float bx=dx(tipX-pxp*wTip), by=dy(tipY-pyp*wTip);
        float cxp=dx(backX-pxp*wHub), cyp=dy(backY-pyp*wHub);
        float ex=dx(backX+pxp*wHub), ey=dy(backY+pyp*wHub);
        drawTriangle(ax,ay,bx,by,cxp,cyp, r,g,b,a);
        drawTriangle(ax,ay,cxp,cyp,ex,ey, r,g,b,a);
    };
    auto drawHandsShapes = [&](float r, float g, float b, float a){
        handShape(hourFrac, 86.0f,       11.0f, 3.6f, 2.4f, r,g,b,a);   // hour
        handShape(minFrac,  141.0f-2.0f, 13.0f, 1.3f, 1.0f, r,g,b,a);   // minute
        handShape(secFrac,  141.0f+2.0f, 20.0f, 0.7f, 0.55f, r,g,b,a);  // second
    };
    // Hub filled circle (radius rr in PSP units).
    auto drawHubDisc = [&](float rr, float r, float g, float b, float a){
        const int SEG = 20; float cxd = dx(CX), cyd = dy(CY), rd = rr*sc;
        float px = cxd + rd, py = cyd;
        for (int i = 1; i <= SEG; i++) {
            float ang = (float)i / SEG * 2.0f * (float)M_PI;
            float nx = cxd + rd*cosf(ang), ny = cyd + rd*sinf(ang);
            drawTriangle(cxd,cyd, px,py, nx,ny, r,g,b,a); px=nx; py=ny;
        }
    };

    // ---- soft Gaussian glow (faithful to the web canvas shadowBlur) --------------
    // Numerals get a WIDE halo (web shadowBlur 12+7); the ticks/hands/hub a TIGHTER
    // one (web shadowBlur 3/4/6). The ticks are gated by detailFade so their glow
    // fades in with the crisp bars. glowPulse breathes it gently.
    pspClockChromeGlowPass(
        [&](float r,float g,float b,float a){ drawNumeralGlyphs(r,g,b,a); },
        2, 2, gr, gg, gb, 1.05f * glowPulse);
    pspClockChromeGlowPass(
        [&](float r,float g,float b,float a){
            // All flat-colour: batch the ~170 tick/hand/hub triangles into one draw before
            // the pass blurs the result (same verts/blend, byte-identical). Numerals glow in
            // the SEPARATE pass above (textured), so no program interleave here.
            beginSolidBatch();
            drawTicksShapes(r,g,b, a*detail);
            drawHandsShapes(r,g,b,a);
            drawHubDisc(5.5f, r,g,b,a);
            endSolidBatch();
        },
        2, 1, gr, gg, gb, 0.90f * glowPulse);

    // ---- 2x supersampled FACE target (AA the crisp cores + comet trail) -------------
    // Render the sharp drawTriangle/drawIconTex geometry (ticks, hands, hub, numerals and
    // the second-hand trail dashes) into a 2x offscreen texture, then composite it down
    // through GL_LINEAR so the hard polygon edges box-filter into anti-aliased ones. The
    // soft Gaussian glow above stays at 1x (needs no AA, and shares blurGlassChain which
    // must not thrash resolution). Primitives map device-px via mWidth/mHeight and dx()/dy()
    // via sc + mPspLensCx/Cy, so ALL of {mWidth,mHeight,sc,mPspLensCx/Cy/R} double for the
    // FBO pass and are restored on resolve. Mirrors pspClockChromeGlowPass's FBO+composite.
    bool  faceSS = (mTextProgram != 0);
    bool  faceMsaa = false;      // MSAA (native 1x + on-tile resolve) vs 2x2 supersample
    GLint faceVp[4] = {0,0,0,0}, facePrevFbo = 0;
    int   faceSavedW = mWidth, faceSavedH = mHeight;
    float faceSavedSc = sc, faceSavedCx = mPspLensCx, faceSavedCy = mPspLensCy, faceSavedR = mPspLensR;
    if (faceSS) {
        glGetIntegerv(GL_VIEWPORT, faceVp);
        int vw = faceVp[2], vh = faceVp[3];
        if (vw < 16 || vh < 16) faceSS = false;
        else {
            // AA strategy: MSAA render-to-texture takes 4 coverage samples per pixel and
            // resolves them in tile memory (~1x fragment cost, the whole point on the Brick's
            // PowerVR), so the offscreen target is NATIVE 1x. When MSAA is unavailable or the
            // tuning prop is off, fall back to the historic 2x2 SUPERSAMPLE (target = 2x device
            // px, everything below double-shaded then box-downsampled). Equal-or-better edge AA
            // either way; MSAA just gets there far cheaper. See pspFaceMsaaAvailable() at top.
            faceMsaa = pspFaceMsaaEnabled() && pspFaceMsaaAvailable();
            const int fbW = faceMsaa ? vw : vw * 2;
            const int fbH = faceMsaa ? vh : vh * 2;
            glGetIntegerv(GL_FRAMEBUFFER_BINDING, &facePrevFbo);
            if (mPspFaceTex == 0 || mPspFaceW != fbW || mPspFaceH != fbH) {
                if (mPspFaceTex == 0) glGenTextures(1, &mPspFaceTex);
                glBindTexture(GL_TEXTURE_2D, mPspFaceTex);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, fbW, fbH, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                mPspFaceW = fbW; mPspFaceH = fbH;
            }
            if (mPspFaceFbo == 0) glGenFramebuffers(1, &mPspFaceFbo);
            glBindFramebuffer(GL_FRAMEBUFFER, mPspFaceFbo);
            // MSAA: attach the resolve texture with a sample count; the driver keeps the
            // implicit multisample buffer and resolves into the texture on FBO unbind below.
            if (faceMsaa)
                pGlFramebufferTexture2DMultisampleEXT(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                                                      GL_TEXTURE_2D, mPspFaceTex, 0, gPspFaceMsaaSamples);
            else
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mPspFaceTex, 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                glBindFramebuffer(GL_FRAMEBUFFER, facePrevFbo);
                faceSS = false;
            } else {
                glViewport(0, 0, fbW, fbH);
                glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                glClear(GL_COLOR_BUFFER_BIT);
                // Only the SUPERSAMPLE path renders at 2x device px (double every px-space
                // mapper); MSAA renders at native 1x (multisampling is implicit) so it leaves
                // mWidth/sc/lens untouched.
                if (!faceMsaa) {
                    mWidth = faceSavedW * 2; mHeight = faceSavedH * 2;
                    sc *= 2.0f; mPspLensCx *= 2.0f; mPspLensCy *= 2.0f; mPspLensR *= 2.0f;
                }
            }
        }
    }

    // Additive blend for the glowing chrome + trail (spec: composite 'lighter').
    glBlendFunc(GL_SRC_ALPHA, GL_ONE);

    // Sections 2/3/4/6/7 are all FLAT-COLOUR additive geometry (comet trail + ticks + hands
    // + hub). Coalesce them through the solid batcher: drawTriangle already appends to it, so
    // this collapses the ~430 separate glDrawArrays (up to 240 trail + 144 tick + 6 hand + 40
    // hub) into a single draw - the face is draw-call bound on the Brick's PowerVR. The result
    // is byte-identical (same verts, same colours, same additive blend; the batcher preserves
    // the caller's glBlendFunc and per-frame uRotation). The numerals are TEXTURED (drawIconTex
    // switches to mTextProgram, which the batcher cannot carry), so they are drawn AFTER the
    // batch closes; additive blend is order-independent, so moving them out of the middle of
    // the crisp run does not change a pixel.
    beginSolidBatch();

    // --- section 2: second-hand comet trail (120 short radial dashes at the rim) ---
    if (detail > 0.002f) {
        const float TR = 200.0f/255.0f, TG = 236.0f/255.0f, TB = 250.0f/255.0f;
        for (int i = 0; i < 120; i++) {
            float a = mPspTrail[i];
            if (a < 0.02f) continue;
            float fr = i / 120.0f;
            float ax, ay, bx, by; polar(fr, 141.0f - 1.0f, ax, ay); polar(fr, 141.0f - 11.0f, bx, by);
            float axd = dx(ax), ayd = dy(ay), bxd = dx(bx), byd = dy(by);
            float ux = bxd-axd, uy = byd-ayd, L = sqrtf(ux*ux+uy*uy); if (L < 1e-3f) continue;
            float nx = -uy/L, ny = ux/L;
            float hw = (1.5f + a*0.9f) * 0.5f * sc;
            float aa = a * 0.6f * detail;
            float p0x=axd+nx*hw, p0y=ayd+ny*hw, p1x=bxd+nx*hw, p1y=byd+ny*hw;
            float p2x=bxd-nx*hw, p2y=byd-ny*hw, p3x=axd-nx*hw, p3y=ayd-ny*hw;
            drawTriangle(p0x,p0y,p1x,p1y,p2x,p2y, TR,TG,TB,aa);
            drawTriangle(p0x,p0y,p2x,p2y,p3x,p3y, TR,TG,TB,aa);
        }
    }

    // --- crisp white cores on top of the soft glow (additive 'lighter', web draw
    // sections 3/4/6/7). The glow behind them is the Gaussian halo composited above;
    // here we lay only the sharp cores, so nothing reads as a stacked-copy stroke. ---
    drawTicksShapes(1.0f, 1.0f, 1.0f, detail);      // hour ticks (fade in with detail)
    drawHandsShapes(1.0f, 1.0f, 1.0f, 1.0f);        // hands hour/minute/second
    drawHubDisc(5.5f, 0.92f, 0.97f, 1.0f, 1.0f);    // hub #eaf7ff
    drawHubDisc(2.5f, 1.0f, 1.0f, 1.0f, 1.0f);      // hub white
    endSolidBatch();

    drawNumeralGlyphs(1.0f, 1.0f, 1.0f, 1.0f);      // numerals 12/3/6/9 (textured cores, post-batch)

    // ---- resolve the face target: restore scale (a no-op on the MSAA path, which never
    // doubled), rebind the previous FBO (this is where MSAA resolves its samples into the
    // texture), then composite 1:1 additively with an IDENTITY rotation (the texture already
    // baked in the scene rotation). On the supersample path GL_LINEAR box-filters its 2x edges;
    // on the MSAA path the tile resolve already produced the AA. Mirrors pspClockChromeGlowPass. ----
    if (faceSS) {
        mWidth = faceSavedW; mHeight = faceSavedH;
        sc = faceSavedSc; mPspLensCx = faceSavedCx; mPspLensCy = faceSavedCy; mPspLensR = faceSavedR;
        glBindFramebuffer(GL_FRAMEBUFFER, facePrevFbo);
        glViewport(faceVp[0], faceVp[1], faceVp[2], faceVp[3]);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE);                 // additive ('lighter'), matches the cores
        static const GLfloat q[]  = { -1,-1,  1,-1,  1,1,  1,1, -1,1, -1,-1 };
        static const GLfloat qt[] = {  0, 0,  1, 0,  1,1,  1,1,  0,1,  0, 0 };
        GLfloat cols[6*4];
        for (int i = 0; i < 6; i++) { cols[i*4]=1.0f; cols[i*4+1]=1.0f; cols[i*4+2]=1.0f; cols[i*4+3]=1.0f; }
        static const GLfloat ident[4] = { 1.0f, 0.0f, 0.0f, 1.0f };
        glUseProgram(mTextProgram);
        if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);
        glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, ident);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mPspFaceTex);
        glUniform1i(mTextLocTexture, 0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, q);
        glEnableVertexAttribArray(mTextLocPosition);
        glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, qt);
        glEnableVertexAttribArray(mTextLocTexCoord);
        glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 0, cols);
        glEnableVertexAttribArray(mTextLocColor);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisableVertexAttribArray(mTextLocPosition);
        glDisableVertexAttribArray(mTextLocTexCoord);
        glDisableVertexAttribArray(mTextLocColor);
        glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, sDrmRotMat);   // restore scene rotation
    }

    // Restore normal UI blend for whatever draws next (date text, dialogs).
    setUiBlend();

    // --- section 5: date "DDD D" below centre (frosted-glass, additive) --------------
    // Web (psp_clock.js): PS3/Arial-Narrow at sizePx, fill rgba(224,237,248,0.32), a faint
    // white edge stroke rgba(255,255,255,0.28) width max(0.7, sizePx*0.064), composited
    // 'lighter'. Nano bundles no condensed/Arial-Narrow face, so (as on the web) the visible
    // weight comes from the additive fill + a synthesized white edge, not the typeface.
    // NOTE: drawTextStroke() is a no-op on this path and drawText's built-in outline paints
    // BLACK, so the white edge is drawn manually as four additive white offset copies under
    // one additive coloured fill. The old code also used the WRONG scale base (/32 vs the
    // real FONT_CHAR_H=16), rendering the date at HALF size - fixed here.
    {
        time_t now = time(nullptr); struct tm lt; localtime_r(&now, &lt);
        static const char* DAYS[7] = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
        char dtxt[24];
        snprintf(dtxt, sizeof(dtxt), "%s %d", DAYS[lt.tm_wday], lt.tm_mday);
        // Web max(11, round(11*sc/2)*2); round to an even device-px em so drawText rasterizes
        // crisply at-size (grid snap) instead of a blurry fractional downscale of the master.
        float sizePx = 11.0f * sc;
        if (sizePx < 11.0f) sizePx = 11.0f;
        sizePx = floorf(sizePx / 2.0f + 0.5f) * 2.0f;
        float ts = sizePx / (float)FONT_CHAR_H;   // drawText scale (displayEm = FONT_CHAR_H*scale)
        float tw = measureText(dtxt, ts);
        float bx = dx(CX) - tw * 0.5f;
        float by = dy(CY + 33.0f) - sizePx * 0.5f;
        float strokePx = fmaxf(0.7f, sizePx * 0.064f);   // web stroke width -> white-edge offset

        const int prevOutline = mTextOutlineMode;
        mTextOutlineMode = 2;                             // no built-in (black) outline
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);               // additive ('lighter')
        const float edgeA = 0.28f * 0.5f;                // 4 overlapping copies ~= web 0.28 edge
        static const float ed[4][2] = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int e = 0; e < 4; e++)
            drawText(dtxt, bx + ed[e][0]*strokePx, by + ed[e][1]*strokePx, ts, 1.0f, 1.0f, 1.0f, edgeA);
        // Frosted-glass fill: exact web rgba(224,237,248) at ~web alpha (0.36, a hair over the
        // web 0.32 to offset nano's thinner additive AA; keep <=0.40 or it clips to white).
        drawText(dtxt, bx, by, ts, 0.878f, 0.929f, 0.973f, 0.36f);
        mTextOutlineMode = prevOutline;
        setUiBlend();
    }
    (void)reveal;
}

// Bow + magnify a sprite behind the glass ball (spec 5.8).
static inline void pspLensBow(bool valid, float lcx, float lcy, float lr,
                              float& cx, float& cy, float& size) {
    if (!valid) return;
    float dxx = cx - lcx, dyy = cy - lcy, dist = sqrtf(dxx*dxx + dyy*dyy);
    if (dist < lr) { float t = dist/lr, m = 1.0f + 0.42f*(1.0f - t*t);
        cx = lcx + dxx*m; cy = lcy + dyy*m; size *= m; }
}

// One PS-button glyph as stroked shapes (device px, additive already set).
void NanoMenu::pspClockEntrance(float sc, float ox, float oy, float reveal,
                                float angOffset, float alphaMul) {
    if (reveal <= 0.0f || reveal >= 0.995f || alphaMul <= 0.002f) return;
    pspInitEGlyphs();
    // 32 glyphs of flat-colour stroke triangles: batch the whole burst into one draw
    // (each call issues hundreds of drawTriangle otherwise, three times per frame during
    // the entrance). Byte-identical - same additive verts, batcher keeps the blend.
    beginSolidBatch();
    const float hw = 0.8f;   // half of the web's 1.6px stroke (canvas px == device px here)
    auto line = [&](float x0,float y0,float x1,float y1, float r,float g,float b,float a){
        float ux=x1-x0, uy=y1-y0, L=sqrtf(ux*ux+uy*uy); if (L<1e-3f) return;
        float nx=-uy/L*hw, ny=ux/L*hw;
        drawTriangle(x0+nx,y0+ny, x1+nx,y1+ny, x1-nx,y1-ny, r,g,b,a);
        drawTriangle(x0+nx,y0+ny, x1-nx,y1-ny, x0-nx,y0-ny, r,g,b,a);
    };
    auto glyph = [&](float x,float y,float s,int type,float rot,float a){
        const float R=205.0f/255.0f, G=238.0f/255.0f, B=255.0f/255.0f;
        float cr=cosf(rot), sr=sinf(rot);
        auto rp=[&](float px,float py,float&ox2,float&oy2){ ox2=x+px*cr-py*sr; oy2=y+px*sr+py*cr; };
        if (type==0) {
            const int N=18; float pax,pay; rp(s*0.5f,0,pax,pay);
            for(int i=1;i<=N;i++){ float ang=(float)i/N*2*(float)M_PI; float qx,qy; rp(cosf(ang)*s*0.5f,sinf(ang)*s*0.5f,qx,qy); line(pax,pay,qx,qy,R,G,B,a); pax=qx;pay=qy; }
        } else if (type==1) {
            float a0x,a0y,a1x,a1y,b0x,b0y,b1x,b1y;
            rp(-s*0.4f,-s*0.4f,a0x,a0y); rp(s*0.4f,s*0.4f,a1x,a1y);
            rp(s*0.4f,-s*0.4f,b0x,b0y); rp(-s*0.4f,s*0.4f,b1x,b1y);
            line(a0x,a0y,a1x,a1y,R,G,B,a); line(b0x,b0y,b1x,b1y,R,G,B,a);
        } else if (type==2) {
            float c0x,c0y,c1x,c1y,c2x,c2y,c3x,c3y;
            rp(-s*0.4f,-s*0.4f,c0x,c0y); rp(s*0.4f,-s*0.4f,c1x,c1y);
            rp(s*0.4f,s*0.4f,c2x,c2y); rp(-s*0.4f,s*0.4f,c3x,c3y);
            line(c0x,c0y,c1x,c1y,R,G,B,a); line(c1x,c1y,c2x,c2y,R,G,B,a);
            line(c2x,c2y,c3x,c3y,R,G,B,a); line(c3x,c3y,c0x,c0y,R,G,B,a);
        } else {
            float t0x,t0y,t1x,t1y,t2x,t2y;
            rp(0,-s*0.5f,t0x,t0y); rp(s*0.46f,s*0.4f,t1x,t1y); rp(-s*0.46f,s*0.4f,t2x,t2y);
            line(t0x,t0y,t1x,t1y,R,G,B,a); line(t1x,t1y,t2x,t2y,R,G,B,a); line(t2x,t2y,t0x,t0y,R,G,B,a);
        }
    };
    for (int i = 0; i < 32; i++) {
        const PspEGlyph& g = sEGlyphs[i];
        float lt = (reveal - g.start) / (0.87f - g.start);
        if (lt <= 0.0f) continue;
        float t = std::min(1.0f, lt);
        float dist = g.Rmax * e3(t);
        float ang = g.ang + angOffset;
        float gx = 240.0f + cosf(ang)*dist + sinf(g.wob + t*6.28f)*g.wobA*t;
        float gy = 122.0f + sinf(ang)*dist;
        float fade = std::min(1.0f, t/0.14f) * (t < 0.55f ? 1.0f : std::max(0.0f, 1.0f-(t-0.55f)/0.45f));
        float a = g.peak * fade * alphaMul;
        if (a <= 0.01f) continue;
        float sz = (7.0f + (g.size1-7.0f)*std::min(1.0f, t*3.0f)) * sc;
        float cx = ox + gx*sc, cy = oy + gy*sc;
        pspLensBow(mPspLensValid, mPspLensCx, mPspLensCy, mPspLensR, cx, cy, sz);
        glyph(cx, cy, sz, g.type, g.rot*t, a);
    }
    endSolidBatch();
}

// The 6 tumbling XMB category icons along their bezier-ish paths (spec 5.11).
void NanoMenu::pspClockEntranceIcons(float sc, float ox, float oy, float reveal,
                                     float alphaMul, uint32_t seed) {
    if (reveal <= 0.0f || alphaMul <= 0.002f) return;
    struct EIcon { int id; float emit; float px[4]; float py[4]; float s0,s1,peak,spin; };
    static const EIcon EI[6] = {
        {1,0.03f,{240,172,130,95},{122,137,190,196},24,55,1.00f,-0.85f},
        {2,0.04f,{240,300,360,432},{122,124,150,168},22,32,0.95f, 0.70f},
        {3,0.11f,{244,290,360,430},{124,145,170,176},22,30,0.85f, 0.55f},
        {4,0.17f,{240,224,204,198},{124,155,190,206},24,34,0.90f,-0.60f},
        {6,0.23f,{250,272,330,400},{122,140,170,190},24,40,0.62f, 0.95f},
        {5,0.28f,{254,268,300,316},{122,110,96,90},  28,46,0.68f,-0.45f},
    };
    const float ICON_LIFE = 0.26f;
    for (int ii = 0; ii < 6; ii++) {
        const EIcon& ic = EI[ii];
        float lt = (reveal - ic.emit) / ICON_LIFE;
        if (lt <= 0.0f || lt >= 1.0f) continue;
        GLuint tex = iconTexForIcon(ic.id);
        if (tex == 0) continue;
        float e = e3(lt), seg = e * 3.0f;
        int si = std::min(2, (int)seg); float sf = seg - si;
        float px = ic.px[si] + (ic.px[si+1]-ic.px[si])*sf;
        float py = ic.py[si] + (ic.py[si+1]-ic.py[si])*sf;
        // hashIconRnd (integer avalanche), same as index.html.
        uint32_t n = ((uint32_t)(seed) * 73856093u) ^ ((uint32_t)(ii+1) * 19349663u);
        n = (n ^ (n>>13)) * 0x85ebca6bu; float ra = n / 4294967296.0f;
        n = (n ^ (n>>16)) * 0xc2b2ae35u; float rb = n / 4294967296.0f;
        float rang = ra * 2.0f * (float)M_PI, rdist = 0.78f + rb*0.55f;
        float rct = cosf(rang), rst = sinf(rang);
        float cx0 = ic.px[0], cy0 = ic.py[0];
        float rx = (px-cx0)*rdist, ry = (py-cy0)*rdist;
        px = cx0 + rx*rct - ry*rst; py = cy0 + rx*rst + ry*rct;
        float sz = (ic.s0 + (ic.s1-ic.s0)*e) * sc;
        float fade = std::min(1.0f, lt/0.18f) * (lt < 0.6f ? 1.0f : std::max(0.0f, 1.0f-(lt-0.6f)/0.4f));
        float a = ic.peak * fade * alphaMul;
        if (a <= 0.01f) continue;
        float cx = ox + px*sc, cy = oy + py*sc, isz = sz;
        pspLensBow(mPspLensValid, mPspLensCx, mPspLensCy, mPspLensR, cx, cy, isz);
        float rot = ic.spin * e;
        // rotated textured quad (mTextProgram), additive.
        float hw = isz*0.5f, cr = cosf(rot), sr = sinf(rot);
        auto rc = [&](float lx,float ly,float&Ox,float&Oy){ Ox=cx+lx*cr-ly*sr; Oy=cy+lx*sr+ly*cr; };
        float ax,ay,bx,by,cx2,cy2,dxq,dyq;
        rc(-hw,-hw,ax,ay); rc(hw,-hw,bx,by); rc(hw,hw,cx2,cy2); rc(-hw,hw,dxq,dyq);
        auto toN=[&](float X,float Y,float&nx,float&ny){ nx=(X/mWidth)*2.0f-1.0f; ny=1.0f-(Y/mHeight)*2.0f; };
        float n0x,n0y,n1x,n1y,n2x,n2y,n3x,n3y;
        toN(ax,ay,n0x,n0y); toN(bx,by,n1x,n1y); toN(cx2,cy2,n2x,n2y); toN(dxq,dyq,n3x,n3y);
        GLfloat verts[]={n0x,n0y,n1x,n1y,n2x,n2y, n0x,n0y,n2x,n2y,n3x,n3y};
        GLfloat uvs[]={0,0, 1,0, 1,1, 0,0, 1,1, 0,1};
        GLfloat col[6*4]; for(int k=0;k<6;k++){col[k*4]=1;col[k*4+1]=1;col[k*4+2]=1;col[k*4+3]=a;}
        glUseProgram(mTextProgram);
        if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, tex);
        glUniform1i(mTextLocTexture, 0); glBindBuffer(GL_ARRAY_BUFFER, 0);
        glVertexAttribPointer(mTextLocPosition,2,GL_FLOAT,GL_FALSE,0,verts); glEnableVertexAttribArray(mTextLocPosition);
        glVertexAttribPointer(mTextLocTexCoord,2,GL_FLOAT,GL_FALSE,0,uvs); glEnableVertexAttribArray(mTextLocTexCoord);
        glVertexAttribPointer(mTextLocColor,4,GL_FLOAT,GL_FALSE,0,col); glEnableVertexAttribArray(mTextLocColor);
        glDrawArrays(GL_TRIANGLES,0,6);
        glDisableVertexAttribArray(mTextLocPosition); glDisableVertexAttribArray(mTextLocTexCoord); glDisableVertexAttribArray(mTextLocColor);
    }
}

// 16 ambient PS glyphs drifting rightward, visible once settled (spec 5.7).
void NanoMenu::pspClockAmbientGlyphs(float dtMs) {
    mPspGlyphBurst *= 0.992f;
    if (mPspDetailFade <= 0.002f) return;
    struct AG { float x,y,v,size,phase; int type; };
    static AG ag[16]; static bool agInit=false;
    if (!agInit) {
        unsigned int st=0x51ed270bu;
        auto R=[&](){ st^=st<<13; st^=st>>17; st^=st<<5; return (st&0xffffff)/(float)0x1000000; };
        for(int i=0;i<16;i++){ ag[i].x=R()*1.3f-0.15f; ag[i].y=0.18f+fmodf(i*0.05f,0.62f);
            ag[i].v=0.028f+R()*0.05f; ag[i].size=13.0f+R()*15.0f; ag[i].type=i%4; ag[i].phase=R()*6.283f; }
        agInit=true;
    }
    float sc = std::min(mWidth/480.0f, mHeight/272.0f);
    float ox=0, oy=(mHeight-272.0f*sc)*0.5f;
    const float hw=1.1f;   // #4: thicker stroke so the drifting glyphs read at the glow alpha
    auto line=[&](float x0,float y0,float x1,float y1,float r,float g,float b,float a){
        float ux=x1-x0,uy=y1-y0,L=sqrtf(ux*ux+uy*uy); if(L<1e-3f)return; float nx=-uy/L*hw,ny=ux/L*hw;
        drawTriangle(x0+nx,y0+ny,x1+nx,y1+ny,x1-nx,y1-ny,r,g,b,a); drawTriangle(x0+nx,y0+ny,x1-nx,y1-ny,x0-nx,y0-ny,r,g,b,a); };
    beginSolidBatch();   // 16 flat-colour drifting glyphs -> one draw (byte-identical additive)
    for(int i=0;i<16;i++){ AG& g=ag[i];
        g.x += g.v*(dtMs/1000.0f)*(0.5f+mPspGlyphBurst*2.0f);
        if(g.x>1.18f){ g.x=-0.18f; g.type=(g.type+1)%4; }
        g.phase += dtMs*0.0012f;
        float px=g.x*mWidth, py=(g.y*272.0f+sinf(g.phase)*6.0f)*sc+oy; (void)ox;
        float gs=g.size*sc;
        pspLensBow(mPspLensValid, mPspLensCx, mPspLensCy, mPspLensR, px, py, gs);
        float cl=clamp01(g.x);
        float a=(0.14f+mPspGlyphBurst*0.14f)*sinf(cl*(float)M_PI)*mPspDetailFade;   // #4: base 0.05->0.14 (clearly visible behind the settled clock)
        if(a<=0.008f) continue;
        const float R=205.0f/255.0f,G=238.0f/255.0f,B=255.0f/255.0f; float s=gs;
        if(g.type==0){ const int N=16; float pax=px+s*0.5f,pay=py; for(int k=1;k<=N;k++){ float an=(float)k/N*2*(float)M_PI; float qx=px+cosf(an)*s*0.5f,qy=py+sinf(an)*s*0.5f; line(pax,pay,qx,qy,R,G,B,a); pax=qx;pay=qy; } }
        else if(g.type==1){ line(px-s*0.4f,py-s*0.4f,px+s*0.4f,py+s*0.4f,R,G,B,a); line(px+s*0.4f,py-s*0.4f,px-s*0.4f,py+s*0.4f,R,G,B,a); }
        else if(g.type==2){ line(px-s*0.4f,py-s*0.4f,px+s*0.4f,py-s*0.4f,R,G,B,a); line(px+s*0.4f,py-s*0.4f,px+s*0.4f,py+s*0.4f,R,G,B,a); line(px+s*0.4f,py+s*0.4f,px-s*0.4f,py+s*0.4f,R,G,B,a); line(px-s*0.4f,py+s*0.4f,px-s*0.4f,py-s*0.4f,R,G,B,a); }
        else { line(px,py-s*0.5f,px+s*0.46f,py+s*0.4f,R,G,B,a); line(px+s*0.46f,py+s*0.4f,px-s*0.46f,py+s*0.4f,R,G,B,a); line(px-s*0.46f,py+s*0.4f,px,py-s*0.5f,R,G,B,a); }
    }
    endSolidBatch();
}

} // namespace android
