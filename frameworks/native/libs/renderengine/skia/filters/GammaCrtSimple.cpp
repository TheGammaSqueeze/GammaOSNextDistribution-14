#include "GammaCrtSimple.h"

#include <SkCanvas.h>
#include <SkColor.h>
#include <SkColorSpace.h>
#include <SkImage.h>
#include <SkPaint.h>
#include <SkRuntimeEffect.h>
#include <android-base/properties.h>
#include <chrono>
#include <algorithm>
#include <cmath>
#include <log/log.h>

#include "../ColorSpaces.h"
#include "../debug/SkiaCapture.h"

using android::base::GetBoolProperty;
using android::base::GetProperty;

namespace android {
namespace renderengine {
namespace skia {

static std::once_flag gFxOnce;
static sk_sp<SkRuntimeEffect> gFx;

static const char* kSkSL = R"(
    uniform shader src;
    uniform int    use_src;      // 1=sample src, 0=mask-only (white)

    // CRT parameters
    uniform float  scan_px;
    uniform float  scan_strength;
    uniform float  triad_px;
    uniform float  mask_strength;
    uniform float  scan_phase;
    uniform float  scan_angle_deg;

    // CPU-precomputed
    uniform float  scan_ca;      // cos(angle)
    uniform float  scan_sa;      // sin(angle)
    uniform float  inv_period;   // 1/max(1, scan_px)

    // Cosmetics
    uniform float  curv;
    uniform float  vignette;
    uniform float  edge_soft_px;

    // Framebuffer size
    uniform float2 fb_size; // (w,h)

    float2 warp_pixel(float2 p){
        if (abs(curv) <= 1e-4) return p;
        float2 wh = fb_size;
        float a = wh.x / wh.y;
        float2 uv = p / wh * 2.0 - 1.0;
        uv.x *= a;
        float r2 = dot(uv, uv);
        float2 uv2 = uv * (1.0 + curv * r2);
        uv2.x /= a;
        return (uv2 * 0.5 + 0.5) * wh;
    }

    float edge_mask(float2 q, float2 wh) {
        if (edge_soft_px <= 0.0) return 1.0;
        float2 d = min(q, wh - q);
        float distEdge = min(d.x, d.y);
        return clamp(distEdge / max(1e-3, edge_soft_px), 0.0, 1.0);
    }

