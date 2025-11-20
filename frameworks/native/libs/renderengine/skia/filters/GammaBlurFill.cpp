#include "GammaBlurFill.h"

#include <SkCanvas.h>
#include <SkColor.h>
#include <SkColorSpace.h>
#include <SkImage.h>
#include <SkImageFilters.h>
#include <SkPaint.h>
#include <SkRuntimeEffect.h>
#include <SkSurface.h>
#include <android-base/properties.h>
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <mutex>
#include <string>
#include <log/log.h>

#include "../ColorSpaces.h"
#include "../debug/SkiaCapture.h"

namespace android {
namespace renderengine {
namespace skia {

using android::base::GetBoolProperty;
using android::base::GetProperty;

namespace {

// Simple float property helper with clamping.
float getFloatProp(const char* key, const char* defv, float lo, float hi) {
    const std::string s = GetProperty(key, defv);
    char* endp = nullptr;
    float v = std::strtof(s.c_str(), &endp);
    if (endp == s.c_str()) {
        v = std::strtof(defv, nullptr);
    }
    if (std::isnan(v) || std::isinf(v)) {
        v = std::strtof(defv, nullptr);
    }
    if (v < lo) v = lo;
    if (v > hi) v = hi;
    return v;
}

// SkSL: blur-fill in a fixed-width band along the screen edges,
// but only on pixels that are dark enough to look like a border.
static const char kSkSL[] = R"SkSL(
uniform shader src;
uniform shader blur;
uniform float2 wh;              // [width, height] in px (full-res)
uniform float2 blur_wh;         // [width, height] of blur buffer
uniform float edge_px;          // width of the border band in px
uniform float feather_px;       // inner soft transition in px
uniform float strength;         // 0..1 overall intensity
uniform float orientation;      // 0: auto, 1: vertical, 2: horizontal
uniform float luma_threshold;   // <= threshold considered "border black"

half4 main(float2 p) {
    half4 c = src.eval(p);

    // Luma of the current pixel.
    float y = dot(float3(c.rgb), float3(0.2126, 0.7152, 0.0722));

    // Distances to each edge.
    float dl = p.x;
    float dr = wh.x - 1.0 - p.x;
    float dt = p.y;
    float db = wh.y - 1.0 - p.y;

    float band = max(edge_px, 0.0);
    float feather = clamp(feather_px, 0.0, band);

    float edgeStart = feather;  // full strength from edge to 'feather'
    float edgeEnd   = band;     // fade to 0 between feather..band

    float maskLeft  = 0.0;
    float maskRight = 0.0;
    float maskTop   = 0.0;
    float maskBottom= 0.0;

    if (band > 0.0) {
        // 1.0 from [0, edgeStart], then smooth falloff to 0 at edgeEnd.
        maskLeft   = 1.0 - smoothstep(edgeStart, edgeEnd, dl);
        maskRight  = 1.0 - smoothstep(edgeStart, edgeEnd, dr);
        maskTop    = 1.0 - smoothstep(edgeStart, edgeEnd, dt);
        maskBottom = 1.0 - smoothstep(edgeStart, edgeEnd, db);
    }

    // Combine sides into vertical vs horizontal bands.
    float borderX = max(maskLeft, maskRight);   // left/right band
    float borderY = max(maskTop, maskBottom);   // top/bottom band

    float borderMask;
    float useVertical;
    if (orientation == 1.0) {
        // Force pillarbox: left/right only.
        borderMask = borderX;
        useVertical = 1.0;
    } else if (orientation == 2.0) {
        // Force letterbox: top/bottom only.
        borderMask = borderY;
        useVertical = 0.0;
    } else {
        // Auto: pick whichever band is stronger for this pixel.
        if (borderX >= borderY) {
            borderMask = borderX;
            useVertical = 1.0;
        } else {
            borderMask = borderY;
            useVertical = 0.0;
        }
    }

    // Dark-only gating: only treat genuinely dark pixels as borders.
    // Use a small ramp above luma_threshold to avoid harsh steps.
    float dark = 1.0 - smoothstep(luma_threshold,
                                  luma_threshold + 0.05, // 5% luma ramp
                                  y);

    float border = borderMask * dark * strength;
    if (border <= 0.0) {
        return c;
    }

    // Clamp sampling direction so we only pull from the interior, never from
    // outside the content; this avoids "triangles" and gaps.
    float2 q = p;
    if (useVertical > 0.5) {
        // Pillarbox: clamp along X into content region.
        q.x = clamp(q.x, edge_px, wh.x - 1.0 - edge_px);
    } else {
        // Letterbox: clamp along Y into content region.
        q.y = clamp(q.y, edge_px, wh.y - 1.0 - edge_px);
    }

    // Map to the downscaled blur buffer.
    float2 scale = blur_wh / wh;
    half4 cb = blur.eval(q * scale);

    return mix(c, cb, border);
}
)SkSL";

std::once_flag gFxOnce;
sk_sp<SkRuntimeEffect> gFx;

} // anonymous namespace

bool GammaBlurFill::apply(SkSurface* dstSurface,
                          SkSurface* srcSurface,
                          SkiaCapture* capture,
                          ui::Dataspace /*outDataspace*/,
                          bool isProtected,
                          bool testOverlay,
                          bool /*ctmBfiBlack*/,
                          float /*defaultScanAngleDeg*/) {
    if (!dstSurface || !srcSurface || !capture) {
        return false;
    }

    const bool debugLog = GetBoolProperty("persist.gammaos.shader.debug", false);

    SkCanvas* dstCanvas = capture->tryCapture(dstSurface);
    if (!dstCanvas) {
        if (debugLog) {
            ALOGE("GammaOS BlurFill: no canvas at post-process; skipping.");
        }
        return false;
    }

    const bool shaderOn = GetBoolProperty("persist.gammaos.shader.enable", false);
    const std::string type = GetProperty("persist.gammaos.shader.type", "crt-simple");

    const bool typeMatches =
            (type == "blur-fill") || (type == "blurfill") || (type == "blur_fill");

    if (!shaderOn || !typeMatches) {
        if (debugLog) {
            ALOGD("GammaOS BlurFill: disabled or type mismatch (type=%s, on=%d).",
                  type.c_str(), shaderOn ? 1 : 0);
        }
        return false;
    }

    const bool testOverlayForce = GetBoolProperty("persist.gammaos.shader.test_overlay", false);
    if (testOverlay || testOverlayForce) {
        if (debugLog) {
            ALOGD("GammaOS BlurFill: test_overlay active (multiply 50%% gray).");
        }
        SkPaint p;
        p.setColor(SkColorSetARGB(255, 128, 128, 128));
        p.setBlendMode(SkBlendMode::kMultiply);
        dstCanvas->save();
        dstCanvas->resetMatrix();
        dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
        dstCanvas->restore();
        return true;
    }

    if (isProtected) {
        if (debugLog) {
            ALOGD("GammaOS BlurFill: content protected; skipping effect.");
        }
        return false;
    }

    std::call_once(gFxOnce, [&] {
        auto pair = SkRuntimeEffect::MakeForShader(SkString(kSkSL));
        gFx = pair.effect;
        if (!gFx) {
            ALOGE("GammaOS BlurFill: SkSL compile failed: %s", pair.errorText.c_str());
        }
    });
    if (!gFx) {
        return false;
    }

    // Snapshot source surface.
    sk_sp<SkImage> srcImage = srcSurface->makeImageSnapshot();
    if (!srcImage) {
        if (debugLog) {
            ALOGW("GammaOS BlurFill: snapshot failed; skipping effect.");
        }
        return false;
    }

    const int srcW = srcImage->width();
    const int srcH = srcImage->height();

    // Blur parameters.
    const float sigma = getFloatProp(
            "persist.gammaos.shader.blurfill.sigma", "12.0", 0.1f, 64.0f);
    const float blurScale = getFloatProp(
            "persist.gammaos.shader.blurfill.blur_scale", "1.0", 0.25f, 4.0f);
    const float sigmaEff = sigma * blurScale;

    const float edgePx = getFloatProp(
            "persist.gammaos.shader.blurfill.edge_px", "160.0", 1.0f, 4096.0f);
    const float featherPx = getFloatProp(
            "persist.gammaos.shader.blurfill.feather_px", "40.0", 0.0f, 1024.0f);

    const float strength = getFloatProp(
            "persist.gammaos.shader.blurfill.strength", "1.0", 0.0f, 1.0f);

    // Resolution scale for the blur buffer (downscale factor).
    const float resScale = getFloatProp(
            "persist.gammaos.shader.blurfill.res_scale", "0.5", 0.1f, 1.0f);

    const float lumaThreshold = getFloatProp(
            "persist.gammaos.shader.blurfill.luma_threshold", "0.06", 0.0f, 0.3f);

    const std::string orientStr =
            GetProperty("persist.gammaos.shader.blurfill.orientation", "auto");
    float orientationUniform = 0.0f;
    if (orientStr == "vertical" || orientStr == "pillar" || orientStr == "v") {
        orientationUniform = 1.0f;
    } else if (orientStr == "horizontal" || orientStr == "letterbox" || orientStr == "h") {
        orientationUniform = 2.0f;
    } else {
        orientationUniform = 0.0f; // auto
    }

    // If strength or edge width is zero, nothing to do.
    if (strength <= 0.0f || edgePx <= 0.0f) {
        if (debugLog) {
            ALOGD("GammaOS BlurFill: strength or edgePx is zero; skipping.");
        }
        return false;
    }

    int blurW = std::max(1, static_cast<int>(std::round(srcW * resScale)));
    int blurH = std::max(1, static_cast<int>(std::round(srcH * resScale)));
    blurW = std::min(blurW, srcW);
    blurH = std::min(blurH, srcH);

    if (debugLog) {
        ALOGD("GammaOS BlurFill: sigma=%.3f blurScale=%.3f (eff=%.3f) "
              "edgePx=%.3f featherPx=%.3f strength=%.3f "
              "resScale=%.3f lumaThreshold=%.3f orientation=%s src=%dx%d blur=%dx%d",
              sigma, blurScale, sigmaEff,
              edgePx, featherPx, strength,
              resScale, lumaThreshold, orientStr.c_str(), srcW, srcH, blurW, blurH);
    }

    // Build a downscaled blurred copy of the source.
    SkImageInfo blurInfo = srcImage->imageInfo();
    if (blurInfo.colorType() == kUnknown_SkColorType) {
        blurInfo = SkImageInfo::MakeN32(srcW, srcH, kPremul_SkAlphaType);
    }
    blurInfo = blurInfo.makeWH(blurW, blurH);

    sk_sp<SkSurface> blurSurface = dstSurface->makeSurface(blurInfo);
    if (!blurSurface) {
        blurSurface = srcSurface->makeSurface(blurInfo);
    }
    if (!blurSurface) {
        blurSurface = SkSurfaces::Raster(blurInfo);
    }
    if (!blurSurface) {
        if (debugLog) {
            ALOGW("GammaOS BlurFill: failed to create blur surface.");
        }
        return false;
    }

    SkCanvas* blurCanvas = blurSurface->getCanvas();
    blurCanvas->clear(SK_ColorBLACK);

    SkPaint blurPaint;
    blurPaint.setBlendMode(SkBlendMode::kSrc);
    blurPaint.setImageFilter(
            SkImageFilters::Blur(sigmaEff, sigmaEff, SkTileMode::kClamp, nullptr));

    SkRect srcRect = SkRect::MakeWH(static_cast<SkScalar>(srcW),
                                    static_cast<SkScalar>(srcH));
    SkRect dstRect = SkRect::MakeWH(static_cast<SkScalar>(blurW),
                                    static_cast<SkScalar>(blurH));

    blurCanvas->drawImageRect(srcImage.get(),
                              srcRect,
                              dstRect,
                              SkSamplingOptions{SkFilterMode::kLinear, SkMipmapMode::kNone},
                              &blurPaint,
                              SkCanvas::kStrict_SrcRectConstraint);

    sk_sp<SkImage> blurImage = blurSurface->makeImageSnapshot();
    if (!blurImage) {
        if (debugLog) {
            ALOGW("GammaOS BlurFill: blur snapshot failed.");
        }
        return false;
    }

    SkRuntimeShaderBuilder builder(gFx);
    builder.child("src") = srcImage->makeShader(
            SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNone));
    builder.child("blur") = blurImage->makeShader(
            SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNone));

    const float w = static_cast<float>(dstSurface->width());
    const float h = static_cast<float>(dstSurface->height());
    builder.uniform("wh") = SkV2{w, h};
    builder.uniform("blur_wh") = SkV2{
            static_cast<float>(blurW),
            static_cast<float>(blurH)};
    builder.uniform("edge_px") = edgePx;
    builder.uniform("feather_px") = featherPx;
    builder.uniform("strength") = strength;
    builder.uniform("orientation") = orientationUniform;
    builder.uniform("luma_threshold") = lumaThreshold;

    SkPaint p;
    p.setShader(builder.makeShader());
    p.setBlendMode(SkBlendMode::kSrc);

    dstCanvas->save();
    dstCanvas->resetMatrix();
    dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
    dstCanvas->restore();

    if (debugLog) {
        ALOGD("GammaOS BlurFill: applied post-pass.");
    }
    return true;
}

} // namespace skia
} // namespace renderengine
} // namespace android
