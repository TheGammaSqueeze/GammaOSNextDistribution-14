#include "GammaLcd3x.h"

#include <SkCanvas.h>
#include <SkColor.h>
#include <SkColorSpace.h>
#include <SkImage.h>
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
    uniform int    use_src;             // 1=sample src, 0=mask-only (white)

    uniform float  brighten_scanlines;  // >= 1.0
    uniform float  brighten_lcd;        // >= 1.0
    uniform float2 grid_px;             // pixel size of LCD cell (x,y)
    uniform float2 fb_size;             // framebuffer size (w,h) (reserved, not used now)

    const float PI = 3.141592654;

    half4 main(float2 p) {
        // Base color: either sampled source or white mask for protected content.
        half4 base = use_src != 0 ? src.eval(p) : half4(1.0);

        // Grid period in pixels. Higher grid_px => larger cells on screen.
        float2 cell  = max(grid_px, float2(1.0, 1.0));
        float2 omega = (2.0 * PI) / cell;
        float2 angle = p * omega;

        // Vertical scanline modulation.
        float yfactor = (brighten_scanlines + sin(angle.y)) / (brighten_scanlines + 1.0);

        // Horizontal RGB column modulation.
        float3 offsets = float3(PI * 0.5, PI * 1.5, PI * 2.5);
        float3 xfactors = (brighten_lcd + sin(angle.x + offsets)) / (brighten_lcd + 1.0);

        float3 color = float3(base.rgb) * yfactor * xfactors;
        return half4(color, base.a);
    }
)";

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
    b.uniform("fb_size") = SkV2{
            static_cast<float>(dstSurface->width()),
            static_cast<float>(dstSurface->height())};

    SkPaint p;
    if (!isProtected) {
        sk_sp<SkImage> srcImage = srcSurface->makeImageSnapshot();
        if (!srcImage) {
            if (debugLog) ALOGW("GammaOS LCD3x: snapshot failed; skipping effect.");
            return false;
        }
        b.child("src") = srcImage->makeShader(
                SkSamplingOptions(SkFilterMode::kNearest, SkMipmapMode::kNone));
        b.uniform("use_src") = 1;
        p.setShader(b.makeShader());
        p.setBlendMode(SkBlendMode::kSrc);
    } else {
        // Avoid sampling protected content; just apply a mask over what is already in dst.
        b.child("src") = SkShaders::Color(SkColors::kWhite, toSkColorSpace(outDataspace));
        b.uniform("use_src") = 0;
        p.setShader(b.makeShader());
        p.setBlendMode(SkBlendMode::kMultiply);
    }

    dstCanvas->save();
    dstCanvas->resetMatrix();
    dstCanvas->drawRect(SkRect::MakeWH(dstSurface->width(), dstSurface->height()), p);
    dstCanvas->restore();

    if (debugLog) {
        ALOGD("GammaOS LCD3x: applied post-pass (brighten_scanlines=%.3f, brighten_lcd=%.3f, grid_px=(%.3f,%.3f))",
              brightenScanlines, brightenLcd, gridPxX, gridPxY);
    }

    return true;
}

} // namespace skia
} // namespace renderengine
} // namespace android