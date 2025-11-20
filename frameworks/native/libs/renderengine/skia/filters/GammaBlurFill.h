#pragma once
#include <SkSurface.h>
#include <ui/GraphicTypes.h>

namespace android {
namespace renderengine {
namespace skia {

class SkiaCapture;

// Blur-fill border shader: detects letterbox/pillarbox style borders and
// fills them by sampling a blurred version of the source content. Inspired
// by RetroArch "blur_fill" border shaders, but implemented as a single SkSL
// runtime effect pass.
struct GammaBlurFill {
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
