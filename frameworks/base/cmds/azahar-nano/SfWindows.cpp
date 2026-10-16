#define LOG_TAG "azahar-nano"

#include "SfWindows.h"

#include <cstring>

#include <android/native_window.h>
#include <binder/ProcessState.h>
#include <cutils/properties.h>
#include <gui/ISurfaceComposer.h>
#include <log/log.h>
#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>
#include <ui/Rotation.h>

using namespace android;

namespace azahar_nano {

bool SfWindows::createPanel(const PhysicalDisplayId& id, SfPanel* out) {
    out->token = SurfaceComposerClient::getPhysicalDisplayToken(id);
    if (out->token == nullptr) return false;

    ui::DisplayState state;
    ui::LayerStack stack = ui::DEFAULT_LAYER_STACK;
    const bool haveState =
            SurfaceComposerClient::getDisplayState(out->token, &state) == NO_ERROR;
    if (haveState) stack = state.layerStack;
    out->layerStack = stack.id;

    ui::DisplayMode mode;
    if (SurfaceComposerClient::getActiveDisplayMode(out->token, &mode) != NO_ERROR) {
        ALOGE("SF getActiveDisplayMode failed");
        return false;
    }
    const int panelW = mode.resolution.getWidth();
    const int panelH = mode.resolution.getHeight();

    // Render at the framework's logical size and inherit its projection (see
    // drastic-nano's SfDisplayBackend for the reasoning); impose one only when
    // SurfaceFlinger never projected this display.
    int logicalW = 0, logicalH = 0;
    bool inherit = false;
    ui::Rotation rot = ui::ROTATION_0;
    if (haveState && state.layerStackSpaceRect.getWidth() > 0 &&
        state.layerStackSpaceRect.getHeight() > 0) {
        logicalW = state.layerStackSpaceRect.getWidth();
        logicalH = state.layerStackSpaceRect.getHeight();
        inherit = true;
    } else {
        char o[PROPERTY_VALUE_MAX] = {};
        property_get("ro.surface_flinger.primary_display_orientation", o, "");
        if (!strcmp(o, "ORIENTATION_90")) rot = ui::ROTATION_90;
        else if (!strcmp(o, "ORIENTATION_180")) rot = ui::ROTATION_180;
        else if (!strcmp(o, "ORIENTATION_270")) rot = ui::ROTATION_270;
        const bool swap = (rot == ui::ROTATION_90 || rot == ui::ROTATION_270);
        logicalW = swap ? panelH : panelW;
        logicalH = swap ? panelW : panelH;
    }
    out->width = (uint32_t)logicalW;
    out->height = (uint32_t)logicalH;
    ALOGI("SF panel logical %dx%d panel %dx%d layerStack %u (%s projection)", logicalW,
          logicalH, panelW, panelH, out->layerStack, inherit ? "inherit" : "impose");

    out->control = mSession->createSurface(String8("azahar-nano"), logicalW, logicalH,
                                           PIXEL_FORMAT_RGBX_8888,
                                           ISurfaceComposerClient::eOpaque);
    if (out->control == nullptr || !out->control->isValid()) {
        ALOGE("SF createSurface failed");
        return false;
    }
    SurfaceComposerClient::Transaction t;
    if (!inherit) {
        t.setDisplayProjection(out->token, rot, Rect(0, 0, logicalW, logicalH),
                               Rect(0, 0, panelW, panelH));
    }
    t.setLayer(out->control, 0x40000002);
    t.setLayerStack(out->control, stack);
    t.setFrameRate(out->control, 60.0f, ANATIVEWINDOW_FRAME_RATE_COMPATIBILITY_DEFAULT,
                   ANATIVEWINDOW_CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
    t.show(out->control);
    t.apply();

    out->surface = out->control->getSurface();
    out->surface->setMaxDequeuedBufferCount(3);
    return true;
}

int SfWindows::create() {
    ProcessState::self()->startThreadPool();
    mSession = new SurfaceComposerClient();
    if (mSession->initCheck() != NO_ERROR) {
        ALOGE("SurfaceComposerClient init failed");
        return 0;
    }
    std::vector<PhysicalDisplayId> ids = SurfaceComposerClient::getPhysicalDisplayIds();
    if (ids.empty()) {
        ALOGE("no physical displays");
        return 0;
    }
    // Primary first: persist.gammaos.primary_port names its connector port; without it
    // persist.gammaos.nano.primary_display does (the home's top screen: port 1 on the
    // RG DS, where SurfaceFlinger's display 0 is the bottom panel).
    char portProp[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.primary_port", portProp, "");
    if (!portProp[0]) property_get("persist.gammaos.nano.primary_display", portProp, "");
    if (portProp[0]) {
        const long port = strtol(portProp, nullptr, 10);
        for (size_t i = 0; i < ids.size(); i++) {
            if ((long)ids[i].getPort() == port) {
                if (i != 0) std::swap(ids[0], ids[i]);
                ALOGI("SF primary panel: port %ld", port);
                break;
            }
        }
    }
    for (const auto& id : ids) {
        SfPanel p;
        if (createPanel(id, &p)) {
            mPanels.push_back(std::move(p));
        }
        if (mPanels.size() == 2) break;
    }
    ALOGI("SF backend up: %zu panel(s)", mPanels.size());
    return (int)mPanels.size();
}

ANativeWindow* SfWindows::window(int idx) const {
    if (idx < 0 || idx >= (int)mPanels.size()) return nullptr;
    return mPanels[idx].surface.get();
}

sp<SurfaceControl> SfWindows::createLayer(const SfPanel& p, const char* name, int format,
                                          uint32_t flags, int32_t z) {
    sp<SurfaceControl> sc = mSession->createSurface(String8(name), p.width, p.height, format, flags);
    if (sc == nullptr || !sc->isValid()) {
        ALOGE("SF createSurface(%s) failed", name);
        return nullptr;
    }
    SurfaceComposerClient::Transaction t;
    t.setLayer(sc, z);
    t.setLayerStack(sc, ui::LayerStack::fromValue(p.layerStack));
    t.hide(sc);
    t.apply();
    return sc;
}

ANativeWindow* SfWindows::overlayWindow(int idx) {
    if (idx < 0 || idx >= (int)mPanels.size()) return nullptr;
    SfPanel& p = mPanels[idx];
    if (p.ovSurface == nullptr) {
        // Opaque like the game layer under it: the menu replaces the game picture
        // on this panel while it is open, the way the DRM presenter switches sources.
        p.ovControl = createLayer(p, idx == 0 ? "azahar-nano-menu-top" : "azahar-nano-menu-bottom",
                                  PIXEL_FORMAT_RGBX_8888, ISurfaceComposerClient::eOpaque,
                                  0x40000003);
        if (p.ovControl == nullptr) return nullptr;
        p.ovSurface = p.ovControl->getSurface();
        p.ovSurface->setMaxDequeuedBufferCount(2);
    }
    return p.ovSurface.get();
}

void SfWindows::setOverlayActive(int idx, bool active) {
    if (idx < 0 || idx >= (int)mPanels.size()) return;
    SfPanel& p = mPanels[idx];
    if (p.ovControl == nullptr) return;
    SurfaceComposerClient::Transaction t;
    if (active) t.show(p.ovControl);
    else t.hide(p.ovControl);
    t.apply();
}

ANativeWindow* SfWindows::hudWindow(int idx, uint32_t* w, uint32_t* h) {
    if (idx < 0 || idx >= (int)mPanels.size()) return nullptr;
    SfPanel& p = mPanels[idx];
    if (p.hudSurface == nullptr) {
        // Alpha blended over the game (and over the menu, which draws no HUD).
        p.hudControl = createLayer(p, idx == 0 ? "azahar-nano-hud-top" : "azahar-nano-hud-bottom",
                                   PIXEL_FORMAT_RGBA_8888, 0, 0x40000004);
        if (p.hudControl == nullptr) return nullptr;
        p.hudSurface = p.hudControl->getSurface();
        p.hudSurface->setMaxDequeuedBufferCount(2);
        // The HUD drawer must not block forever behind a full queue while the
        // composer is idle (the core paused, nothing else changing on screen).
        p.hudSurface->setDequeueTimeout(50 * 1000 * 1000);
    }
    if (w) *w = p.width;
    if (h) *h = p.height;
    return p.hudSurface.get();
}

void SfWindows::setHudActive(int idx, bool active) {
    if (idx < 0 || idx >= (int)mPanels.size()) return;
    SfPanel& p = mPanels[idx];
    if (p.hudControl == nullptr) return;
    SurfaceComposerClient::Transaction t;
    if (active) t.show(p.hudControl);
    else t.hide(p.hudControl);
    t.apply();
}

void SfWindows::teardown() {
    for (auto& p : mPanels) {
        SurfaceComposerClient::Transaction t;
        if (p.hudControl != nullptr) t.hide(p.hudControl);
        if (p.ovControl != nullptr) t.hide(p.ovControl);
        if (p.control != nullptr) t.hide(p.control);
        t.apply(true);
        p.hudSurface.clear();
        p.hudControl.clear();
        p.ovSurface.clear();
        p.ovControl.clear();
        p.surface.clear();
        p.control.clear();
    }
    mPanels.clear();
    mSession.clear();
}

} // namespace azahar_nano
