/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "Utils.h"

#include "ConfigManager.h"

#include <android/hardware/automotive/evs/1.0/IEvsEnumerator.h>
#include <hidl/HidlTransportSupport.h>
#include <log/log.h>

namespace android {
namespace automotive {
namespace evs {
namespace support {

using ::android::hardware::hidl_vec;
using ::android::hardware::automotive::evs::V1_0::CameraDesc;
using ::android::hardware::automotive::evs::V1_0::IEvsEnumerator;

std::vector<std::string> Utils::sCameraIds;

std::vector<std::string> Utils::getRearViewCameraIds() {
    // If we already get the camera list, re-use it.
    if (!sCameraIds.empty()) {
        return sCameraIds;
    }

    const char* evsServiceName = "default";

    // Load our configuration information
    ConfigManager config;
    if (!config.initialize("/system/etc/automotive/evs_support_lib/camera_config.json")) {
        ALOGE("Missing or improper configuration for the EVS application.  Exiting.");
        return std::vector<std::string>();
    }

    ALOGI("Acquiring EVS Enumerator");
    sp<IEvsEnumerator> evs = IEvsEnumerator::getService(evsServiceName);
    if (evs.get() == nullptr) {
        ALOGE("getService(%s) returned NULL.  Exiting.", evsServiceName);
        return std::vector<std::string>();
    }

    // static variable cannot be passed into capture, so we create a local
    // variable instead.
    std::vector<std::string> cameraIds;
    ALOGD("Requesting camera list");
    evs->getCameraList([&config, &cameraIds](hidl_vec<CameraDesc> cameraList) {
        ALOGI("Camera list callback received %zu cameras", cameraList.size());
        for (auto&& cam : cameraList) {
            ALOGD("Found camera %s", cam.cameraId.c_str());

            // If there are more than one rear-view cameras, return the first
            // one.
            for (auto&& info : config.getCameras()) {
                if (cam.cameraId == info.cameraId) {
                    // We found a match!
                    if (info.function.find("reverse") != std::string::npos) {
                        ALOGD("Camera %s is matched with reverse state", cam.cameraId.c_str());
                        cameraIds.emplace_back(cam.cameraId);
                    }
                }
            }
        }
    });
    sCameraIds = cameraIds;
    return sCameraIds;
}

std::string Utils::getDefaultRearViewCameraId() {
    auto list = getRearViewCameraIds();
    if (!list.empty()) {
        return list[0];
    } else {
        return std::string();
    }
}

}  // namespace support
}  // namespace evs
}  // namespace automotive
}  // namespace android
