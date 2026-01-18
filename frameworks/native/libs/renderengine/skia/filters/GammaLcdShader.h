#pragma once

#include <SkSurface.h>
#include <ui/GraphicTypes.h>

namespace android {
namespace renderengine {
namespace skia {

class SkiaCapture;

// GammaOS LCD shader (single-pass) with optional LCD response-time emulation.
//
// This is intended as a very low-overhead post-process that:
//  - preserves nearest-neighbor sampling (no bilinear),
//  - optionally blends with the previous frame to emulate LCD pixel response,
//  - applies a simple scanline + RGB stripe mask.
struct GammaLcdShader {
    // Returns true if an effect was drawn, false otherwise.
    static bool apply(SkSurface* dstSurface,
                      SkSurface* srcSurface,
                      SkiaCapture* capture,
                      ui::Dataspace outDataspace,
                      bool isProtected,
                      bool testOverlay,
                      bool ctmBfiBlack,
                      float defaultScanAngleDeg);
};

} // namespace skia
} // namespace renderengine
} // namespace android
