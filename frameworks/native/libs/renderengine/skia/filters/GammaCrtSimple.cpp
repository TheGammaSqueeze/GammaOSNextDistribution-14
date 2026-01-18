#include "GammaCrtSimple.h"

#include <SkCanvas.h>
#include <SkColor.h>
#include <SkColorSpace.h>
#include <SkImage.h>
#include <SkMatrix.h>
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

static sk_sp<SkImage> makeHalfResNearest(SkSurface* refSurface,
                                        const sk_sp<SkImage>& srcImage,
                                        bool debugLog,
                                        const char* tag) {
    if (!refSurface || !srcImage) return nullptr;

    const int srcW = srcImage->width();
    const int srcH = srcImage->height();
    const int halfW = std::max(1, srcW / 2);
    const int halfH = std::max(1, srcH / 2);

    const SkImageInfo srcInfo = srcImage->imageInfo();
    const SkImageInfo halfInfo = srcInfo.makeWH(halfW, halfH);

    sk_sp<SkSurface> halfSurface = refSurface->makeSurface(halfInfo);
    if (!halfSurface) {
        if (debugLog) ALOGW("%s: failed to allocate half-res surface (%dx%d).", tag, halfW, halfH);
        return nullptr;
    }

    SkCanvas* c = halfSurface->getCanvas();
    if (!c) return nullptr;

    // Downscale with nearest neighbor only (no bilinear).
    c->save();
    c->resetMatrix();
    c->scale((float)halfW / (float)srcW, (float)halfH / (float)srcH);
    SkPaint p;
    p.setBlendMode(SkBlendMode::kSrc);
    c->drawImage(srcImage, 0.0f, 0.0f,
                 SkSamplingOptions(SkFilterMode::kNearest, SkMipmapMode::kNone), &p);
    c->restore();

    return halfSurface->makeImageSnapshot();
}

