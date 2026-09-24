/*
 * Copyright (C) 2026 GammaOS
 *
 * GPU rasterizer for libdrastic's hi-res 3D path. libdrastic's geometry engine
 * writes screen-space polygons (512x384, already clipped and culled) into a
 * double-buffered raster bank; its CPU rasterizer (+0x5eebc on the 3D worker
 * thread) bins them into 32-line bands and fills them with up to three threads.
 * This module replaces that fill: the polygons are drawn with GLES 3 into a
 * 512x384 FBO on an EGL context owned by the worker thread, read back and
 * scattered into drastic's column de-interleaved output buffer, so the 2D
 * compositor, display capture and run-ahead see exactly the buffer they expect.
 *
 * Layout facts (libdrastic_arm64.so, md5 7c5f33a3), R = the render struct:
 *   S = R+0x3606e8    worker state: [0] gx-side pointer, +80 DISP3DCNT source,
 *                     +135 rear plane alpha, +136 bank byte, +151 geometry dirty
 *   regs = R+0x34eb40 3D regs: +0 DISP3DCNT, +4 clear colour (r6 g6 b6 a5 bytes),
 *                     +12 clear depth, +24 target buffer, +32 published, +40 last drawn
 *   gx = R+0x356cb0   raster bank base: vertices gx+0x9ad4 + bank*0x18004 (16 B),
 *                     opaque polys gx+0x39ae0 + bank*0x10008, translucent +0x59af0 (32 B),
 *                     gx[0x9acc] bit1 = W-buffering
 *   band lists        R+0x2856c0 (opaque), R+0x2916f0 (translucent): 12 bands of
 *                     u16 poly indices with the count at +0x1000 (stride 0x1004)
 *   output            0xc0000 bytes, pixel (x,y) at (2y + (x&1))*0x400 + (x>>1)*4,
 *                     bytes r6 g6 b6 a5
 */

#define LOG_TAG "GammaOSNano.Gpu3d"

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <EGL/eglext.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <string.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>
#include <math.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <thread>
#include <chrono>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <sys/mman.h>
#include <stdlib.h>
#include <algorithm>
#include <signal.h>
#include <pthread.h>
#include <sched.h>
#include <ucontext.h>
#include <dlfcn.h>
#if defined(__aarch64__)
#include <arm_neon.h>
#endif

namespace android {

extern "C" void gpu3dSetBandMask(uint32_t m);   // DrasticRunner.cpp (mode-5 band pipeline)
extern "C" void gpu3dSetPending(int p);         // DrasticRunner.cpp: compose hook keeps waiting on bands while set

namespace {

constexpr int kW = 512, kH = 384;

struct Vtx {
    float x, y, depth, w;   // screen px, depth 0..1, clip W (perspective-correct texel and colour interpolation; 1 = affine)
    float r, g, b;          // 0..63
    float s, t;             // texels
    int32_t tex0[4];        // layer, big array flag, tex w, tex h (w = 0: untextured)
    int32_t tex1[4];        // fmt | c0t<<3 | wrap<<4, palette row, poly alpha, mode
};

struct TexEntry {
    int layer = -1, big = 0, palRow = -1;       // current slot (copied from slot[cur])
    int direct = 0;                             // colour-per-texel entry (formats 5 and 7): layer in the RGBA arrays
    int sl[2] = {-1, -1}, sp[2] = {-1, -1}; int cur = 0;   // two slots, alternated on re-upload
    uint32_t texp = 0;
    uint64_t dataPtr = 0, palPtr = 0;
    uint32_t stamp = 0;
    uint32_t hash = 0;
    int w = 0, h = 0;
};
constexpr int kSmall = 256, kSmallLayers = 128, kBig = 1024, kBigLayers = 4, kPalRows = 512;
// Compressed (5) and direct colour (7) textures: drastic's cache keeps them decoded to one colour
// word per texel (its bytes-per-texel table, lib+0x10ebfc: 4 for both), r, g, b 6-bit and a 5-bit
// bytes like the palette words. They live in two RGBA8 arrays of their own.
constexpr int kDirLayers = 64, kDirBigLayers = 2;   // 64 small RGBA layers (16 MB): Mario Kart slot 0 needs about 45 entries

struct Upload { bool big; bool direct; bool dbg; int layer, palRow, w, h; std::vector<uint8_t> data; uint8_t pal[256 * 4]; };
static std::atomic<int> gDbgArm{0}; static int gDbgFrame = 0; static int gDbgHits = 0;   // polygon logging on the dumped frame
extern "C" void gpu3dDbgArm() { gDbgArm.store(1, std::memory_order_relaxed); }
static int gPersp = 1;    // sys gpu3d_persp: perspective-correct interpolation from the vertex W (default on)
static int gTexDbg = 0;   // sys gpu3d_texdump: log converted entries, their GL readback and the polygons using them
struct Stream { std::vector<Vtx> v[8]; };   // [deq | dwrite<<1 | nodiscard<<2]
// Shadow polygons (mode 3), in list order: id 0 = mask (stencil where the depth test fails),
// others draw where the stencil is set. Consecutive polygons of one kind form a segment.
struct ShadowSeg { uint32_t start, count; bool mask, deq; };
struct Job {
    Stream opaque, transl;
    std::vector<Vtx> shadow; std::vector<ShadowSeg> shadowSegs;
    std::vector<Upload> uploads;
    uint8_t* target = nullptr;
    uint32_t clearC = 0, clearD = 0, clearId = 0;
    uint32_t earlyMask = 0;   // bands marked complete at hand-off (no-wait policy)
    int fog = 0, fogShift = 0, fogOffset = 0; uint32_t fogColor = 0; int fogTable[32] = {}; int fogDelta[32] = {};
    bool decoupled = false;   // whole-frame job into a buffer the compositor is not reading
    bool cancel = false;      // dropped before rendering (a decoupled frame overtaken by a synchronous one)
    uint32_t presentSeqAtQueue = 0;   // presents seen when the job was queued (decoupled: run after the next one)
    bool edge = false;
    uint32_t edgeTbl[8] = {};
    size_t verts = 0;
};

struct Gpu3d {
    bool inited = false, failed = false;
    // GL thread hand-off: the worker builds a Job, the GL thread renders it and marks the bands
    std::thread glThread; bool threadStarted = false;
    std::mutex mtx; std::condition_variable cvSubmit, cvDone;
    Job* pending = nullptr;     // job being processed by the GL thread
    Job* rendering = nullptr;   // job inside renderJob (cannot be cancelled any more)
    Job* queue[2] = {}; int qHead = 0, qCount = 0;   // jobs handed to the GL thread, in order (decoupled mode: up to two)
    bool quit = false;
    Job jobs[3]; int jobIdx = 0;
    std::atomic<uint8_t*> latest{nullptr};   // newest fully rendered target buffer (decoupled mode)
    uint8_t* bufC = nullptr;                 // third target buffer: the GL thread never writes what the compositor reads
    std::atomic<bool> decoupledActive{false};
    uint32_t presentSeq = 0; std::condition_variable cvPresent;   // flips landed (gpu3dNotePresent)
    bool joinWanted = false;   // the worker is waiting for the queue: skip the present wait, render now
    int64_t sumPresentWaitUs = 0; uint32_t presentWaitTimeouts = 0, presentWaitSkips = 0;
    int64_t sumRoomUs = 0;                   // worker time spent waiting for room in the GL queue (decoupled mode)
    EGLDisplay dpy = EGL_NO_DISPLAY;
    EGLContext ctx = EGL_NO_CONTEXT;
    EGLSurface surf = EGL_NO_SURFACE;
    GLuint progNoFetch = 0, progTrivial = 0, progOpaque = 0, progExp6 = 0, progExp7 = 0, uPassNoFetch = 0;
    GLuint curProg = 0; bool curBlend = false;
    GLuint prog = 0, fbo = 0, colorTex = 0, depthRb = 0, vbo = 0, vao = 0, smallTex = 0, bigTex = 0, palTex = 0;
    GLuint attrTex = 0, edgeProg = 0, edgeFbo = 0, edgeTex = 0, edgeVao = 0;
    // 4x supersampling: a second set of targets at 1024x768 and a resolve pass into the 2x ones
    GLuint ssFbo = 0, ssColor = 0, ssAttr = 0, ssDepth = 0, ssEdgeFbo = 0, ssEdgeTex = 0, resolveProg = 0;
    GLuint msFbo = 0, msColor = 0, msDepth = 0; int msSamples = 0;   // 4x MSAA on the 2x target
    bool msImplicit = false;   // EXT_multisampled_render_to_texture: resolved in-tile, no blit
    int ss = 1;   // 1 plain, 2 supersampled 1024x768, 3 MSAA 4x at 512x384
    GLint uAlphaMulNoFetch = -1, uAlphaMulOpaque = -1;
    bool attrWanted = false;   // the current job runs the edge pass
    bool timerExt = false; GLuint tq = 0; int64_t sumGpuNs = 0; uint32_t gpuSamples = 0;
    GLint uEdgeTbl = -1, uEdgeClear = -1, uEdgeSize = -1;
    GLint uIdx = -1, uPal = -1, uPass = -1;
    // layer allocators: texels live in two 2D arrays (256x256 and 1024x1024 layers), palettes in
    // a 256x1 array; one layer per slot so an update never touches memory another layer uses
    uint32_t fogPolys = 0;   // polygons with the fog bit (census)
    int smallNext = 0, bigNext = 0, palNext = 0, dirNext = 0, dirBigNext = 0;
    std::vector<int> freeSmall, freeBig, freePal, freeDir, freeDirBig;   // layers released by evicted entries
    uint32_t atlasGen = 0;   // bumped by a reset or an eviction pass: cached TexEntry pointers are stale
    GLuint dirTex = 0, dirBigTex = 0;
    bool fbFetch = false;
    std::unordered_map<uint64_t, TexEntry> texCache;
    std::vector<Vtx> verts;
    std::vector<uint8_t> readback;
    uint32_t frame = 0;
    uint32_t glFrames = 0;
    // per-second diagnostics (GL thread): frames, first quarter max, job max, uploads
    int64_t secStart = 0, secFqMax = 0, secJobMax = 0; uint32_t secFrames = 0, secUploads = 0; int secLog = 0;
    int64_t sumUs = 0, maxUs = 0, sumSubmitUs = 0, sumGpuUs = 0, sumReadUs = 0, sumScatterUs = 0;
    uint32_t frames = 0;
    uint32_t texReuploads = 0;
    uint32_t lagFrames = 0, lagEnters = 0;   // adaptive no-wait policy stats
    int64_t sumBuildUs = 0, sumTexUs = 0, sumUploadUs = 0, sumDrawUs = 0, texUsFrame = 0, sumWaitUs = 0;
    Job* cur = nullptr;         // job being built on the worker thread
    // adaptive fallback: when the GL thread cannot keep a frame under the budget the scene
    // goes back to the CPU rasterizer for a while (GPU path off, retried later)
    float emaUs = 0.f, firstUs = 0.f; int64_t sumFirstUs = 0; uint32_t backoffFrames = 0, backoffs = 0, backoffElapsed = 0, overBudget = 0;
    size_t vertCount = 0;
    uint32_t modeCount[4] = {}, fmtCount[8] = {}, lastDisp3d = 0xffffffff;   // feature census
};

Gpu3d g;
volatile int gStage = 0;          // last stage reached in gpu3dFrame (crash diagnostics)
struct sigaction gPrevSegv{};
void segvHandler(int sig, siginfo_t* si, void* uc) {
    ucontext_t* u = static_cast<ucontext_t*>(uc);
    unsigned long pc = u ? u->uc_mcontext.pc : 0, lr = u ? u->uc_mcontext.regs[30] : 0;
    Dl_info di{}; const char* mod = dladdr((void*)pc, &di) && di.dli_fname ? di.dli_fname : "?";
    unsigned long off = di.dli_fbase ? pc - (unsigned long)di.dli_fbase : pc;
    ALOGE("gpu3d: SIGSEGV stage=%d addr=%p pc=%lx (%s+0x%lx) lr=%lx frame=%u", gStage, si ? si->si_addr : nullptr, pc, mod, off, lr, g.frame);
    sigaction(SIGSEGV, &gPrevSegv, nullptr);
    raise(sig);
}

inline int64_t nowUs() {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000 + ts.tv_nsec / 1000;
}
std::atomic<int> gGlStage{0};     // GL thread: 0 idle, 1 present wait, 10+c chunk c fence wait, 20+c chunk c readback, 30 whole-frame, 40 done
std::atomic<int64_t> gGlStageTs{0};
static void glStage(int st) { gGlStage.store(st, std::memory_order_relaxed); gGlStageTs.store(nowUs(), std::memory_order_relaxed); }
// Called by the compositor's band wait on a timeout: the GL thread's stage and how long it has been there.
// Lock the file-backed, non-writable mappings of one library as their pages fault in
// (MLOCK_ONFAULT: nothing is read ahead). Knob sys gpu3d_lock_libs 0 disables.
extern "C" void drasticLockLibrary(const char* nameSubstr) {
    if (!property_get_int32("sys.gammaos.drastic_nano.gpu3d_lock_libs", 1)) return;
    FILE* f = fopen("/proc/self/maps", "r"); if (!f) return;
    char line[512]; size_t total = 0; int segs = 0, fails = 0;
    while (fgets(line, sizeof line, f)) {
        if (!strstr(line, nameSubstr)) continue;
        unsigned long lo = 0, hi = 0; char perms[8] = {};
        if (sscanf(line, "%lx-%lx %7s", &lo, &hi, perms) != 3) continue;
        if (perms[1] == 'w') continue;   // data stays as it is
        if (mlock2(reinterpret_cast<void*>(lo), hi - lo, MLOCK_ONFAULT) == 0) { total += hi - lo; segs++; } else fails++;
    }
    fclose(f);
    ALOGI("gpu3d: locked %s on fault: %d segments, %zu kB (%d failed%s%s)", nameSubstr, segs, total / 1024, fails, fails ? ": " : "", fails ? strerror(errno) : "");
}
extern "C" void gpu3dStallReport(uint32_t bandMask, int pending) {
    static int64_t lastLog = 0; const int64_t now = nowUs();
    if (now - lastLog < 500000) return; lastLog = now;
    int qc; { std::lock_guard<std::mutex> lk(g.mtx); qc = g.qCount; }
    ALOGW("gpu3d: STALL compositor band wait timed out: bands %03x pending %d, GL stage %d for %.1f ms, queue %d, worker stage %d, decoupled %d",
          bandMask, pending, gGlStage.load(), (now - gGlStageTs.load()) / 1000.0, qc, gStage, g.decoupledActive.load() ? 1 : 0);
}
// Phase probe: presenter flip times (from the flip site) against our job start and first
// quarter ready times; dumps 20 consecutive frames once, after the 600th rendered frame.
namespace {
struct PhaseRec { int64_t jobStart, firstReady, jobEnd; };
PhaseRec gPhase[32]; int gPhaseN = 0; int64_t gPresentTs[64]; int gPresentN = 0; bool gPhaseDumped = false;
std::mutex gPhaseMtx;
}

const char* kVert = R"(#version 300 es
layout(location = 0) in vec4 aPos;   // x, y, depth, clip W
uniform vec2 uVtxShift;              // screen-space vertex shift in pixels (sampling position A/B knob)
layout(location = 1) in vec3 aCol;
layout(location = 2) in vec2 aUv;
layout(location = 3) in ivec4 aTex0;
layout(location = 4) in ivec4 aTex1;
out vec3 vCol;
out vec2 vUv;
out vec3 vColW;   // colour * W and W: their perspective-correct ratio is the affine interpolant (uInterp bits)
out vec3 vUvW;
flat out ivec4 vTex0;
flat out ivec4 vTex1;
void main() {
    vCol = aCol;
    vUv = aUv;
    vColW = aCol * aPos.w;
    vUvW = vec3(aUv * aPos.w, aPos.w);
    vTex0 = aTex0;
    vTex1 = aTex1;
    // Screen-space vertices with the DS clip W as the homogeneous coordinate: the GPU then
    // interpolates texels and colours perspective-correctly (attribute / W linear on screen) the
    // way the DS and drastic's rasterizer do, while depth (z / w) stays linear on screen.
    gl_Position = vec4(((aPos.x + uVtxShift.x) / 256.0 - 1.0) * aPos.w, ((aPos.y + uVtxShift.y) / 192.0 - 1.0) * aPos.w, (aPos.z * 2.0 - 1.0) * aPos.w, aPos.w);
}
)";

// The fragment shader keeps drastic's integer arithmetic: 6-bit colour lanes and
// 5-bit alpha, modulation (tex * (vertex + 1)) >> 6, alpha (texA * (polyA + 1)) >> 5,
// blend (src * (a + 1) + dst * (31 - a)) >> 5 with an untouched destination
// (alpha 0) overwritten. Output lanes are stored as value * 4 (colour) and
// value * 8 (alpha) in the RGBA8 target so the readback recovers them exactly.
const char* kFragHead = R"(#version 300 es
precision highp float;
precision highp int;
precision highp usampler2D;
precision highp usampler2DArray;
precision highp sampler2DArray;
in vec3 vCol;
in vec2 vUv;
in vec3 vColW;
in vec3 vUvW;
uniform int uInterp;   // bit 0: affine colour, bit 1: affine texcoords (A/B knob, default perspective)
uniform int uUvOff;    // A/B knob: sample the texcoord half a pixel off centre: bit 0 -x, bit 1 -y, bit 2 +x, bit 3 +y
flat in ivec4 vTex0;
flat in ivec4 vTex1;
uniform highp usampler2DArray uIdx;   // 256x256 layers
uniform highp usampler2DArray uBig;   // 1024x1024 layers
uniform highp sampler2DArray uDir;    // 256x256 RGBA layers (formats 5 and 7, decoded colours)
uniform highp sampler2DArray uDirBig; // 1024x1024 RGBA layers
uniform highp sampler2DArray uPal;    // 256x1 palette layers
uniform int uPass;         // 0 all fragments, 1 only alpha 31, 2 only alpha < 31
// Fog (DISP3DCNT bit 7), drastic's post pass folded into the fragment: uFog 1 colour and alpha,
// 2 alpha only; uFogParam = (shift, (0x400 >> shift) + offset); uFogColor r,g,b 6-bit, a 5-bit;
// uFogTable[i] = density i (0..127, 127 counts as 128), uFogDelta[i] = table[i+1] - table[i].
uniform int uFog;
uniform ivec2 uFogParam;
uniform ivec2 uFogTune;    // A/B knob: depth bias in 24-bit units, and 1 = truncate the fractional step instead of rounding
uniform vec4 uFogColor;
uniform int uFogTable[32];
uniform int uFogDelta[32];
uniform float uAlphaMul;   // alpha lane scale: 1/255 stores the 5-bit value, 1/31 makes it a blend factor
)";

