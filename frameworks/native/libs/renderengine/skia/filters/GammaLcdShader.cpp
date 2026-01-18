#include "GammaLcdShader.h"

#include <SkCanvas.h>
#include <SkColor.h>
#include <SkColorSpace.h>
#include <SkImage.h>
#include <SkMatrix.h>
#include <SkPaint.h>
#include <SkRuntimeEffect.h>
#include <SkSamplingOptions.h>
#include <android-base/properties.h>

#include <algorithm>
#include <cmath>
#include <mutex>

#include <log/log.h>

#include "../ColorSpaces.h"
#include "../debug/SkiaCapture.h"

namespace android {
namespace renderengine {
namespace skia {

namespace {

static std::once_flag gFxOnce;
static sk_sp<SkRuntimeEffect> gFxNoPrev;
static sk_sp<SkRuntimeEffect> gFxPrev;

static std::mutex gPrevMutex;
static sk_sp<SkImage> gPrevImage;
static int gPrevW = 0;
static int gPrevH = 0;

inline float clampf(float v, float lo, float hi) {
    return std::max(lo, std::min(v, hi));
}

inline float getFloatProp(const char* key, const char* defv) {
    using android::base::GetProperty;
    const std::string s = GetProperty(key, defv);
    return static_cast<float>(atof(s.c_str()));
}

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

    // Downscale with nearest-neighbor only (no bilinear).
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

// A single-pass LCD mask intended to be very cheap:
//  - scanline darkening every other row
//  - RGB stripe mask every 3 columns
//  - optional gap darkening near pixel edges
//  - optional response-time blending with previous frame (one extra texture sample)
static const char* kSkSL_NoPrev = R"(
    uniform shader src;

    uniform float  scan_strength;      // 0..1
    uniform float  subpixel_strength;  // 0..1
    uniform float  gap_strength;       // 0..1
    uniform float  gap_px;             // 0..0.45 (fraction of a pixel)
    uniform float  scan_phase;         // 0 or 1

    float scan_mul(float y) {
        float s = clamp(scan_strength, 0.0, 1.0);
        float odd = mod(floor(y) + scan_phase, 2.0);
        return 1.0 - s * odd;
    }

    float gap_mul(float2 p) {
        float g = clamp(gap_px, 0.0, 0.45);
        float fx = fract(p.x);
        float fy = fract(p.y);
        float inside = step(g, fx) * step(fx, 1.0 - g) * step(g, fy) * step(fy, 1.0 - g);
        return mix(1.0 - clamp(gap_strength, 0.0, 1.0), 1.0, inside);
    }

    float3 stripe_mul(float x) {
        float t = mod(floor(x), 3.0);
        float m0 = 1.0 - step(0.5, t);
        float m1 = step(0.5, t) * (1.0 - step(1.5, t));
        float m2 = step(1.5, t);

        float off = 1.0 - clamp(subpixel_strength, 0.0, 1.0);
        float3 w = float3(off);
        w += (1.0 - off) * float3(m0, m1, m2);
        return w;
    }

    half4 main(float2 p) {
        half4 c = src.eval(p);
        float mul = scan_mul(p.y) * gap_mul(p);
        half3 rgb = c.rgb * half(mul) * half3(stripe_mul(p.x));
        return half4(rgb, c.a);
    }
)";

static const char* kSkSL_Prev = R"(
    uniform shader src;
    uniform shader prev;

    uniform float  response_time;      // 0..0.78
    uniform float  scan_strength;      // 0..1
    uniform float  subpixel_strength;  // 0..1
    uniform float  gap_strength;       // 0..1
    uniform float  gap_px;             // 0..0.45 (fraction of a pixel)
    uniform float  scan_phase;         // 0 or 1

    float scan_mul(float y) {
        float s = clamp(scan_strength, 0.0, 1.0);
        float odd = mod(floor(y) + scan_phase, 2.0);
        return 1.0 - s * odd;
    }

    float gap_mul(float2 p) {
        float g = clamp(gap_px, 0.0, 0.45);
        float fx = fract(p.x);
        float fy = fract(p.y);
        float inside = step(g, fx) * step(fx, 1.0 - g) * step(g, fy) * step(fy, 1.0 - g);
        return mix(1.0 - clamp(gap_strength, 0.0, 1.0), 1.0, inside);
    }

    float3 stripe_mul(float x) {
        float t = mod(floor(x), 3.0);
        float m0 = 1.0 - step(0.5, t);
        float m1 = step(0.5, t) * (1.0 - step(1.5, t));
        float m2 = step(1.5, t);

        float off = 1.0 - clamp(subpixel_strength, 0.0, 1.0);
        float3 w = float3(off);
        w += (1.0 - off) * float3(m0, m1, m2);
        return w;
    }

