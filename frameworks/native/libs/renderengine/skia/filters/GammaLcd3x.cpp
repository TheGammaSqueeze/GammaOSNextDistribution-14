#include "GammaLcd3x.h"

#include <SkCanvas.h>
#include <SkColor.h>
#include <SkColorSpace.h>
#include <SkImage.h>
#include <SkMatrix.h>
#include <SkPaint.h>
#include <SkRuntimeEffect.h>
#include <android-base/properties.h>
#include <mutex>
#include <algorithm>
#include <cmath>
#include <log/log.h>

#include "../ColorSpaces.h"
#include "../debug/SkiaCapture.h"

namespace android {
namespace renderengine {
namespace skia {

namespace {

static std::once_flag gFxOnce;
static sk_sp<SkRuntimeEffect> gFx;

// Simple LCD-style subpixel grid + scanline modulation, adapted from RetroArch's lcd3x shader.
// Adds configurable scanline and RGB column brightening plus grid scaling for high-DPI panels.
static const char* kSkSL = R"(
    uniform shader src;

    uniform float  brighten_scanlines;  // >= 1.0
    uniform float  brighten_lcd;        // >= 1.0
    uniform float2 grid_px;             // pixel size of LCD cell (x,y)

    const float PI = 3.141592654;
    const float TWO_PI = 6.283185307;

    half4 main(float2 p) {
        // Base color: src is either the real content or a constant-color mask for protected content.
        half4 base = src.eval(p);

        // Grid period in pixels. Higher grid_px => larger cells on screen.
        float2 cell = max(grid_px, float2(1.0, 1.0));
        float2 omega = TWO_PI / cell;
        float2 angle = p * omega;

        // Vertical scanline modulation.
        float invScanDen = 1.0 / (brighten_scanlines + 1.0);
        float y = (brighten_scanlines + sin(angle.y)) * invScanDen;

        // Horizontal RGB column modulation.
        // Offsets were {pi/2, 3pi/2, 5pi/2}, so:
        //   sin(x + pi/2)  =  cos(x)
        //   sin(x + 3pi/2) = -cos(x)
        //   sin(x + 5pi/2) =  cos(x)
        // This reduces 3 trig evaluations down to 1.
        float invLcdDen = 1.0 / (brighten_lcd + 1.0);
        float cx = cos(angle.x);
        float3 x = (brighten_lcd + float3(cx, -cx, cx)) * invLcdDen;

        half3 outRgb = base.rgb * half(y) * half3(x);
        return half4(outRgb, base.a);
    }
)";
 
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

inline float getFloatProp(const char* key, const char* defv) {
    using android::base::GetProperty;
    const std::string s = GetProperty(key, defv);
    return static_cast<float>(atof(s.c_str()));
}

} // anonymous namespace

bool GammaLcd3x::apply(SkSurface* dstSurface,
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

    const bool debugLog = GetBoolProperty("persist.gammaos.shader.debug", false);

    SkCanvas* dstCanvas = capture->tryCapture(dstSurface);
    if (!dstCanvas) {
        if (debugLog) ALOGE("GammaOS LCD3x: no canvas at post-process; skipping.");
        return false;
    }

    // Optional debug overlay: 50% gray multiply.
    const bool testOverlayForce = GetBoolProperty("persist.gammaos.shader.test_overlay", false);
    if (testOverlay || testOverlayForce) {
        if (debugLog) ALOGD("GammaOS LCD3x: test_overlay active (multiply 50%% gray).");
        SkPaint p;
        p.setColor(SkColorSetARGB(255, 128, 128, 128));
        p.setBlendMode(SkBlendMode::kMultiply);
        dstCanvas->save();
        dstCanvas->resetMatrix();
        dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
        dstCanvas->restore();
        return true;
    }

    (void)ctmBfiBlack; // Unused in this shader.

    std::call_once(gFxOnce, [&]{
        auto pair = SkRuntimeEffect::MakeForShader(SkString(kSkSL));
        gFx = pair.effect;
        if (!gFx && debugLog) {
            ALOGE("GammaOS LCD3x: SkSL compile failed: %s", pair.errorText.c_str());
        }
    });
    if (!gFx) return false;

    // Global shader gating and per-type selection.
    const bool shaderOn = GetBoolProperty("persist.gammaos.shader.enable", false);
    const std::string type = GetProperty("persist.gammaos.shader.type", "crt-simple");
    if (!shaderOn || type != "lcd3x") {
        if (debugLog) {
            ALOGD("GammaOS LCD3x: disabled or type mismatch (type=%s, on=%d).",
                  type.c_str(), shaderOn ? 1 : 0);
        }
        return false;
    }
 
    const bool halfRes = GetBoolProperty("persist.gammaos.shader.lcd3x.half_res", false);

    // LCD3x parameters with sane defaults, clamped to useful ranges.
    float brightenScanlines = std::max(1.0f,
            getFloatProp("persist.gammaos.shader.lcd3x.brighten_scanlines", "4.0"));
    float brightenLcd = std::max(1.0f,
            getFloatProp("persist.gammaos.shader.lcd3x.brighten_lcd", "4.0"));

    // Grid size in output pixels: higher values = larger LCD cells on high-DPI panels.
    float gridPxX = std::max(1.0f,
            getFloatProp("persist.gammaos.shader.lcd3x.grid_px_x", "4.0"));
    float gridPxY = std::max(1.0f,
            getFloatProp("persist.gammaos.shader.lcd3x.grid_px_y", "4.0"));

    SkRuntimeShaderBuilder b(gFx);
    b.uniform("brighten_scanlines") = brightenScanlines;
    b.uniform("brighten_lcd") = brightenLcd;
    b.uniform("grid_px") = SkV2{gridPxX, gridPxY};

    SkPaint p;
    if (!isProtected) {
        sk_sp<SkImage> srcImage = srcSurface->makeImageSnapshot();
        if (!srcImage) {
            if (debugLog) ALOGW("GammaOS LCD3x: snapshot failed; skipping effect.");
            return false;
        }

        sk_sp<SkImage> srcForEffect = srcImage;
        if (halfRes) {
            sk_sp<SkImage> half = makeHalfResNearest(dstSurface, srcImage, debugLog, "GammaOS LCD3x");
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
        // Avoid sampling protected content; just apply a mask over what is already in dst.
        b.child("src") = SkShaders::Color(SkColors::kWhite, toSkColorSpace(outDataspace));
        p.setShader(b.makeShader());
        p.setBlendMode(SkBlendMode::kMultiply);
    }

    dstCanvas->save();
    dstCanvas->resetMatrix();
    dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
    dstCanvas->restore();

    if (debugLog) {
        ALOGD("GammaOS LCD3x: applied post-pass (half_res=%d brighten_scanlines=%.3f brighten_lcd=%.3f grid_px=(%.3f,%.3f))",
              halfRes ? 1 : 0, brightenScanlines, brightenLcd, gridPxX, gridPxY);
    }

    return true;
}

} // namespace skia
} // namespace renderengine
} // namespace android