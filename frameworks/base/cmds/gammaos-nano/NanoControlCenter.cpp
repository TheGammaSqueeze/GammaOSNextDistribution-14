// GammaOS Nano - bottom-screen Control Center (AYN-THOR-inspired dashboard).
//
// Shown on the BOTTOM panel of a dual-screen device (RG DS) while a SINGLE-SCREEN
// (non-dual-stack) app is running fullscreen on the top panel, in nano overlay mode.
// Live performance dashboard: CPU/GPU/temp/RAM/battery ring gauges, a best-effort FPS
// hero, plus (M2) interactive brightness/volume sliders, Quick Clean, and a bottom-
// screen sleep tile. Gated by persist.gammaos.nano.ps3xmb.controlcenter.
//
// The enabler lives in NanoMenu.cpp threadLoop (the overlay park branch) + the EGL
// plumbing in NanoMenuRender.cpp (renderControlCenterFrame). This file owns the data
// poll (sysfs, root) and the GLES2 dashboard draw. Design space is 640x480, scaled
// uniformly to the panel so it adapts to any secondary size. The visual style is kept
// deliberately flat (solid cards, single-pass numerals, single-pass ring gauges, no glow)
// so the bottom panel costs as little GPU as possible while a game runs on the top panel.

#define LOG_TAG "GammaOSNano"
#include "NanoMenu.h"
#include "NanoMenuShaders.h"   // FONT_CHAR_H
#include <cutils/properties.h>
#include <sys/system_properties.h>
#include <GLES2/gl2.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <time.h>
#include <linux/input.h>
#include <map>
#include <thread>
#include <atomic>
#include <utils/Log.h>

