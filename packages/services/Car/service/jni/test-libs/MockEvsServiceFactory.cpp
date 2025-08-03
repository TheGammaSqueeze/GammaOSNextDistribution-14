/*
 * Copyright 2022 The Android Open Source Project
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

#include "MockEvsServiceFactory.h"

namespace {

using ::aidl::android::automotive::evs::implementation::MockEvsHal;

// Number of mock hardware components
inline constexpr int kNumberOfMockCameras = 3;
inline constexpr int kNumberOfMockDisplays = 1;

}  // namespace

namespace android::automotive::evs {

bool MockEvsServiceFactory::init() {
    mMockEvs = std::make_unique<MockEvsHal>(kNumberOfMockCameras, kNumberOfMockDisplays);
    if (!mMockEvs) {
        return false;
    }

    mMockEvs->initialize();
    mService = mMockEvs->getEnumerator();
    return true;
}

binder_status_t MockLinkUnlinkToDeath::linkToDeath(AIBinder*, AIBinder_DeathRecipient*,
                                                   void* cookie) {
    mCookie = cookie;
    return STATUS_OK;
}

binder_status_t MockLinkUnlinkToDeath::unlinkToDeath(AIBinder*) {
    // Do nothing.
    return STATUS_OK;
}

}  // namespace android::automotive::evs