const char* kFragBody = R"(
// All lane arithmetic is done in highp float with exact integer values (< 4096), which
// matches drastic's integer maths bit for bit and is much cheaper than int ops on Mali.
int wrapCoord(int v, int sizeLog, int rep, int flip) {
    int size = 1 << sizeLog;
    if (rep == 0) return clamp(v, 0, size - 1);
    int m = v & (size - 1);
    if (flip != 0 && ((v >> sizeLog) & 1) != 0) return size - 1 - m;
    return m;
}
void main() {
    float tr = 63.0, tg = 63.0, tb = 63.0, ta = 31.0;
    int fmt = vTex1.x & 7;
    float polyA = float(vTex1.z & 31);
    int fogPoly = (vTex1.z >> 8) & 1;
    int mode = vTex1.w;
    bool texOn = vTex0.z > 0;
    vec2 uv = ((uInterp & 2) != 0) ? vUvW.xy / vUvW.z : vUv;
    if (uUvOff != 0) {
        vec2 ddx = dFdx(uv), ddy = dFdy(uv);
        if ((uUvOff & 1) != 0) uv -= 0.5 * ddx;
        if ((uUvOff & 2) != 0) uv -= 0.5 * ddy;
        if ((uUvOff & 4) != 0) uv += 0.5 * ddx;
        if ((uUvOff & 8) != 0) uv += 0.5 * ddy;
    }
    if (texOn) {
        int c0t = (vTex1.x >> 3) & 1;
        int s = wrapCoord(int(floor(uv.x)), (vTex1.x >> 8) & 15, (vTex1.x >> 4) & 1, (vTex1.x >> 6) & 1);
        int t = wrapCoord(int(floor(uv.y)), (vTex1.x >> 12) & 15, (vTex1.x >> 5) & 1, (vTex1.x >> 7) & 1);
        if ((vTex0.y & 2) != 0) {
            vec4 dc = ((vTex0.y & 1) != 0) ? texelFetch(uDirBig, ivec3(s, t, vTex0.x), 0) : texelFetch(uDir, ivec3(s, t, vTex0.x), 0);
            tr = floor(dc.r * 255.0 + 0.5); tg = floor(dc.g * 255.0 + 0.5); tb = floor(dc.b * 255.0 + 0.5); ta = floor(dc.a * 255.0 + 0.5);
        } else {
        uint raw = ((vTex0.y & 1) != 0) ? texelFetch(uBig, ivec3(s, t, vTex0.x), 0).r : texelFetch(uIdx, ivec3(s, t, vTex0.x), 0).r;
        uint idx = raw;
        if (fmt == 1) { idx = raw & 31u; float a3 = float(raw >> 5u); ta = floor(a3 * 4.5); }
        else if (fmt == 6) { idx = raw & 7u; ta = float(raw >> 3u); }
        else if (fmt == 5 || fmt == 7) { ta = (raw == 0u) ? 0.0 : 31.0; }   // drastic's private palette: index 0 transparent
        else { ta = (raw == 0u && c0t != 0) ? 0.0 : 31.0; }
        vec4 pc = texelFetch(uPal, ivec3(int(idx & 255u), 0, vTex1.y), 0);
        tr = floor(pc.r * 255.0 + 0.5); tg = floor(pc.g * 255.0 + 0.5); tb = floor(pc.b * 255.0 + 0.5);
        if (fmt == 7 && raw != 0u) ta = floor(pc.a * 255.0 + 0.5);
        }
    }
    vec3 vcIn = ((uInterp & 1) != 0) ? vColW / vUvW.z : vCol;
    if ((uUvOff & 16) != 0) vcIn -= 0.5 * (dFdx(vcIn) + dFdy(vcIn));   // colour at the pixel corner too (bit 4)
    vec3 vc = floor(vcIn + 0.5);
    float r, gg, b;
    if (mode == 1 && texOn) {
        r = floor((tr * ta + vc.r * (31.0 - ta)) / 31.0); gg = floor((tg * ta + vc.g * (31.0 - ta)) / 31.0); b = floor((tb * ta + vc.b * (31.0 - ta)) / 31.0);
        ta = 31.0;
    } else {
        r = floor(tr * (vc.r + 1.0) * (1.0 / 64.0)); gg = floor(tg * (vc.g + 1.0) * (1.0 / 64.0)); b = floor(tb * (vc.b + 1.0) * (1.0 / 64.0));
    }
    float a = floor(ta * (polyA + 1.0) * (1.0 / 32.0));
    if (uFog != 0 && fogPoly != 0) {
        int dz = int(floor(gl_FragCoord.z * 16777215.0 + 0.5)) + uFogTune.x;
        int z16 = (dz >> 9) & 0x7fff;
        int t = z16 - uFogParam.y; if (t < 0) t = 0;
        int z2 = t << uFogParam.x; if (z2 > 0x7fff) z2 = 0x7fff;
        int idx = z2 >> 10, frac = z2 & 0x3ff;
        int d = (uFogTable[idx] + ((uFogDelta[idx] * frac + (uFogTune.y != 0 ? 0 : 512)) >> 10)) & 255;
        if (d == 127) d = 128;
        float fd = float(d);
        if (uFog == 1) {
            r = r + floor((uFogColor.r - r) * fd * (1.0 / 128.0));
            gg = gg + floor((uFogColor.g - gg) * fd * (1.0 / 128.0));
            b = b + floor((uFogColor.b - b) * fd * (1.0 / 128.0));
        }
        a = a + floor((uFogColor.a - a) * fd * (1.0 / 128.0));
    }
//DISCARD//
)";
const char* kFragDiscard = R"(
    if (a == 0.0) discard;
    if (uPass == 1 && a != 31.0) discard;
    if (uPass == 2 && a == 31.0) discard;
)";

const char* kFragFetch = R"(
    vec4 d = fragColor;
    float da = floor(d.a * 255.0 + 0.5);
    if (a < 31.0 && da > 0.0) {
        vec3 dc = floor(d.rgb * 255.0 + 0.5);
        float w1 = a + 1.0, w0 = 31.0 - a;
        r = floor((r * w1 + dc.r * w0) * (1.0 / 32.0)); gg = floor((gg * w1 + dc.g * w0) * (1.0 / 32.0)); b = floor((b * w1 + dc.b * w0) * (1.0 / 32.0));
        a = max(a, da);
    }
    fragColor = vec4(r, gg, b, a) * (1.0 / 255.0);
    // polygon id + 24-bit depth for the edge marking pass (only the opaque pass keeps this output)
    float dz = floor(gl_FragCoord.z * 16777215.0 + 0.5);
    float d0 = mod(dz, 256.0), d1 = mod(floor(dz / 256.0), 256.0), d2 = floor(dz / 65536.0);
    attrOut = vec4(float(((vTex1.x >> 16) & 63) + 1), d0, d1, d2) / 255.0;
}
)";

const char* kFragNoFetch = R"(
    fragColor = vec4(r / 255.0, gg / 255.0, b / 255.0, a * uAlphaMul);
    // polygon id + 24-bit depth for the edge marking pass (only the opaque pass keeps this output)
    float dz = floor(gl_FragCoord.z * 16777215.0 + 0.5);
    float d0 = mod(dz, 256.0), d1 = mod(floor(dz / 256.0), 256.0), d2 = floor(dz / 65536.0);
    attrOut = vec4(float(((vTex1.x >> 16) & 63) + 1), d0, d1, d2) / 255.0;
}
)";

// Edge marking (DISP3DCNT bit 5): an opaque pixel whose polygon id differs from a 4-neighbour
// and whose depth is nearer than that neighbour takes the edge colour of its id group;
// off-screen neighbours use the clear polygon id and clear depth.
const char* kEdgeVert = R"(#version 300 es
void main() {
    vec2 p = vec2((gl_VertexID == 1) ? 3.0 : -1.0, (gl_VertexID == 2) ? 3.0 : -1.0);
    gl_Position = vec4(p, 0.0, 1.0);
}
)";
// 2x2 box resolve of a supersampled frame: lanes are drastic's 6-bit values, averaged with
// rounding; alpha takes the maximum so a partially covered edge pixel stays opaque-ish.
const char* kResolveFrag = R"(#version 300 es
precision highp float;
uniform sampler2D uSrc;
out vec4 fragColor;
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy) * 2;
    vec4 a = texelFetch(uSrc, p, 0), b = texelFetch(uSrc, p + ivec2(1, 0), 0), c = texelFetch(uSrc, p + ivec2(0, 1), 0), d = texelFetch(uSrc, p + ivec2(1, 1), 0);
    vec3 sum = floor(a.rgb * 255.0 + 0.5) + floor(b.rgb * 255.0 + 0.5) + floor(c.rgb * 255.0 + 0.5) + floor(d.rgb * 255.0 + 0.5);
    vec3 rgb = floor((sum + 2.0) / 4.0);
    float al = max(max(a.a, b.a), max(c.a, d.a));
    fragColor = vec4(rgb / 255.0, al);
}
)";
const char* kEdgeFrag = R"(#version 300 es
precision highp float;
precision highp int;
uniform sampler2D uCol;
uniform sampler2D uAttr;
uniform vec4 uEdge[8];
uniform vec2 uClear;   // clear polygon id (0..63), clear depth (0..16777215)
uniform ivec2 uSize;
out vec4 fragColor;
float depthOf(vec4 a) { return floor(a.g * 255.0 + 0.5) + floor(a.b * 255.0 + 0.5) * 256.0 + floor(a.a * 255.0 + 0.5) * 65536.0; }
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    vec4 c = texelFetch(uCol, p, 0);
    vec4 a = texelFetch(uAttr, p, 0);
    int id = int(a.r * 255.0 + 0.5);
    if (id == 0) { fragColor = c; return; }
    id -= 1;
    float d = depthOf(a);
    bool edge = false;
    ivec2 offs[4] = ivec2[4](ivec2(-1, 0), ivec2(1, 0), ivec2(0, -1), ivec2(0, 1));
    for (int i = 0; i < 4; i++) {
        ivec2 q = p + offs[i];
        int id2; float d2;
        if (q.x < 0 || q.y < 0 || q.x >= uSize.x || q.y >= uSize.y) { id2 = int(uClear.x); d2 = uClear.y; }
        else {
            vec4 b = texelFetch(uAttr, q, 0);
            id2 = int(b.r * 255.0 + 0.5);
            if (id2 == 0) { id2 = int(uClear.x); d2 = uClear.y; } else { id2 -= 1; d2 = depthOf(b); }
        }
        if (id2 != id && d < d2) edge = true;
    }
    fragColor = edge ? vec4(uEdge[id >> 3].rgb, c.a) : c;
}
)";

GLuint compile(GLenum type, const std::string& src) {
    GLuint sh = glCreateShader(type);
    const char* p = src.c_str();
    glShaderSource(sh, 1, &p, nullptr);
    glCompileShader(sh);
    GLint ok = 0; glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[2048] = {}; glGetShaderInfoLog(sh, sizeof log, nullptr, log);
        ALOGE("gpu3d: shader compile failed: %s", log);
        glDeleteShader(sh); return 0;
    }
    return sh;
}

