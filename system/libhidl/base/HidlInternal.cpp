/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "HidlInternal"

#include <hidl/HidlInternal.h>

#ifdef __ANDROID__
#include <android/api-level.h>
#endif
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>

namespace android {
namespace hardware {
namespace details {

void logAlwaysFatal(const char* message) {
    LOG(FATAL) << message;
}

std::string getVndkSpHwPath(const char* lib) {
    static std::string vndk_version = base::GetProperty("ro.vndk.version", "");
#ifdef __ANDROID__
    static int api_level = android_get_device_api_level();
    if (api_level >= __ANDROID_API_R__) {
        return android::base::StringPrintf("/apex/com.android.vndk.v%s/%s/hw/",
                                           vndk_version.c_str(), lib);
    }
#endif
    return android::base::StringPrintf("/system/%s/vndk-sp-%s/hw/", lib, vndk_version.c_str());
}

// ----------------------------------------------------------------------
// HidlInstrumentor implementation.
HidlInstrumentor::HidlInstrumentor(const std::string& package, const std::string& interface)
    : mEnableInstrumentation(false),
      mInstrumentationLibPackage(package),
      mInterfaceName(interface) {}

HidlInstrumentor::~HidlInstrumentor() {}

void HidlInstrumentor::configureInstrumentation(bool log) {
    mEnableInstrumentation = base::GetBoolProperty("hal.instrumentation.enable", false);
    if (mEnableInstrumentation) {
        if (log) {
            LOG(INFO) << "Enable instrumentation.";
        }
        mInstrumentationCallbacks.clear();
        registerInstrumentationCallbacks(&mInstrumentationCallbacks);
    } else {
        if (log) {
            LOG(INFO) << "Disable instrumentation.";
        }
        mInstrumentationCallbacks.clear();
    }
}

void HidlInstrumentor::registerInstrumentationCallbacks(
        std::vector<InstrumentationCallback> *instrumentationCallbacks) {
    // No-op, historical
    (void) instrumentationCallbacks;
    return;
}

bool HidlInstrumentor::isInstrumentationLib(const dirent *file) {
    (void) file;
    return false;
}

}  // namespace details
}  // namespace hardware
}  // namespace android
