/*****************************************************************************
 * Copyright ©2017-2019 Gemalto – a Thales Company. All rights Reserved.
 *
 * This copy is licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0 or https://www.apache.org/licenses/LICENSE-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.

 ****************************************************************************/
#ifndef ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_SECUREELEMENTHALCALLBACK_H
#define ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_SECUREELEMENTHALCALLBACK_H

#include <android/hardware/secure_element/1.0/ISecureElementHalCallback.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace secure_element {
namespace V1_0 {
namespace implementation {

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct SecureElementHalCallback : public ISecureElementHalCallback {
    // Methods from ::android::hardware::secure_element::V1_0::ISecureElementHalCallback follow.
    Return<void> onStateChange(bool connected) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.

};

// FIXME: most likely delete, this is only for passthrough implementations
// extern "C" ISecureElementHalCallback* HIDL_FETCH_ISecureElementHalCallback(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace secure_element
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_SECURE_ELEMENT_V1_0_SECUREELEMENTHALCALLBACK_H