namespace android {

// ---------------------------------------------------------------------------
// Live system stats (root-readable sysfs on the RK3568 RG DS). File-static: only
// this translation unit touches them; the render reads the last poll.
// ---------------------------------------------------------------------------
namespace {

struct CcStat {
    int   cpuMhz = 0,  cpuMaxMhz = 2160;
    int   cpuPct = 0;
    int   gpuMhz = 0,  gpuMaxMhz = 900;
    int   gpuPct = 0;
    int   socTempC = 0, gpuTempC = 0;
    int   ramUsedMb = 0, ramTotalMb = 0;
    int   battPct = 0;
    float watts = 0.0f;
    bool  charging = false;
    int   briTop = 0,  briTopMax = 255;
    int   briBot = 0,  briBotMax = 255;
    int   volCur = 0,  volMax = 15,  volMin = 0;
    int   fps = 60;
    int   hh = 0, mm = 0;
    bool  wifiOn = false;
    int64_t lastPollMs = 0;
    int64_t lastVolMs  = 0;
    int64_t lastSlowMs = 0;
    unsigned long long prevIdle = 0, prevTotal = 0;
    bool  primed = false;
    // Async volume/wifi refresh: the popen reads run on a detached worker so they never stall the
    // render thread. The worker writes ONLY these staging fields; the render thread publishes them
    // to the live fields above, staying the single writer of the live values.
    int   sVolCur = 0, sVolMin = 0, sVolMax = 15;
    bool  sWifiOn = false;
    std::atomic<bool> volFresh{false};   // worker: staging ready; render: consume + clear
    std::atomic<bool> volBusy{false};    // a refresh is in flight (prevents overlapping workers)
};
CcStat sCc;

int64_t nowMs() {
    struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
    return (int64_t)t.tv_sec * 1000 + t.tv_nsec / 1000000;
}

int readIntFile(const char* path, int def) {
    FILE* f = fopen(path, "r");
    if (!f) return def;
    int v = def;
    if (fscanf(f, "%d", &v) != 1) v = def;
    fclose(f);
    return v;
}

long long readLLFile(const char* path, long long def) {
    FILE* f = fopen(path, "r");
    if (!f) return def;
    long long v = def;
    if (fscanf(f, "%lld", &v) != 1) v = def;
    fclose(f);
    return v;
}

// Backlight node paths. The CC renders on the BOTTOM panel; on the RG DS that panel is driven by
// the "backlight" node and the TOP (game) panel by "backlight1" (the sysfs naming does NOT match
// the SF primary/secondary ordering). Flip via persist.gammaos.nano.cc.bl_bottom / bl_top if wrong.
const char* ccBlBri(bool bottom) {
    static std::string sBot, sTop; static bool r = false;
    if (!r) {
        char b[PROPERTY_VALUE_MAX] = {}, t[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.cc.bl_bottom", b, "backlight");
        property_get("persist.gammaos.nano.cc.bl_top",    t, "backlight1");
        sBot = std::string("/sys/class/backlight/") + b + "/brightness";
        sTop = std::string("/sys/class/backlight/") + t + "/brightness";
        r = true;
    }
    return bottom ? sBot.c_str() : sTop.c_str();
}
const char* ccBlMax(bool bottom) {
    static std::string sBot, sTop; static bool r = false;
    if (!r) {
        char b[PROPERTY_VALUE_MAX] = {}, t[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.cc.bl_bottom", b, "backlight");
        property_get("persist.gammaos.nano.cc.bl_top",    t, "backlight1");
        sBot = std::string("/sys/class/backlight/") + b + "/max_brightness";
        sTop = std::string("/sys/class/backlight/") + t + "/max_brightness";
        r = true;
    }
    return bottom ? sBot.c_str() : sTop.c_str();
}

void ccReadCpu() {
    int mx = 0;
    for (int c = 0; c < 8; c++) {
        char p[96];
        snprintf(p, sizeof(p), "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", c);
        int khz = readIntFile(p, 0);
        if (khz > mx) mx = khz;
    }
    if (mx > 0) sCc.cpuMhz = mx / 1000;
    int maxk = readIntFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", 2160000);
    if (maxk > 0) sCc.cpuMaxMhz = maxk / 1000;
    FILE* f = fopen("/proc/stat", "r");
    if (f) {
        char line[256];
        if (fgets(line, sizeof(line), f)) {
            unsigned long long u = 0, n = 0, s = 0, idle = 0, io = 0, irq = 0, sirq = 0, st = 0;
            int got = sscanf(line, "cpu %llu %llu %llu %llu %llu %llu %llu %llu",
                             &u, &n, &s, &idle, &io, &irq, &sirq, &st);
            if (got >= 4) {
                unsigned long long tot = u + n + s + idle + io + irq + sirq + st;
                unsigned long long idl = idle + io;
                if (sCc.prevTotal && tot > sCc.prevTotal) {
                    unsigned long long dt = tot - sCc.prevTotal;
                    unsigned long long di = (idl > sCc.prevIdle) ? (idl - sCc.prevIdle) : 0;
                    int pct = (int)(100.0 * (double)(dt - di) / (double)dt);
                    sCc.cpuPct = pct < 0 ? 0 : (pct > 100 ? 100 : pct);
                }
                sCc.prevTotal = tot;
                sCc.prevIdle = idl;
            }
        }
        fclose(f);
    }
}

void ccReadGpu() {
    long long cur = readLLFile("/sys/class/devfreq/fde60000.gpu/cur_freq", 0);
    long long mx  = readLLFile("/sys/class/devfreq/fde60000.gpu/max_freq", 900000000);
    if (cur > 0) sCc.gpuMhz = (int)(cur / 1000000);
    if (mx  > 0) sCc.gpuMaxMhz = (int)(mx / 1000000);
    FILE* f = fopen("/sys/class/devfreq/fde60000.gpu/load", "r");
    if (f) {
        int pct = 0;
        if (fscanf(f, "%d", &pct) == 1) sCc.gpuPct = pct < 0 ? 0 : (pct > 100 ? 100 : pct);
        fclose(f);
    }
}

int ccThermal(const char* wantType) {
    for (int z = 0; z < 12; z++) {
        char tp[96], vp[96];
        snprintf(tp, sizeof(tp), "/sys/class/thermal/thermal_zone%d/type", z);
        FILE* f = fopen(tp, "r");
        if (!f) continue;
        char type[64] = {};
        if (fgets(type, sizeof(type), f)) { char* nl = strchr(type, '\n'); if (nl) *nl = 0; }
        fclose(f);
        if (strcmp(type, wantType) == 0) {
            snprintf(vp, sizeof(vp), "/sys/class/thermal/thermal_zone%d/temp", z);
            return readIntFile(vp, 0) / 1000;
        }
    }
    return 0;
}

void ccReadRam() {
    FILE* f = fopen("/proc/meminfo", "r");
    if (!f) return;
    char line[128];
    long total = 0, avail = 0;
    while (fgets(line, sizeof(line), f)) {
        long v;
        if (sscanf(line, "MemTotal: %ld kB", &v) == 1) total = v;
        else if (sscanf(line, "MemAvailable: %ld kB", &v) == 1) avail = v;
    }
    fclose(f);
    if (total > 0) { sCc.ramTotalMb = (int)(total / 1024); sCc.ramUsedMb = (int)((total - avail) / 1024); }
}

void ccReadBattery() {
    sCc.battPct = readIntFile("/sys/class/power_supply/battery/capacity", sCc.battPct);
    long long uv = readLLFile("/sys/class/power_supply/battery/voltage_now", 0);
    long long ua = readLLFile("/sys/class/power_supply/battery/current_now", 0);
    sCc.watts = (float)(((double)uv / 1e6) * ((double)(ua < 0 ? -ua : ua) / 1e6));
    FILE* f = fopen("/sys/class/power_supply/battery/status", "r");
    if (f) {
        char s[32] = {};
        if (fgets(s, sizeof(s), f)) sCc.charging = (strncmp(s, "Charging", 8) == 0 || strncmp(s, "Full", 4) == 0);
        fclose(f);
    }
}

void ccReadBrightness() {
    sCc.briTop    = readIntFile(ccBlBri(false), sCc.briTop);
    sCc.briTopMax = readIntFile(ccBlMax(false), 255);
    sCc.briBot    = readIntFile(ccBlBri(true),  sCc.briBot);
    sCc.briBotMax = readIntFile(ccBlMax(true),  255);
}

// Runs on the detached worker thread. Writes ONLY the staging fields (sVol*); the render thread
// publishes them to the live volCur/volMin/volMax under its slider guard.
void ccReadVolume() {
    // media_session prints, among its verbose [V] lines: "volume is <CUR> in range [<MIN>..<MAX>]".
    // MIN is the stream's min volume (usually 0 but non-zero on absolute-volume / vendor routes), so
    // parse all three; on any parse miss keep the previous staging values so the slider never snaps.
    FILE* d = popen("cmd media_session volume --stream 3 --get 2>/dev/null", "r");
    if (!d) return;
    char line[256];
    while (fgets(line, sizeof(line), d)) {
        char* p = strstr(line, "volume is ");
        if (!p) continue;
        int cur = -1, lo = -1, hi = -1;
        if (sscanf(p, "volume is %d in range [%d..%d]", &cur, &lo, &hi) == 3 && hi > lo && cur >= lo) {
            sCc.sVolCur = cur;
            sCc.sVolMin = lo;
            sCc.sVolMax = hi;
        }
        break;
    }
    pclose(d);
}

void ccReadClock() {
    time_t t = time(nullptr);
    struct tm lt;
    if (localtime_r(&t, &lt)) { sCc.hh = lt.tm_hour; sCc.mm = lt.tm_min; }
}

// Wi-Fi enabled state (settings global). Runs on the detached worker thread; writes only staging.
void ccReadWifi() {
    FILE* f = popen("settings get global wifi_on 2>/dev/null", "r");
    if (!f) return;
    char s[16] = {};
    if (fgets(s, sizeof(s), f)) sCc.sWifiOn = (atoi(s) != 0);
    pclose(f);
}

} // namespace

void NanoMenu::pollControlCenterStats() {
    int64_t t = nowMs();
    if (sCc.primed && t - sCc.lastPollMs < 500) return;
    sCc.lastPollMs = t;
    ccReadCpu();          // cpu% + freq (fast-moving) - kept at 500ms
    ccReadGpu();          // gpu% + freq (fast-moving) - kept at 500ms
    ccReadClock();        // trivial
    // Slow-moving values shown as integers / one decimal (temps, battery, watts, RAM, brightness):
    // a 1s cadence is pixel-identical to 500ms and halves the sysfs I/O (the thermal type-scan alone
    // fopens ~24 files per read). Brightness slider fills still track a live drag because ccApplySlider
    // writes sCc.briTop/briBot directly; this 1s re-read only catches an externally-changed backlight.
    if (!sCc.primed || t - sCc.lastSlowMs >= 1000) {
        sCc.socTempC = ccThermal("soc-thermal");
        sCc.gpuTempC = ccThermal("gpu-thermal");
        ccReadRam();
        ccReadBattery();
        ccReadBrightness();
        sCc.lastSlowMs = t;
    }
    // Volume + Wi-Fi popen a shell + binder round-trip (tens to ~150ms). Run them on a DETACHED
    // worker so they never stall the render thread; the worker writes only the staging fields and we
    // publish below. Skipped while a slider is held so a live volume drag is never overwritten. The
    // volBusy guard prevents overlapping workers if a read ever outlasts the 2s interval.
    // Require the previous worker's result to be consumed (volFresh == false) before spawning a new
    // one, so the publish below never reads sVol* while a fresh worker is writing them. Combined with
    // volBusy (test-and-set, render-thread-only) this keeps exactly one producer's staging live at a
    // time, so there is no data race on the non-atomic staging ints. The volBusy.exchange is the last
    // term so it only runs (and only latches) when a spawn will actually happen.
    if ((!sCc.primed || t - sCc.lastVolMs > 2000) && mCcHeldSlider < 0 &&
        !sCc.volFresh.load(std::memory_order_acquire) && !sCc.volBusy.exchange(true)) {
        std::thread([]{
            ccReadVolume();     // popen off the render thread -> sVol*
            ccReadWifi();       // popen off the render thread -> sWifiOn
            sCc.volFresh.store(true, std::memory_order_release);
            sCc.volBusy.store(false, std::memory_order_release);
        }).detach();
        sCc.lastVolMs = t;
    }
    // Publish staging -> live on the render thread (the sole writer of the live fields). Volume only
    // when no slider is held, so a live drag is never clobbered; Wi-Fi always (the optimistic tile
    // toggle is idempotent with the real read). The release/acquire on volFresh orders the staging ints.
    if (sCc.volFresh.exchange(false, std::memory_order_acquire)) {
        if (mCcHeldSlider < 0) { sCc.volCur = sCc.sVolCur; sCc.volMin = sCc.sVolMin; sCc.volMax = sCc.sVolMax; }
        sCc.wifiOn = sCc.sWifiOn;
    }
    sCc.primed = true;
}

// ---------------------------------------------------------------------------
// Control tiles: shared geometry (design space) + definitions + live on-state, used by
// BOTH the render and the touch hit-test so a tap always matches what is drawn.
// ---------------------------------------------------------------------------
namespace {
constexpr float CC_TX0 = 180, CC_TY0 = 58, CC_TW = 106, CC_TH = 70, CC_GX = 114, CC_GY = 82;
inline void ccTileXY(int i, float& x, float& y) { x = CC_TX0 + (i % 4) * CC_GX; y = CC_TY0 + (i / 4) * CC_GY; }
enum { A_SLEEP, A_PERF, A_SPLITBRI, A_SHADER, A_EQ, A_ABXY, A_SHOT, A_WIFI };
// renderCcPass mask bits: STATIC layers are baked into the cache, DYNAMIC layers redraw over it each frame.
enum { CC_PASS_STATIC = 1, CC_PASS_DYNAMIC = 2 };
struct CcTileDef { const char* label; int act; };
const CcTileDef CC_TILE[8] = {
    { "Sleep Screen", A_SLEEP },  { "Performance", A_PERF },   { "Split Bright", A_SPLITBRI }, { "Shader", A_SHADER },
    { "Gamma EQ",     A_EQ },      { "ABXY Swap",   A_ABXY },   { "Screenshot",   A_SHOT },     { "Wi-Fi", A_WIFI },
};
// live on-state / mode of a tile action. 0 = off/inactive; 1 = on (or perf=powersave); 2 = perf=max.
int ccActState(int act, bool sleeping) {
    char v[PROPERTY_VALUE_MAX] = {};
    switch (act) {
        case A_SLEEP:    return sleeping ? 1 : 0;
        case A_PERF:     property_get("persist.gammaos.performance_mode", v, "stock");
                         if (!strcmp(v, "powersave")) return 1;
                         if (!strcmp(v, "max"))       return 2;
                         return 0;
        case A_SPLITBRI: return property_get_int32("persist.gammaos.multidisplay.split_brightness", 0) ? 1 : 0;
        case A_SHADER:   return property_get_int32("persist.gammaos.shader.enable", 0) ? 1 : 0;
        case A_EQ:       return property_get_int32("persist.sys.gammaeq.enable", 0) ? 1 : 0;
        case A_ABXY:     return property_get_int32("persist.gammaos.gamepad.abxy_swap", 0) ? 1 : 0;
        case A_WIFI:     return sCc.wifiOn ? 1 : 0;
        default:         return 0;
    }
}
} // namespace

// ---------------------------------------------------------------------------
// Dashboard draw. Design space 640x480, scaled uniformly + centred to the panel.
// Flat low-cost style: solid cards, single-pass numerals + ring gauges, no glow passes.
// ---------------------------------------------------------------------------
// Draws the dashboard for a given pass mask: CC_PASS_STATIC bakes the frame-invariant layers (into the
// static cache), CC_PASS_DYNAMIC redraws only the live elements over the composited cache, and both bits
// together is the legacy all-immediate render. Each element is drawn exactly once and in the same screen
// position in every pass, so cached-static + dynamic-overlay is byte-identical to the immediate path.
void NanoMenu::renderCcPass(int pass) {
    const bool st = (pass & CC_PASS_STATIC)  != 0;   // static layers (baked into mCcStaticTex)
    const bool dy = (pass & CC_PASS_DYNAMIC) != 0;   // dynamic layers (redrawn each frame over the cache)
    const float DW = 640.0f, DH = 480.0f;
    const float u  = fminf((float)mWidth / DW, (float)mHeight / DH);
    const float ox = ((float)mWidth  - DW * u) * 0.5f;
    const float oy = ((float)mHeight - DH * u) * 0.5f;
    auto X  = [&](float x){ return ox + x * u; };
    auto Y  = [&](float y){ return oy + y * u; };
    auto S  = [&](float s){ return s * u; };
    auto TS = [&](float px){ return (px * u) / (float)FONT_CHAR_H; };

    setUiBlend();

    // ---- background: flat dark fill (deep blue-black). No glow band: keep GPU fill to a minimum. ----
    if (st) drawQuad(0, 0, (float)mWidth, (float)mHeight, 0.015f, 0.017f, 0.028f, 1.0f);

    // Batch every flat-solid primitive (disc fans, ring arcs, procedural icons, clock, gauge arcs) into
    // a few glDrawArrays instead of ~1500 individual ones. drawRoundedRect/drawText self-flush the batch
    // first so painter z-order is byte-identical to the immediate path. Closed by endSolidBatch() below.
    beginSolidBatch();

    // ---- text helpers ----
    auto textL = [&](const char* s, float x, float topY, float px, float r, float g, float b, float a){
        drawText(s, X(x), Y(topY), TS(px), r, g, b, a);
    };
    auto textC = [&](const char* s, float cx, float topY, float px, float r, float g, float b, float a){
        float sc = TS(px); drawText(s, X(cx) - measureText(s, sc) * 0.5f, Y(topY), sc, r, g, b, a);
    };
    auto textR = [&](const char* s, float rightX, float topY, float px, float r, float g, float b, float a){
        float sc = TS(px); drawText(s, X(rightX) - measureText(s, sc), Y(topY), sc, r, g, b, a);
    };
    // Bright flat numeral (centred / left). No drawTextGlow: the 15-tap glow was by far the heaviest
    // GPU cost on this panel, so the big readouts are drawn as a single near-white pass instead.
    auto numC = [&](const char* s, float cx, float topY, float px){
        float sc = TS(px);
        float w  = measureText(s, sc);
        drawText(s, X(cx) - w * 0.5f, Y(topY), sc, 0.95f, 0.97f, 1.0f, 1.0f);
    };
    auto numL = [&](const char* s, float x, float topY, float px){
        drawText(s, X(x), Y(topY), TS(px), 0.95f, 0.97f, 1.0f, 1.0f);
    };
    // section header: tiny dim letter-spaced caps
    auto header = [&](const char* s, float x, float topY){
        float sc = TS(11.0f), cx = X(x);
        for (const char* p = s; *p; ++p) {
            char c[2] = { *p, 0 };
            drawText(c, cx, Y(topY), sc, 0.46f, 0.52f, 0.64f, 1.0f);
            cx += measureText(c, sc) + S(2.2f);
        }
    };

    // ---- card: single flat rounded body. The thin top-edge highlight pass was dropped to halve
    // ---- the rounded-rect (SDF) draws per card and keep GPU load minimal. ----
    auto card = [&](float x, float y, float w, float h){
        drawRoundedRect(X(x), Y(y), S(w), S(h), S(13.0f), 0.075f, 0.082f, 0.11f, 0.94f);
    };

    // ---- filled disc (triangle fan) ----
    auto disc = [&](float cxD, float cyD, float rD, float r, float g, float b, float a){
        const int N = 28; float px = cxD + rD, py = cyD;
        for (int i = 1; i <= N; i++) {
            float ang = (float)i / N * 2.0f * (float)M_PI;
            float nx = cxD + rD * cosf(ang), ny = cyD + rD * sinf(ang);
            drawTriangle(cxD, cyD, px, py, nx, ny, r, g, b, a); px = nx; py = ny;
        }
    };
    // ---- ring arc (rounded feel): from angle a0, sweep, between rI..rO ----
    auto ringArc = [&](float CX, float CY, float rI, float rO, float a0, float a1,
                       float r, float g, float b, float a){
        int n = 56; float span = a1 - a0; if (span <= 0) return;
        int segs = (int)fmaxf(2.0f, n * (span / (2.0f * (float)M_PI)));
        for (int i = 0; i < segs; i++) {
            float t0 = a0 + span * (float)i / segs, t1 = a0 + span * (float)(i + 1) / segs;
            float c0 = cosf(t0), s0 = sinf(t0), c1 = cosf(t1), s1 = sinf(t1);
            float ox0 = CX + c0 * rO, oy0 = CY + s0 * rO, ix0 = CX + c0 * rI, iy0 = CY + s0 * rI;
            float ox1 = CX + c1 * rO, oy1 = CY + s1 * rO, ix1 = CX + c1 * rI, iy1 = CY + s1 * rI;
            drawTriangle(ox0, oy0, ix0, iy0, ox1, oy1, r, g, b, a);
            drawTriangle(ix0, iy0, ix1, iy1, ox1, oy1, r, g, b, a);
        }
    };

    // ---- procedural monochrome icons (drawn in device px around cx,cy, radius r) ----
    auto icoSpeaker = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r);
        // cone: small box + triangle
        drawQuad(CX - rr*0.75f, CY - rr*0.35f, rr*0.4f, rr*0.7f, R, G, B, A);
        drawTriangle(CX - rr*0.35f, CY - rr*0.7f, CX - rr*0.35f, CY + rr*0.7f, CX + rr*0.15f, CY, R, G, B, A);
        drawTriangle(CX - rr*0.35f, CY - rr*0.7f, CX + rr*0.15f, CY, CX + rr*0.15f, CY - rr*0.9f, R, G, B, A);
        drawTriangle(CX - rr*0.35f, CY + rr*0.7f, CX + rr*0.15f, CY, CX + rr*0.15f, CY + rr*0.9f, R, G, B, A);
        // two sound waves (arcs)
        ringArc(CX - rr*0.35f, CY, rr*0.55f, rr*0.68f, -0.7f, 0.7f, R, G, B, A);
        ringArc(CX - rr*0.35f, CY, rr*0.9f,  rr*1.03f, -0.6f, 0.6f, R, G, B, A*0.85f);
    };
    auto icoSun = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r);
        disc(CX, CY, rr*0.5f, R, G, B, A);
        for (int k = 0; k < 8; k++) {
            float a = k / 8.0f * 2.0f * (float)M_PI;
            float ix = CX + cosf(a)*rr*0.72f, iy = CY + sinf(a)*rr*0.72f;
            float ox = CX + cosf(a)*rr*1.0f,  oy = CY + sinf(a)*rr*1.0f;
            float nx = -sinf(a)*S(1.4f), ny = cosf(a)*S(1.4f);
            drawTriangle(ix+nx, iy+ny, ix-nx, iy-ny, ox, oy, R, G, B, A);
        }
    };
    auto icoBroom = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r);
        // handle
        drawTriangle(CX+rr*0.5f, CY-rr, CX+rr*0.7f, CY-rr, CX-rr*0.2f, CY+rr*0.2f, R,G,B,A);
        drawTriangle(CX+rr*0.7f, CY-rr, CX-rr*0.0f, CY+rr*0.2f, CX-rr*0.2f, CY+rr*0.2f, R,G,B,A);
        // bristles (fan of thin triangles)
        for (int k = 0; k < 5; k++) {
            float t = (k - 2) * 0.16f;
            float bx = CX - rr*0.1f + t*rr*0.9f, by = CY + rr*0.9f;
            drawTriangle(CX-rr*0.25f, CY+rr*0.15f, bx-S(1.3f), by, bx+S(1.3f), by, R,G,B,A);
        }
    };
    auto icoMoon = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r);
        disc(CX, CY, rr*0.85f, R, G, B, A);
        disc(CX + rr*0.42f, CY - rr*0.28f, rr*0.72f, 0.075f, 0.082f, 0.11f, 1.0f);   // bite (card colour)
    };
    auto icoCamera = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r);
        drawRoundedRect(CX - rr*0.95f, CY - rr*0.55f, rr*1.9f, rr*1.25f, rr*0.28f, R, G, B, A);
        drawQuad(CX - rr*0.35f, CY - rr*0.8f, rr*0.7f, rr*0.3f, R, G, B, A);   // hump
        disc(CX, CY + rr*0.05f, rr*0.42f, 0.075f, 0.082f, 0.11f, 1.0f);        // lens hole
        disc(CX, CY + rr*0.05f, rr*0.26f, R, G, B, A);
    };
    auto icoWifi = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy + r*0.5f), rr = S(r);
        ringArc(CX, CY, rr*1.15f, rr*1.35f, -2.36f, -0.78f, R, G, B, A);
        ringArc(CX, CY, rr*0.7f,  rr*0.9f,  -2.2f,  -0.94f, R, G, B, A);
        disc(CX, CY, rr*0.22f, R, G, B, A);
    };
    auto icoThermo = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r);
        drawRoundedRect(CX - rr*0.22f, CY - rr, rr*0.44f, rr*1.6f, rr*0.22f, R, G, B, A);
        disc(CX, CY + rr*0.75f, rr*0.5f, R, G, B, A);
    };
    // performance: a speedometer/tachometer dial with a needle swept up-right (revs pinned).
    auto icoGauge = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy + r*0.30f), rr = S(r);
        ringArc(CX, CY, rr*0.74f, rr*0.98f, (float)M_PI*0.75f, (float)M_PI*2.25f, R, G, B, A);  // dial (top ~270deg)
        float na = (float)M_PI * 1.66f;                                                          // needle up-right
        float nx = cosf(na), ny = sinf(na);
        float tx = CX + nx*rr*0.86f, ty = CY + ny*rr*0.86f;
        float pnx = -ny*S(1.7f), pny = nx*S(1.7f);
        drawTriangle(CX+pnx, CY+pny, CX-pnx, CY-pny, tx, ty, R, G, B, A);                        // needle
        disc(CX, CY, rr*0.17f, R, G, B, A);                                                      // hub
    };
    // split-brightness: two small screens side by side, one bright, one dim.
    auto icoSplitBri = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r);
        drawRoundedRect(CX - rr*1.0f,  CY - rr*0.7f, rr*0.9f, rr*1.4f, rr*0.2f, R, G, B, A);
        drawRoundedRect(CX + rr*0.1f,  CY - rr*0.7f, rr*0.9f, rr*1.4f, rr*0.2f, R, G, B, A*0.42f);
    };
    // shader: a screen/monitor outline with an inner sparkle.
    auto icoShader = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r);
        drawRoundedRect(CX - rr*1.1f, CY - rr*0.8f, rr*2.2f, rr*1.5f, rr*0.25f, R, G, B, A);
        disc(CX, CY - rr*0.05f, rr*0.55f, 0.075f, 0.082f, 0.11f, 1.0f);
        drawQuad(CX - rr*0.5f, CY + rr*0.75f, rr*1.0f, rr*0.28f, R, G, B, A);   // stand
    };
    // music note (Gamma EQ).
    auto icoMusic = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r);
        disc(CX - rr*0.45f, CY + rr*0.7f, rr*0.45f, R, G, B, A);              // note head
        drawQuad(CX - rr*0.05f, CY - rr*0.9f, rr*0.24f, rr*1.7f, R, G, B, A); // stem
        drawTriangle(CX - rr*0.05f, CY - rr*0.9f, CX + rr*0.7f, CY - rr*0.7f, CX + rr*0.7f, CY - rr*0.2f, R, G, B, A); // flag
        drawTriangle(CX - rr*0.05f, CY - rr*0.9f, CX + rr*0.7f, CY - rr*0.2f, CX - rr*0.05f, CY - rr*0.35f, R, G, B, A);
    };
    // ABXY: four face buttons in a diamond.
    auto icoAbxy = [&](float cx, float cy, float r, float R, float G, float B, float A){
        float CX = X(cx), CY = Y(cy), rr = S(r), br = rr*0.36f, o = rr*0.72f;
        disc(CX, CY - o, br, R, G, B, A);     // top (Y)
        disc(CX, CY + o, br, R, G, B, A);     // bottom (A)
        disc(CX - o, CY, br, R, G, B, A);     // left (X)
        disc(CX + o, CY, br, R, G, B, A);     // right (B)
    };

    char buf[80];

    // ==== STATUS BAR ====================================================
    if (st) header("CONTROL CENTER", 10, 8);
    if (dy) {
        snprintf(buf, sizeof(buf), "%02d:%02d", sCc.hh, sCc.mm);
        textR(buf, 632, 6, 15.0f, 0.92f, 0.94f, 1.0f, 1.0f);
        snprintf(buf, sizeof(buf), "%s  %d%%  %.1fW", sCc.charging ? "CHG" : "BAT", sCc.battPct, sCc.watts);
        textR(buf, 570, 8, 12.0f, 0.6f, 0.66f, 0.8f, 1.0f);
    }

    // ==== LEFT: three vertical sliders (Volume, Top brightness, Bottom brightness) ====
    if (st) card(8, 30, 150, 202);
    {
        if (st) header("OUTPUT", 22, 40);
        float sy = 66, sh = 120, sw = 20;
        float px[3] = { 34, 76, 118 };
        float frac[3] = {
            sCc.volMax > sCc.volMin ? (float)(sCc.volCur - sCc.volMin) / (sCc.volMax - sCc.volMin) : 0,
            sCc.briTopMax > 0 ? (float)sCc.briTop / sCc.briTopMax : 0,
            sCc.briBotMax > 0 ? (float)sCc.briBot / sCc.briBotMax : 0,
        };
        for (int i = 0; i < 3; i++) {
            float x = px[i] - sw*0.5f;
            if (st) drawRoundedRect(X(x), Y(sy), S(sw), S(sh), S(sw*0.5f), 0.16f, 0.17f, 0.21f, 1.0f);  // track
            if (dy) {
                float fh = sh * frac[i]; if (fh < sw) fh = (frac[i] > 0.01f) ? sw : 0;
                if (fh > 0) drawRoundedRect(X(x), Y(sy + sh - fh), S(sw), S(fh), S(sw*0.5f), 0.85f, 0.90f, 1.0f, 1.0f);
            }
        }
        // icons under each
        if (st) {
            icoSpeaker(px[0], 208, 9, 0.7f, 0.75f, 0.85f, 1.0f);
            icoSun(px[1], 208, 8, 0.7f, 0.75f, 0.85f, 1.0f);
            icoSun(px[2], 208, 6.5f, 0.55f, 0.6f, 0.72f, 1.0f);
        }
    }

    // ==== TOP-RIGHT: control tiles (2x4). All static (state-dependent -> in the cache signature). ====
    if (st) card(166, 30, 466, 202);
    if (st) {
        header("CONTROLS", 180, 40);
        for (int i = 0; i < 8; i++) {
            const CcTileDef& t = CC_TILE[i];
            int st = ccActState(t.act, mCcSleeping);   // 0 off, 1 on / perf=powersave, 2 perf=max
            bool on = st > 0;
            float x, y; ccTileXY(i, x, y);
            drawRoundedRect(X(x), Y(y), S(CC_TW), S(CC_TH), S(11.0f),
                            on ? 0.34f : 0.11f, on ? 0.16f : 0.12f, on ? 0.52f : 0.155f, 1.0f);
            float icx = x + CC_TW*0.5f, icy = y + 24;
            float ir = on ? 0.99f : 0.82f, ig = on ? 0.92f : 0.86f, ib = on ? 1.0f : 0.94f;
            switch (t.act) {
                case A_SLEEP:    icoMoon(icx, icy, 10, ir,ig,ib,1); break;
                case A_PERF:     icoGauge(icx, icy, 11, ir,ig,ib,1); break;
                case A_SPLITBRI: icoSplitBri(icx, icy, 10, ir,ig,ib,1); break;
                case A_SHADER:   icoShader(icx, icy, 9,  ir,ig,ib,1); break;
                case A_EQ:       icoMusic(icx, icy, 11, ir,ig,ib,1); break;
                case A_ABXY:     icoAbxy(icx, icy, 11, ir,ig,ib,1); break;
                case A_SHOT:     icoCamera(icx, icy, 11, ir,ig,ib,1); break;
                case A_WIFI:     icoWifi(icx, icy, 10, ir,ig,ib,1); break;
            }
            // Performance shows its mode name; others the plain label.
            const char* lbl = t.label;
            if (t.act == A_PERF) lbl = (st == 2) ? "Max" : (st == 1) ? "Powersave" : "Stock";
            textC(lbl, icx, y + CC_TH - 20, 11.0f, 0.82f, 0.85f, 0.92f, 1.0f);
        }
    }

    // ==== BOTTOM-LEFT: analog clock (PS3/PSP clock aesthetic) ====
    if (st) card(8, 240, 210, 200);
    {
        struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
        time_t tt = ts.tv_sec; struct tm lt; localtime_r(&tt, &lt);
        float sub  = (float)ts.tv_nsec / 1e9f;
        float sAng = ((lt.tm_sec + sub) / 60.0f)              * 2.0f*(float)M_PI - (float)M_PI*0.5f;
        float mAng = ((lt.tm_min + lt.tm_sec/60.0f) / 60.0f)  * 2.0f*(float)M_PI - (float)M_PI*0.5f;
        float hAng = (((lt.tm_hour%12) + lt.tm_min/60.0f)/12.0f)*2.0f*(float)M_PI - (float)M_PI*0.5f;
        const float ccx = 113, ccy = 346, R = 72;
        const float CX = X(ccx), CY = Y(ccy);
        // flat disc face + hour ticks (STATIC)
        if (st) {
            disc(CX, CY, S(R), 0.09f, 0.11f, 0.17f, 0.72f);
            for (int k = 0; k < 12; k++) {   // hour ticks (majors at 12/3/6/9)
                float a = k/12.0f*2.0f*(float)M_PI - (float)M_PI*0.5f;
                bool major = (k % 3 == 0);
                float c = cosf(a), s = sinf(a);
                float r0 = major ? R-12 : R-7, r1 = R-3;
                float nx = -s*S(major?2.2f:1.1f), ny = c*S(major?2.2f:1.1f);
                float axp = CX+c*S(r0), ayp = CY+s*S(r0), bxp = CX+c*S(r1), byp = CY+s*S(r1);
                drawTriangle(axp+nx,ayp+ny, axp-nx,ayp-ny, bxp+nx,byp+ny, 0.85f,0.9f,1.0f, major?0.95f:0.55f);
                drawTriangle(axp-nx,ayp-ny, bxp-nx,byp-ny, bxp+nx,byp+ny, 0.85f,0.9f,1.0f, major?0.95f:0.55f);
            }
        }
        // hands + hub + date (DYNAMIC: they move / advance every frame; date paints OVER the hands so it
        // must stay dynamic to preserve the exact painter order).
        if (dy) {
            // clean rectangle hand from a back tail to a tip
            auto hand = [&](float ang, float len, float hw, float tail, float r, float g, float b, float a){
                float c = cosf(ang), s = sinf(ang);
                float nx = -s*S(hw), ny = c*S(hw);
                float txp = CX+c*S(len), typ = CY+s*S(len), bxp = CX-c*S(tail), byp = CY-s*S(tail);
                float p0x=bxp+nx,p0y=byp+ny, p1x=txp+nx,p1y=typ+ny, p2x=txp-nx,p2y=typ-ny, p3x=bxp-nx,p3y=byp-ny;
                drawTriangle(p0x,p0y,p1x,p1y,p2x,p2y, r,g,b,a);
                drawTriangle(p0x,p0y,p2x,p2y,p3x,p3y, r,g,b,a);
            };
            hand(hAng, R*0.50f, 2.6f, 9, 0.96f,0.97f,1.0f, 1.0f);   // hour
            hand(mAng, R*0.72f, 1.9f, 9, 0.96f,0.97f,1.0f, 1.0f);   // minute
            hand(sAng, R*0.80f, 0.9f, 15, 0.95f,0.35f,0.4f, 1.0f);  // second (red)
            disc(CX, CY, S(3.6f), 0.96f,0.97f,1.0f, 1.0f);          // hub
            disc(CX, CY, S(1.9f), 0.95f,0.35f,0.4f, 1.0f);
            // date label (weekday + day), PSP-clock style
            static const char* WD[7] = {"SUN","MON","TUE","WED","THU","FRI","SAT"};
            snprintf(buf, sizeof(buf), "%s %d", WD[lt.tm_wday % 7], lt.tm_mday);
            textC(buf, ccx, ccy + 18, 11.0f, 0.6f, 0.66f, 0.8f, 1.0f);
        }
    }

    // ==== BOTTOM-MIDDLE: temps ====
    if (st) card(226, 240, 132, 200);
    {
        if (st) header("THERMAL", 240, 254);
        if (st) icoThermo(252, 292, 11, 1.0f, 0.62f, 0.36f, 1.0f);
        if (dy) { snprintf(buf, sizeof(buf), "%d\xC2\xB0", sCc.socTempC); numL(buf, 272, 274, 40.0f); }
        if (st) textL("SoC", 272, 316, 11.0f, 0.55f, 0.6f, 0.7f, 1.0f);
        if (st) icoThermo(252, 384, 11, 0.36f, 0.82f, 1.0f, 1.0f);
        if (dy) { snprintf(buf, sizeof(buf), "%d\xC2\xB0", sCc.gpuTempC); numL(buf, 272, 366, 40.0f); }
        if (st) textL("GPU", 272, 408, 11.0f, 0.55f, 0.6f, 0.7f, 1.0f);
    }

    // ==== BOTTOM-RIGHT: 2x2 ring gauges (CPU / GPU / PWR / RAM) ====
    if (st) card(366, 240, 266, 200);
    {
        if (st) header("PERFORMANCE", 380, 254);
        struct G { float cx, cy; float frac; float r,g,b; const char* big; const char* unit; const char* label; };
        char cpuS[16], gpuS[16], pwrS[16], ramS[16];
        snprintf(cpuS, sizeof(cpuS), "%.2f", sCc.cpuMhz / 1000.0f);
        snprintf(gpuS, sizeof(gpuS), "%d",   sCc.gpuMhz);
        snprintf(pwrS, sizeof(pwrS), "%.1f", sCc.watts);
        snprintf(ramS, sizeof(ramS), "%.1f", sCc.ramUsedMb / 1024.0f);
        float ramFrac = sCc.ramTotalMb > 0 ? (float)sCc.ramUsedMb / sCc.ramTotalMb : 0;
        G gs[4] = {
            { 440, 312, sCc.cpuPct/100.0f, 0.96f,0.2f,0.56f, cpuS, "GHz", "CPU" },
            { 560, 312, sCc.gpuPct/100.0f, 0.24f,0.82f,0.98f, gpuS, "MHz", "GPU" },
            { 440, 400, sCc.watts/15.0f,   1.0f,0.62f,0.2f,   pwrS, "W",   "PWR" },
            { 560, 400, ramFrac,           0.45f,0.9f,0.42f,  ramS, "GB",  "RAM" },
        };
        for (int i = 0; i < 4; i++) {
            const G& g = gs[i];
            float frac = g.frac < 0 ? 0 : (g.frac > 1 ? 1 : g.frac);
            float CX = X(g.cx), CY = Y(g.cy), rO = S(40.0f), rI = S(30.0f);
            const float start = -(float)M_PI * 0.5f;
            if (st) ringArc(CX, CY, rI, rO, 0.0f, 2.0f*(float)M_PI, 0.16f, 0.17f, 0.21f, 1.0f);       // track
            if (dy && frac > 0.004f)
                ringArc(CX, CY, rI, rO, start, start + 2.0f*(float)M_PI*frac, g.r, g.g, g.b, 1.0f);   // arc (no glow pass)
            if (dy) numC(g.big, g.cx, g.cy - 15, 26.0f);
            if (st) textC(g.unit, g.cx, g.cy + 10, 12.0f, 0.62f, 0.66f, 0.76f, 1.0f);
            if (st) textC(g.label, g.cx, g.cy + 30, 13.0f, g.r, g.g, g.b, 1.0f);
        }
    }

    endSolidBatch();   // flush the final batched solids (opened after the background fill above)
}