bool initGl() {
    if (g.inited) return true;
    if (g.failed) return false;
    g.failed = true;   // cleared on success
    g.dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g.dpy == EGL_NO_DISPLAY || !eglInitialize(g.dpy, nullptr, nullptr)) {
        ALOGE("gpu3d: eglInitialize failed 0x%x", eglGetError()); return false;
    }
    const EGLint cfgAttrs[] = { EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                                EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE };
    EGLConfig cfg; EGLint n = 0;
    if (!eglChooseConfig(g.dpy, cfgAttrs, &cfg, 1, &n) || n < 1) { ALOGE("gpu3d: no ES3 pbuffer config"); return false; }
    const EGLint pb[] = { EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE };
    g.surf = eglCreatePbufferSurface(g.dpy, cfg, pb);
    // The GPU is shared with drastic-nano's presenter (video filter at panel resolution on two
    // panels). Progressive (wait) mode wants the 3D job ahead of the presenter so the first band
    // chunk lands early: high priority. Decoupled mode (the 4x default) renders a frame ahead and
    // the presenter is the latency-critical client: low priority, otherwise an 8 ms whole-frame
    // job delays the presenter past the vblank and the flips land a frame late (measured 42 fps
    // presented with the emulator at 60). sys gpu3d_prio: 1 high, 2 low, 0 default (medium).
    const char* eglExt = eglQueryString(g.dpy, EGL_EXTENSIONS);
    const bool hasPrio = eglExt && strstr(eglExt, "EGL_IMG_context_priority");
    int prioSel = property_get_int32("sys.gammaos.drastic_nano.gpu3d_prio", -1);
    if (prioSel < 0) {
        // Decoupled is the default at both settings now, so LOW unless the wait policy is forced.
        // (Left on the old 4x-only rule the 2x path got HIGH: Pokemon 59.2 / 55.5 with 25 long-flip
        // seconds per 90 against 59.8 / 59.3 with LOW.)
        const int dk = property_get_int32("sys.gammaos.drastic_nano.gpu3d_decouple", -1);
        prioSel = (dk >= 0 ? dk != 0 : true) ? 2 : 1;
    }
    const bool prio = hasPrio && prioSel != 0;
    const EGLint caPrio[] = { EGL_CONTEXT_CLIENT_VERSION, 3, 0x3100 /* EGL_CONTEXT_PRIORITY_LEVEL_IMG */, prioSel == 2 ? 0x3103 /* LOW */ : 0x3101 /* HIGH */, EGL_NONE };
    const EGLint ca[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    g.ctx = eglCreateContext(g.dpy, cfg, EGL_NO_CONTEXT, prio ? caPrio : ca);
    if (g.ctx == EGL_NO_CONTEXT && prio) g.ctx = eglCreateContext(g.dpy, cfg, EGL_NO_CONTEXT, ca);
    ALOGI("gpu3d: context priority extension %d, level %s", hasPrio ? 1 : 0, prioSel == 2 ? "low" : prioSel == 1 ? "high" : "default");
    if (g.surf == EGL_NO_SURFACE || g.ctx == EGL_NO_CONTEXT) { ALOGE("gpu3d: context/surface failed 0x%x", eglGetError()); return false; }
    if (!eglMakeCurrent(g.dpy, g.surf, g.surf, g.ctx)) { ALOGE("gpu3d: eglMakeCurrent failed 0x%x", eglGetError()); return false; }
    const char* ext = (const char*)glGetString(GL_EXTENSIONS);
    g.fbFetch = ext && strstr(ext, "GL_EXT_shader_framebuffer_fetch");
    g.timerExt = ext && strstr(ext, "GL_EXT_disjoint_timer_query");
    ALOGI("gpu3d: %s / %s, framebuffer fetch %d, timer query %d", glGetString(GL_RENDERER), glGetString(GL_VERSION), g.fbFetch, g.timerExt);

    auto build = [&](bool fetch, bool trivial, bool noDiscard = false, int expt = 0) -> GLuint {
        std::string frag = kFragHead;
        if (fetch) frag += "#extension GL_EXT_shader_framebuffer_fetch : require\nlayout(location = 0) inout vec4 fragColor;\n";
        else frag += "layout(location = 0) out vec4 fragColor;\n";
        frag += "layout(location = 1) out vec4 attrOut;\n";
        if (trivial) frag += "void main() { fragColor = vec4(vCol / 255.0, 31.0 / 255.0); attrOut = vec4(0.0); }\n";
        else {
            std::string body = kFragBody;
            size_t m = body.find("//DISCARD//");
            body.replace(m, 11, noDiscard ? "" : kFragDiscard);
            auto swapStmt = [&](const char* anchor, const char* repl) {
                size_t q = body.find(anchor);
                if (q == std::string::npos) { ALOGE("gpu3d: experiment anchor missing: %s", anchor); return; }
                body.replace(q, body.find(';', q) + 1 - q, repl);
            };
            if (expt == 6) {   // keep the index fetch, drop the palette fetch
                swapStmt("vec4 pc = texelFetch(uPal", "vec4 pc = vec4(float(idx & 63u) / 255.0, 0.1, 0.2, 1.0);");
            } else if (expt == 7) {   // no texture fetch at all
                swapStmt("uint raw = (vTex0.y != 0)", "uint raw = uint(s + t) & 255u;");
                swapStmt("vec4 pc = texelFetch(uPal", "vec4 pc = vec4(float(idx & 63u) / 255.0, 0.1, 0.2, 1.0);");
            }
            frag += body; frag += fetch ? kFragFetch : kFragNoFetch;
        }
        if (fetch) {   // #extension must precede other declarations: move it right after #version
            size_t p = frag.find("#extension"); std::string e = frag.substr(p, frag.find('\n', p) - p + 1);
            frag.erase(p, e.size());
            size_t v = frag.find('\n') + 1; frag.insert(v, e);
        }
        GLuint vs = compile(GL_VERTEX_SHADER, kVert), fs = compile(GL_FRAGMENT_SHADER, frag);
        if (!vs || !fs) return 0;
        GLuint pr = glCreateProgram(); glAttachShader(pr, vs); glAttachShader(pr, fs); glLinkProgram(pr);
        GLint ok = 0; glGetProgramiv(pr, GL_LINK_STATUS, &ok);
        if (!ok) { char log[2048] = {}; glGetProgramInfoLog(pr, sizeof log, nullptr, log); ALOGE("gpu3d: link failed: %s", log); return 0; }
        glUseProgram(pr);
        glUniform1i(glGetUniformLocation(pr, "uDir"), 5); glUniform1i(glGetUniformLocation(pr, "uDirBig"), 6);
        glUniform1i(glGetUniformLocation(pr, "uIdx"), 0); glUniform1i(glGetUniformLocation(pr, "uPal"), 1); glUniform1i(glGetUniformLocation(pr, "uBig"), 2);
        GLint am = glGetUniformLocation(pr, "uAlphaMul"); if (am >= 0) glUniform1f(am, 1.0f / 255.0f);
        return pr;
    };
    g.prog = build(g.fbFetch, false);
    g.progNoFetch = build(false, false);
    g.progTrivial = build(false, true);
    g.progOpaque = build(false, false, true);   // no discard, no fetch: early depth rejection works
    g.progExp6 = build(g.fbFetch, false, false, 6); g.progExp7 = build(g.fbFetch, false, false, 7);
    if (!g.prog || !g.progNoFetch || !g.progTrivial || !g.progOpaque) return false;
    g.uAlphaMulNoFetch = glGetUniformLocation(g.progNoFetch, "uAlphaMul");
    g.uAlphaMulOpaque = glGetUniformLocation(g.progOpaque, "uAlphaMul");
    glUseProgram(g.prog);
    g.uPass = glGetUniformLocation(g.prog, "uPass");
    glUniform1i(g.uPass, 0);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    auto arr = [](GLuint& tex, GLenum unit, GLenum ifmt, int w, int h, int layers) {
        glGenTextures(1, &tex); glActiveTexture(unit); glBindTexture(GL_TEXTURE_2D_ARRAY, tex);
        glTexStorage3D(GL_TEXTURE_2D_ARRAY, 1, ifmt, w, h, layers);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    };
    arr(g.smallTex, GL_TEXTURE0, GL_R8UI, kSmall, kSmall, kSmallLayers);
    arr(g.palTex, GL_TEXTURE1, GL_RGBA8, 256, 1, kPalRows);
    arr(g.bigTex, GL_TEXTURE2, GL_R8UI, kBig, kBig, kBigLayers);
    arr(g.dirTex, GL_TEXTURE5, GL_RGBA8, kSmall, kSmall, kDirLayers);
    arr(g.dirBigTex, GL_TEXTURE6, GL_RGBA8, kBig, kBig, kDirBigLayers);
    glGenTextures(1, &g.colorTex); glBindTexture(GL_TEXTURE_2D, g.colorTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, kW, kH, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glGenRenderbuffers(1, &g.depthRb); glBindRenderbuffer(GL_RENDERBUFFER, g.depthRb);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, kW, kH);
    glGenTextures(1, &g.attrTex); glBindTexture(GL_TEXTURE_2D, g.attrTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, kW, kH, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glGenFramebuffers(1, &g.fbo); glBindFramebuffer(GL_FRAMEBUFFER, g.fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g.colorTex, 0);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, g.attrTex, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, g.depthRb);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { ALOGE("gpu3d: FBO incomplete"); return false; }
    // edge marking pass target + program
    glGenTextures(1, &g.edgeTex); glBindTexture(GL_TEXTURE_2D, g.edgeTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, kW, kH, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glGenFramebuffers(1, &g.edgeFbo); glBindFramebuffer(GL_FRAMEBUFFER, g.edgeFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g.edgeTex, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { ALOGE("gpu3d: edge FBO incomplete"); return false; }
    {
        GLuint vs = compile(GL_VERTEX_SHADER, kEdgeVert), fs = compile(GL_FRAGMENT_SHADER, kEdgeFrag);
        if (!vs || !fs) return false;
        g.edgeProg = glCreateProgram(); glAttachShader(g.edgeProg, vs); glAttachShader(g.edgeProg, fs); glLinkProgram(g.edgeProg);
        GLint ok = 0; glGetProgramiv(g.edgeProg, GL_LINK_STATUS, &ok);
        if (!ok) { char log[2048] = {}; glGetProgramInfoLog(g.edgeProg, sizeof log, nullptr, log); ALOGE("gpu3d: edge link failed: %s", log); return false; }
        glUseProgram(g.edgeProg);
        glUniform1i(glGetUniformLocation(g.edgeProg, "uCol"), 3); glUniform1i(glGetUniformLocation(g.edgeProg, "uAttr"), 4);
        g.uEdgeTbl = glGetUniformLocation(g.edgeProg, "uEdge"); g.uEdgeClear = glGetUniformLocation(g.edgeProg, "uClear"); g.uEdgeSize = glGetUniformLocation(g.edgeProg, "uSize");
        glGenVertexArrays(1, &g.edgeVao);
    }
    {
        auto tex2d = [](GLuint& t, int w, int h) {
            glGenTextures(1, &t); glBindTexture(GL_TEXTURE_2D, t);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        };
        tex2d(g.ssColor, kW * 2, kH * 2); tex2d(g.ssAttr, kW * 2, kH * 2); tex2d(g.ssEdgeTex, kW * 2, kH * 2);
        glGenRenderbuffers(1, &g.ssDepth); glBindRenderbuffer(GL_RENDERBUFFER, g.ssDepth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, kW * 2, kH * 2);
        glGenFramebuffers(1, &g.ssFbo); glBindFramebuffer(GL_FRAMEBUFFER, g.ssFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g.ssColor, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, g.ssAttr, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, g.ssDepth);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { ALOGE("gpu3d: 4x FBO incomplete"); return false; }
        glGenFramebuffers(1, &g.ssEdgeFbo); glBindFramebuffer(GL_FRAMEBUFFER, g.ssEdgeFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g.ssEdgeTex, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { ALOGE("gpu3d: 4x edge FBO incomplete"); return false; }
        GLint maxS = 0; glGetIntegerv(GL_MAX_SAMPLES, &maxS);
        g.msSamples = maxS >= 4 ? 4 : maxS;
        const char* glext = (const char*)glGetString(GL_EXTENSIONS);
        auto pFbTex2DMs = (PFNGLFRAMEBUFFERTEXTURE2DMULTISAMPLEEXTPROC)eglGetProcAddress("glFramebufferTexture2DMultisampleEXT");
        auto pRbMsExt = (PFNGLRENDERBUFFERSTORAGEMULTISAMPLEEXTPROC)eglGetProcAddress("glRenderbufferStorageMultisampleEXT");
        g.msImplicit = glext && strstr(glext, "GL_EXT_multisampled_render_to_texture") && pFbTex2DMs && pRbMsExt;
        if (g.msSamples >= 2 && g.msImplicit) {
            // colour resolves into the plain colorTex at tile write-out; only depth is multisampled
            glGenRenderbuffers(1, &g.msDepth); glBindRenderbuffer(GL_RENDERBUFFER, g.msDepth);
            pRbMsExt(GL_RENDERBUFFER, g.msSamples, GL_DEPTH24_STENCIL8, kW, kH);
            glGenFramebuffers(1, &g.msFbo); glBindFramebuffer(GL_FRAMEBUFFER, g.msFbo);
            pFbTex2DMs(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g.colorTex, 0, g.msSamples);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, g.msDepth);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { ALOGW("gpu3d: implicit MSAA FBO incomplete, falling back to explicit"); g.msImplicit = false; glDeleteFramebuffers(1, &g.msFbo); g.msFbo = 0; }
        }
        if (g.msSamples >= 2 && !g.msImplicit) {
            glGenRenderbuffers(1, &g.msColor); glBindRenderbuffer(GL_RENDERBUFFER, g.msColor);
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, g.msSamples, GL_RGBA8, kW, kH);
            glGenRenderbuffers(1, &g.msDepth); glBindRenderbuffer(GL_RENDERBUFFER, g.msDepth);
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, g.msSamples, GL_DEPTH24_STENCIL8, kW, kH);
            glGenFramebuffers(1, &g.msFbo); glBindFramebuffer(GL_FRAMEBUFFER, g.msFbo);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, g.msColor);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, g.msDepth);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { ALOGW("gpu3d: MSAA FBO incomplete, MSAA off"); g.msSamples = 0; }
        }
        ALOGI("gpu3d: MSAA implicit resolve %d", g.msImplicit);
        ALOGI("gpu3d: MSAA samples %d (max %d)", g.msSamples, maxS);
        GLuint vs = compile(GL_VERTEX_SHADER, kEdgeVert), fs = compile(GL_FRAGMENT_SHADER, kResolveFrag);
        if (!vs || !fs) return false;
        g.resolveProg = glCreateProgram(); glAttachShader(g.resolveProg, vs); glAttachShader(g.resolveProg, fs); glLinkProgram(g.resolveProg);
        GLint ok = 0; glGetProgramiv(g.resolveProg, GL_LINK_STATUS, &ok);
        if (!ok) { ALOGE("gpu3d: resolve link failed"); return false; }
        glUseProgram(g.resolveProg); glUniform1i(glGetUniformLocation(g.resolveProg, "uSrc"), 3);
    }
    glGenVertexArrays(1, &g.vao); glBindVertexArray(g.vao);
    glGenBuffers(1, &g.vbo); glBindBuffer(GL_ARRAY_BUFFER, g.vbo);
    glEnableVertexAttribArray(0); glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE, sizeof(Vtx), (void*)0);
    glEnableVertexAttribArray(1); glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, sizeof(Vtx), (void*)(4 * sizeof(float)));
    glEnableVertexAttribArray(2); glVertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, sizeof(Vtx), (void*)(7 * sizeof(float)));
    glEnableVertexAttribArray(3); glVertexAttribIPointer(3, 4, GL_INT, sizeof(Vtx), (void*)(9 * sizeof(float)));
    glEnableVertexAttribArray(4); glVertexAttribIPointer(4, 4, GL_INT, sizeof(Vtx), (void*)(9 * sizeof(float) + 4 * sizeof(int32_t)));
    glViewport(0, 0, kW, kH);
    glEnable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST); glDisable(GL_CULL_FACE); glDisable(GL_DITHER);
    if (!g.fbFetch) { glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); }
    g.readback.resize(kW * kH * 4);
    g.verts.reserve(65536);
    { struct sigaction sa{}; sa.sa_sigaction = segvHandler; sa.sa_flags = SA_SIGINFO | SA_NODEFER; sigemptyset(&sa.sa_mask);
      sigaction(SIGSEGV, &sa, &gPrevSegv); }
    // Warm-up: the driver allocates the render targets, the texture arrays and the pipelines
    // on first use, which cost 755 ms on the first real frame; do it now on the GL thread.
    {
        const int64_t w0 = nowUs();
        Vtx tri[3] = {};
        tri[0].x = 0; tri[0].y = 0; tri[1].x = 512; tri[1].y = 0; tri[2].x = 0; tri[2].y = 384;
        for (int i = 0; i < 3; i++) { tri[i].depth = 0.5f; tri[i].w = 1.f; tri[i].r = tri[i].g = tri[i].b = 63.f; tri[i].tex0[2] = 0; }
        std::vector<uint8_t> z((size_t)kSmall * kSmall, 0);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D_ARRAY, g.smallTex);
        for (int l = 0; l < kSmallLayers; l++) glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, l, kSmall, kSmall, 1, GL_RED_INTEGER, GL_UNSIGNED_BYTE, z.data());
        std::vector<uint8_t> zb((size_t)kBig * kBig, 0);
        glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D_ARRAY, g.bigTex);
        for (int l = 0; l < kBigLayers; l++) glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, l, kBig, kBig, 1, GL_RED_INTEGER, GL_UNSIGNED_BYTE, zb.data());
        { std::vector<uint8_t> zd((size_t)kSmall * kSmall * 4, 0);
          glActiveTexture(GL_TEXTURE5); glBindTexture(GL_TEXTURE_2D_ARRAY, g.dirTex);
          for (int l = 0; l < kDirLayers; l++) glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, l, kSmall, kSmall, 1, GL_RGBA, GL_UNSIGNED_BYTE, zd.data());
          std::vector<uint8_t> zdb((size_t)kBig * kBig * 4, 0);
          glActiveTexture(GL_TEXTURE6); glBindTexture(GL_TEXTURE_2D_ARRAY, g.dirBigTex);
          for (int l = 0; l < kDirBigLayers; l++) glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, l, kBig, kBig, 1, GL_RGBA, GL_UNSIGNED_BYTE, zdb.data()); }
        uint8_t pal[1024] = {};
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D_ARRAY, g.palTex);
        for (int l = 0; l < kPalRows; l++) glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, l, 256, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pal);
        for (GLuint fb : { g.fbo, g.ssFbo }) {
            glBindFramebuffer(GL_FRAMEBUFFER, fb);
            glViewport(0, 0, fb == g.ssFbo ? kW * 2 : kW, fb == g.ssFbo ? kH * 2 : kH);
            const GLenum bufs[2] = { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 };
            glDrawBuffers(2, bufs);
            glClearColor(0, 0, 0, 0); glClearDepthf(1.f); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            glBindVertexArray(g.vao); glBindBuffer(GL_ARRAY_BUFFER, g.vbo);
            glBufferData(GL_ARRAY_BUFFER, sizeof tri, tri, GL_STREAM_DRAW);
            for (GLuint pr : { g.prog, g.progNoFetch, g.progOpaque, g.progTrivial }) { glUseProgram(pr); glDrawArrays(GL_TRIANGLES, 0, 3); }
            glReadPixels(0, 0, 8, 8, GL_RGBA, GL_UNSIGNED_BYTE, g.readback.data());
        }
        if (g.msFbo) {   // the MSAA target too, read back exactly as renderJob does, full size:
            // the 8x8 read left the first real frame's full readback to allocate the resolve
            // path on the fly (measured 7.8 s in the first GL job of a session).
            glBindFramebuffer(GL_FRAMEBUFFER, g.msFbo); glViewport(0, 0, kW, kH);
            { const GLenum b1[1] = { GL_COLOR_ATTACHMENT0 }; glDrawBuffers(1, b1); }
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            glBindVertexArray(g.vao); glUseProgram(g.progNoFetch); glDrawArrays(GL_TRIANGLES, 0, 3);
            if (!g.msImplicit) {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, g.msFbo); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, g.fbo);
                glBlitFramebuffer(0, 0, kW, kH, 0, 0, kW, kH, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            }
            glBindFramebuffer(GL_READ_FRAMEBUFFER, g.fbo);
            glReadPixels(0, 0, kW, kH, GL_RGBA, GL_UNSIGNED_BYTE, g.readback.data());
        }
        // Full-size read of the plain target as well (the 2x path and the no-MSAA fallback).
        glBindFramebuffer(GL_FRAMEBUFFER, g.fbo); glBindFramebuffer(GL_READ_FRAMEBUFFER, g.fbo);
        glReadPixels(0, 0, kW, kH, GL_RGBA, GL_UNSIGNED_BYTE, g.readback.data());
        glFinish();
        glBindFramebuffer(GL_FRAMEBUFFER, g.edgeFbo); glUseProgram(g.edgeProg); glBindVertexArray(g.edgeVao); glDrawArrays(GL_TRIANGLES, 0, 3);
        glUseProgram(g.resolveProg); glDrawArrays(GL_TRIANGLES, 0, 3);
        glReadPixels(0, 0, 8, 8, GL_RGBA, GL_UNSIGNED_BYTE, g.readback.data());
        glViewport(0, 0, kW, kH);
        ALOGI("gpu3d: warm-up took %.1f ms", (nowUs() - w0) / 1000.0);
    }
    drasticLockLibrary("libGLES_mali.so");
    g.failed = false; g.inited = true;
    return true;
}

// Texture cache entry (0x2a0 bytes at the polygon's +16 pointer): +0 texparam,
// +16 raw texel bytes (one per texel for every palette format), +24 palette u32s.
// Texels go into layers of two R8UI 2D arrays (256 and 1024 square), palettes into 256x1 layers.
void resetAtlas() {
    g.texCache.clear(); g.smallNext = g.bigNext = g.palNext = g.dirNext = g.dirBigNext = 0;
    g.freeSmall.clear(); g.freeBig.clear(); g.freePal.clear(); g.freeDir.clear(); g.freeDirBig.clear();
    g.atlasGen++;
}
static bool takeLayer(std::vector<int>& freeList, int& next, int limit, int& out) {
    if (!freeList.empty()) { out = freeList.back(); freeList.pop_back(); return true; }
    if (next >= limit) return false;
    out = next++; return true;
}
bool allocLayer(bool big, int& layer, int& pal, bool direct = false) {
    if (direct) {
        pal = 0;
        return big ? takeLayer(g.freeDirBig, g.dirBigNext, kDirBigLayers, layer) : takeLayer(g.freeDir, g.dirNext, kDirLayers, layer);
    }
    if (!takeLayer(g.freePal, g.palNext, kPalRows, pal)) return false;
    if (!(big ? takeLayer(g.freeBig, g.bigNext, kBigLayers, layer) : takeLayer(g.freeSmall, g.smallNext, kSmallLayers, layer))) { g.freePal.push_back(pal); return false; }
    return true;
}
// Release both slots of an entry back to the free lists.
static void releaseEntry(const TexEntry& t) {
    for (int i = 0; i < 2; i++) {
        if (t.sl[i] < 0) continue;
        if (t.direct) (t.big ? g.freeDirBig : g.freeDir).push_back(t.sl[i]);
        else { (t.big ? g.freeBig : g.freeSmall).push_back(t.sl[i]); if (t.sp[i] >= 0) g.freePal.push_back(t.sp[i]); }
    }
}
// Layers ran out: drop every entry not used for a while (drastic recreates cache entries at new
// addresses as VRAM changes, so the map fills with dead pointers; Mario Kart tripped a full reset
// every few hundred frames, re-uploading every live texture). Returns the number evicted.
static size_t evictStale(uint32_t minAge) {
    size_t n = 0;
    for (auto it = g.texCache.begin(); it != g.texCache.end();) {
        if (it->second.stamp + minAge < g.frame) { releaseEntry(it->second); it = g.texCache.erase(it); n++; }
        else ++it;
    }
    g.atlasGen++;
    return n;
}
TexEntry* texFor(uint64_t entryPtr, uint32_t texp) {
    entryPtr &= 0x00ffffffffffffffull;
    if (!entryPtr) return nullptr;
    const uint8_t* e = reinterpret_cast<const uint8_t*>(entryPtr);
    uint64_t dataPtr = *reinterpret_cast<const uint64_t*>(e + 16) & 0x00ffffffffffffffull;
    uint64_t palPtr = *reinterpret_cast<const uint64_t*>(e + 24) & 0x00ffffffffffffffull;
    int w = 8 << ((texp >> 20) & 7), h = 8 << ((texp >> 23) & 7);
    int fmt = (texp >> 26) & 7;
    // Compressed (5) and direct colour (7) entries: drastic's fill (+0x70f5c, decoder +0x708f0)
    // first expands every texel to a colour word, then, when the texture has at most 256
    // distinct colours, converts the words in place to one palette index per texel with a
    // private palette at +24 (count at +0x48, index 0 = transparent) and sets the entry's
    // format byte at +0x4b to 8. Only a texture with more colours keeps the colour words.
    const int stored = e[0x4b];
    const bool converted = (fmt == 5 || fmt == 7) && stored == 8;
    const bool direct = (fmt == 5 || fmt == 7) && !converted;
    const int convPal = converted ? *reinterpret_cast<const uint16_t*>(e + 0x48) : 0;
    if (!dataPtr || (!palPtr && !direct) || (converted && (convPal < 1 || convPal > 256))) return nullptr;
    TexEntry& t = g.texCache[entryPtr];
    // layers are fixed size, so an entry that changes dimensions keeps its layers unless it
    // crosses between the small and big arrays
    const bool bigNow = w > kSmall || h > kSmall;
    bool fresh = t.layer < 0 || t.big != (int)bigNow || t.direct != (int)direct;
    if (fresh && t.layer >= 0) { releaseEntry(t); t.sl[0] = t.sl[1] = t.sp[0] = t.sp[1] = -1; t.layer = -1; }   // resized: give the old slots back
    // Re-upload when the entry changed identity or its content hash (32 texel words spread over
    // the data plus the first 8 palette words) changed. A periodic refresh is far too costly:
    // a glTexSubImage into an atlas the previous job still reads makes Mali copy the atlas.
    // Content hash: every texel word of a texture up to 16 KB (the common DS sizes), 256 sampled
    // words above that, plus the WHOLE palette the format uses. The old 32-word sample with 8
    // palette words could miss a streamed animation frame or a palette change past entry 8
    // (pal16 has 16 entries, pal256 has 256): stale texels or colours on a re-used entry.
    const int palWords = converted ? convPal : ((fmt == 4 || fmt == 7) ? 256 : (fmt == 3 ? 16 : (fmt == 2 ? 4 : (fmt == 6 ? 8 : 32))));
    uint32_t hsh = 2166136261u;
    {
        const uint32_t* d = reinterpret_cast<const uint32_t*>(dataPtr);
        const size_t words = direct ? (size_t)w * h : (size_t)w * h / 4;
        if (words <= 4096) { for (size_t i = 0; i < words; i++) hsh = (hsh ^ d[i]) * 16777619u; }
        else { for (int i = 0; i < 256; i++) hsh = (hsh ^ d[(words * i) / 256]) * 16777619u; }
        if (!direct) { const uint32_t* pw = reinterpret_cast<const uint32_t*>(palPtr); for (int i = 0; i < palWords; i++) hsh = (hsh ^ pw[i]) * 16777619u; }
    }
    bool stale = fresh || t.texp != texp || t.dataPtr != dataPtr || t.palPtr != palPtr || t.hash != hsh;
    if (!stale) { t.stamp = g.frame; return &t; }   // last use, so eviction never takes a layer this frame samples
    if (!fresh) g.texReuploads++;
    // Writing into an atlas region the previous frame's job still samples makes Mali copy the
    // whole atlas, so a re-upload goes to the entry's other slot (allocated on first re-upload).
    int want = fresh ? 0 : (t.cur ^ 1);
    const bool big = bigNow;
    if (fresh || t.sl[want] < 0) {
        int layer, pal;
        if (!allocLayer(big, layer, pal, direct)) {
            // Evict entries unused for 120 frames, then anything but this frame's, then reset.
            // The map may rehash on erase, so re-fetch this entry afterwards.
            const size_t before = g.texCache.size();
            size_t ev = evictStale(120);
            if (!ev) ev = evictStale(1);
            ALOGW("gpu3d: texture layers exhausted at frame %u (%zu entries), evicted %zu", g.frame, before, ev);
            if (!ev) { resetAtlas(); }
            TexEntry& t2 = g.texCache[entryPtr];
            if (t2.layer >= 0) { releaseEntry(t2); t2.layer = -1; }   // this entry survived the eviction: its old slots come back first
            if (!allocLayer(big, layer, pal, direct)) return nullptr;
            t2.sl[0] = layer; t2.sp[0] = pal; t2.sl[1] = t2.sp[1] = -1; t2.cur = 1; t2.layer = layer; t2.palRow = pal; t2.big = big; t2.direct = direct; t2.w = w; t2.h = h; t2.texp = 0;
            return texFor(entryPtr, texp);
        }
        if (fresh) { t.sl[1] = t.sp[1] = -1; }
        t.sl[want] = layer; t.sp[want] = pal;
    }
    t.cur = want; t.layer = t.sl[want]; t.palRow = t.sp[want]; t.big = big; t.direct = direct; t.w = w; t.h = h;
    gStage = 20 + fmt;
    // Stage the texel bytes and palette for the GL thread (drastic may rewrite the cache entry
    // before the GL thread runs, so the copy is taken here on the worker).
    g.cur->uploads.emplace_back();
    Upload& u = g.cur->uploads.back();
    u.big = big; u.direct = direct; u.layer = t.layer; u.palRow = t.palRow; u.w = w; u.h = h; u.dbg = false;
    u.data.resize((size_t)w * h * (direct ? 4 : 1));
    memcpy(u.data.data(), reinterpret_cast<const void*>(dataPtr), u.data.size());
    if (gTexDbg && converted) {
        static int files = 0;
        if (files < 48) {
            files++;
            char path[96]; snprintf(path, sizeof path, "/data/local/tmp/texc_%d_%p.bin", files, e);
            if (FILE* f = fopen(path, "wb")) {
                uint32_t hdr[4] = { (uint32_t)w, (uint32_t)h, texp, (uint32_t)convPal };
                fwrite(hdr, sizeof hdr, 1, f); fwrite(u.data.data(), 1, (size_t)w * h, f); fwrite(reinterpret_cast<const void*>(palPtr), 4, (size_t)convPal, f); fclose(f);
            }
        }
        static int n = 0;
        if (n++ < 12) {
            u.dbg = true;
            uint32_t sum = 0; for (uint8_t b : u.data) sum = sum * 31 + b;
            ALOGW("gpu3d: texdbg entry %p fmt %d conv pal %d %dx%d layer %d big %d palRow %d data %02x %02x %02x %02x %02x %02x %02x %02x .. sum %08x pal1 %08x", e, fmt, convPal, w, h, t.layer, big, t.palRow,
                  u.data[0], u.data[1], u.data[2], u.data[3], u.data[4], u.data[5], u.data[6], u.data[7], sum, *reinterpret_cast<const uint32_t*>(palPtr + 4));
        }
    }
    const int palN = palWords;
    memset(u.pal, 0, sizeof u.pal);
    if (!direct) memcpy(u.pal, reinterpret_cast<const void*>(palPtr), palN * 4);
    t.texp = texp; t.dataPtr = dataPtr; t.palPtr = palPtr; t.stamp = g.frame; t.hash = hsh;
    // Compressed (5) and direct colour (7) entries have not been seen in the three control games;
    // dump the first few so their cache layout can be read off the log when a game uses them.
    if ((fmt == 5 || fmt == 7) && property_get_int32("sys.gammaos.drastic_nano.gpu3d_texdump", 0)) {
        static int dumps = 0;
        if (dumps < 4) {
            dumps++;
            const uint8_t* db = reinterpret_cast<const uint8_t*>(dataPtr); const uint32_t* pw = reinterpret_cast<const uint32_t*>(palPtr);
            const uint32_t* eh = reinterpret_cast<const uint32_t*>(e);
            ALOGW("gpu3d: fmt %d texture %dx%d texp %08x entry[0..7] %08x %08x %08x %08x %08x %08x %08x %08x data %02x%02x%02x%02x %02x%02x%02x%02x %02x%02x%02x%02x %02x%02x%02x%02x pal %08x %08x %08x %08x %08x %08x %08x %08x",
                  fmt, w, h, texp, eh[0], eh[1], eh[2], eh[3], eh[4], eh[5], eh[6], eh[7],
                  db[0], db[1], db[2], db[3], db[4], db[5], db[6], db[7], db[8], db[9], db[10], db[11], db[12], db[13], db[14], db[15],
                  pw[0], pw[1], pw[2], pw[3], pw[4], pw[5], pw[6], pw[7]);
            // Range of the decoded texel bytes and the palette length drastic keeps for this entry
            // (bytes w*h are what texFor uploads; the palette row holds 256 words).
            int mx = 0; for (int i = 0; i < w * h; i++) if (db[i] > mx) mx = db[i];
            int lastNz = -1; for (int i = 0; i < 1024; i++) if (pw[i]) lastNz = i;
            const uint16_t* dw = reinterpret_cast<const uint16_t*>(dataPtr); int mx16 = 0; for (int i = 0; i < w * h; i++) if (dw[i] > mx16) mx16 = dw[i];
            ALOGW("gpu3d: fmt %d texel byte max %d over %d texels (as halfwords max %d); last nonzero palette word within 1024: %d", fmt, mx, w * h, mx16, lastNz);
            // Whole entry to a file for the host-side layout check: header, entry bytes, texels, palette.
            char path[64]; snprintf(path, sizeof path, "/data/local/tmp/tex%d_%d.bin", fmt, dumps);
            if (FILE* f = fopen(path, "wb")) {
                uint32_t hdr[4] = { (uint32_t)w, (uint32_t)h, texp, 0x2a0 };
                fwrite(hdr, sizeof hdr, 1, f); fwrite(e, 1, 0x2a0, f); fwrite(db, 1, (size_t)w * h, f); fwrite(pw, 4, (size_t)w * h * 2, f);
                // the entry's other pointers (words 8..15 as three 64-bit pointers at +32, +40, +48, +56): blocks*8 bytes each
                for (int po = 32; po <= 56; po += 8) {
                    const uint64_t pp = *reinterpret_cast<const uint64_t*>(e + po) & 0x00ffffffffffffffull;
                    static uint8_t zero[8192]; const size_t n = (size_t)w * h / 16 * 8;
                    fwrite(pp ? reinterpret_cast<const void*>(pp) : zero, 1, n < sizeof zero ? n : sizeof zero, f);
                }
                fclose(f);
            }
        }
    }
    gStage = 30;
    return &t;
}