static const char* kSkSL = R"(
    uniform shader src;

    // CRT parameters
    uniform float  scan_strength;   // 0..1
    uniform float  triad_px;
    uniform float  mask_strength;
    uniform float  scan_phase;
    uniform float  scan_angle_deg;

    // CPU-precomputed
    uniform float  scan_ca;         // cos(angle)
    uniform float  scan_sa;         // sin(angle)
    uniform float  inv_period;      // 1/max(1, scan_px)
    uniform float  scan_dark;       // (1 - scan_strength)

    // Cosmetics
    uniform float  curv;
    uniform float  vignette;
    uniform float  edge_soft_px;

    // Optional CRT blur (CPU-precomputed radius; 0 disables)
    uniform float  blur_r;

    // Framebuffer size
    uniform float2 fb_size; // (w,h)

    half4 sample_base(float2 q) {
        float r = blur_r;
        if (r <= 0.01) {
            return src.eval(q);
        }

        float2 offX = float2(r, 0.0);
        float2 offY = float2(0.0, r);

        // 9-tap kernel: center, 4 cardinals (weight 2), 4 diagonals (weight 1)
        half4 c0 = src.eval(q);

        half4 c1 = src.eval(q + offX);
        half4 c2 = src.eval(q - offX);
        half4 c3 = src.eval(q + offY);
        half4 c4 = src.eval(q - offY);

        half4 c5 = src.eval(q + offX + offY);
        half4 c6 = src.eval(q + offX - offY);
        half4 c7 = src.eval(q - offX + offY);
        half4 c8 = src.eval(q - offX - offY);

        // Total weight = 4 (center) + 4*2 (cardinals) + 4*1 (diagonals) = 20
        return (c0 * 4.0 + (c1 + c2 + c3 + c4) * 2.0 + (c5 + c6 + c7 + c8)) * (1.0 / 20.0);
    }

    float edge_mask(float2 q, float2 wh) {
        if (edge_soft_px <= 0.0) return 1.0;
        float2 d = min(q, wh - q);
        float distEdge = min(d.x, d.y);
        return clamp(distEdge / max(1e-3, edge_soft_px), 0.0, 1.0);
    }

    half4 main(float2 p) {
        float2 wh = fb_size;
        float2 invWh = 1.0 / wh;
        float aspect = wh.x * invWh.y;
        float invAspect = wh.y * invWh.x;

        // Centered UV and r^2 (reused for warp and vignette).
        float2 uv = p * invWh * 2.0 - 1.0;
        float2 uvA = uv;
        uvA.x *= aspect;
        float r2 = dot(uvA, uvA);

        // Warp in-place (keeps scanlines stable since scanlines are based on p).
        float2 q = p;
        if (abs(curv) > 1e-4) {
            float k = 1.0 + curv * r2;
            float2 u = uvA * k;
            u.x *= invAspect;
            q = (u * 0.5 + 0.5) * wh;
        }

        // Oriented scanline coordinate
        float2 pp = p - 0.5 * wh;
        float coord = dot(pp, float2(-scan_sa, scan_ca)) + 0.5 * wh.y;

        float t = fract((coord + scan_phase) * inv_period);
        float band = step(0.5, t); // 0 or 1
        float scanMul = mix(scan_dark, 1.0, band);

        // Triad mask (optional).
        float3 triRGB = float3(1.0);
        if (triad_px >= 1.0) {
            float px = max(1.0, triad_px);
            float seg = floor(3.0 * fract(q.x / px));

            float is0 = 1.0 - step(0.5, seg);
            float is1 = step(0.5, seg) * (1.0 - step(1.5, seg));
            float is2 = step(1.5, seg);

            float3 c0 = float3(1.0, 0.80, 0.80);
            float3 c1 = float3(0.80, 1.0, 0.80);
            float3 c2 = float3(0.80, 0.80, 1.0);
            float3 tri = c0 * is0 + c1 * is1 + c2 * is2;

            triRGB = mix(float3(1.0), tri, clamp(mask_strength, 0.0, 1.0));
        }

        half4 base = sample_base(q);
        base.rgb *= half3(triRGB) * half(scanMul);

        // Mild vignette (optional).
        if (vignette > 0.0) {
            float r2 = dot(uv, uv);
            float vig = 1.0 - vignette * smoothstep(0.6, 1.0, r2);
            base.rgb *= half(vig);
        }

        // Soft edges (optional).
        base.rgb *= half(edge_mask(q, wh));
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

    (void)ctmBfiBlack; // BFI removed

    std::call_once(gFxOnce, []{
        auto pair = SkRuntimeEffect::MakeForShader(SkString(kSkSL));
        gFx = pair.effect;
        if (!gFx) {
            const bool dbg = GetBoolProperty("persist.gammaos.shader.debug", false);
            if (dbg) {
                ALOGE("GammaOS CRT: SkSL compile failed: %s", pair.errorText.c_str());
            }
        }
    });
    if (!gFx) return false;

    // Shader gating and defaults
    const bool shaderOn = GetBoolProperty("persist.gammaos.shader.enable", false);
 
    const bool halfRes = GetBoolProperty("persist.gammaos.shader.crt-simple.half_res", false);

    float scan_px        = 4.0f;
    float scan_strength  = 0.40f;
    float triad_px       = 0.0f;
    float mask_strength  = 0.0f;
    float scan_phase     = 0.0f;
    float scan_angle_deg = defaultScanAngleDeg;
    float curv           = 0.03f;
    float vignette       = 0.01f;
    float edge_soft_px   = 4.0f;
    float blur_intensity = 0.0f;

    // Read per-type params (crt-simple)
    const std::string type = GetProperty("persist.gammaos.shader.type", "crt-simple");
    if (!shaderOn || type != "crt-simple") {
        if (debugLog) {
            ALOGD("GammaOS CRT: disabled or type mismatch (type=%s, on=%d).",
                  type.c_str(), shaderOn ? 1 : 0);
        }
        return false;
    }
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
        blur_intensity = std::clamp(getf("persist.gammaos.shader.crt-simple.blur_intensity", "0"), 0.0f, 1.0f);
    }

    if (debugLog) {
        ALOGD("GammaOS CRT: params scan_px=%.2f scan_strength=%.2f curv=%.3f "
              "vignette=%.3f edge_soft_px=%.2f blur_intensity=%.3f",
              scan_px, scan_strength, curv, vignette, edge_soft_px, blur_intensity);
    }

    // Optional fast mode: strip cosmetics
    const bool fastMode = GetBoolProperty("persist.gammaos.shader.fast", false);
    if (fastMode) {
        triad_px = 0.0f; mask_strength = 0.0f;
        curv = 0.0f; vignette = 0.0f; edge_soft_px = 0.0f;
        blur_intensity = 0.0f;
    }
    const float ang = scan_angle_deg * static_cast<float>(M_PI / 180.0);
    const float scan_ca_cpu = cosf(ang);
    const float scan_sa_cpu = sinf(ang);
    const float period_cpu  = std::max(1.0f, scan_px);
    const float inv_period  = 1.0f / period_cpu;
    const float scan_dark   = 1.0f - scan_strength;


    // Precompute blur radius in pixels so the shader can skip per-fragment resolution math.
    float blur_r_px = 0.0f;
    if (blur_intensity > 0.0f) {
        const float minDim = std::min((float)dstSurface->width(), (float)dstSurface->height());
        const float baseRadius = std::max(1.0f, minDim / 480.0f);
        blur_r_px = blur_intensity * 3.0f * baseRadius;
    }

    SkRuntimeShaderBuilder b(gFx);
    b.uniform("scan_strength")  = scan_strength;
    b.uniform("scan_dark")      = scan_dark;
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
    b.uniform("blur_r")         = blur_r_px;

    SkPaint p;
    if (!isProtected) {
        sk_sp<SkImage> srcImage = srcSurface->makeImageSnapshot();
        if (!srcImage) {
            if (debugLog) ALOGW("GammaOS CRT: snapshot failed; skipping effect.");
            return false;
        }
        sk_sp<SkImage> srcForEffect = srcImage;
        if (halfRes) {
            sk_sp<SkImage> half = makeHalfResNearest(dstSurface, srcImage, debugLog, "GammaOS CRT");
            if (half) srcForEffect = half;
        }

        // Always nearest. If half-res is enabled, scale the sampling coords by 0.5 so the
        // half-res texture covers the full output without bilinear filtering.
        sk_sp<SkShader> child = srcForEffect->makeShader(
                SkSamplingOptions(SkFilterMode::kNearest, SkMipmapMode::kNone));
        if (halfRes && srcForEffect != srcImage) {
            // IMPORTANT: Skia inverts the total matrix when mapping device -> shader local space.
            // To sample the half-res image at (p * 0.5), we must provide a local matrix of 2.0,
            // so the inverse becomes 0.5 in the device-to-local mapping.
            child = child ? child->makeWithLocalMatrix(SkMatrix::Scale(2.0f, 2.0f)) : nullptr;
        }

        b.child("src") = child;
        p.setShader(b.makeShader());
        p.setBlendMode(SkBlendMode::kSrc);
    } else {
        b.child("src") = SkShaders::Color(SkColors::kWhite, toSkColorSpace(outDataspace));
        p.setShader(b.makeShader());
        p.setBlendMode(SkBlendMode::kMultiply);
    }

    dstCanvas->save();
    dstCanvas->resetMatrix();
    dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
    dstCanvas->restore();
    if (debugLog) {
        ALOGD("GammaOS CRT: applied unified post-pass (half_res=%d).", halfRes ? 1 : 0);
    }
    return true;
}

} // namespace skia
} // namespace renderengine
} // namespace android