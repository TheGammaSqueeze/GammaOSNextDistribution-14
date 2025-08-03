/*
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKSUBSCRIPTIONCLIENT_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKSUBSCRIPTIONCLIENT_H_

#include "MockVehicle.h"

#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_interface_utils.h>
#include <gmock/gmock.h>

#include <AidlVhalClient.h>

namespace android {
namespace automotive {
namespace watchdog {

std::string toString(const std::vector<int32_t>& values) {
    std::vector<std::string> strings;
    for (int32_t value : values) {
        strings.push_back(std::to_string(value));
    }
    return android::base::StringPrintf("[%s]", android::base::Join(strings, ",").c_str());
}

class MockSubscriptionClient final :
      public android::frameworks::automotive::vhal::ISubscriptionClient {
public:
    MockSubscriptionClient(
            const std::shared_ptr<MockVehicle>& hal,
            const std::shared_ptr<android::frameworks::automotive::vhal::ISubscriptionCallback>&
                    callback) {
        mHal = hal;
        mCallback = ndk::SharedRefBase::make<
                android::frameworks::automotive::vhal::SubscriptionVehicleCallback>(callback);
    }
    ~MockSubscriptionClient() {
        mHal.reset();
        mCallback.reset();
    }

    MOCK_METHOD(
            android::frameworks::automotive::vhal::VhalClientResult<void>, subscribe,
            (const std::vector<aidl::android::hardware::automotive::vehicle::SubscribeOptions>&),
            (override));

    android::frameworks::automotive::vhal::VhalClientResult<void> unsubscribe(
            const std::vector<int32_t>& propIds) override {
        if (auto status = mHal->unsubscribe(mCallback, propIds); !status.isOk()) {
            return android::frameworks::automotive::vhal::ClientStatusError(
                           static_cast<aidl::android::hardware::automotive::vehicle::StatusCode>(
                                   status.getServiceSpecificError()))
                    << "failed to unsubscribe to prop IDs: " << toString(propIds)
                    << ", error: " << status.getMessage();
        }
        return {};
    }

private:
    std::shared_ptr<MockVehicle> mHal;
    std::shared_ptr<android::frameworks::automotive::vhal::SubscriptionVehicleCallback> mCallback;
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_TESTS_MOCKSUBSCRIPTIONCLIENT_H_