// One vertex stream per list. Polygons are appended in first-seen band order (drastic bins
// each polygon into every band it covers, in a consistent global order), grouped by depth
// mode so the whole list draws with a few calls: opaque LESS / LEQUAL(attr bit 14), then the
// translucent list in two passes (alpha-31 fragments with the depth mask, the rest without),
// translucent polygons with attr bit 11 write depth in both passes.
uint32_t gSeenStamp = 1;
std::vector<uint32_t> gSeen;   // per poly index, stamped when appended

struct PolyRef { uint32_t key; uint16_t pi; };
std::vector<PolyRef> gRefs;

void emitPoly(const uint8_t* rec, const uint8_t* vb, const uint32_t* shapeTbl, bool wbuf, bool translucent, bool texEnabled,
              Stream& out, uint64_t& lastTex, uint32_t& lastTexp, TexEntry*& lastT) {
    uint32_t texp = *reinterpret_cast<const uint32_t*>(rec + 0);
    uint32_t pattr = *reinterpret_cast<const uint32_t*>(rec + 4);
    uint32_t ra = *reinterpret_cast<const uint32_t*>(rec + 8);
    uint64_t tex = *reinterpret_cast<const uint64_t*>(rec + 16);
    uint16_t vbase = *reinterpret_cast<const uint16_t*>(rec + 26);
    int n = ra & 15;
    if (n < 3) return;
    uint32_t order = shapeTbl[(ra >> 16) & 0x7f];
    int fmt = (texp >> 26) & 7;
    g.modeCount[(pattr >> 4) & 3]++; g.fmtCount[fmt]++; if ((pattr >> 15) & 1) g.fogPolys++;
    // Shadow polygons (mode 3) are a two-pass stencil effect on the DS (id 0 polygons write the
    // mask, others draw only where the mask is set); drawn as plain polygons they cover the scene
    // in black (Mario Kart slot 0). Skipped until the stencil pass exists: shadows are missing,
    // the scene is right. sys gpu3d_shadow=1 draws them anyway.
    const bool shadowPoly = ((pattr >> 4) & 3) == 3;
    if (shadowPoly) {
        // Off by default: drastic's own rasterizer draws no shadow polygons (Mario Kart's karts
        // cast none on the CPU path), and the GPU path matches it. sys gpu3d_shadow 1 draws
        // them with the stencil passes below.
        static int shadowKnob = 0; static uint32_t polls = 0;
        if ((polls++ & 1023) == 0) shadowKnob = property_get_int32("sys.gammaos.drastic_nano.gpu3d_shadow", 0);
        if (!shadowKnob || !g.cur) return;
    }
    TexEntry* t;
    if (texEnabled && fmt != 0) {
        static uint32_t lastGen = 0;
        if (tex == lastTex && texp == lastTexp && lastGen == g.atlasGen) t = lastT;
        else { t = texFor(tex, texp); lastTex = tex; lastTexp = texp; lastT = t; lastGen = g.atlasGen; }
    } else t = nullptr;
    int32_t tex0[4] = { t ? t->layer : 0, t ? (t->big | (t->direct << 1)) : 0, t ? t->w : 0, t ? t->h : 0 };
    int32_t tex1[4] = { (int32_t)(fmt | (((texp >> 29) & 1) << 3) | (((texp >> 16) & 15) << 4) |
                                  ((((texp >> 20) & 7) + 3) << 8) | ((((texp >> 23) & 7) + 3) << 12) |
                                  (((pattr >> 24) & 63) << 16)),
                        t ? t->palRow : 0, (int32_t)(((pattr >> 16) & 31) | (((pattr >> 15) & 1) << 8)), (int32_t)((pattr >> 4) & 3) };
    int deq = (pattr >> 14) & 1, dwrite = translucent ? ((pattr >> 11) & 1) : 1;
    if (shadowPoly) {
        // Stencil shadows: the mask (polygon id 0) marks pixels where it fails the depth test,
        // the shadow polygons (id 1..63) blend where the mask is set and their own depth test
        // passes. (The DS also skips pixels whose polygon id equals the shadow's, so a caster
        // never darkens itself; that needs the destination id per pixel and is not done here.)
        const bool mask = ((pattr >> 24) & 63) == 0;
        std::vector<Vtx>& sv = g.cur->shadow; std::vector<ShadowSeg>& segs = g.cur->shadowSegs;
        if (segs.empty() || segs.back().mask != mask || segs.back().deq != (deq != 0)) segs.push_back({ (uint32_t)sv.size(), 0, mask, deq != 0 });
        Vtx v[16];
        for (int k = 0; k < n; k++) {
            int kk = (order >> (4 * k)) & 15;
            const uint8_t* vr = vb + (vbase + kk) * 16;
            uint32_t Wv = *reinterpret_cast<const uint32_t*>(vr + 0);
            uint16_t x = *reinterpret_cast<const uint16_t*>(vr + 4), y = *reinterpret_cast<const uint16_t*>(vr + 6);
            uint16_t z = *reinterpret_cast<const uint16_t*>(vr + 8), col = *reinterpret_cast<const uint16_t*>(vr + 10);
            int16_t s = *reinterpret_cast<const int16_t*>(vr + 12), tt = *reinterpret_cast<const int16_t*>(vr + 14);
            uint32_t depth = wbuf ? Wv : ((uint32_t)z << 9);
            int c5r = col & 31, c5g = (col >> 5) & 31, c5b = (col >> 10) & 31;
            v[k].x = x; v[k].y = y; v[k].depth = (float)(depth & 0xffffff) / 16777215.0f;
            v[k].w = (gPersp && Wv > 0) ? (float)Wv * (1.0f / 65536.0f) : 1.0f;
            v[k].r = (float)((c5r << 1) | (c5r >> 4)); v[k].g = (float)((c5g << 1) | (c5g >> 4)); v[k].b = (float)((c5b << 1) | (c5b >> 4));
            v[k].s = s / 16.0f; v[k].t = tt / 16.0f;
            memcpy(v[k].tex0, tex0, sizeof tex0); memcpy(v[k].tex1, tex1, sizeof tex1);
        }
        for (int k = 1; k + 1 < n; k++) { sv.push_back(v[0]); sv.push_back(v[k]); sv.push_back(v[k + 1]); }
        segs.back().count = (uint32_t)sv.size() - segs.back().start;
        return;
    }
    // An opaque polygon whose texels can never be transparent (no a3i5/a5i3, no colour-0 key,
    // or untextured) and whose alpha is 31 needs no discard: it goes to a group drawn with
    // the no-discard program so the GPU can reject hidden fragments before shading.
    const bool mayDiscard = translucent || ((pattr >> 16) & 31) != 31 ||
                            (t && (fmt == 1 || fmt == 6 || fmt == 5 || fmt == 7 || ((texp >> 29) & 1)));
    std::vector<Vtx>& dst = out.v[deq | (dwrite << 1) | ((mayDiscard ? 0 : 1) << 2)];
    Vtx v[16];
    for (int k = 0; k < n; k++) {
        int kk = (order >> (4 * k)) & 15;
        const uint8_t* vr = vb + (vbase + kk) * 16;
        uint32_t Wv = *reinterpret_cast<const uint32_t*>(vr + 0);
        uint16_t x = *reinterpret_cast<const uint16_t*>(vr + 4), y = *reinterpret_cast<const uint16_t*>(vr + 6);
        uint16_t z = *reinterpret_cast<const uint16_t*>(vr + 8), col = *reinterpret_cast<const uint16_t*>(vr + 10);
        // Texel coordinates are SIGNED 12.4 (DS TEXCOORD): read as unsigned, a polygon whose t runs
        // from -32 to 32 interpolated across 4032 texels (126 repeats of a 32-texel texture over a
        // few pixels: the moire and speckle on Mario Kart's barriers, stands and grass).
        int16_t s = *reinterpret_cast<const int16_t*>(vr + 12), tt = *reinterpret_cast<const int16_t*>(vr + 14);
        uint32_t depth = wbuf ? Wv : ((uint32_t)z << 9);
        int c5r = col & 31, c5g = (col >> 5) & 31, c5b = (col >> 10) & 31;
        v[k].x = x; v[k].y = y; v[k].depth = (float)(depth & 0xffffff) / 16777215.0f;
        // Perspective-correct interpolation from the vertex W (Mario Kart's foreshortened track,
        // barriers and stands: affine interpolation under-sampled the near ends into speckle).
        // sys gpu3d_persp 0 restores affine for A/B.
        v[k].w = (gPersp && Wv > 0) ? (float)Wv * (1.0f / 65536.0f) : 1.0f;
        v[k].r = (float)((c5r << 1) | (c5r >> 4)); v[k].g = (float)((c5g << 1) | (c5g >> 4)); v[k].b = (float)((c5b << 1) | (c5b >> 4));
        v[k].s = s / 16.0f; v[k].t = tt / 16.0f;
        memcpy(v[k].tex0, tex0, sizeof tex0); memcpy(v[k].tex1, tex1, sizeof tex1);
    }
    for (int k = 1; k + 1 < n; k++) { dst.push_back(v[0]); dst.push_back(v[k]); dst.push_back(v[k + 1]); }
    if (gTexDbg) {
        // Suspect polygons: a screen extent under 3 px with a texel extent above 8 (collapsed vertices)
        float minx = 1e9f, maxx = -1e9f, miny = 1e9f, maxy = -1e9f, mins = 1e9f, maxs = -1e9f;
        for (int k = 0; k < n; k++) { minx = std::min(minx, v[k].x); maxx = std::max(maxx, v[k].x); miny = std::min(miny, v[k].y); maxy = std::max(maxy, v[k].y); mins = std::min(mins, v[k].s); maxs = std::max(maxs, v[k].s); }
        static int m = 0;
        if (maxx - minx < 3.f && maxy - miny < 3.f && maxs - mins > 8.f && m++ < 8) {
            const uint32_t* rw = reinterpret_cast<const uint32_t*>(rec);
            const uint8_t* vr0 = vb + (vbase + (order & 15)) * 16;
            const uint32_t* vw = reinterpret_cast<const uint32_t*>(vr0);
            ALOGW("gpu3d: texdbg SUSPECT poly n %d rec %08x %08x %08x %08x %08x %08x %08x %08x vbase %u order %08x v0 %08x %08x %08x %08x v1 %08x %08x %08x %08x", n,
                  rw[0], rw[1], rw[2], rw[3], rw[4], rw[5], rw[6], rw[7], vbase, order, vw[0], vw[1], vw[2], vw[3], vw[4], vw[5], vw[6], vw[7]);
        }
        static int hits = 0; static float dx = -1.f, dy = -1.f;
        if (dx < 0.f) { dx = (float)property_get_int32("sys.gammaos.drastic_nano.gpu3d_dbg_x", 50); dy = (float)property_get_int32("sys.gammaos.drastic_nano.gpu3d_dbg_y", 230); }
        static uint32_t dbgTexp = 0; static bool dbgTexpRead = false;
        if (!dbgTexpRead) { dbgTexpRead = true; char tb[PROPERTY_VALUE_MAX] = {}; property_get("sys.gammaos.drastic_nano.gpu3d_dbg_texp", tb, "0"); dbgTexp = (uint32_t)strtoul(tb, nullptr, 16); }
        const bool hit = dbgTexp ? ((texp & 0x3ff0ffffu) == (dbgTexp & 0x3ff0ffffu)) : (minx <= dx && maxx >= dx && miny <= dy && maxy >= dy);
        if (gDbgFrame && hits++ < 3) ALOGW("gpu3d: texdbg dump-frame poly texp %08x dbgTexp %08x hit %d", texp, dbgTexp, hit ? 1 : 0);
        if (hit && gDbgFrame && gDbgHits++ < 60) {
            char buf[640]; int o = 0;
            for (int k = 0; k < n && o < 520; k++) {
                const uint8_t* vr = vb + (vbase + ((order >> (4 * k)) & 15)) * 16;
                o += snprintf(buf + o, sizeof buf - o, " [%.0f,%.0f W%u s%.2f t%.2f c%.0f,%.0f,%.0f]", v[k].x, v[k].y, *reinterpret_cast<const uint32_t*>(vr), v[k].s, v[k].t, v[k].r, v[k].g, v[k].b);
            }
            ALOGW("gpu3d: texdbg AT(%.0f,%.0f) poly n %d texp %08x pattr %08x fmt %d tex %dx%d entry %p transl %d:%s", dx, dy, n, texp, pattr, fmt, t ? t->w : 0, t ? t->h : 0, (void*)(uintptr_t)tex, translucent ? 1 : 0, buf);
        }
        static int ok = 0;
        if (maxx - minx > 20.f && ok++ < 4) {
            const uint32_t* rw = reinterpret_cast<const uint32_t*>(rec);
            const uint8_t* vr0 = vb + (vbase + (order & 15)) * 16;
            const uint32_t* vw = reinterpret_cast<const uint32_t*>(vr0);
            ALOGW("gpu3d: texdbg NORMAL poly n %d rec %08x %08x %08x %08x %08x %08x %08x %08x vbase %u order %08x v0 %08x %08x %08x %08x v1 %08x %08x %08x %08x", n,
                  rw[0], rw[1], rw[2], rw[3], rw[4], rw[5], rw[6], rw[7], vbase, order, vw[0], vw[1], vw[2], vw[3], vw[4], vw[5], vw[6], vw[7]);
        }
    }
    if (gTexDbg && t && (fmt == 5 || fmt == 7)) {
        static int n = 0;
        if (n++ < 4) ALOGW("gpu3d: texdbg poly entry %p fmt %d %dx%d layer %d palRow %d n %d uv (%.1f,%.1f) (%.1f,%.1f) (%.1f,%.1f) xy (%.0f,%.0f) (%.0f,%.0f) (%.0f,%.0f) texp %08x pattr %08x", (void*)(uintptr_t)tex, fmt, t->w, t->h, t->layer, t->palRow, n,
                          v[0].s, v[0].t, v[1].s, v[1].t, v[2].s, v[2].t, v[0].x, v[0].y, v[1].x, v[1].y, v[2].x, v[2].y, texp, pattr);
    }
}

