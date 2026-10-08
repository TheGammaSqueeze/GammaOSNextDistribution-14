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
    // Primary first: persist.gammaos.primary_port names its connector port.
    char portProp[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.primary_port", portProp, "");
    if (portProp[0]) {
        const long port = strtol(portProp, nullptr, 10);
        for (size_t i = 0; i < ids.size(); i++) {
            if ((long)ids[i].getPort() == port && i != 0) {
                std::swap(ids[0], ids[i]);
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

void SfWindows::teardown() {
    for (auto& p : mPanels) {
        if (p.control != nullptr) {
            SurfaceComposerClient::Transaction t;
            t.hide(p.control);
            t.apply(true);
        }
        p.surface.clear();
        p.control.clear();
    }
    mPanels.clear();
    mSession.clear();
}

} // namespace azahar_nano
