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
#include "SecureElementHalCallback.h"

namespace android {
namespace hardware {
namespace secure_element {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::secure_element::V1_0::ISecureElementHalCallback follow.
Return<void> SecureElementHalCallback::onStateChange(bool connected) {
    // TODO implement
    return Void();
}


// Methods from ::android::hidl::base::V1_0::IBase follow.

//ISecureElementHalCallback* HIDL_FETCH_ISecureElementHalCallback(const char* /* name */) {
    //return new SecureElementHalCallback();
//}
//
}  // namespace implementation
}  // namespace V1_0
}  // namespace secure_element
}  // namespace hardware
}  // namespace android