uint32_t polyMinDepth(const uint8_t* rec, const uint8_t* vb, const uint32_t* shapeTbl, bool wbuf) {
    uint32_t ra = *reinterpret_cast<const uint32_t*>(rec + 8);
    uint16_t vbase = *reinterpret_cast<const uint16_t*>(rec + 26);
    int n = ra & 15; uint32_t order = shapeTbl[(ra >> 16) & 0x7f]; uint32_t best = 0xffffffffu;
    for (int k = 0; k < n; k++) {
        const uint8_t* vr = vb + (vbase + ((order >> (4 * k)) & 15)) * 16;
        uint32_t d = wbuf ? *reinterpret_cast<const uint32_t*>(vr + 0) : ((uint32_t)*reinterpret_cast<const uint16_t*>(vr + 8) << 9);
        if (d < best) best = d;
    }
    return best;
}

void buildList(const uint8_t* lists, const uint8_t* pb, const uint8_t* vb, const uint32_t* shapeTbl,
               bool wbuf, bool translucent, bool texEnabled, Stream& out) {
    if (gSeen.size() < 2048) gSeen.assign(2048, 0);
    const uint32_t stamp = gSeenStamp++;
    uint64_t lastTex = 0; uint32_t lastTexp = 0; TexEntry* lastT = nullptr;
    gRefs.clear();
    for (int band = 0; band < 12; band++) {
        uint32_t cnt = *reinterpret_cast<const uint32_t*>(lists + band * 0x1004 + 0x1000);
        const uint16_t* ids = reinterpret_cast<const uint16_t*>(lists + band * 0x1004);
        for (uint32_t i = 0; i < cnt && i < 2048; i++) {
            uint16_t pi = ids[i];
            if (gSeen[pi] == stamp) continue;
            gSeen[pi] = stamp;
            const uint8_t* rec = pb + pi * 32;
            if (translucent) emitPoly(rec, vb, shapeTbl, wbuf, true, texEnabled, out, lastTex, lastTexp, lastT);
            else if (((*reinterpret_cast<const uint32_t*>(rec + 4) >> 4) & 3) == 3) emitPoly(rec, vb, shapeTbl, wbuf, false, texEnabled, out, lastTex, lastTexp, lastT);   // shadow: list order
            else gRefs.push_back({ polyMinDepth(rec, vb, shapeTbl, wbuf), pi });
        }
    }
    if (!translucent) {
        // Opaque polygons with the plain LESS test are order independent apart from exact depth
        // ties, so draw them nearest first: early depth rejection then skips the overdraw.
        std::stable_sort(gRefs.begin(), gRefs.end(), [](const PolyRef& a, const PolyRef& b) { return a.key < b.key; });
        for (const PolyRef& r : gRefs) emitPoly(pb + r.pi * 32, vb, shapeTbl, wbuf, false, texEnabled, out, lastTex, lastTexp, lastT);
    }
}

void useProgram(GLuint prog, bool blend) {
    if (prog != g.curProg) { glUseProgram(prog); g.curProg = prog; g.uPass = glGetUniformLocation(prog, "uPass"); }
    if (blend != g.curBlend) { if (blend) glEnable(GL_BLEND); else glDisable(GL_BLEND); g.curBlend = blend; }
}

void drawStream(Stream& st, bool translucent, size_t& base, bool issue, GLuint mainProg, bool mainBlend) {
    // vertices of all eight groups were uploaded contiguously; draw each group with its state.
    // Only the opaque list writes the polygon id / depth attachment, and only when the edge
    // pass will use it (a second RGBA8 attachment doubles the tile write bandwidth).
    if (issue) {
        const GLenum bufsOpaque[2] = { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 };
        const GLenum bufsTransl[2] = { GL_COLOR_ATTACHMENT0, GL_NONE };
        glDrawBuffers(2, (translucent || !g.attrWanted) ? bufsTransl : bufsOpaque);
    }
    for (int grp = 0; grp < 8; grp++) {
        std::vector<Vtx>& vv = st.v[grp];
        if (vv.empty()) continue;
        if (issue) {
            const bool noDiscard = (grp >> 2) & 1;
            useProgram(noDiscard ? g.progOpaque : mainProg, noDiscard ? false : mainBlend);
            glDepthFunc((grp & 1) ? GL_LEQUAL : GL_LESS);
            bool dwrite = (grp >> 1) & 1;
            if (!translucent || dwrite || noDiscard) {
                glDepthMask(GL_TRUE); if (g.uPass >= 0) glUniform1i(g.uPass, 0);
                glDrawArrays(GL_TRIANGLES, (GLint)base, (GLsizei)vv.size());
            } else {
                glDepthMask(GL_TRUE); glUniform1i(g.uPass, 1);
                glDrawArrays(GL_TRIANGLES, (GLint)base, (GLsizei)vv.size());
                glDepthMask(GL_FALSE); glUniform1i(g.uPass, 2);
                glDrawArrays(GL_TRIANGLES, (GLint)base, (GLsizei)vv.size());
                glUniform1i(g.uPass, 0);
            }
        }
        base += vv.size();
    }
}

// MSAA / GL-blend mode stores alpha as a blend factor (a/31 * 255); the DS layer wants the
// 5-bit value in the byte: a5 = (a8 + 4) >> 3.
void alphaToDs(uint8_t* buf, int y0, int y1) {
    uint32_t* p = reinterpret_cast<uint32_t*>(buf + (size_t)y0 * kW * 4);
    const size_t n = (size_t)(y1 - y0) * kW;
#if defined(__aarch64__)
    const uint32x4_t four = vdupq_n_u32(4), ff = vdupq_n_u32(255), maskC = vdupq_n_u32(0x00ffffffu);
    for (size_t i = 0; i + 4 <= n; i += 4) {
        uint32x4_t v = vld1q_u32(p + i);
        uint32x4_t a = vshrq_n_u32(vminq_u32(vaddq_u32(vshrq_n_u32(v, 24), four), ff), 3);
        vst1q_u32(p + i, vorrq_u32(vandq_u32(v, maskC), vshlq_n_u32(a, 24)));
    }
#else
    for (size_t i = 0; i < n; i++) { uint32_t v = p[i]; uint32_t a = (((v >> 24) + 4) >> 3) & 31; p[i] = (v & 0x00ffffffu) | (a << 24); }
#endif
}

// Scatter rows [y0,y1) of the 512-wide readback into the column de-interleaved target.
void scatterRows(const uint8_t* src, uint8_t* target, int y0, int y1) {
    for (int y = y0; y < y1; y++) {
        const uint32_t* row = reinterpret_cast<const uint32_t*>(src + y * kW * 4);
        uint32_t* even = reinterpret_cast<uint32_t*>(target + (2 * y) * 0x400);
        uint32_t* odd = reinterpret_cast<uint32_t*>(target + (2 * y + 1) * 0x400);
#if defined(__aarch64__)
        for (int x = 0; x < kW; x += 8) {
            uint32x4x2_t p = vld2q_u32(row + x);
            vst1q_u32(even + (x >> 1), p.val[0]);
            vst1q_u32(odd + (x >> 1), p.val[1]);
        }
#else
        for (int x = 0; x < kW; x += 2) { even[x >> 1] = row[x]; odd[x >> 1] = row[x + 1]; }
#endif
    }
}

