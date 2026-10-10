#include "Brightness.h"

#include <condition_variable>
#include <mutex>
#include <thread>

#include <aidl/android/hardware/light/ILights.h>
#include <android/binder_manager.h>
#include <android/hardware/light/2.0/ILight.h>
#include <cutils/properties.h>
#include <log/log.h>
#include <sys/wait.h>
#include <unistd.h>

#include "../gammaos-nano/NanoBacklight.h"

#undef LOG_TAG
#define LOG_TAG "azahar-nano"

namespace azahar_nano {
namespace {

std::mutex gMu;
std::condition_variable gCv;
int gPending = -1;   // level waiting for the worker, -1 = none
bool gWorker = false;

// The lights HAL: AIDL, else HIDL light@2.0 (devices without the AIDL service route the
// backlight through it, as drastic-nano and the nano home do).
void halSet(int level) {
    using aidl::android::hardware::light::HwLight;
    using aidl::android::hardware::light::HwLightState;
    using aidl::android::hardware::light::ILights;
    using aidl::android::hardware::light::LightType;
    const int color = 0xFF000000 | (level << 16) | (level << 8) | level;
    bool applied = false;
    ndk::SpAIBinder binder(AServiceManager_checkService("android.hardware.light.ILights/default"));
    if (binder.get()) {
        std::shared_ptr<ILights> hal = ILights::fromBinder(binder);
        if (hal) {
            std::vector<HwLight> lights;
            hal->getLights(&lights);
            for (const auto& light : lights) {
                if (light.type != LightType::BACKLIGHT) continue;
                HwLightState state{};
                state.color = color;
                hal->setLightState(light.id, state);
                applied = true;
            }
        }
    }
    if (applied) return;
    using ::android::hardware::light::V2_0::Brightness;
    using ::android::hardware::light::V2_0::Flash;
    using ::android::hardware::light::V2_0::ILight;
    using ::android::hardware::light::V2_0::LightState;
    using ::android::hardware::light::V2_0::Type;
    android::sp<ILight> hal = ILight::getService();
    if (hal == nullptr) return;
    LightState st{};
    st.color = color;
    st.flashMode = Flash::NONE;
    st.brightnessMode = Brightness::USER;
    hal->setLight(Type::BACKLIGHT, st);
}

// Settings.System.screen_brightness through the settings CLI, double forked so nothing waits
// on it and no zombie is left (the framework re-asserts this value when the game exits).
void pushToSettings(int level) {
    char val[16];
    snprintf(val, sizeof(val), "%d", level);
    const pid_t pid = fork();
    if (pid == 0) {
        if (fork() == 0) {
            execl("/system/bin/settings", "settings", "put", "system", "screen_brightness", val,
                  (char*)nullptr);
            _exit(127);
        }
        _exit(0);
    }
    if (pid > 0) waitpid(pid, nullptr, 0);
}

void workerLoop() {
    std::unique_lock<std::mutex> lk(gMu);
    for (;;) {
        gCv.wait(lk, [] { return gPending >= 0; });
        const int level = gPending;
        gPending = -1;
        lk.unlock();
        halSet(level);
        char buf[16];
        snprintf(buf, sizeof(buf), "%d", level);
        property_set("persist.gammaos.nano.brightness", buf);
        pushToSettings(level);
        lk.lock();
    }
}

} // namespace

int brightnessLevel() {
    return property_get_int32("persist.gammaos.nano.brightness", 128);
}

void setBrightness(int level) {
    if (level < 8) level = 8;
    if (level > 255) level = 255;
    android::nanobl::nanoBacklightSet(level);   // immediate
    std::lock_guard<std::mutex> lk(gMu);
    gPending = level;   // the worker takes the latest level only
    if (!gWorker) {
        gWorker = true;
        std::thread(workerLoop).detach();
    }
    gCv.notify_one();
    ALOGI("brightness %d", level);
}

} // namespace azahar_nano
