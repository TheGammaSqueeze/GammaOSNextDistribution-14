#pragma once
#include <SkSurface.h>
#include <ui/GraphicTypes.h>

namespace android {
namespace renderengine {
namespace skia {

class SkiaCapture;

struct GammaLcd3x {
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