// ---- GL thread: owns the context, renders one Job at a time.
void renderJob(Job& j) {
    const int64_t t0 = nowUs();
    g.glFrames++;   // GL-thread-owned counter for the knob polls (g.frame is the worker's and races)
    for (Upload& u : j.uploads) {
        if (u.direct) {
            glActiveTexture(u.big ? GL_TEXTURE6 : GL_TEXTURE5); glBindTexture(GL_TEXTURE_2D_ARRAY, u.big ? g.dirBigTex : g.dirTex);
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, u.layer, u.w, u.h, 1, GL_RGBA, GL_UNSIGNED_BYTE, u.data.data());
            continue;
        }
        glActiveTexture(u.big ? GL_TEXTURE2 : GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D_ARRAY, u.big ? g.bigTex : g.smallTex);
        glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, u.layer, u.w, u.h, 1, GL_RED_INTEGER, GL_UNSIGNED_BYTE, u.data.data());
        if (u.dbg) {
            GLuint fbo = 0; glGenFramebuffers(1, &fbo); glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
            glFramebufferTextureLayer(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, u.big ? g.bigTex : g.smallTex, 0, u.layer);
            const GLenum st = glCheckFramebufferStatus(GL_READ_FRAMEBUFFER);
            GLuint px[8 * 4] = {}; glReadPixels(0, 0, 8, 1, GL_RGBA_INTEGER, GL_UNSIGNED_INT, px);
            GLuint py[8 * 4] = {}; glReadPixels(0, 1, 8, 1, GL_RGBA_INTEGER, GL_UNSIGNED_INT, py);
            ALOGW("gpu3d: texdbg GL layer %d big %d fbo status %x err %x row0 %u %u %u %u %u %u %u %u row1 %u %u %u %u %u %u %u %u (source row1 %u %u %u %u)", u.layer, u.big, st, glGetError(),
                  px[0], px[4], px[8], px[12], px[16], px[20], px[24], px[28], py[0], py[4], py[8], py[12], py[16], py[20], py[24], py[28], u.data[u.w], u.data[u.w + 1], u.data[u.w + 2], u.data[u.w + 3]);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, 0); glDeleteFramebuffers(1, &fbo);
        }
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D_ARRAY, g.palTex);
        glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, u.palRow, 256, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, u.pal);
    }
    const int64_t t1 = nowUs();
    g.sumTexUs += t1 - t0;
    static int ssOpt = 1;
    if ((g.glFrames & 63) == 0) {
        const int ov = property_get_int32("sys.gammaos.drastic_nano.gpu3d_ss", -1);   // session override: 2 = on, 1 = off
        ssOpt = ov >= 0 ? (ov >= 2 ? 2 : 1) : (property_get_bool("persist.gammaos.drastic_nano.gpu3d_ss", false) ? 2 : 1);
        if (ssOpt == 2) {
            char mode[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.drastic_nano.gpu3d_ss_mode", mode, "msaa");
            if (strcmp(mode, "ssaa") != 0 && g.msSamples >= 2) ssOpt = 3;   // 4x MSAA on the 2x target (default)
        }
    }
    g.ss = ssOpt;
    const bool msaa = g.ss == 3;
    const int rw = g.ss == 2 ? kW * 2 : kW, rh = g.ss == 2 ? kH * 2 : kH;
    glViewport(0, 0, rw, rh);
    glBindFramebuffer(GL_FRAMEBUFFER, msaa ? g.msFbo : g.ss == 2 ? g.ssFbo : g.fbo);
    glDepthMask(GL_TRUE);
    if (msaa) j.edge = false;   // no id attachment on the multisampled target
    g.attrWanted = j.edge;
    if (g.attrWanted) {
        const GLenum bufs[2] = { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 };
        glDrawBuffers(2, bufs);
        const GLfloat attrClear[4] = { 0.f, 0.f, 0.f, 0.f };
        glClearBufferfv(GL_COLOR, 1, attrClear);
    }
    glClearColor((j.clearC & 0x3f) / 255.0f, ((j.clearC >> 8) & 0x3f) / 255.0f, ((j.clearC >> 16) & 0x3f) / 255.0f,
                 ((j.clearC >> 24) & 0x1f) / 255.0f);
    glClearDepthf((float)j.clearD / 16777215.0f);
    { const GLenum bufs0[2] = { GL_COLOR_ATTACHMENT0, GL_NONE }; glDrawBuffers(2, bufs0); }
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    static int progSel = 0; if ((g.glFrames & 63) == 0) progSel = property_get_int32("sys.gammaos.drastic_nano.gpu3d_rbtest", 0);
    GLuint useProg = progSel == 3 ? g.progNoFetch : progSel == 4 ? g.progTrivial : progSel == 6 ? g.progExp6 : progSel == 7 ? g.progExp7 : g.prog;
    bool mainBlend = progSel == 3 || !g.fbFetch;
    if (msaa) { useProg = g.progNoFetch; mainBlend = true; }
    // Both no-fetch programs draw in the MSAA path (the opaque stream through progOpaque), so
    // both take the blend-factor alpha scale there; with only progNoFetch set, opaque polygons
    // landed with alpha 31/255 -> 4 of 31 after the readback (Sonic transparent at 4x).
    glUseProgram(g.progNoFetch); glUniform1f(g.uAlphaMulNoFetch, msaa ? 1.0f / 31.0f : 1.0f / 255.0f);
    glUseProgram(g.progOpaque); glUniform1f(g.uAlphaMulOpaque, msaa ? 1.0f / 31.0f : 1.0f / 255.0f);
    for (GLuint pr : { g.prog, g.progNoFetch, g.progOpaque }) {
        glUseProgram(pr);
        { static int interp = 0; if ((g.glFrames & 15) == 0) interp = property_get_int32("sys.gammaos.drastic_nano.gpu3d_interp", 1);   // colour affine, texels perspective: closest to drastic on Golden Sun and Mario Kart
          const GLint li = glGetUniformLocation(pr, "uInterp"); if (li >= 0) glUniform1i(li, interp);
          // drastic samples coverage, texcoords and colours at integer pixel positions, GL at the
          // pixel centre. Shifting the vertices half a pixel (uVtxShift, default 2 quarter pixels)
          // moves GL's sample to drastic's for free: Golden Sun 14183 -> 5048 px over 4 levels,
          // Mario Kart 27122 -> 12113, Sonic 568 -> 367. The derivative-based corner sampling
          // (uUvOff bits) did the same for texcoords alone at +3 ms of GPU time a frame; A/B only.
          static int uvoff = 0; if ((g.glFrames & 15) == 0) uvoff = property_get_int32("sys.gammaos.drastic_nano.gpu3d_uvoff", 0);
          const GLint lo = glGetUniformLocation(pr, "uUvOff"); if (lo >= 0) glUniform1i(lo, uvoff);
          static int vsh = 2; if ((g.glFrames & 15) == 0) vsh = property_get_int32("sys.gammaos.drastic_nano.gpu3d_vshift", 2);   // in quarter pixels
          const GLint lv = glGetUniformLocation(pr, "uVtxShift"); if (lv >= 0) glUniform2f(lv, vsh * 0.25f, vsh * 0.25f); }
        const GLint lf = glGetUniformLocation(pr, "uFog");
        if (lf < 0) continue;
        glUniform1i(lf, j.fog);
        if (j.fog) {
            glUniform2i(glGetUniformLocation(pr, "uFogParam"), j.fogShift, j.fogOffset);
            { static int fb = 0, ft = 0; if ((g.glFrames & 15) == 0) { fb = property_get_int32("sys.gammaos.drastic_nano.gpu3d_fog_bias", 0); ft = property_get_int32("sys.gammaos.drastic_nano.gpu3d_fog_trunc", 0); }
              glUniform2i(glGetUniformLocation(pr, "uFogTune"), fb, ft); }
            glUniform4f(glGetUniformLocation(pr, "uFogColor"), (float)(j.fogColor & 255), (float)((j.fogColor >> 8) & 255), (float)((j.fogColor >> 16) & 255), (float)((j.fogColor >> 24) & 255));
            glUniform1iv(glGetUniformLocation(pr, "uFogTable"), 32, j.fogTable);
            glUniform1iv(glGetUniformLocation(pr, "uFogDelta"), 32, j.fogDelta);
        }
    }
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    g.curProg = 0; g.curBlend = false; glDisable(GL_BLEND);
    useProgram(useProg, mainBlend);
    glBindVertexArray(g.vao); glBindBuffer(GL_ARRAY_BUFFER, g.vbo);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D_ARRAY, g.smallTex);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D_ARRAY, g.palTex);
    glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D_ARRAY, g.bigTex);
    glActiveTexture(GL_TEXTURE5); glBindTexture(GL_TEXTURE_2D_ARRAY, g.dirTex);
    glActiveTexture(GL_TEXTURE6); glBindTexture(GL_TEXTURE_2D_ARRAY, g.dirBigTex);
    size_t total = 0;
    for (int i = 0; i < 8; i++) total += j.opaque.v[i].size() + j.transl.v[i].size();
    const size_t shadowBase = total;
    total += j.shadow.size();
    g.vertCount = total;
    static int chunks = 4; if ((g.glFrames & 63) == 0) chunks = property_get_int32("sys.gammaos.drastic_nano.gpu3d_chunks", 4);
    if (chunks < 1) chunks = 1; if (chunks > 12) chunks = 12;
    // Decoupled jobs run as one job (sys gpu3d_decoupled_chunks 1 = the four fenced chunks): the
    // chunked variant let the presenter in between chunks but the four blocking waits took the GL
    // thread to 18 ms a frame, the queue filled, the worker and then the emulator waited, and the
    // audio broke up (2 s of silence per 90 s on Pokemon). Off.
    static int decChunks = 0; if ((g.glFrames & 63) == 0) decChunks = property_get_int32("sys.gammaos.drastic_nano.gpu3d_decoupled_chunks", 0);
    // No chunked readbacks on the multisampled target: a glReadPixels of the implicit-resolve
    // texture between chunks stalled the GL thread for 4 to 6 s (Mali, Pokemon 4x on the first
    // synchronous frame after an engine swap: emulator blocked, 289 underrun frames). Whole frame,
    // one readback. sys gpu3d_msaa_chunks 1 restores the chunked path for A/B.
    static int msaaChunks = 0; if ((g.glFrames & 63) == 0) msaaChunks = property_get_int32("sys.gammaos.drastic_nano.gpu3d_msaa_chunks", 0);
    const bool progressive = chunks > 1 && !j.edge && g.ss != 2 && (!msaa || msaaChunks) && (!j.decoupled || decChunks > 0);
    if (total) {
        glBufferData(GL_ARRAY_BUFFER, total * sizeof(Vtx), nullptr, GL_STREAM_DRAW);   // orphan
        size_t off = 0;
        for (Stream* st : { &j.opaque, &j.transl })
            for (int i = 0; i < 8; i++) {
                if (st->v[i].empty()) continue;
                glBufferSubData(GL_ARRAY_BUFFER, off * sizeof(Vtx), st->v[i].size() * sizeof(Vtx), st->v[i].data());
                off += st->v[i].size();
            }
        if (!j.shadow.empty()) glBufferSubData(GL_ARRAY_BUFFER, off * sizeof(Vtx), j.shadow.size() * sizeof(Vtx), j.shadow.data());
    }
    // Shadow pass, after the translucent list: stencil masks then shadow polygons per segment.
    auto drawShadows = [&](GLuint mainProg, bool mainBlend) {
        if (j.shadowSegs.empty()) return;
        glEnable(GL_STENCIL_TEST);
        glStencilMask(0xff);
        bool prevMask = false, first = true;
        for (const ShadowSeg& sg : j.shadowSegs) {
            if (!sg.count) continue;
            glDepthFunc(sg.deq ? GL_LEQUAL : GL_LESS);
            if (sg.mask) {
                if (first || !prevMask) glClear(GL_STENCIL_BUFFER_BIT);   // a new mask group starts with a clean stencil
                glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE); glDepthMask(GL_FALSE);
                glStencilFunc(GL_ALWAYS, 1, 0xff); glStencilOp(GL_KEEP, GL_REPLACE, GL_KEEP);   // set where the depth test fails
                useProgram(g.progOpaque, false); if (g.uPass >= 0) glUniform1i(g.uPass, 0);
            } else {
                glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE); glDepthMask(GL_FALSE);
                glStencilFunc(GL_EQUAL, 1, 0xff); glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
                useProgram(mainProg, mainBlend); if (g.uPass >= 0) glUniform1i(g.uPass, 0);
            }
            glDrawArrays(GL_TRIANGLES, (GLint)(shadowBase + sg.start), (GLsizei)sg.count);
            prevMask = sg.mask; first = false;
        }
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE); glDepthMask(GL_TRUE);
        glDisable(GL_STENCIL_TEST);
    };
    static int gpuTime = 0; if ((g.glFrames & 63) == 0) gpuTime = property_get_int32("sys.gammaos.drastic_nano.gpu3d_gputime", 0);
    static PFNGLGENQUERIESEXTPROC pGenQ = nullptr; static PFNGLBEGINQUERYEXTPROC pBeginQ = nullptr; static PFNGLENDQUERYEXTPROC pEndQ = nullptr;
    static PFNGLGETQUERYOBJECTUI64VEXTPROC pGetQ64 = nullptr; static PFNGLGETQUERYOBJECTUIVEXTPROC pGetQ = nullptr;
    if (gpuTime && g.timerExt && !pGenQ) {
        pGenQ = (PFNGLGENQUERIESEXTPROC)eglGetProcAddress("glGenQueriesEXT"); pBeginQ = (PFNGLBEGINQUERYEXTPROC)eglGetProcAddress("glBeginQueryEXT");
        pEndQ = (PFNGLENDQUERYEXTPROC)eglGetProcAddress("glEndQueryEXT"); pGetQ64 = (PFNGLGETQUERYOBJECTUI64VEXTPROC)eglGetProcAddress("glGetQueryObjectui64vEXT");
        pGetQ = (PFNGLGETQUERYOBJECTUIVEXTPROC)eglGetProcAddress("glGetQueryObjectuivEXT");
        if (pGenQ) pGenQ(1, &g.tq);
    }
    const bool timing = gpuTime && g.tq && pBeginQ;
    if (timing) pBeginQ(0x88BF /* GL_TIME_ELAPSED_EXT */, g.tq);
    if (progressive) {
        // Draw the whole list once per band group under a scissor, then fence + read back just
        // those rows and mark their bands, so the compositor's early chunks stop waiting for
        // the full frame (drastic's CPU rasterizer also hands bands over as they finish).
        glEnable(GL_SCISSOR_TEST);
        const int64_t tq0 = nowUs(); int64_t gpuUs = 0, rbUs = 0, scUs = 0;
        uint32_t maskDone = j.earlyMask;
        if (j.earlyMask) g.lagFrames++;
        int64_t firstReadyTs = 0;
        // Queue every chunk's draws with a fence after each, then consume them in order: the
        // GPU works through the whole frame while the top quarter is waited for and read back.
        // Pipelining all chunks ahead of the first wait queues four jobs on the high priority
        // context and starves the presenter (a 175 ms presenter frame was seen); off by default.
        static int pipelined = 0; if ((g.glFrames & 63) == 0) pipelined = property_get_int32("sys.gammaos.drastic_nano.gpu3d_pipeline", 0);
        GLsync fences[12] = {};
        int64_t drawUs = 0, fenceUs = 0, flushUs = 0;
        static int firstBands = 3; if ((g.glFrames & 63) == 0) firstBands = property_get_int32("sys.gammaos.drastic_nano.gpu3d_first_bands", 3);
        if (firstBands < 1) firstBands = 1; if (firstBands > 11) firstBands = 11;
        auto bandRange = [&](int c, int& b0, int& b1) {
            if (chunks == 2) { b0 = c == 0 ? 0 : firstBands; b1 = c == 0 ? firstBands : 12; }   // uneven: a small early chunk, then the rest
            else { b0 = (12 * c) / chunks; b1 = (12 * (c + 1)) / chunks; }
        };
        auto issue = [&](int c) {
            int b0, b1; bandRange(c, b0, b1);
            if (b0 == b1) return;
            glScissor(0, b0 * 32, kW, (b1 - b0) * 32);
            const int64_t d0 = nowUs();
            if (total) {
                size_t base = 0;
                drawStream(j.opaque, false, base, true, useProg, mainBlend);
                drawStream(j.transl, true, base, true, useProg, mainBlend);
                drawShadows(useProg, mainBlend);
            }
            const int64_t d1 = nowUs();
            fences[c] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            const int64_t d2 = nowUs();
            glFlush();
            const int64_t d3 = nowUs();
            drawUs += d1 - d0; fenceUs += d2 - d1; flushUs += d3 - d2;
        };
        // pipelined 1: everything queued up front; 2: the first quarter alone (so it lands
        // early), then the remaining chunks queued together while it is read back.
        if (pipelined == 1) for (int c = 0; c < chunks; c++) issue(c);
        else issue(0);
        for (int c = 0; c < chunks; c++) {
            int b0, b1; bandRange(c, b0, b1);
            if (b0 == b1) continue;
            const int y0 = b0 * 32, y1 = b1 * 32;
            if (pipelined == 0 && c > 0) issue(c);
            if (pipelined == 2 && c == 1) for (int k = 1; k < chunks; k++) issue(k);
            const int64_t ta = nowUs();
            static int fencePoll = 0; if ((g.glFrames & 63) == 0) fencePoll = property_get_int32("sys.gammaos.drastic_nano.gpu3d_fence_poll", 0);
            if (fencePoll) {
                for (int i = 0; i < 400; i++) {
                    GLenum r = glClientWaitSync(fences[c], 0, 0);
                    if (r == GL_ALREADY_SIGNALED || r == GL_CONDITION_SATISFIED) break;
                    usleep(100);
                }
            } else {
                glStage(10 + c);
                glClientWaitSync(fences[c], GL_SYNC_FLUSH_COMMANDS_BIT, 40000000ull);   // blocking wait, 40 ms cap
            }
            glStage(20 + c);
            glDeleteSync(fences[c]); fences[c] = nullptr;
            const int64_t tb = nowUs(); gpuUs += tb - ta;
            glScissor(0, y0, kW, y1 - y0);
            if (msaa && !g.msImplicit) {   // resolve these rows into the single-sampled target, read from there
                glBindFramebuffer(GL_READ_FRAMEBUFFER, g.msFbo); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, g.fbo);
                glBlitFramebuffer(0, y0, kW, y1, 0, y0, kW, y1, GL_COLOR_BUFFER_BIT, GL_NEAREST);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, g.fbo);
            } else if (msaa) {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, g.fbo);   // colorTex holds the resolved rows
            }
            glPixelStorei(GL_PACK_ALIGNMENT, 4);
            glReadPixels(0, y0, kW, y1 - y0, GL_RGBA, GL_UNSIGNED_BYTE, g.readback.data() + (size_t)y0 * kW * 4);
            if (msaa) glBindFramebuffer(GL_FRAMEBUFFER, g.msFbo);
            const int64_t tc = nowUs(); rbUs += tc - tb;
            if (msaa) alphaToDs(g.readback.data(), y0, y1);
            scatterRows(g.readback.data(), j.target, y0, y1);
            scUs += nowUs() - tc;
            for (int b = b0; b < b1; b++) maskDone |= 1u << b;
            gpu3dSetBandMask(maskDone);
            if (c == 0) { firstReadyTs = nowUs(); const float f = (float)(firstReadyTs - t0); g.firstUs = g.firstUs == 0.f ? f : g.firstUs * 0.9f + f * 0.1f; g.sumFirstUs += (int64_t)f; }
        }
        glDisable(GL_SCISSOR_TEST);
        const int64_t tLoop = nowUs();
        if (timing) {
            pEndQ(0x88BF);
            GLuint avail = 0; for (int i = 0; i < 200 && !avail; i++) { pGetQ(g.tq, 0x8867 /* RESULT_AVAILABLE */, &avail); if (!avail) usleep(100); }
            GLuint64 ns = 0; if (avail) { pGetQ64(g.tq, 0x8866 /* QUERY_RESULT */, &ns); g.sumGpuNs += (int64_t)ns; g.gpuSamples++; }
        }
        const int64_t tTiming = nowUs();
        {
            // Pair diagnostic companion: the 3D job's window on the GL thread (CLOCK_MONOTONIC us,
            // same clock as the flip pair log) so a late presenter fence can be placed against it.
            if ((g.glFrames & 63) == 0) g.secLog = property_get_int32("sys.gammaos.drastic_nano.gpu3d_seclog", 0);
            if (g.secLog && tTiming - t0 > 15000) {
                struct timespec rt; clock_gettime(CLOCK_REALTIME, &rt);
                ALOGI("gpu3d: long job: %.1f ms (gpu wait %.1f, readback %.1f, first quarter %.1f) ended at %02lld:%02lld:%06.3f",
                      (tTiming - t0) / 1000.0, gpuUs / 1000.0, rbUs / 1000.0, (firstReadyTs ? firstReadyTs - t0 : 0) / 1000.0,
                      (long long)((rt.tv_sec / 3600) % 24), (long long)((rt.tv_sec / 60) % 60), (rt.tv_sec % 60) + rt.tv_nsec / 1e9);
            }
            const int64_t fqUs = firstReadyTs ? firstReadyTs - t0 : 0, jobUs = tTiming - t0;
            if (fqUs > g.secFqMax) g.secFqMax = fqUs; if (jobUs > g.secJobMax) g.secJobMax = jobUs;
            g.secFrames++; g.secUploads += (uint32_t)j.uploads.size();
            if (!g.secStart) g.secStart = tTiming;
            if (tTiming - g.secStart >= 1000000) {
                if (g.secLog) ALOGI("gpu3d: sec: %u frames, first quarter max %.1f ms, job max %.1f ms, uploads %u, early %s, backoff %u",
                                    g.secFrames, g.secFqMax / 1000.0, g.secJobMax / 1000.0, g.secUploads, j.earlyMask ? "on" : "off", g.backoffFrames);
                g.secStart = tTiming; g.secFqMax = g.secJobMax = 0; g.secFrames = g.secUploads = 0;
            }
        }
        if (g.frame >= 600 && !gPhaseDumped) {
            std::lock_guard<std::mutex> lk(gPhaseMtx);
            if (gPhaseN < 20) { gPhase[gPhaseN++] = { t0, firstReadyTs, tTiming }; }
            else {
                gPhaseDumped = true;
                for (int i = 0; i < 20; i++) {
                    // nearest present before this job start
                    int64_t pb = 0; for (int k = 0; k < 64; k++) { int64_t p = gPresentTs[k]; if (p && p <= gPhase[i].jobStart && p > pb) pb = p; }
                    ALOGI("gpu3d: phase %d: job start %+lld us after present, first quarter ready %+lld, job end %+lld",
                          i, (long long)(gPhase[i].jobStart - pb), (long long)(gPhase[i].firstReady - pb), (long long)(gPhase[i].jobEnd - pb));
                }
            }
        }
        // switch-on diagnostics: per-frame breakdown for the first 300 rendered frames, every 10th
        if (g.frame <= 10 || (g.frame <= 300 && (g.frame % 10) == 0))
            ALOGI("gpu3d: frame %u: tex %.2f setup %.2f loop %.2f (draw %.2f fence %.2f flush %.2f gpu wait %.2f readback %.2f scatter %.2f) total %.2f ms (%zu uploads)",
                  g.frame, (t1 - t0) / 1000.0, (tq0 - t1) / 1000.0, (tLoop - tq0) / 1000.0, drawUs / 1000.0, fenceUs / 1000.0, flushUs / 1000.0,
                  gpuUs / 1000.0, rbUs / 1000.0, scUs / 1000.0, (tTiming - t0) / 1000.0, j.uploads.size());
        g.sumUploadUs += tq0 - t1; g.sumDrawUs += gpuUs; g.sumReadUs += rbUs; g.sumScatterUs += scUs;
        gpu3dSetPending(0);
        const int64_t dt = nowUs() - t0;
        g.emaUs = g.firstUs;   // budget on the first quarter, the part the compositor waits for
        g.sumUs += dt; if (dt > g.maxUs) g.maxUs = dt; g.frames++;
        if (g.frames % 300 == 0) {
            ALOGI("gpu3d: first quarter ready after %.2f ms avg; GPU time elapsed (timer query) %.2f ms avg over %u frames; early-marked frames %u of 300 (%u lag enters)", g.sumFirstUs / 1000.0 / g.frames,
                  g.gpuSamples ? g.sumGpuNs / 1e6 / g.gpuSamples : 0.0, g.gpuSamples, g.lagFrames, g.lagEnters); g.sumFirstUs = 0; g.sumGpuNs = 0; g.gpuSamples = 0; g.lagFrames = 0; g.lagEnters = 0;
            ALOGI("gpu3d: %u frames, GL thread %.2f ms (tex %.2f, upload+draw %.2f, gpu wait %.2f, readback %.2f, scatter %.2f) progressive %d chunks, worker build %.2f ms, worker join wait %.2f ms, %zu verts, %u tex reuploads, max %.2f ms, %zu textures cached",
                  g.frames, g.sumUs / 1000.0 / g.frames, g.sumTexUs / 1000.0 / g.frames, g.sumUploadUs / 1000.0 / g.frames,
                  g.sumDrawUs / 1000.0 / g.frames, g.sumReadUs / 1000.0 / g.frames, g.sumScatterUs / 1000.0 / g.frames, chunks,
                  g.sumBuildUs / 1000.0 / g.frames, g.sumWaitUs / 1000.0 / g.frames, g.vertCount, g.texReuploads, g.maxUs / 1000.0, g.texCache.size());
            ALOGI("gpu3d: census polys by mode modulate %u decal %u toon %u shadow %u, fog bit %u; textures by fmt none %u a3i5 %u pal4 %u pal16 %u pal256 %u comp %u a5i3 %u direct %u",
                  g.modeCount[0], g.modeCount[1], g.modeCount[2], g.modeCount[3], g.fogPolys, g.fmtCount[0], g.fmtCount[1], g.fmtCount[2], g.fmtCount[3], g.fmtCount[4], g.fmtCount[5], g.fmtCount[6], g.fmtCount[7]);
            memset(g.modeCount, 0, sizeof g.modeCount); memset(g.fmtCount, 0, sizeof g.fmtCount); g.fogPolys = 0;
            g.sumUs = 0; g.maxUs = 0; g.frames = 0; g.sumTexUs = g.sumUploadUs = g.sumDrawUs = g.sumReadUs = g.sumScatterUs = g.sumBuildUs = g.sumWaitUs = 0; g.texReuploads = 0;
        }
        return;
    }
    if (total) {
        size_t base = 0;
        drawStream(j.opaque, false, base, true, useProg, mainBlend);
        drawStream(j.transl, true, base, true, useProg, mainBlend);
        drawShadows(useProg, mainBlend);
    }
    GLuint finalColor = g.ss == 2 ? g.ssColor : g.colorTex;
    if (j.edge) {
        glBindFramebuffer(GL_FRAMEBUFFER, g.ss == 2 ? g.ssEdgeFbo : g.edgeFbo);
        { const GLenum b1[1] = { GL_COLOR_ATTACHMENT0 }; glDrawBuffers(1, b1); }
        glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND);
        glUseProgram(g.edgeProg);
        glUniform2i(g.uEdgeSize, rw, rh);
        glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, g.ss == 2 ? g.ssColor : g.colorTex);
        glActiveTexture(GL_TEXTURE4); glBindTexture(GL_TEXTURE_2D, g.ss == 2 ? g.ssAttr : g.attrTex);
        finalColor = g.ss == 2 ? g.ssEdgeTex : g.edgeTex;
        GLfloat tbl[32];
        for (int i = 0; i < 8; i++) { uint32_t e = j.edgeTbl[i]; tbl[i * 4] = (e & 0x3f) / 255.f; tbl[i * 4 + 1] = ((e >> 8) & 0x3f) / 255.f; tbl[i * 4 + 2] = ((e >> 16) & 0x3f) / 255.f; tbl[i * 4 + 3] = 0.f; }
        glUniform4fv(g.uEdgeTbl, 8, tbl);
        glUniform2f(g.uEdgeClear, (float)(j.clearId & 63), (float)j.clearD);
        glBindVertexArray(g.edgeVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glEnable(GL_DEPTH_TEST);
        glBindVertexArray(g.vao);
    }
    g.curProg = 0;
    if (g.ss == 2) {
        // resolve 1024x768 into the 512x384 colour target the readback expects
        glViewport(0, 0, kW, kH);
        glBindFramebuffer(GL_FRAMEBUFFER, g.edgeFbo);
        { const GLenum b1[1] = { GL_COLOR_ATTACHMENT0 }; glDrawBuffers(1, b1); }
        glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND);
        glUseProgram(g.resolveProg);
        glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, finalColor);
        glBindVertexArray(g.edgeVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glEnable(GL_DEPTH_TEST);
        glBindVertexArray(g.vao);
    }
    const int64_t t2 = nowUs();
    g.sumUploadUs += t2 - t1;
    glPixelStorei(GL_PACK_ALIGNMENT, 4);
    static int rbTest = -1; if ((g.glFrames & 63) == 0) rbTest = property_get_int32("sys.gammaos.drastic_nano.gpu3d_rbtest", 0);
    if (rbTest != 5) {
        // Sleep on a fence instead of letting glReadPixels spin a core while the GPU finishes.
        GLsync fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        glFlush();
        for (int i = 0; i < 400; i++) {
            GLenum r = glClientWaitSync(fence, 0, 0);
            if (r == GL_ALREADY_SIGNALED || r == GL_CONDITION_SATISFIED) break;
            usleep(100);
        }
        glDeleteSync(fence);
    }
    const int64_t t3 = nowUs();
    g.sumDrawUs += t3 - t2;
    if (msaa && !g.msImplicit) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, g.msFbo); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, g.fbo);
        glBlitFramebuffer(0, 0, kW, kH, 0, 0, kW, kH, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, g.fbo);
    } else if (msaa) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, g.fbo);
    }
    glReadPixels(0, 0, kW, kH, GL_RGBA, GL_UNSIGNED_BYTE, g.readback.data());
    if (msaa) alphaToDs(g.readback.data(), 0, kH);
    const int64_t t4 = nowUs();
    g.sumReadUs += t4 - t3;
    const uint8_t* src = g.readback.data();
    uint8_t* target = j.target;
    scatterRows(src, target, 0, kH);
    const int64_t t5 = nowUs();
    g.sumScatterUs += t5 - t4;
    gpu3dSetBandMask(0xfffu);
    gpu3dSetPending(0);
    const int64_t dt = t5 - t0;
    {
        // Pair diagnostic companion: the 3D job's window on the GL thread (CLOCK_MONOTONIC us,
        // same clock as the flip pair log) so a late presenter fence can be placed against it.
        static int pairLog = 0; static int pairLogN = 0;
        if ((g.glFrames & 7) == 0) { const int on = property_get_int32("sys.gammaos.drastic_nano.flip_pair_log", 0); if (on && !pairLog) pairLogN = 0; pairLog = on; }
        if (pairLog && pairLogN < 3000) { pairLogN++; ALOGW("JOB3D s=%lld e=%lld gpu=%lld", (long long)t0, (long long)t5, (long long)(t3 - t2)); }
    }
    g.emaUs = g.emaUs == 0.f ? (float)dt : g.emaUs * 0.9f + (float)dt * 0.1f;
    g.sumUs += dt; if (dt > g.maxUs) g.maxUs = dt; g.frames++;
    if (g.frames % 300 == 0) {
        ALOGI("gpu3d: %u frames, GL thread %.2f ms (tex %.2f, upload+draw %.2f, gpu wait %.2f, readback %.2f, scatter %.2f), worker build %.2f ms, worker join wait %.2f ms, %zu verts, %u tex reuploads, max %.2f ms, %zu textures cached",
              g.frames, g.sumUs / 1000.0 / g.frames, g.sumTexUs / 1000.0 / g.frames, g.sumUploadUs / 1000.0 / g.frames,
              g.sumDrawUs / 1000.0 / g.frames, g.sumReadUs / 1000.0 / g.frames, g.sumScatterUs / 1000.0 / g.frames,
              g.sumBuildUs / 1000.0 / g.frames, g.sumWaitUs / 1000.0 / g.frames, g.vertCount, g.texReuploads, g.maxUs / 1000.0, g.texCache.size());
        ALOGI("gpu3d: census polys by mode modulate %u decal %u toon %u shadow %u, fog bit %u; textures by fmt none %u a3i5 %u pal4 %u pal16 %u pal256 %u comp %u a5i3 %u direct %u",
              g.modeCount[0], g.modeCount[1], g.modeCount[2], g.modeCount[3], g.fogPolys, g.fmtCount[0], g.fmtCount[1], g.fmtCount[2], g.fmtCount[3], g.fmtCount[4], g.fmtCount[5], g.fmtCount[6], g.fmtCount[7]);
        memset(g.modeCount, 0, sizeof g.modeCount); memset(g.fmtCount, 0, sizeof g.fmtCount); g.fogPolys = 0;
        g.sumUs = 0; g.maxUs = 0; g.frames = 0; g.sumTexUs = g.sumUploadUs = g.sumDrawUs = g.sumReadUs = g.sumScatterUs = g.sumBuildUs = g.sumWaitUs = 0; g.texReuploads = 0;
    }
}

