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

#pragma once

#include <mutex>

#include <android/hardware/usb/1.2/IUsbCallback.h>
#include <android/hardware/usb/1.2/types.h>
#include <android/hardware/usb/1.3/IUsb.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

#define UEVENT_MSG_LEN 2048

namespace android {
namespace hardware {
namespace usb {
namespace V1_3 {
namespace implementation {

using ::android::sp;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::usb::V1_0::PortRole;
using ::android::hardware::usb::V1_3::IUsb;
using ::android::hidl::base::V1_0::IBase;

class Usb : public IUsb {
  public:
    // Methods from ::android::hardware::usb::V1_0::IUsb follow.
    Return<void> switchRole(const hidl_string& portName, const PortRole& role) override;
    Return<void> setCallback(const sp<V1_0::IUsbCallback>& callback) override;
    Return<void> queryPortStatus() override;

    // Methods from ::android::hardware::usb::V1_2::IUsb follow.
    Return<void> enableContaminantPresenceDetection(const hidl_string& portName,
                                                    bool enable) override;
    Return<void> enableContaminantPresenceProtection(const hidl_string& portName,
                                                     bool enable) override;

    // Methods from ::android::hardware::usb::V1_3::IUsb follow.
    Return<bool> enableUsbDataSignal(bool enable) override;

  private:
    sp<V1_0::IUsbCallback> mCallback;
    sp<V1_1::IUsbCallback> mCallback_1_1;
    sp<V1_2::IUsbCallback> mCallback_1_2;
    std::mutex mMutex;
};

}  // namespace implementation
}  // namespace V1_3
}  // namespace usb
}  // namespace hardware
}  // namespace android
