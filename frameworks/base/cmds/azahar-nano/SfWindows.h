// SfWindows: the SurfaceFlinger backend's panels for azahar-nano.
//
// One opaque layer per physical display, sized to the display's logical
// (framework-projected) space, handed to the core as plain ANativeWindows. The
// core's own Vulkan or GL renderer presents into them; SurfaceFlinger scans them
// out on hardware pipes (one layer per panel keeps them there).
//
// The in-game menu and the HUD get their own layers over the game on the first
// panel, the way the DRM presenter gives them their own plane: the overlay
// layer is opaque and shown in place of the game while the menu is open, the
// HUD layer is alpha blended and shown while a counter or notice is up.
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
    android::sp<android::SurfaceControl> ovControl;
    android::sp<android::Surface> ovSurface;
    android::sp<android::SurfaceControl> hudControl;
    android::sp<android::Surface> hudSurface;
    uint32_t width = 0;
    uint32_t height = 0;
    uint32_t layerStack = 0;
};

class SfWindows {
public:
    // Brings up the composer client and one layer per physical display. The
    // first panel is the one on the port named by persist.gammaos.primary_port,
    // else by persist.gammaos.nano.primary_display (the top screen on the dual
    // panel handhelds, the same property the home uses), else the first display.
    // Returns the number of panels (0 on failure).
    int create();
    void teardown();

    int count() const { return (int)mPanels.size(); }
    ANativeWindow* window(int idx) const;
    uint32_t width(int idx) const { return mPanels[idx].width; }
    uint32_t height(int idx) const { return mPanels[idx].height; }

    // The menu's window on a panel (created on first use, hidden until active).
    ANativeWindow* overlayWindow(int idx);
    void setOverlayActive(int idx, bool active);
    // The HUD's window on a panel: an alpha blended layer the panel's size.
    ANativeWindow* hudWindow(int idx, uint32_t* w, uint32_t* h);
    void setHudActive(int idx, bool active);

private:
    bool createPanel(const android::PhysicalDisplayId& id, SfPanel* out);
    android::sp<android::SurfaceControl> createLayer(const SfPanel& p, const char* name,
                                                     int format, uint32_t flags, int32_t z);

    android::sp<android::SurfaceComposerClient> mSession;
    std::vector<SfPanel> mPanels;
};

} // namespace azahar_nano