// Thin pass wrappers. The static bake and the dynamic overlay share the ONE body above so their layout
// can never desync. Poll happens once per frame in renderControlCenterFrame, not here.
void NanoMenu::renderControlCenterUI() { renderCcPass(CC_PASS_STATIC | CC_PASS_DYNAMIC); }  // all-immediate fallback
void NanoMenu::renderCcStatic()        { renderCcPass(CC_PASS_STATIC); }
void NanoMenu::renderCcDynamic()       { renderCcPass(CC_PASS_DYNAMIC); }

// Capture the STATIC-layer cache signature: every runtime input that changes a BAKED pixel (never a live
// number). st = ccActState(act, mCcSleeping) folds mCcSleeping (A_SLEEP), the perf mode (incl. the
// Stock/Powersave/Max label), split_brightness, shader, gammaeq, abxy_swap and sCc.wifiOn; plus the clock
// DATE (belt-and-braces; the date is drawn dynamic) and the panel size (drives the layout scale u).
NanoMenu::CcStaticSig NanoMenu::ccStaticSignature() const {
    CcStaticSig s;
    s.w = mWidth;
    s.h = mHeight;
    for (int i = 0; i < 8; i++)
        s.tile[i] = (uint8_t)ccActState(CC_TILE[i].act, mCcSleeping);
    time_t t = time(nullptr);
    struct tm lt;
    if (localtime_r(&t, &lt)) { s.wday = lt.tm_wday % 7; s.mday = lt.tm_mday; }
    return s;
}

