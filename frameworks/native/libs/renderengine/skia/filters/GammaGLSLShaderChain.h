#pragma once
#include <SkSurface.h>
#include <ui/GraphicTypes.h>

namespace android {
namespace renderengine {
namespace skia {

class SkiaCapture;

struct GammaGLSLShaderChain {
    // Returns true if an effect was drawn, false if skipped.
    // Loads .glslp presets with native GLSL shaders — no SPIR-V transpilation.
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
