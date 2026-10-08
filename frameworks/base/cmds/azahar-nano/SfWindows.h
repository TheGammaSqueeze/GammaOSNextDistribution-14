// SfWindows: the SurfaceFlinger backend's panels for azahar-nano.
//
// One opaque layer per physical display, sized to the display's logical
// (framework-projected) space, handed to the core as plain ANativeWindows. The
// core's own Vulkan or GL renderer presents into them; SurfaceFlinger scans them
// out on hardware pipes (one layer per panel keeps them there).
#pragma once

#include <cstdint>
#include <vector>

#include <binder/IBinder.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>
#include <utils/StrongPointer.h>

struct ANativeWindow;

namespace azahar_nano {

struct SfPanel {
    android::sp<android::IBinder> token;
    android::sp<android::SurfaceControl> control;
    android::sp<android::Surface> surface;
    uint32_t width = 0;
    uint32_t height = 0;
    uint32_t layerStack = 0;
};

class SfWindows {
public:
    // Brings up the composer client and one layer per physical display
    // (primary = the port named by persist.gammaos.primary_port when set).
    // Returns the number of panels (0 on failure).
    int create();
    void teardown();

    int count() const { return (int)mPanels.size(); }
    ANativeWindow* window(int idx) const;
    uint32_t width(int idx) const { return mPanels[idx].width; }
    uint32_t height(int idx) const { return mPanels[idx].height; }

private:
    bool createPanel(const android::PhysicalDisplayId& id, SfPanel* out);

    android::sp<android::SurfaceComposerClient> mSession;
    std::vector<SfPanel> mPanels;
};

} // namespace azahar_nano