// (Re)bake the frame-invariant layers into mCcStaticTex when the signature changes. No-op when current.
// Runs on the already-current secondary EGL context (called from renderControlCenterFrame). On FBO failure
// it frees the cache and returns, so the frame falls back to the all-immediate path (pixel-identical).
void NanoMenu::ccEnsureStaticCache() {
    CcStaticSig sig = ccStaticSignature();
    if (mCcStaticValid && mCcStaticFbo && mCcStaticTex && mCcStaticSig == sig) return;

    GLint prevFbo = 0;           glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint prevVp[4] = {0,0,0,0}; glGetIntegerv(GL_VIEWPORT, prevVp);

    bool reallocTex = (mCcStaticTex == 0 || mCcStaticSig.w != mWidth || mCcStaticSig.h != mHeight);
    if (mCcStaticTex == 0) glGenTextures(1, &mCcStaticTex);
    glBindTexture(GL_TEXTURE_2D, mCcStaticTex);
    if (reallocTex)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mWidth, mHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);   // 1:1 texel copy -> exact
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    if (mCcStaticFbo == 0) glGenFramebuffers(1, &mCcStaticFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, mCcStaticFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mCcStaticTex, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        ALOGW("cc: static cache FBO incomplete - using immediate path");
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
        glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
        ccFreeStaticCache();
        return;
    }

    glViewport(0, 0, mWidth, mHeight);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);   // opaque base under the (opaque) background fill
    glClear(GL_COLOR_BUFFER_BIT);
    // Bake the STATIC subset. renderCcStatic() -> renderCcPass() calls setUiBlend() itself, so the static
    // layers draw over the opaque background with the exact same blend as the immediate path -> the baked
    // RGB is identical. FBO alpha is not load-bearing: the per-frame composite blits with blend OFF (a 1:1
    // RGB replace), and the opaque background keeps FBO alpha ~1 regardless.
    renderCcStatic();

    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    mCcStaticSig = sig; mCcStaticValid = true;
    ALOGI("cc: baked static cache tex=%u %dx%d", mCcStaticTex, mWidth, mHeight);
}