    half4 main(float2 p) {
        float2 wh = fb_size;
        float2 q = warp_pixel(p);

        // Oriented scanline coordinate
        float2 pp = p - 0.5 * wh;
        float coord = pp.x * (-scan_sa) + pp.y * (scan_ca);
        coord += 0.5 * wh.y;

        // 0..1 position inside scanline period
        float t = fract((coord + scan_phase) * inv_period);
        float band = step(0.5, t); // 0 or 1
        float darkMul = 1.0 - clamp(scan_strength, 0.0, 1.0);
        float scanMul = mix(darkMul, 1.0, band);

        // Triad mask (optional)
        float3 triRGB = float3(1.0, 1.0, 1.0);
        if (triad_px >= 1.0) {
            float px = max(1.0, triad_px);
            float tt = fract(q.x / px);
            float seg = floor(3.0 * tt);
            if (seg < 0.5)       triRGB = float3(1.0, 0.80, 0.80);
            else if (seg < 1.5)  triRGB = float3(0.80, 1.0, 0.80);
            else                 triRGB = float3(0.80, 0.80, 1.0);
            triRGB = mix(float3(1.0, 1.0, 1.0), triRGB, clamp(mask_strength, 0.0, 1.0));
        }

        half4 base = use_src != 0 ? src.eval(q) : half4(1.0);
        base.rgb *= triRGB * scanMul;

        // Mild vignette (optional)
        if (vignette > 0.0) {
            float a = wh.x / wh.y;
            float2 uv = p / wh * 2.0 - 1.0;
            uv.x *= a;
            float r2 = dot(uv, uv);
            float vig = 1.0 - vignette * smoothstep(0.6, 1.0, r2);
            base.rgb *= vig;
        }

        // Soft edges (optional)
        base.rgb *= edge_mask(q, wh);
        return base;
    }
)";

bool GammaCrtSimple::apply(SkSurface* dstSurface,
                           SkSurface* srcSurface,
                           SkiaCapture* capture,
                           ui::Dataspace outDataspace,
                           bool isProtected,
                           bool testOverlay,
                           bool ctmBfiBlack,
                           float defaultScanAngleDeg) {
    if (!dstSurface || !srcSurface || !capture) return false;

    const bool debugLog = GetBoolProperty("persist.gammaos.shader.debug", false);

    SkCanvas* dstCanvas = capture->tryCapture(dstSurface);
    if (!dstCanvas) {
        if (debugLog) ALOGE("GammaOS CRT: no canvas at post-process; skipping.");
        return false;
    }

    // Only draw the 50% gray overlay if explicitly requested.
    const bool testOverlayForce = GetBoolProperty("persist.gammaos.shader.test_overlay", false);
    if (testOverlay || testOverlayForce) {
        if (debugLog) ALOGD("GammaOS CRT: test_overlay active (multiply 50%% gray).");
        SkPaint p;
        p.setColor(SkColorSetARGB(255, 128, 128, 128));
        p.setBlendMode(SkBlendMode::kMultiply);
        dstCanvas->save();
        dstCanvas->resetMatrix();
        dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
        dstCanvas->restore();
        return true;
    }

    (void)ctmBfiBlack; // BFI removed

    std::call_once(gFxOnce, [&]{
        auto pair = SkRuntimeEffect::MakeForShader(SkString(kSkSL));
        gFx = pair.effect;
        if (!gFx && debugLog) {
            ALOGE("GammaOS CRT: SkSL compile failed: %s", pair.errorText.c_str());
        }
    });
    if (!gFx) return false;

    // Shader gating and defaults
    const bool shaderOn = GetBoolProperty("persist.gammaos.shader.enable", false);

    float scan_px        = 4.0f;
    float scan_strength  = 0.40f;
    float triad_px       = 0.0f;
    float mask_strength  = 0.0f;
    float scan_phase     = 0.0f;
    float scan_angle_deg = defaultScanAngleDeg;
    float curv           = 0.03f;
    float vignette       = 0.01f;
    float edge_soft_px   = 4.0f;

    // Read per-type params (crt-simple)
    const std::string type = GetProperty("persist.gammaos.shader.type", "crt-simple");
    if (type == "crt-simple") {
        auto getf = [](const char* k, const char* defv) -> float {
            const std::string s = GetProperty(k, defv);
            return static_cast<float>(atof(s.c_str()));
        };
        scan_px       = std::max(1.0f, getf("persist.gammaos.shader.crt-simple.scan_px", "4"));
        scan_strength = std::clamp(getf("persist.gammaos.shader.crt-simple.scan_strength", "0.4"), 0.0f, 1.0f);
        curv          = getf("persist.gammaos.shader.crt-simple.curv", "0.03");
        vignette      = std::clamp(getf("persist.gammaos.shader.crt-simple.vignette", "0.01"), 0.0f, 1.0f);
        edge_soft_px  = std::max(0.0f, getf("persist.gammaos.shader.crt-simple.edge_soft_px", "4"));
    }

    // Optional fast mode: strip cosmetics
    const bool fastMode = GetBoolProperty("persist.gammaos.shader.fast", false);
    if (fastMode) {
        triad_px = 0.0f; mask_strength = 0.0f;
        curv = 0.0f; vignette = 0.0f; edge_soft_px = 0.0f;
    }

    const float ang = scan_angle_deg * static_cast<float>(M_PI / 180.0);
    const float scan_ca_cpu = cosf(ang);
    const float scan_sa_cpu = sinf(ang);
    const float period_cpu  = std::max(1.0f, scan_px);
    const float inv_period  = 1.0f / period_cpu;

    SkRuntimeShaderBuilder b(gFx);
    b.uniform("scan_px")        = scan_px;
    b.uniform("scan_strength")  = scan_strength;
    b.uniform("triad_px")       = triad_px;
    b.uniform("mask_strength")  = mask_strength;
    b.uniform("scan_phase")     = scan_phase;
    b.uniform("scan_ca")        = scan_ca_cpu;
    b.uniform("scan_sa")        = scan_sa_cpu;
    b.uniform("inv_period")     = inv_period;
    b.uniform("fb_size")        = SkV2{(float)dstSurface->width(), (float)dstSurface->height()};
    b.uniform("curv")           = curv;
    b.uniform("vignette")       = vignette;
    b.uniform("edge_soft_px")   = edge_soft_px;

    SkPaint p;
    if (!isProtected) {
        sk_sp<SkImage> srcImage = srcSurface->makeImageSnapshot();
        if (!srcImage) {
            if (debugLog) ALOGW("GammaOS CRT: snapshot failed; skipping effect.");
            return false;
        }
        b.child("src") = srcImage->makeShader(
                SkSamplingOptions(SkFilterMode::kNearest, SkMipmapMode::kNone));
        b.uniform("use_src") = 1;
        p.setShader(b.makeShader());
        p.setBlendMode(SkBlendMode::kSrc);
    } else {
        b.child("src") = SkShaders::Color(SkColors::kWhite, toSkColorSpace(outDataspace));
        b.uniform("use_src") = 0;
        p.setShader(b.makeShader());
        p.setBlendMode(SkBlendMode::kMultiply);
    }

    dstCanvas->save();
    dstCanvas->resetMatrix();
    dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
    dstCanvas->restore();
    if (debugLog) ALOGD("GammaOS CRT: applied unified post-pass.");
    return true;
}

} // namespace skia
} // namespace renderengine
} // namespace android