    half4 main(float2 p) {
        half4 c0 = src.eval(p);
        // response_time: 0 = no ghosting; higher values retain more of previous frame
        float rt = clamp(response_time, 0.0, 0.78);
        half4 c1 = prev.eval(p);
        half3 c = mix(c0.rgb, c1.rgb, half(rt));

        float mul = scan_mul(p.y) * gap_mul(p);
        half3 rgb = c * half(mul) * half3(stripe_mul(p.x));
        return half4(rgb, c0.a);
    }
)";

static void ensureEffects(bool debugLog) {
    std::call_once(gFxOnce, [&] {
        {
            auto pair = SkRuntimeEffect::MakeForShader(SkString(kSkSL_NoPrev));
            gFxNoPrev = pair.effect;
            if (!gFxNoPrev && debugLog) {
                ALOGE("GammaOS LCD: SkSL(NoPrev) compile failed: %s", pair.errorText.c_str());
            }
        }
        {
            auto pair = SkRuntimeEffect::MakeForShader(SkString(kSkSL_Prev));
            gFxPrev = pair.effect;
            if (!gFxPrev && debugLog) {
                ALOGE("GammaOS LCD: SkSL(Prev) compile failed: %s", pair.errorText.c_str());
            }
        }
    });
}

} // anonymous namespace

bool GammaLcdShader::apply(SkSurface* dstSurface,
                           SkSurface* srcSurface,
                           SkiaCapture* capture,
                           ui::Dataspace outDataspace,
                           bool isProtected,
                           bool testOverlay,
                           bool ctmBfiBlack,
                           float /*defaultScanAngleDeg*/) {
    using android::base::GetBoolProperty;
    using android::base::GetProperty;

    if (!dstSurface || !srcSurface || !capture) return false;

    (void)ctmBfiBlack;

    const bool debugLog = GetBoolProperty("persist.gammaos.shader.debug", false);

    // Shader gating and type selection.
    const bool shaderOn = GetBoolProperty("persist.gammaos.shader.enable", false);
    const std::string type = GetProperty("persist.gammaos.shader.type", "crt-simple");
    if (!shaderOn || (type != "lcd-shader" && type != "lcd_shader" && type != "lcd")) {
        if (!shaderOn) {
            std::lock_guard<std::mutex> lk(gPrevMutex);
            gPrevImage.reset();
            gPrevW = gPrevH = 0;
        }
        return false;
    }

    SkCanvas* dstCanvas = capture->tryCapture(dstSurface);
    if (!dstCanvas) return false;

    // Optional overlay used by other GammaOS shaders.
    const bool testOverlayForce = GetBoolProperty("persist.gammaos.shader.test_overlay", false);
    if (testOverlay || testOverlayForce) {
        SkPaint p;
        p.setColor(SkColorSetARGB(255, 128, 128, 128));
        p.setBlendMode(SkBlendMode::kMultiply);
        dstCanvas->save();
        dstCanvas->resetMatrix();
        dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
        dstCanvas->restore();
        return true;
    }

    ensureEffects(debugLog);
    if (!gFxNoPrev) return false;

    const float responseTime = clampf(
            getFloatProp("persist.gammaos.shader.lcd.response_time", "0"), 0.0f, 0.78f);
    const float scanStrength = clampf(
            getFloatProp("persist.gammaos.shader.lcd.scan_strength", "0.20"), 0.0f, 1.0f);
    const float subpixelStrength = clampf(
            getFloatProp("persist.gammaos.shader.lcd.subpixel_strength", "0.40"), 0.0f, 1.0f);
    const float gapStrength = clampf(
            getFloatProp("persist.gammaos.shader.lcd.gap_strength", "0.10"), 0.0f, 1.0f);
    const float gapPx = clampf(
            getFloatProp("persist.gammaos.shader.lcd.gap_px", "0.05"), 0.0f, 0.45f);
    const float scanPhase = clampf(
            getFloatProp("persist.gammaos.shader.lcd.scan_phase", "0"), 0.0f, 1.0f);

    const bool halfRes = GetBoolProperty("persist.gammaos.shader.lcd.half_res", false);
    const bool resetHistory = GetBoolProperty("persist.gammaos.shader.lcd.reset_history", false);

    if (resetHistory) {
        std::lock_guard<std::mutex> lk(gPrevMutex);
        gPrevImage.reset();
        gPrevW = gPrevH = 0;
    }

    SkPaint paint;

    if (!isProtected) {
        sk_sp<SkImage> srcImage = srcSurface->makeImageSnapshot();
        if (!srcImage) return false;

        // Store current frame for next frame's response-time simulation.
        {
            std::lock_guard<std::mutex> lk(gPrevMutex);
            gPrevImage = srcImage;
            gPrevW = srcImage->width();
            gPrevH = srcImage->height();
        }

        sk_sp<SkImage> srcForEffect = srcImage;
        if (halfRes) {
            sk_sp<SkImage> half = makeHalfResNearest(dstSurface, srcImage, debugLog, "GammaOS LCD");
            if (half) srcForEffect = half;
        }

        sk_sp<SkShader> srcChild = srcForEffect->makeShader(
                SkSamplingOptions(SkFilterMode::kNearest, SkMipmapMode::kNone));
        if (halfRes && srcForEffect != srcImage && srcChild) {
            srcChild = srcChild->makeWithLocalMatrix(SkMatrix::Scale(2.0f, 2.0f));
        }

        // Decide whether we can (and should) sample previous frame.
        sk_sp<SkImage> prevImage;
        {
            std::lock_guard<std::mutex> lk(gPrevMutex);
            if (gPrevImage && gPrevW == srcImage->width() && gPrevH == srcImage->height()) {
                prevImage = gPrevImage;
            }
        }

        const bool usePrev = (responseTime > 0.0001f) && (prevImage != nullptr) && (gFxPrev != nullptr);

        if (usePrev) {
            SkRuntimeShaderBuilder b(gFxPrev);
            b.uniform("response_time") = responseTime;
            b.uniform("scan_strength") = scanStrength;
            b.uniform("subpixel_strength") = subpixelStrength;
            b.uniform("gap_strength") = gapStrength;
            b.uniform("gap_px") = gapPx;
            b.uniform("scan_phase") = scanPhase;

            b.child("src") = srcChild;

            sk_sp<SkImage> prevForEffect = prevImage;
            if (halfRes) {
                sk_sp<SkImage> halfPrev = makeHalfResNearest(dstSurface, prevImage, debugLog, "GammaOS LCD(prev)");
                if (halfPrev) prevForEffect = halfPrev;
            }
            sk_sp<SkShader> prevChild = prevForEffect->makeShader(
                    SkSamplingOptions(SkFilterMode::kNearest, SkMipmapMode::kNone));
            if (halfRes && prevForEffect != prevImage && prevChild) {
                prevChild = prevChild->makeWithLocalMatrix(SkMatrix::Scale(2.0f, 2.0f));
            }
            b.child("prev") = prevChild;

            paint.setShader(b.makeShader());
            paint.setBlendMode(SkBlendMode::kSrc);
        } else {
            SkRuntimeShaderBuilder b(gFxNoPrev);
            b.uniform("scan_strength") = scanStrength;
            b.uniform("subpixel_strength") = subpixelStrength;
            b.uniform("gap_strength") = gapStrength;
            b.uniform("gap_px") = gapPx;
            b.uniform("scan_phase") = scanPhase;

            b.child("src") = srcChild;
            paint.setShader(b.makeShader());
            paint.setBlendMode(SkBlendMode::kSrc);
        }
    } else {
        // Do not sample protected content. Apply only the mask as a multiply over current dst.
        SkRuntimeShaderBuilder b(gFxNoPrev);
        b.uniform("scan_strength") = scanStrength;
        b.uniform("subpixel_strength") = subpixelStrength;
        b.uniform("gap_strength") = gapStrength;
        b.uniform("gap_px") = gapPx;
        b.uniform("scan_phase") = scanPhase;

        b.child("src") = SkShaders::Color(SkColors::kWhite, toSkColorSpace(outDataspace));
        paint.setShader(b.makeShader());
        paint.setBlendMode(SkBlendMode::kMultiply);

        // Never retain history from protected content.
        std::lock_guard<std::mutex> lk(gPrevMutex);
        gPrevImage.reset();
        gPrevW = gPrevH = 0;
    }

    dstCanvas->save();
    dstCanvas->resetMatrix();
    dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), paint);
    dstCanvas->restore();

    if (debugLog) {
        ALOGD("GammaOS LCD: applied (rt=%.3f scan=%.3f sub=%.3f gap=%.3f gap_px=%.3f phase=%.1f half=%d prot=%d)",
              responseTime, scanStrength, subpixelStrength, gapStrength, gapPx, scanPhase,
              halfRes ? 1 : 0, isProtected ? 1 : 0);
    }

    return true;
}

} // namespace skia
} // namespace renderengine
} // namespace android