// Free the static cache (CC teardown / GPU failure). Caller must have a surface with mContext current.
void NanoMenu::ccFreeStaticCache() {
    if (mCcStaticFbo) { glDeleteFramebuffers(1, &mCcStaticFbo); mCcStaticFbo = 0; }
    if (mCcStaticTex) { glDeleteTextures(1, &mCcStaticTex);     mCcStaticTex = 0; }
    mCcStaticValid = false;
    mCcStaticSig = CcStaticSig{};   // sentinel -> next activation always rebuilds
}

// ---------------------------------------------------------------------------
// Interactivity: bottom-panel touch, tile actions, and the graceful sleep ramp.
// ---------------------------------------------------------------------------
namespace {
// writeSysfsInt is an existing NanoMenu member (NanoMenuSystem.cpp) - reused for backlight writes.
void shellCmd(const char* cmd) {
    FILE* f = popen(cmd, "r");
    if (f) pclose(f);
}
} // namespace

// Read the BOTTOM digitizer (RG DS: gt9xx-0 by default; override with
// persist.gammaos.nano.cc.touchdev) and dispatch each SYN frame. Called from the park loop so
// the CC has input while the render thread is not running the normal pollInput().
void NanoMenu::ccPollTouch() {
    char dev[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.cc.touchdev", dev, "gt9xx-0");
    int bfd = -1;
    for (const auto& kv : mInputFdNames) if (kv.second == dev) { bfd = kv.first; break; }
    if (bfd < 0) return;
    struct input_event ev;
    while (read(bfd, &ev, sizeof(ev)) == sizeof(ev)) {
        if (ev.type == EV_ABS) {
            if      (ev.code == ABS_MT_POSITION_X) mCcRawX = ev.value;
            else if (ev.code == ABS_MT_POSITION_Y) mCcRawY = ev.value;
            else if (ev.code == ABS_X)             mCcRawX = ev.value;
            else if (ev.code == ABS_Y)             mCcRawY = ev.value;
            else if (ev.code == ABS_MT_TRACKING_ID) mCcTouchDownRaw = (ev.value >= 0);
        } else if (ev.type == EV_KEY && ev.code == BTN_TOUCH) {
            mCcTouchDownRaw = (ev.value != 0);
        } else if (ev.type == EV_SYN && ev.code == SYN_REPORT) {
            ccTouchFrame();
        }
    }
}

void NanoMenu::ccTouchFrame() {
    // Map raw digitizer (0..640 x 0..480) to the 640x480 design space; optional axis fixups via props
    // (persist.gammaos.nano.cc.touch_swap/flipx/flipy) in case the bottom panel is mounted rotated.
    static bool sRead = false, sSwap = false, sFlipX = false, sFlipY = false;
    if (!sRead) {
        sSwap  = property_get_bool("persist.gammaos.nano.cc.touch_swap",  false);
        sFlipX = property_get_bool("persist.gammaos.nano.cc.touch_flipx", false);
        sFlipY = property_get_bool("persist.gammaos.nano.cc.touch_flipy", false);
        sRead = true;
    }
    float rx = (float)mCcRawX, ry = (float)mCcRawY;
    if (sSwap) { float t = rx; rx = ry; ry = t; }
    float dx = rx / 640.0f, dy = ry / 480.0f;
    if (sFlipX) dx = 1.0f - dx;
    if (sFlipY) dy = 1.0f - dy;
    float px = dx * 640.0f, py = dy * 480.0f;

    bool down = mCcTouchDownRaw;
    bool downEdge = down && !mCcTouchWas;
    bool upEdge   = !down && mCcTouchWas;
    static bool sWokeThisTouch = false;
    if (downEdge) {
        mCcDownX = px; mCcDownY = py;
        mCcLastTouchMs = nowMs();                        // any press resets the 30s idle auto-sleep window
        // ANY touch wakes the slept bottom screen; that touch is consumed (no tile toggled / no drag).
        if (mCcSleeping || mCcSleepDir < 0) {
            mCcSleeping = false; mCcSleepDir = +1;
            sWokeThisTouch = true;
            mCcHeldSlider = -1;
        } else {
            sWokeThisTouch = false;
            mCcHeldSlider = ccSliderAt(px, py);          // grab a slider if the press landed on one
            if (mCcHeldSlider >= 0) ccApplySlider(mCcHeldSlider, py);
        }
    } else if (down) {
        mCcLastTouchMs = nowMs();                        // held/moving finger keeps the panel awake
        // held: drag a grabbed slider live (track Y only, so the finger can drift horizontally)
        if (mCcHeldSlider >= 0 && !sWokeThisTouch) ccApplySlider(mCcHeldSlider, py);
    } else if (upEdge) {
        // release: a short, non-drag press on a tile is a tap; a slider drag is not a tap.
        if (!sWokeThisTouch && mCcHeldSlider < 0) {
            float ddx = px - mCcDownX, ddy = py - mCcDownY;
            if (ddx*ddx + ddy*ddy < 400.0f) ccOnTap(mCcDownX, mCcDownY);   // < ~20px = a tap
        }
        // Flush the final volume: the drag debounced the intermediate --set calls, so commit the value
        // the finger ended on (guarded so it is skipped when the last debounced set already sent it).
        if (mCcHeldSlider == 0 && sCc.volCur != mCcVolLastSet) ccSendVolume(sCc.volCur);
        // Persist a brightness change through the framework so it survives a device sleep/wake. The raw
        // backlight node ccApplySlider writes is VOLATILE: on wake the display framework re-applies the
        // stored brightness and clobbers it. In unified mode both panels follow system screen_brightness
        // (verified 1:1 on this panel); in split mode the top panel (slot d1) is driven+persisted through
        // the gammaos split override the display worker re-applies. Done on release only (one shell fork,
        // never per drag frame). Slider 1 = top (backlight1/d1), slider 2 = bottom (backlight/slot0).
        if (mCcHeldSlider == 1 || mCcHeldSlider == 2) {
            int v = (mCcHeldSlider == 1) ? sCc.briTop : sCc.briBot;
            char c[176];
            if (mCcHeldSlider == 1 && property_get_bool("persist.gammaos.multidisplay.split_brightness", false)) {
                snprintf(c, sizeof(c),
                    "setprop sys.gammaos.multidisplay.split_brightness.d1.override %d; "
                    "setprop persist.gammaos.multidisplay.split_brightness.d1.last %d", v, v);
            } else {
                snprintf(c, sizeof(c), "settings put system screen_brightness %d 2>/dev/null", v);
            }
            shellCmd(c);
        }
        mCcHeldSlider = -1;
        sWokeThisTouch = false;
    }
    mCcTouchWas = down;
}

void NanoMenu::ccOnTap(float px, float py) {
    // --- control tiles ---
    for (int i = 0; i < 8; i++) {
        float x, y; ccTileXY(i, x, y);
        if (px >= x && px <= x + CC_TW && py >= y && py <= y + CC_TH) {
            switch (CC_TILE[i].act) {
                case A_SLEEP: {
                    if (!mCcSleeping && mCcSleepDir >= 0) {
                        ccBeginSleep();                         // graceful fade to off (shared with the 30s idle auto-sleep)
                    } else {
                        mCcSleeping = false; mCcSleepDir = +1;  // wake back up
                    }
                    break;
                }
                case A_PERF: {
                    char v[PROPERTY_VALUE_MAX] = {};
                    property_get("persist.gammaos.performance_mode", v, "stock");
                    const char* next = "stock";
                    if      (!strcmp(v, "stock"))     next = "powersave";
                    else if (!strcmp(v, "powersave")) next = "max";
                    else                              next = "stock";
                    property_set("persist.gammaos.performance_mode", next);
                    break;
                }
                case A_SPLITBRI: {
                    int on = property_get_int32("persist.gammaos.multidisplay.split_brightness", 0);
                    property_set("persist.gammaos.multidisplay.split_brightness", on ? "0" : "1");
                    break;
                }
                case A_SHADER: {
                    int on = property_get_int32("persist.gammaos.shader.enable", 0);
                    property_set("persist.gammaos.shader.enable", on ? "0" : "1");
                    break;
                }
                case A_EQ: {
                    int on = property_get_int32("persist.sys.gammaeq.enable", 0);
                    property_set("persist.sys.gammaeq.enable", on ? "0" : "1");
                    break;
                }
                case A_ABXY: {
                    int on = property_get_int32("persist.gammaos.gamepad.abxy_swap", 0);
                    property_set("persist.gammaos.gamepad.abxy_swap", on ? "0" : "1");
                    int ver = property_get_int32("persist.gammaos.gamepad.config_version", 0);
                    char vb[16]; snprintf(vb, sizeof(vb), "%d", ver + 1);
                    property_set("persist.gammaos.gamepad.config_version", vb);   // trigger gamepad reconfig
                    break;
                }
                case A_SHOT:
                    shellCmd("mkdir -p /sdcard/Pictures/Screenshots 2>/dev/null; "
                             "screencap -p /sdcard/Pictures/Screenshots/CC_$(date +%Y%m%d_%H%M%S).png 2>/dev/null");
                    break;
                case A_WIFI:
                    shellCmd(sCc.wifiOn ? "svc wifi disable" : "svc wifi enable");
                    sCc.wifiOn = !sCc.wifiOn;
                    break;
            }
            return;
        }
    }
}

// Which left-card slider (0=Volume, 1=Top brightness, 2=Bottom brightness) is under (px,py)?
// A touch that lands here is "grabbed" so the finger can then drag it anywhere vertically.
int NanoMenu::ccSliderAt(float px, float py) {
    const float sy = 66, sh = 120; const float sx[3] = { 34, 76, 118 };
    for (int i = 0; i < 3; i++)
        if (px >= sx[i] - 20 && px <= sx[i] + 20 && py >= sy - 12 && py <= sy + sh + 12) return i;
    return -1;
}

// Push a media_session volume --set. This forks a shell + does a binder round-trip to system_server
// (tens of ms), so callers DEBOUNCE it; the on-screen fill is driven by sCc.volCur separately and the
// final value is always flushed on touch release. Honors a non-zero stream floor (volMin).
void NanoMenu::ccSendVolume(int v) {
    if (v < sCc.volMin) v = sCc.volMin;
    if (v > sCc.volMax) v = sCc.volMax;
    char c[96]; snprintf(c, sizeof(c), "cmd media_session volume --stream 3 --set %d 2>/dev/null", v);
    shellCmd(c);
    mCcVolLastSet = v; mCcVolLastSetMs = nowMs();
}

// Apply a live value to a grabbed slider from the current touch Y (called on grab + every drag frame).
// Volume updates the on-screen fill immediately but debounces the (expensive) media_session --set so a
// drag never forks per frame; brightness writes straight to the backlight node so a drag tracks smoothly.
void NanoMenu::ccApplySlider(int i, float py) {
    const float sy = 66, sh = 120;
    float frac = 1.0f - (py - sy) / sh;
    if (frac < 0) frac = 0; if (frac > 1) frac = 1;
    if (i == 0) {
        int v = sCc.volMin + (int)lroundf(frac * (float)(sCc.volMax - sCc.volMin));
        if (v < sCc.volMin) v = sCc.volMin;
        if (v > sCc.volMax) v = sCc.volMax;
        sCc.volCur = v;                                        // immediate on-screen fill (no fork here)
        if (v != mCcVolLastSet && nowMs() - mCcVolLastSetMs >= 90)  // debounce: a fast sweep issues a
            ccSendVolume(v);                                  // handful of sets, not one per frame
    } else if (i == 1) {
        int v = (int)lroundf(frac * sCc.briTopMax); if (v < 4) v = 4;
        if (v != sCc.briTop) { writeSysfsInt(ccBlBri(false), v); sCc.briTop = v; }
    } else {
        int v = (int)lroundf(frac * sCc.briBotMax); if (v < 4) v = 4;
        if (v != sCc.briBot) {
            writeSysfsInt(ccBlBri(true), v);
            sCc.briBot = v; mCcSleepFromBri = v;   // keep the sleep-restore value in sync
        }
    }
}

// Start the graceful bottom-screen dim-to-off. Shared by the Sleep tile tap and the 30s idle
// auto-sleep so both take the exact same ramp. Captures the current bottom backlight as the value
// to restore on wake (never a value that is already effectively off).
void NanoMenu::ccBeginSleep() {
    if (mCcSleeping || mCcSleepDir < 0) return;        // already sleeping / dimming
    mCcSleepFromBri = readIntFile(ccBlBri(true), 128);
    if (mCcSleepFromBri < 8) mCcSleepFromBri = 128;    // never capture an already-off value
    mCcSleeping = true; mCcSleepDir = -1;              // graceful fade to off
}

// On CC teardown (exiting to the overlay or a new app), if the bottom panel was slept or mid-dim,
// snap its backlight back to the captured pre-sleep value and clear the sleep state. Without this
// the panel would be left dark for whoever owns the bottom screen next.
void NanoMenu::ccRestoreBacklightIfSlept() {
    // Skip only when truly settled at full brightness. A mid-wake ramp is mCcSleepDir=+1 with
    // mCcSleepRamp<1 (ccUpdateSleep last wrote a fractional value), so it must NOT be treated as
    // "awake, nothing to restore" or teardown during the 0.6s wake would leave the panel dimmed.
    if (!mCcSleeping && mCcSleepDir == 0 && mCcSleepRamp >= 1.0f) return;
    int restore = mCcSleepFromBri;
    if (restore < 8) restore = readIntFile(ccBlMax(true), 128);
    writeSysfsInt(ccBlBri(true), restore);
    mCcSleeping = false; mCcSleepDir = 0; mCcSleepRamp = 1.0f;
}

// Graceful bottom-screen dim-to-off / wake ramp. Called every frame from the park loop.
void NanoMenu::ccUpdateSleep() {
    if (mCcSleepDir == 0) return;
    float step = (mFrameDt > 0.0f ? mFrameDt : 0.016f) / 0.6f;   // ~0.6s full ramp
    mCcSleepRamp += (float)mCcSleepDir * step;
    if (mCcSleepRamp <= 0.0f) { mCcSleepRamp = 0.0f; mCcSleepDir = 0; }
    if (mCcSleepRamp >= 1.0f) { mCcSleepRamp = 1.0f; mCcSleepDir = 0; }
    int target = (int)lroundf((float)mCcSleepFromBri * mCcSleepRamp);
    writeSysfsInt(ccBlBri(true), target);
}

// The control center shows while: the feature prop is on, this is the resident overlay instance,
// the overlay menu is NOT up (that takes the whole screen), an app IS launched, the theme is XMB
// (DSi drives its own bottom carousel), the device is dual-screen, and the launched app is NOT a
// dual-stack app (those already use the bottom panel themselves).
bool NanoMenu::controlCenterActive() {
    if (!mControlCenterEnabled) return false;
    if (!mOverlayMode) return false;
    if (mNdsTheme) return false;
    if (property_get_bool("sys.gammaos.nano.show_overlay", false)) return false;
    if (!property_get_bool("sys.gammaos.nano.app_launched", false)) return false;
    if (!hasSecondaryDisplay()) return false;
    char pkg[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.launch_app", pkg, "");
    if (pkg[0] && dualstackHas(pkg)) return false;
    return true;
}

} // namespace android