void glThreadMain() {
    // The thread is spawned from the 3D worker and inherits its SCHED_FIFO 80; its CPU phases
    // (readback copies, scatter, texture staging) must not compete with the emulation and
    // audio threads, so run it at a lower realtime priority (sys gpu3d_gl_prio, default 40;
    // 0 = SCHED_OTHER) which still keeps it above the ordinary background threads.
    {
        const int prio = property_get_int32("sys.gammaos.drastic_nano.gpu3d_gl_prio", 40);
        struct sched_param sp = {}; sp.sched_priority = prio > 0 ? prio : 0;
        const int rc = pthread_setschedparam(pthread_self(), prio > 0 ? SCHED_FIFO : SCHED_OTHER, &sp);
        ALOGI("gpu3d: GL thread scheduling %s %d (rc %d)", prio > 0 ? "FIFO" : "OTHER", prio, rc);
    }
    initGl();
    std::unique_lock<std::mutex> lk(g.mtx);
    g.cvDone.notify_all();   // init outcome visible
    while (!g.quit) {
        g.cvSubmit.wait(lk, [] { return g.qCount > 0 || g.quit; });
        if (g.quit) break;
        Job* j = g.queue[g.qHead];
        g.pending = j;
        // Decoupled mode: start the job only after the next flip has landed (gpu3dNotePresent),
        // so the presenter's frame is on the GPU before the 8 ms 3D job. Measured otherwise: the
        // presenter's fence waited 5 to 14 ms whenever a 3D job started 3 to 6 ms into its wait
        // (98 of 98 slow fences in one capture), a 25 to 31 ms flip every 20 to 30 s. The job
        // has a frame of slack (kick to next kick); a present that never comes is bounded by
        // gpu3d_present_wait_us (default 16000, a whole period, 0 disables).
        // Only worth it when the present is due soon enough for the job to still land before the
        // next kick: Pokemon's kick sits 2 to 3 ms before the flip (wait, then render), Sonic's 7 to
        // 12 ms before it (waiting there pushed the job past the next kick: 34 stall seconds per 90
        // against 24 without). Predicted from the last two presents; sys gpu3d_present_wait_fit_us
        // adds margin (default 4000: with it Sonic skips the wait on 298 of 300 jobs and Pokemon on 294, both at their best stall counts, so the wait now only covers a present due within about 3 ms).
        if (j->decoupled) {
            // Fit margin: 4 ms with the 4x job (8 to 13 ms), 1.5 ms with the 2x job (7 ms; the 4 ms
            // margin skipped a third of the waits there and measured min 58.2 against 59.3).
            static int waitUs = 16000, fitUs = 4000;
            if ((g.glFrames & 63) == 0) { waitUs = property_get_int32("sys.gammaos.drastic_nano.gpu3d_present_wait_us", 16000); fitUs = property_get_int32("sys.gammaos.drastic_nano.gpu3d_present_wait_fit_us", g.ss == 1 ? 1500 : 4000); }
            bool fits = true;
            if (waitUs > 0) {
                int64_t lastP = 0, prevP = 0;
                { std::lock_guard<std::mutex> pl(gPhaseMtx); if (gPresentN >= 2) { lastP = gPresentTs[(gPresentN - 1) & 63]; prevP = gPresentTs[(gPresentN - 2) & 63]; } }
                if (lastP > 0) {
                    int64_t per = lastP - prevP; if (per < 8000 || per > 40000) per = 16700;
                    const int64_t now = nowUs();
                    int64_t due = lastP + per - now; if (due < 0) due = 0;
                    const int64_t jobUs = g.emaUs > 0.f ? (int64_t)g.emaUs : 9000;
                    fits = due + jobUs + fitUs <= per;
                    if (!fits) g.presentWaitSkips++;
                }
            }
            if (waitUs > 0 && fits && g.presentSeq == j->presentSeqAtQueue) {
                glStage(1);
                const int64_t w0 = nowUs();
                const bool ok = g.cvPresent.wait_for(lk, std::chrono::microseconds(waitUs), [j] { return g.presentSeq != j->presentSeqAtQueue || g.quit || g.joinWanted; });
                g.sumPresentWaitUs += nowUs() - w0; if (!ok) g.presentWaitTimeouts++;
                if (g.quit) break;
            }
        }
        const bool skip = j->cancel;
        if (!skip) g.rendering = j;
        lk.unlock();
        if (!skip) {
            glStage(2);
            if (g.inited) renderJob(*j);
            else { gpu3dSetBandMask(0xfffu); gpu3dSetPending(0); }
            glStage(40);
        }
        lk.lock();
        g.qHead ^= 1; g.qCount--;
        if (!skip) g.latest.store(j->target, std::memory_order_release);
        g.rendering = nullptr;
        g.pending = nullptr;
        g.cvDone.notify_all();
    }
}

static bool gDecoupleWanted = false;   // decoupled mode selected (knob or the 4x setting)

// Worker side: block until the GL thread has finished every queued job. Also leaves the
// decoupled mode (the CPU rasterizer or the progressive path takes over from here).
void joinPrevious(bool cancelQueued = false) {
    const int64_t t0 = nowUs();
    std::unique_lock<std::mutex> lk(g.mtx);
    // A synchronous frame after an engine swap: the queued decoupled frame (not yet rendering)
    // would show on the wrong screen anyway, and rendering it first cost 40 to 60 ms of GL time
    // at the transition. Drop it.
    if (cancelQueued) for (int i = 0; i < g.qCount; i++) { Job* q = g.queue[(g.qHead + i) & 1]; if (q != g.rendering) q->cancel = true; }
    // A queued decoupled job may be sitting in its present wait (up to 16 ms): the frame that
    // needs this join (a synchronous frame after an engine swap) cannot afford it. Measured on
    // Pokemon: the swap frame ran past the 40 ms band wait and drained the audio queue (960
    // underrun frames in a 10 minute soak).
    if (g.qCount > 0) { g.joinWanted = true; g.cvPresent.notify_all(); }
    while (g.qCount > 0) g.cvDone.wait(lk);
    g.joinWanted = false;
    g.latest.store(nullptr, std::memory_order_release);
    g.decoupledActive.store(false, std::memory_order_release);
    g.sumWaitUs += nowUs() - t0;
}
// Decoupled mode: wait only until the GL queue has room (at most one job running or queued).
static void waitQueueRoom() {
    const int64_t t0 = nowUs();
    std::unique_lock<std::mutex> lk(g.mtx);
    while (g.qCount >= 2) g.cvDone.wait(lk);
    g.sumRoomUs += nowUs() - t0;
}

}  // namespace

// Presenter GPU time (the DS frame's filter pass on the render thread, its own context):
// timer query per frame, results read one frame late, logged every 300 frames when the
// sys gpu3d_gputime knob is set. Sizes the contention with the 3D job on the shared Mali.
namespace {
struct PresTimer { GLuint q[2] = {}; int cur = 0; bool inited = false, ext = false; int64_t sumNs = 0; uint32_t n = 0; uint32_t polls = 0; int on = 0; };
PresTimer gPres;
PFNGLGENQUERIESEXTPROC pqGen = nullptr; PFNGLBEGINQUERYEXTPROC pqBegin = nullptr; PFNGLENDQUERYEXTPROC pqEnd = nullptr;
PFNGLGETQUERYOBJECTUI64VEXTPROC pqGet64 = nullptr; PFNGLGETQUERYOBJECTUIVEXTPROC pqGet = nullptr;
}
extern "C" void gpu3dPresenterTimerBegin() {
    if (gPres.polls < 3) ALOGI("gpu3d: presenter probe call %u (gputime prop %d)", gPres.polls, property_get_int32("sys.gammaos.drastic_nano.gpu3d_gputime", 0));
    if ((gPres.polls++ & 63) == 0) gPres.on = property_get_int32("sys.gammaos.drastic_nano.gpu3d_gputime", 0);
    if (!gPres.on) return;
    if (!gPres.inited) {
        gPres.inited = true;
        const char* ext = (const char*)glGetString(GL_EXTENSIONS);
        gPres.ext = ext && strstr(ext, "GL_EXT_disjoint_timer_query");
        if (gPres.ext) {
            pqGen = (PFNGLGENQUERIESEXTPROC)eglGetProcAddress("glGenQueriesEXT"); pqBegin = (PFNGLBEGINQUERYEXTPROC)eglGetProcAddress("glBeginQueryEXT");
            pqEnd = (PFNGLENDQUERYEXTPROC)eglGetProcAddress("glEndQueryEXT"); pqGet64 = (PFNGLGETQUERYOBJECTUI64VEXTPROC)eglGetProcAddress("glGetQueryObjectui64vEXT");
            pqGet = (PFNGLGETQUERYOBJECTUIVEXTPROC)eglGetProcAddress("glGetQueryObjectuivEXT");
            if (pqGen) pqGen(2, gPres.q);
        }
        ALOGI("gpu3d: presenter probe on the render thread: timer query ext %d, queries %u %u", gPres.ext, gPres.q[0], gPres.q[1]);
    }
    if (!gPres.ext || !gPres.q[0]) return;
    // collect the previous frame's result without blocking
    GLuint other = gPres.q[gPres.cur ^ 1]; GLuint avail = 0;
    if (gPres.n || gPres.polls > 2) { pqGet(other, 0x8867, &avail); if (avail) { GLuint64 ns = 0; pqGet64(other, 0x8866, &ns); gPres.sumNs += (int64_t)ns; gPres.n++; } }
    pqBegin(0x88BF, gPres.q[gPres.cur]);
}
extern "C" void gpu3dPresenterTimerEnd() {
    if (!gPres.on || !gPres.ext || !gPres.q[0]) return;
    pqEnd(0x88BF);
    gPres.cur ^= 1;
    static uint32_t passes = 0;
    if (++passes % 600 == 0) { ALOGI("gpu3d: presenter GPU time %.2f ms avg per screen pass over %u measured passes of 600 (two per frame)", gPres.n ? gPres.sumNs / 1e6 / gPres.n : 0.0, gPres.n); gPres.sumNs = 0; gPres.n = 0; }
}

static volatile int gFastForward = 0;
extern "C" void gpu3dSetFastForward(bool on) { gFastForward = on ? 1 : 0; }

extern "C" void gpu3dNotePresent() {
    { std::lock_guard<std::mutex> lk(gPhaseMtx); gPresentTs[gPresentN++ & 63] = nowUs(); }
    { std::lock_guard<std::mutex> lk(g.mtx); g.presentSeq++; }
    g.cvPresent.notify_all();
}


