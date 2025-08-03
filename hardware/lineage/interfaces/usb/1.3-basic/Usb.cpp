/*
 * Copyright (C) 2017-2018 The LineageOS Project
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
#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>

#define LOG_TAG "android.hardware.usb@1.3-service-basic"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <utils/Errors.h>
#include <utils/StrongPointer.h>

#include "Usb.h"

namespace android {
namespace hardware {
namespace usb {
namespace V1_3 {
namespace implementation {

// Methods from ::android::hardware::usb::V1_0::IUsb follow.
Return<void> Usb::switchRole(const hidl_string& portName __unused,
                             const PortRole& newRole __unused) {
    LOG(ERROR) << __func__ << ": Not supported";
    return Void();
}

Return<void> Usb::queryPortStatus() {
    std::lock_guard<std::mutex> lock(mMutex);

    V1_0::PortStatus currentPortStatus = {
            .portName = "otg_default",
            .currentDataRole = V1_0::PortDataRole::DEVICE,
            .currentPowerRole = V1_0::PortPowerRole::SINK,
            .currentMode = V1_0::PortMode::UFP,
            .canChangeMode = false,
            .canChangeDataRole = false,
            .canChangePowerRole = false,
            .supportedModes = V1_0::PortMode::UFP,
    };

    V1_1::PortStatus_1_1 currentPortStatus_1_1 = {
            .status = currentPortStatus,
            .supportedModes = (unsigned int)V1_1::PortMode_1_1::UFP,
            .currentMode = V1_1::PortMode_1_1::UFP,
    };

    V1_2::PortStatus currentPortStatus_1_2 = {
            .status_1_1 = currentPortStatus_1_1,
            .supportedContaminantProtectionModes =
                    (unsigned int)V1_2::ContaminantProtectionMode::NONE,
            .supportsEnableContaminantPresenceProtection = false,
            .contaminantProtectionStatus = V1_2::ContaminantProtectionStatus::NONE,
            .supportsEnableContaminantPresenceDetection = false,
            .contaminantDetectionStatus = V1_2::ContaminantDetectionStatus::NOT_SUPPORTED,
    };

    if (mCallback_1_2 != NULL) {
        hidl_vec<V1_2::PortStatus> vec_currentPortStatus_1_2;
        vec_currentPortStatus_1_2.resize(1);
        vec_currentPortStatus_1_2[0] = currentPortStatus_1_2;

        Return<void> ret = mCallback_1_2->notifyPortStatusChange_1_2(vec_currentPortStatus_1_2,
                                                                     V1_0::Status::SUCCESS);
        if (!ret.isOk()) {
            LOG(ERROR) << "queryPortStatus V1.2 error " << ret.description();
        }
    } else if (mCallback_1_1 != NULL) {
        hidl_vec<V1_1::PortStatus_1_1> vec_currentPortStatus_1_1;
        vec_currentPortStatus_1_1.resize(1);
        vec_currentPortStatus_1_1[0] = currentPortStatus_1_1;

        Return<void> ret = mCallback_1_1->notifyPortStatusChange_1_1(vec_currentPortStatus_1_1,
                                                                     V1_0::Status::SUCCESS);
        if (!ret.isOk()) {
            LOG(ERROR) << "queryPortStatus V1.1 error " << ret.description();
        }
    } else if (mCallback != NULL) {
        hidl_vec<V1_0::PortStatus> vec_currentPortStatus;
        vec_currentPortStatus.resize(1);
        vec_currentPortStatus[0] = currentPortStatus;

        Return<void> ret =
                mCallback->notifyPortStatusChange(vec_currentPortStatus, V1_0::Status::SUCCESS);
        if (!ret.isOk()) {
            LOG(ERROR) << "queryPortStatus V1.0 error " << ret.description();
        }
    } else {
        LOG(INFO) << "Notifying userspace skipped. Callback is NULL";
    }

    return Void();
}

Return<void> Usb::setCallback(const sp<V1_0::IUsbCallback>& callback) {
    std::lock_guard<std::mutex> lock(mMutex);

    mCallback = callback;
    mCallback_1_1 = V1_1::IUsbCallback::castFrom(mCallback);
    mCallback_1_2 = V1_2::IUsbCallback::castFrom(mCallback);

    LOG(INFO) << "registering callback";

    return Void();
}

// Methods from ::android::hardware::usb::V1_2::IUsb follow.
Return<void> Usb::enableContaminantPresenceDetection(const hidl_string& portName __unused,
                                                     bool enable __unused) {
    LOG(ERROR) << __func__ << ": Not supported";
    return Void();
}

Return<void> Usb::enableContaminantPresenceProtection(const hidl_string& portName __unused,
                                                      bool enable __unused) {
    LOG(ERROR) << __func__ << ": Not supported";
    return Void();
}

// Methods from ::android::hardware::usb::V1_3::IUsb follow.
Return<bool> Usb::enableUsbDataSignal(bool enable) {
    bool success = android::base::WriteStringToFile(
            enable ? USB_CONTROL_DISABLE : USB_CONTROL_ENABLE, USB_CONTROL_PATH);
    if (success)
        queryPortStatus();
    else
        LOG(ERROR) << __func__ << ": Failed to " << (enable ? "enable" : "disable");
    return success;
}

}  // namespace implementation
}  // namespace V1_3
}  // namespace usb
}  // namespace hardware
}  // namespace android
