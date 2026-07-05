// GammaRGB "Follow Screen" sampler for nano's DRM-render mode.
//
// When gammaos-nano owns the panel directly (DRM master), SurfaceFlinger no
// longer composites the visible frame, so its GammaRgbSampler readback would
// only see black. This module makes nano sample its OWN just-presented frame -
// the AHardwareBuffer it renders into is allocated CPU_READ_OFTEN (linear
// RGBA), so we lock it and strided-sample straight from memory with zero GPU
// cost (no glReadPixels, no downscale pass). The pixel-to-colour maths are the
// shared gammargb::selectFromPixels pipeline, byte-identical to the SF sampler,
// so LED colour fidelity matches exactly. gammargb consumes the published
// persist.gammaos.primary.rgb_hex and fades to it, so publishing at rgb.fps is
// smooth without a sampler-side fade loop.
//
// Gated strictly to: DRM mode AND rgb.enable AND gammargb.control!=off AND
// rgb.effect=="follow" AND screen on. Any other mode leaves the prop alone so
// the static/hardware-effect paths (owned by gammargb / the SF sampler) are
// untouched.

#include <android/hardware_buffer.h>
#include <cutils/properties.h>
#include <time.h>
#include <stdlib.h>
#include <string.h>
#include <string>
#include <algorithm>
#include <cmath>

#include "NanoMenuDrm.h"          // AHB ring globals + sDrmActive
#include "GammaRgbProcess.h"      // shared selection pipeline (libgammargb_process_headers)

namespace android {

namespace {

int    propInt  (const char* k, int def)          { char v[PROPERTY_VALUE_MAX]; return (property_get(k, v, "") > 0 && v[0]) ? atoi(v) : def; }
float  propFloat(const char* k, float def)         { char v[PROPERTY_VALUE_MAX]; return (property_get(k, v, "") > 0 && v[0]) ? strtof(v, nullptr) : def; }
bool   propBool (const char* k, bool def)          { char v[PROPERTY_VALUE_MAX]; if (property_get(k, v, "") > 0 && v[0]) return (!strcmp(v, "1") || !strcmp(v, "true")); return def; }
bool   propEq   (const char* k, const char* val)   { char v[PROPERTY_VALUE_MAX]; property_get(k, v, ""); return !strcmp(v, val); }

double nowMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec * 1000.0 + (double)ts.tv_nsec / 1e6;
}

double      sLastSampleMs = 0.0;
double      sLastFadeMs   = 0.0;
std::string sLastHex;

// Fade state: the published colour (sCur) eases from sFrom to sTgt as sProg
// runs 0->1, so a freshly sampled colour ramps in instead of snapping - the
// same "balance out sudden colour changes" the SF sampler's fade provides.
bool   sHaveTarget = false;
int    sFromR = 0, sFromG = 0, sFromB = 0;
int    sTgtR  = 0, sTgtG  = 0, sTgtB  = 0;
int    sCurR  = 0, sCurG  = 0, sCurB  = 0;
double sProg  = 1.0;

// gammargb reads persist.gammaos.primary.rgb_hex on its fixed ~12 Hz loop, so
// publishing the ramp faster than this is wasted work. SF ramps at fade.fps
// (120 Hz); we honour fade.enable/fade.fps but clamp the publish rate here to
// keep the render-thread property_set cost negligible while still handing
// gammargb a fresh value on every read. The ramp DURATION stays one sample
// period regardless of this cap, so the visible fade matches SF.
constexpr int kFadeCapHz = 20;

inline int lerpi(int a, int b, double p) {
    return a + (int)std::lround((double)(b - a) * p);
}

void publishCur() {
    const std::string hex = gammargb::toHex(sCurR, sCurG, sCurB);
    if (hex != sLastHex) {
        sLastHex = hex;
        property_set("persist.gammaos.primary.rgb_hex", hex.c_str());
    }
}

// Lock the most-recently-presented AHB ring slot (GPU-complete, being scanned
// out) and strided-sample it through the shared selection + brightness
// pipeline. sRingPresentIdx points at the NEXT slot to flip, so the last flipped
// is sRingPresentIdx-1. Returns false (caller holds the previous colour) on any
// miss. This is the only expensive step; the caller paces it at rgb.fps.
bool sampleTarget(int& R, int& G, int& B) {
    if (sRingPrimedCount < 2) return false;
    const int idx = (sRingPresentIdx - 1 + AHB_RING_DEPTH) % AHB_RING_DEPTH;
    AHardwareBuffer* ahb = sAhbRingPrimary[idx].ahb;
    if (!ahb) return false;

    AHardwareBuffer_Desc desc = {};
    AHardwareBuffer_describe(ahb, &desc);
    if (desc.width == 0 || desc.height == 0 || desc.stride == 0) return false;

    void* addr = nullptr;
    if (AHardwareBuffer_lock(ahb, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                             -1 /*no fence: slot is complete*/, nullptr, &addr) != 0 || !addr) {
        return false;
    }

    const int w        = (int)desc.width;
    const int h        = (int)desc.height;
    const int rowBytes = (int)desc.stride * 4;   // stride is in pixels, RGBA8888
    const int sampleN  = std::max(8, std::min(256, propInt("persist.gammaos.rgb.sample_size_px", 64)));
    const int stepX    = std::max(1, w / sampleN);
    const int stepY    = std::max(1, h / sampleN);

    gammargb::GammaRgbParams P;
    P.grayTol             = propInt  ("persist.gammaos.rgb.gray_tolerance",         4);
    P.whiteAvg            = propInt  ("persist.gammaos.rgb.white_avg_threshold",  200);
    P.blackAvg            = propInt  ("persist.gammaos.rgb.black_avg_threshold",    3);
    P.boostThresh         = propInt  ("persist.gammaos.rgb.boost_threshold",        1);
    P.maxBoost            = propFloat("persist.gammaos.rgb.max_boost_factor",    1.0f);
    P.satPixelThreshold   = propInt  ("persist.gammaos.rgb.sat_pixel_threshold",   30);
    P.clusterOverWhite    = propBool ("persist.gammaos.rgb.cluster_over_white",  true);
    P.clusterMinShare     = propFloat("persist.gammaos.rgb.cluster_min_share",  0.03f);
    P.colorShareOverWhite = propFloat("persist.gammaos.rgb.color_share_over_white", 0.03f);
    P.bestSatGrayShareMax = propFloat("persist.gammaos.rgb.best_sat_gray_share_max", 0.05f);
    P.scaleWithBrightness = propBool ("persist.gammaos.rgb.scale_with_brightness", false);
    P.brightOverrideThresh= propInt  ("persist.gammaos.rgb.brightness_override_threshold", 3);
    P.backlightExp        = propFloat("persist.gammaos.rgb.brightness_curve_exp", 1.0f);
    P.minLedFloor         = propInt  ("persist.gammaos.rgb.min_led_brightness",     3);
    P.satBoost            = propFloat("persist.gammaos.rgb.saturation_boost",    1.4f);
    P.ledBrightness       = propInt  ("persist.gammaos.rgb.led_brightness",      255);
    // Brightness-follow scalar (only used when scale_with_brightness=1). The
    // framework publishes the panel brightness as a 0..1 float here.
    P.brightnessScalar    = propFloat("debug.tracing.screen_brightness", -1.0f);
    P.rawBrightness       = 255;

    const bool ok = gammargb::selectFromPixels((const uint8_t*)addr, w, h, rowBytes,
                                               stepX, stepY, P, R, G, B);
    AHardwareBuffer_unlock(ahb, nullptr);
    if (!ok) return false;   // blank/secure frame

    gammargb::applyBrightness(R, G, B, P);
    return true;
}

} // namespace

