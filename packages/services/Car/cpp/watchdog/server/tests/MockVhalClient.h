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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKVHALCLIENT_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKVHALCLIENT_H_

#include "MockSubscriptionClient.h"

#include <gmock/gmock.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockVhalClient final : public android::frameworks::automotive::vhal::IVhalClient {
public:
    template <class T>
    using VhalClientResult = android::frameworks::automotive::vhal::VhalClientResult<T>;

    explicit MockVhalClient(const std::shared_ptr<MockVehicle>& vehicle) {
        mVehicle = vehicle;
        ON_CALL(*this, isAidlVhal()).WillByDefault(testing::Return(true));
    }
    ~MockVhalClient() { mVehicle.reset(); }

    MOCK_METHOD(bool, isAidlVhal, (), (override));

    std::unique_ptr<android::frameworks::automotive::vhal::ISubscriptionClient>
    getSubscriptionClient(
            std::shared_ptr<android::frameworks::automotive::vhal::ISubscriptionCallback> callback)
            override {
        return std::make_unique<MockSubscriptionClient>(mVehicle, callback);
    }

    MOCK_METHOD(std::unique_ptr<android::frameworks::automotive::vhal::IHalPropValue>,
                createHalPropValue, (int32_t), (override));
    MOCK_METHOD(std::unique_ptr<android::frameworks::automotive::vhal::IHalPropValue>,
                createHalPropValue, (int32_t, int32_t), (override));
    MOCK_METHOD(void, getValue,
                (const android::frameworks::automotive::vhal::IHalPropValue&,
                 std::shared_ptr<GetValueCallbackFunc>),
                (override));
    MOCK_METHOD(
            VhalClientResult<std::unique_ptr<android::frameworks::automotive::vhal::IHalPropValue>>,
            getValueSync, (const android::frameworks::automotive::vhal::IHalPropValue&),
            (override));
    MOCK_METHOD(void, setValue,
                (const android::frameworks::automotive::vhal::IHalPropValue&,
                 std::shared_ptr<SetValueCallbackFunc>),
                (override));
    MOCK_METHOD(VhalClientResult<void>, setValueSync,
                (const android::frameworks::automotive::vhal::IHalPropValue&), (override));
    MOCK_METHOD(VhalClientResult<void>, addOnBinderDiedCallback,
                (std::shared_ptr<OnBinderDiedCallbackFunc>), (override));
    MOCK_METHOD(VhalClientResult<void>, removeOnBinderDiedCallback,
                (std::shared_ptr<OnBinderDiedCallbackFunc>), (override));
    MOCK_METHOD(VhalClientResult<std::vector<
                        std::unique_ptr<android::frameworks::automotive::vhal::IHalPropConfig>>>,
                getAllPropConfigs, (), (override));
    MOCK_METHOD(VhalClientResult<std::vector<
                        std::unique_ptr<android::frameworks::automotive::vhal::IHalPropConfig>>>,
                getPropConfigs, (std::vector<int32_t>), (override));

private:
    std::shared_ptr<MockVehicle> mVehicle;
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_TESTS_MOCKVHALCLIENT_H_