// Returns true when the frame was rendered on the GPU (drastic's target buffer holds the
// result and every band is marked complete); false = the caller must run the CPU path.
extern "C" bool gpu3dFrame(uint8_t* R, uint32_t arg1, uint8_t* lib) {
    if (!g.threadStarted) {
        g.threadStarted = true;
        g.glThread = std::thread(glThreadMain);
        g.glThread.detach();
    }
    // Do not block the 3D worker while the GL thread initialises and warms up (~300 ms): the
    // CPU rasterizer keeps running until the GPU path is ready.
    if (!g.inited) return false;
    if ((g.frame & 63) == 0 || g.frame < 3) {
        // From the settings, not from the GL thread's g.ss (unset on the first frames: the
        // budget guard then ran with the 6 ms progressive budget and tripped on the first
        // whole-frame jobs, CPU rasterizer for the session).
        // Decoupled at both settings. At plain 2x the progressive policy ran four fenced chunks
        // with partial readbacks (13 ms GL jobs) and made the emulator wait on bands: Pokemon
        // slot 0 measured 59.1 avg / 55.2 min against 59.7 / 58.3 on the CPU rasterizer, while
        // decoupled at 2x measured 59.8 / 59.3 with 7 ms jobs. sys gpu3d_decouple 0 restores it.
        const int k = property_get_int32("sys.gammaos.drastic_nano.gpu3d_decouple", -1);
        gDecoupleWanted = k >= 0 ? k != 0 : true;
    }
    if (gFastForward) { joinPrevious(); return false; }   // CPU rasterizer during fast forward
    {
        // Budget guard (runtime knob gpu3d_budget_ms, default 8): a scene whose GPU frame stays
        // above the budget starves the compositor's band waits, so hand it back to the CPU
        // rasterizer for 600 frames and retry. 0 disables the guard.
        // Budget: the first band quarter must be ready within gpu3d_budget_ms (default 6) in
        // progressive mode (whole frame time otherwise).
        // Decoupled mode (below) renders whole frames into a buffer the compositor is not reading,
        // so its budget is the frame period: a job that stays above 16 ms cannot keep up.
        static int budgetMs = 6; if ((g.frame & 63) == 0 || g.frame < 3) budgetMs = property_get_int32("sys.gammaos.drastic_nano.gpu3d_budget_ms", gDecoupleWanted ? 16 : 6);
        if (g.backoffFrames) {
            // Sticky for the session: a retry costs one over-budget frame and on Pokemon the
            // retry's first GPU frame once stalled for 12 s (fps 50 for 14 s), so a scene that
            // trips the budget stays on the CPU rasterizer until the GPU setting is toggled or
            // the game is relaunched. (sys gpu3d_retry_frames > 0 re-enables timed retries.)
            static int retryFrames = 0; if ((g.frame & 63) == 0) retryFrames = property_get_int32("sys.gammaos.drastic_nano.gpu3d_retry_frames", 0);
            if (retryFrames > 0 && ++g.backoffElapsed >= (uint32_t)retryFrames) {
                g.backoffFrames = 0; g.backoffElapsed = 0; g.emaUs = 0.f; ALOGI("gpu3d: retrying the GPU path");
            } else { joinPrevious(); return false; }
        } else if (budgetMs > 0 && g.frame > 30 && g.emaUs > budgetMs * 1000.f && ++g.overBudget >= 90) {   // ~1.5 s sustained
            g.backoffFrames = 1; g.backoffElapsed = 0; g.backoffs++;
            ALOGW("gpu3d: first quarter / frame %.1f ms over the %d ms budget, CPU rasterizer for the rest of the session (backoff %u)",
                  g.emaUs / 1000.f, budgetMs, g.backoffs);
            joinPrevious(); return false;
        } else if (g.emaUs <= budgetMs * 1000.f) g.overBudget = 0;
    }
    if ((g.frame & 15) == 0) { gTexDbg = property_get_int32("sys.gammaos.drastic_nano.gpu3d_texdump", 0); gPersp = property_get_int32("sys.gammaos.drastic_nano.gpu3d_persp", 1); }
    gDbgFrame = gDbgArm.exchange(0, std::memory_order_relaxed); gDbgHits = 0;
    if (gDbgFrame) ALOGW("gpu3d: texdbg dump frame %u (texdbg %d)", g.frame, gTexDbg);
    uint8_t* S = R + 0x3606e8;
    uint8_t* gSide = *reinterpret_cast<uint8_t**>(S);
    uint8_t* regs = R + 0x34eb40;
    uint8_t* cfg = *reinterpret_cast<uint8_t**>(R + 8);
    uint8_t* bufA = R + 0x1056c0;
    uint8_t* bufB = R + 0x1c56c0;
    uint8_t* gx = R + 0x356cb0;
    const bool threaded = *reinterpret_cast<uint32_t*>(cfg + 1128) != 0;
    // Decoupled mode: the compositor reads the newest finished frame while the GL thread renders
    // the next one into another buffer, so the emulator never waits for the GPU. Measured on
    // Pokemon battles at 4x: the join below stalled the 3D worker 8 to 12 ms a frame, the
    // emulator followed (56 to 58 emulated frames a second), the audio queue ran dry and
    // AudioFlinger padded silence (the pops). The 3D layer is one frame late, two while a GPU
    // frame runs over the period. Needs drastic's threaded 3D (the compositor line fetch reads
    // the target pointer at regs+24). Default with the 4x setting; sys gpu3d_decouple 1/0 overrides.
    // Display capture (render+0x458836, armed for this frame): the captured image must be THIS
    // frame's 3D. Mario Kart DS draws both screens with one engine by capturing the 3D every
    // frame and showing the capture on the other screen; with the layer one frame late every
    // frame landed on the wrong screen (the intro's two cameras swapped, the user's "entire
    // scene corrupted"). Such frames run synchronously: the compositor waits for the job.
    // The same holds when the game swaps the engines between the screens (POWCNT1 bit 15,
    // master+0x1b374): a frame late, the layer lands on the other screen. Both cases are games
    // that alternate views every frame (Mario Kart's pre-race line-up flickered between the two
    // cameras), so any capture or swap holds the synchronous path a few frames; a game that
    // keeps doing it stays synchronous.
    static uint32_t syncHold = 0; static int lastPow15 = -1;
    const uint8_t* master = *reinterpret_cast<uint8_t* const*>(R);
    const int pow15 = master ? (*reinterpret_cast<const uint16_t*>(master + 0x1b374) >> 15) & 1 : 0;
    const bool swapNow = lastPow15 >= 0 && pow15 != lastPow15; lastPow15 = pow15;
    // A capture only matters when the OTHER engine displays it (DISPCNT B display mode 2 = VRAM,
    // the Dragon Ball Origins style of dual-screen 3D). Pokemon White 2 captures every frame and
    // shows it on its own engine A (mode 2): a uniform delay, invisible, so it keeps the
    // decoupled path (and its audio).
    const bool capNow = R[0x458836] != 0;
    const uint32_t modeA = master ? (*reinterpret_cast<const uint32_t*>(master + 0x1b070) >> 16) & 3 : 0;
    const uint32_t modeB = master ? (*reinterpret_cast<const uint32_t*>(master + 0x1c070) >> 16) & 3 : 0;
    const bool capShown = capNow && modeB == 2;
    if (capShown || swapNow) {
        if (!syncHold) ALOGI("gpu3d: %s (display modes A %u B %u), synchronous frames", swapNow ? "engine swap" : "display capture shown", modeA, modeB);
        // Short hold: a game that alternates re-arms it every frame; a one-off swap (Pokemon at a
        // battle transition) costs 4 synchronous frames, not 4 seconds at 53 fps (measured).
        syncHold = 4;
    } else if (syncHold) syncHold--;
    if ((g.frame % 600) == 3 && capNow && !capShown) ALOGI("gpu3d: capture armed but not displayed (modes A %u B %u), decoupled path kept", modeA, modeB);
    const bool capSync = syncHold > 0;
    const bool decoupled = gDecoupleWanted && threaded && !capSync;
    if (decoupled) {
        if (!g.bufC) { void* m = nullptr; if (posix_memalign(&m, 64, 0xc0000) == 0 && m) { memset(m, 0, 0xc0000); g.bufC = (uint8_t*)m; } }
    }
    if (!decoupled || !g.bufC) joinPrevious(capSync);   // the previous GPU frame must be in the buffers before regs/target move on
    else waitQueueRoom();
    const int64_t t0 = nowUs();
    gStage = 1;
    auto fnDirty = reinterpret_cast<uint32_t (*)(uint8_t*)>(lib + 0x714ec);
    auto fnFog = reinterpret_cast<void (*)(uint8_t*, uint8_t*)>(lib + 0x545cc);
    auto fnBinOpaque = reinterpret_cast<void (*)(uint8_t*, uint8_t*, const uint8_t*, const uint8_t*, uint32_t)>(lib + 0x5b144);
    auto fnBinTransl = reinterpret_cast<void (*)(uint8_t*, uint8_t*, const uint8_t*, const uint8_t*, uint32_t)>(lib + 0x5b71c);

    // Bookkeeping exactly as the worker frame (+0x5eebc) does before rasterizing.
    const bool rearDirty = (regs[1] & 0x40) ? (*reinterpret_cast<uint16_t*>(gSide + 0x8012) != 0) : false;
    const uint32_t dirty = fnDirty(gSide);
    uint8_t* target;
    uint8_t* prevDrawn = *reinterpret_cast<uint8_t**>(regs + 40);
    const bool useDecoupled = decoupled && g.bufC;
    if (useDecoupled) {
        // Read pointer: the newest finished frame (the CPU rasterizer's last frame before the
        // first GPU frame lands). Write buffer: one of the three that is neither read now nor
        // the target of the job still in the GL queue.
        uint8_t* latest = g.latest.load(std::memory_order_acquire);
        uint8_t* readBuf = latest ? latest : (prevDrawn ? prevDrawn : bufA);
        uint8_t* busy = nullptr;
        { std::lock_guard<std::mutex> lk(g.mtx); if (g.qCount > 0) busy = g.queue[g.qHead]->target; }
        uint8_t* cands[3] = { bufA, bufB, g.bufC };
        target = nullptr;
        for (uint8_t* c : cands) if (c != readBuf && c != busy) { target = c; break; }
        if (!target) target = g.bufC;   // cannot happen with three buffers
        *reinterpret_cast<uint8_t**>(regs + 24) = readBuf;
        g.decoupledActive.store(true, std::memory_order_release);
        gpu3dSetPending(0);
        gpu3dSetBandMask(0xfffu);   // the compositor never waits: it reads a finished frame
    } else if (threaded) {
        uint8_t* pub = *reinterpret_cast<uint8_t**>(regs + 32);
        target = (pub == bufA) ? bufB : bufA;
        *reinterpret_cast<uint8_t**>(regs + 24) = target;
    } else {
        uint32_t v = *reinterpret_cast<uint32_t*>(S + 80);
        *reinterpret_cast<uint32_t*>(regs) = v;
        *reinterpret_cast<uint32_t*>(regs + 4) = (v & 4) ? S[135] : 0;
        target = *reinterpret_cast<uint8_t**>(regs + 24);
    }
    if (arg1 || ((dirty | (rearDirty ? 1u : 0u)) == 0 && S[151] == 0)) {
        uint8_t* last = *reinterpret_cast<uint8_t**>(regs + 40);
        static int reuseLogs = 0; if (reuseLogs < 3) { reuseLogs++; ALOGI("gpu3d: frame reuse: arg1 %u dirty %u rear %d geom %u target %p last %p", arg1, dirty, rearDirty, S[151], target, last); }
        if (!useDecoupled && threaded && target != last && last) memcpy(target, last, 0xc0000);
        gpu3dSetBandMask(0xfffu);
        return true;
    }
    gStage = 2;
    *reinterpret_cast<uint8_t**>(regs + 40) = target;
    S[151] = 0;
    const uint32_t bank = (S[136] ^ 1) & 1;
    const uint8_t* vb = gx + 0x9ad4 + bank * 0x18004;
    uint8_t* pbo = gx + 0x39ae0 + bank * 0x10008;
    uint8_t* pbt = gx + 0x59af0 + bank * 0x10008;
    gStage = 3;
    // Fog prep takes (gx, R): it folds the 32 densities into their deltas at gx+0x9994 and writes
    // the all-equal flag (the fog colour, or -1) at R+0x249490. The worker frame passes R
    // (x20), not the target buffer; with the target the flag went to regs+16.
    if (regs[0] & 0x80) fnFog(gx, R);
    gStage = 4;
    fnBinOpaque(gx, R + 0x2856c0, pbo, vb, 0);
    fnBinTransl(gx, R + 0x2916f0, pbt, vb, 1);
    gStage = 5;
    R[0x2c1813 + 1] = (uint8_t)*reinterpret_cast<uint32_t*>(cfg + 1180);
    R[0x2c1813] = (uint8_t)*reinterpret_cast<uint32_t*>(cfg + 1168);

    // GPU fill.
    const uint32_t* shapeTbl = reinterpret_cast<const uint32_t*>(lib + 0x10e57c);
    const bool wbuf = (gx[0x9acc] >> 1) & 1;
    const uint32_t disp3d = *reinterpret_cast<uint32_t*>(regs);
    const bool texEnabled = disp3d & 1;
    // Clear colour: the worker state's raw CLEAR_COLOR (S+84, DS format: rgb555, bit 15 fog, alpha
    // in bits 16-20, id in 24-29) expanded to the buffer's r6g6b6a5. regs+4 is what the
    // non-threaded path derives and stays 0 in threaded mode: Sonic Rush's white level
    // transition (rear plane white, alpha 31) was missing on the GPU path because of it.
    uint32_t clearC = *reinterpret_cast<uint32_t*>(regs + 4);
    if (threaded) {
        const uint32_t cc = *reinterpret_cast<uint32_t*>(S + 84);
        const uint32_t r5 = cc & 31, g5 = (cc >> 5) & 31, b5 = (cc >> 10) & 31, a5 = (cc >> 16) & 31;
        auto x6 = [](uint32_t v) { return (v << 1) | (v >> 4); };   // 5 to 6 bits, top bit replicated (31 -> 63, as the CPU layer shows)
        clearC = x6(r5) | (x6(g5) << 8) | (x6(b5) << 16) | (a5 << 24);
    }
    const uint32_t clearD = *reinterpret_cast<uint32_t*>(regs + 12) & 0xffffff;
    g.frame++;
    gStage = 6;
    Job& job = g.jobs[g.jobIdx]; g.jobIdx = (g.jobIdx + 1) % 3;
    g.cur = &job;
    for (int i = 0; i < 8; i++) { job.opaque.v[i].clear(); job.transl.v[i].clear(); }
    job.shadow.clear(); job.shadowSegs.clear(); job.cancel = false;
    job.uploads.clear();
    job.target = target; job.clearC = clearC; job.clearD = clearD; job.decoupled = useDecoupled;
    // Edge marking: drastic-nano's mode-5 pipeline drops drastic's per-band edge pass and the
    // dumped CPU frames show no edge pixels on the controls, so the GPU pass is opt-in
    // (sys.gammaos.drastic_nano.gpu3d_edge=1) until the CPU behaviour is pinned down.
    // drastic-nano ships with "Disable Edge Marking" on (main.cpp seeds disableEdge = true and
    // the config bit makes libdrastic skip its edge pass), which is why the CPU frames carry no
    // edge pixels. The GPU pass follows that same user setting; sys gpu3d_edge overrides for tests.
    static int edgeOpt = 0;
    if ((g.frame & 63) == 1) {
        const int ov = property_get_int32("sys.gammaos.drastic_nano.gpu3d_edge", -1);
        edgeOpt = ov >= 0 ? ov : !property_get_bool("persist.gammaos.drastic_nano.disable_edge", true);
    }
    job.edge = (disp3d & 0x20) != 0 && edgeOpt;
    // Clear colour source check (sys gpu3d_clearlog=1): regs+4 against the gx register mirror
    // around CLEAR_COLOR (gx+0x9964 if the mirror is linear like the fog table at +0x9974).
    {
        static int clearLog = 0; if ((g.frame & 63) == 5) clearLog = property_get_int32("sys.gammaos.drastic_nano.gpu3d_clearlog", 0);
        if (clearLog && (g.frame % 20) == 0) {
            const uint32_t* gw = reinterpret_cast<const uint32_t*>(gx + 0x9960);
            const uint8_t* fb = gx + 0x9974;   // raw FOG_TABLE mirror, as the prep reads it
            ALOGI("gpu3d: fog: gx+9940.. %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x table %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x %02x R+249490 %08x",
                  gw[-8], gw[-7], gw[-6], gw[-5], gw[-4], gw[-3], gw[-2], gw[-1], gw[0], gw[1], gw[2], gw[3], gw[4],
                  fb[0], fb[1], fb[2], fb[3], fb[4], fb[5], fb[6], fb[7], fb[8], fb[9], fb[10], fb[11], fb[12], fb[13], fb[14], fb[15], fb[16], fb[17], fb[18], fb[19], fb[20], fb[21], fb[22], fb[23], fb[24], fb[25], fb[26], fb[27], fb[28], fb[29], fb[30], fb[31],
                  *reinterpret_cast<const uint32_t*>(R + 0x249490));
            const uint32_t* sw = reinterpret_cast<const uint32_t*>(S + 80);
            ALOGI("gpu3d: clear: regs+4 %08x regs+12 %08x S+80.. %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x %08x S135 %02x gx9960 %08x",
                  clearC, *reinterpret_cast<uint32_t*>(regs + 12), sw[0], sw[1], sw[2], sw[3], sw[4], sw[5], sw[6], sw[7], sw[8], sw[9], sw[10], sw[11], sw[12], sw[13], sw[14], sw[15], sw[16], sw[17], sw[18], sw[19], S[135], gw[1]);
        }
    }
    if (disp3d != g.lastDisp3d) {
        g.lastDisp3d = disp3d;
        ALOGI("gpu3d: DISP3DCNT %08x (tex %d shading %d alphatest %d blend %d aa %d edge %d fogmode %d fog %d fogshift %u rear %d) clear %08x %08x %08x",
              disp3d, disp3d & 1, (disp3d >> 1) & 1, (disp3d >> 2) & 1, (disp3d >> 3) & 1, (disp3d >> 4) & 1, (disp3d >> 5) & 1,
              (disp3d >> 6) & 1, (disp3d >> 7) & 1, (disp3d >> 8) & 15, (disp3d >> 14) & 1,
              *reinterpret_cast<uint32_t*>(regs + 4), *reinterpret_cast<uint32_t*>(regs + 8), *reinterpret_cast<uint32_t*>(regs + 12));
    }
    job.clearId = *reinterpret_cast<uint32_t*>(regs + 12) >> 24;   // clear depth word carries the clear polygon id in bits 24-29
    // Fog: drastic's band post pass (+0x5829c) reads the density table at gx+0x9974, the colour word
    // at gx+0x9a9c (r, g, b 6-bit, a 5-bit bytes), the offset halfword at gx+0x9aaa and the shift
    // from DISP3DCNT bits 8-11; the per-pixel maths is in the fragment shader (uFog*).
    static int fogKnob = 1; if ((g.frame & 63) == 7) fogKnob = property_get_int32("sys.gammaos.drastic_nano.gpu3d_fog", 1);
    job.fog = (fogKnob && (disp3d & 0x80)) ? ((disp3d & 0x40) ? 2 : 1) : 0;
    if (job.fog) {
        job.fogShift = (disp3d >> 8) & 15;
        job.fogOffset = (0x400 >> job.fogShift) + (*reinterpret_cast<const uint16_t*>(gx + 0x9aaa) & 0x7fff);
        job.fogColor = *reinterpret_cast<const uint32_t*>(gx + 0x9a9c);
        const uint8_t* ft = gx + 0x9974;
        for (int i = 0; i < 32; i++) { job.fogTable[i] = ft[i]; job.fogDelta[i] = i < 31 ? (int)ft[i + 1] - (int)ft[i] : 0; }
        static int fogLogs = 0;
        if (fogLogs < 3) { fogLogs++; ALOGI("gpu3d: fog on: mode %d shift %d offset(+step) %d colour %08x table %d..%d", job.fog, job.fogShift, job.fogOffset, job.fogColor, job.fogTable[0], job.fogTable[31]); }
    }
    memcpy(job.edgeTbl, gx + 0x99b4, sizeof job.edgeTbl);   // edge colour table, u32 r6 g6 b6 (+0x5c9d4 reads gx+0x99b4)
    if (g.frame < 3) ALOGI("gpu3d: edge %d clearId %x table %08x %08x %08x %08x %08x %08x %08x %08x", job.edge, job.clearId,
                           job.edgeTbl[0], job.edgeTbl[1], job.edgeTbl[2], job.edgeTbl[3], job.edgeTbl[4], job.edgeTbl[5], job.edgeTbl[6], job.edgeTbl[7]);
    gStage = 7;
    g.texUsFrame = 0;
    const int64_t tb0 = nowUs();
    buildList(R + 0x2856c0, pbo, vb, shapeTbl, wbuf, false, texEnabled, job.opaque);
    buildList(R + 0x2916f0, pbt, vb, shapeTbl, wbuf, true, texEnabled, job.transl);
    g.sumBuildUs += nowUs() - tb0;
    g.cur = nullptr;
    gStage = 8;
    if (!useDecoupled) gpu3dSetPending(1);
    // No-wait policy for the top bands (sys gpu3d_nowait_bands, default 0): the previous
    // frame's rows for those bands are copied into the target and the bands marked complete
    // right away, so the compositor composes them without waiting for the first GPU chunk
    // (a one-frame lag on those lines whenever the GPU is late; the GPU chunk overwrites
    // them when it lands). The remaining bands still wait for their chunks.
    // Default: with the 4x setting on, the adaptive policy (up to one frame of 3D lag on heavy
    // frames, as the setting's prompt says); at plain 2x no lag. sys gpu3d_nowait_bands overrides.
    static int nowaitBands = 0;
    if ((g.frame & 63) == 1) {
        const int ov = property_get_int32("sys.gammaos.drastic_nano.gpu3d_nowait_bands", -99);
        // 4x: the whole layer one frame late, always (the adaptive variant, -1, oscillates on
        // heavy scenes: once the layer lags the first quarter lands early, the lag turns off,
        // the waits come back; measured 56.6 to 58.6 vs 59.0 to 59.7 for the fixed policy).
        nowaitBands = ov != -99 ? ov : (g.ss != 1 ? 12 : 0);
    }
    if (nowaitBands > 12) nowaitBands = 12;
    if (capSync) nowaitBands = 0;   // a captured frame cannot be late
    if (nowaitBands < 0) {
        // Adaptive: whole-layer lag only while the first quarter has been landing late
        // (EMA of its ready time above 4.0 ms), back to no-lag once it is under 2.5 ms.
        static int lagOn = 0;
        const float fq = g.firstUs;
        if (!lagOn && fq > 4000.f) { lagOn = 1; g.lagEnters++; ALOGI("gpu3d: lag on (first quarter %.2f ms)", fq / 1000.f); }
        else if (lagOn && fq < 2500.f && fq > 0.f) { lagOn = 0; ALOGI("gpu3d: lag off (first quarter %.2f ms)", fq / 1000.f); }
        nowaitBands = lagOn ? 12 : 0;
    }
    uint32_t earlyMask = 0;
    if (useDecoupled) earlyMask = 0xfffu;
    else if (nowaitBands > 0 && prevDrawn && prevDrawn != target) {
        memcpy(target, prevDrawn, (size_t)nowaitBands * 32 * 0x800);
        earlyMask = (nowaitBands >= 12) ? 0xfffu : ((1u << nowaitBands) - 1u);
    }
    job.earlyMask = earlyMask;
    if ((g.frame % 300) == 2) ALOGI("gpu3d: policy: nowait %d, ss %d, decoupled %d, prevDrawn %p target %p, early mask %03x, queue room wait %.2f ms avg, present wait %.2f ms avg (%u timeouts, %u skipped as not fitting)", nowaitBands, g.ss, useDecoupled ? 1 : 0, prevDrawn, target, earlyMask, g.sumRoomUs / 300000.0, g.sumPresentWaitUs / 300000.0, g.presentWaitTimeouts, g.presentWaitSkips);
    if ((g.frame % 300) == 2) { g.sumRoomUs = 0; g.sumPresentWaitUs = 0; g.presentWaitTimeouts = 0; g.presentWaitSkips = 0; }
    {
        std::lock_guard<std::mutex> lk(g.mtx);
        job.presentSeqAtQueue = g.presentSeq;
        g.queue[(g.qHead + g.qCount) & 1] = &job; g.qCount++;
    }
    g.cvSubmit.notify_one();
    if (earlyMask) gpu3dSetBandMask(earlyMask);
    (void)t0;
    gStage = 11;
    return true;
}

// Compositor side (first compose chunk of a frame, after the band gate): in decoupled mode
// point the line fetch at the newest finished frame. The worker set regs+24 at the kick; a job
// that finished since then is a fresher frame and no job writes it (targets are chosen away
// from the newest frame and from the queued job). Keeps the 3D layer one frame late whenever the
// GPU finishes within the period.
extern "C" void gpu3dJoinForDump() {
    if (!g.threadStarted) return;
    std::unique_lock<std::mutex> lk(g.mtx);
    while (g.qCount > 0) g.cvDone.wait(lk);
}
extern "C" void gpu3dOff() {
    if (!g.threadStarted) return;
    if (g.decoupledActive.load(std::memory_order_acquire) || g.latest.load(std::memory_order_acquire)) joinPrevious();
}
extern "C" void gpu3dLatchRead(uint8_t* R) {
    if (!g.decoupledActive.load(std::memory_order_acquire)) return;
    uint8_t* latest = g.latest.load(std::memory_order_acquire);
    if (!latest) return;
    uint8_t** rd = reinterpret_cast<uint8_t**>(R + 0x34eb40 + 24);
    if (*rd != latest) *rd = latest;
}

}  // namespace android