// Called once per home render iteration (after present) on the render thread.
// Cheap early-out in every non-follow / non-DRM case.
void nanoRgbFollowSample() {
    if (!sDrmActive) return;
    const double t = nowMs();

    // Gating: only drive the prop in genuine Follow-Screen mode.
    if (!propBool("persist.gammaos.rgb.enable", false) ||
        propEq("persist.gammargb.control", "off") ||
        !propEq("persist.gammaos.rgb.effect", "follow") ||
        !propEq("sys.screen.state", "on")) {
        sHaveTarget = false;
        sLastHex.clear();   // republish immediately when follow resumes
        return;
    }

    const int  fps        = std::max(1, std::min(60, propInt("persist.gammaos.rgb.fps", 6)));
    const bool fadeEnable = propBool("persist.gammaos.rgb.fade.enable", true);
    int fadeHz = std::max(1, std::min(240, propInt("persist.gammaos.rgb.fade.fps", 60)));
    if (fadeHz > kFadeCapHz) fadeHz = kFadeCapHz;

    // 1) SAMPLE (the only expensive step) paced at rgb.fps -> new target colour.
    if (t - sLastSampleMs >= 1000.0 / (double)fps) {
        sLastSampleMs = t;
        int R = 0, G = 0, B = 0;
        if (sampleTarget(R, G, B)) {
            if (!sHaveTarget || !fadeEnable) {
                // First sample after (re)entry, or fade off: jump straight.
                sFromR = sTgtR = sCurR = R;
                sFromG = sTgtG = sCurG = G;
                sFromB = sTgtB = sCurB = B;
                sProg = 1.0; sHaveTarget = true;
                publishCur();
            } else if (R != sTgtR || G != sTgtG || B != sTgtB) {
                // New target: restart the ramp from wherever we currently are.
                sFromR = sCurR; sFromG = sCurG; sFromB = sCurB;
                sTgtR = R; sTgtG = G; sTgtB = B;
                sProg = 0.0;
            }
        }
    }

    // 2) FADE (cheap: lerp + one property_set) paced at the capped rate. sProg
    // advances fps/fadeHz per tick so the ramp always spans exactly one sample
    // period, matching SF's fade duration independent of the publish rate.
    if (sHaveTarget && fadeEnable && sProg < 1.0 &&
        (t - sLastFadeMs >= 1000.0 / (double)fadeHz)) {
        sLastFadeMs = t;
        sProg += (double)fps / (double)fadeHz;
        if (sProg > 1.0) sProg = 1.0;
        sCurR = lerpi(sFromR, sTgtR, sProg);
        sCurG = lerpi(sFromG, sTgtG, sProg);
        sCurB = lerpi(sFromB, sTgtB, sProg);
        publishCur();
    }
}

} // namespace android
