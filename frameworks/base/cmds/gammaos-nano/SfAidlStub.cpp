// gammaos_sf_stub -- minimal SurfaceFlingerAIDL responder.
//
// Purpose: when gammaos-nano enters the drastic-nano handoff branch it
// stops hwcomposer-3 and surfaceflinger to free DRM master for nano.
// The out-of-process shim (app_process with drastic.apk + shim.apk)
// instantiates DraSticGlView which extends GLSurfaceView -> SurfaceView.
// SurfaceView's ctor runs libandroid_runtime `android_view_SurfaceView_
// nativeCreate`, which constructs a `SurfaceComposerClient`. SCC's
// onFirstRef() calls `ComposerServiceAIDL::connectLocked()` which does
// a blocking `waitForService<gui::ISurfaceComposer>("SurfaceFlingerAIDL")`.
// With SF stopped, that waitForService never returns and the shim hangs.
//
// This stub registers an empty binder under name "SurfaceFlingerAIDL"
// (and "SurfaceFlinger" for the old-style descriptor) so the shim's
// waitForService resolves immediately. The binder's interface descriptor
// is set to "android.gui.ISurfaceComposer" so asInterface succeeds. All
// actual method transactions return UNKNOWN_TRANSACTION, which
// SurfaceComposerClient::onFirstRef treats gracefully (mStatus stays
// NO_INIT; the client is constructed but never used because drastic's
// render path is steered through our FBO/NanoBridge ring, not SF).

#define LOG_TAG "gammaos_sf_stub"

#include <binder/Binder.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <log/log.h>
#include <utils/String16.h>

using android::BBinder;
using android::defaultServiceManager;
using android::IPCThreadState;
using android::IServiceManager;
using android::OK;
using android::ProcessState;
using android::sp;
using android::status_t;
using android::String16;

namespace {

class SurfaceComposerStub : public BBinder {
public:
    const String16& getInterfaceDescriptor() const override {
        static const String16 kDescriptor(u"android.gui.ISurfaceComposer");
        return kDescriptor;
    }
};

class LegacySurfaceComposerStub : public BBinder {
public:
    const String16& getInterfaceDescriptor() const override {
        static const String16 kDescriptor(u"android.ui.ISurfaceComposer");
        return kDescriptor;
    }
};

}  // namespace

int main(int /*argc*/, char** /*argv*/) {
    ALOGI("gammaos_sf_stub starting");

    // Start binder thread pool so incoming transactions can be served.
    ProcessState::self()->setThreadPoolMaxThreadCount(2);
    ProcessState::self()->startThreadPool();

    sp<IServiceManager> sm = defaultServiceManager();
    if (sm == nullptr) {
        ALOGE("gammaos_sf_stub: defaultServiceManager() returned null");
        return 1;
    }

    sp<SurfaceComposerStub> aidlStub = sp<SurfaceComposerStub>::make();
    status_t err = sm->addService(
            String16("SurfaceFlingerAIDL"), aidlStub, /*allowIsolated=*/false,
            IServiceManager::DUMP_FLAG_PRIORITY_DEFAULT);
    if (err != OK) {
        ALOGE("gammaos_sf_stub: addService(SurfaceFlingerAIDL) failed: %d",
              err);
        return 1;
    }
    ALOGI("gammaos_sf_stub: registered SurfaceFlingerAIDL");

    sp<LegacySurfaceComposerStub> legacyStub = sp<LegacySurfaceComposerStub>::make();
    status_t err2 = sm->addService(
            String16("SurfaceFlinger"), legacyStub, /*allowIsolated=*/false,
            IServiceManager::DUMP_FLAG_PRIORITY_DEFAULT);
    if (err2 != OK) {
        ALOGW("gammaos_sf_stub: addService(SurfaceFlinger) failed: %d (ok to ignore if only AIDL is needed)",
              err2);
    } else {
        ALOGI("gammaos_sf_stub: registered legacy SurfaceFlinger");
    }

    ALOGI("gammaos_sf_stub: joining thread pool");
    IPCThreadState::self()->joinThreadPool();
    return 0;
}
