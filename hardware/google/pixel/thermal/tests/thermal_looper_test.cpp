/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <aidl/android/hardware/thermal/BnThermalChangedCallback.h>
#include <log/log.h>

#include "Thermal.h"
#include "mock_thermal_helper.h"

namespace aidl::android::hardware::thermal::implementation {

class ThermalLooperTest : public testing::Test {
  protected:
    void SetUp() override {
        ON_CALL(*helper, isInitializedOk).WillByDefault(testing::Return(true));
    }

    std::shared_ptr<testing::NiceMock<MockThermalHelper>> helper =
            std::make_shared<testing::NiceMock<MockThermalHelper>>();
    std::shared_ptr<Thermal> thermal = ndk::SharedRefBase::make<Thermal>(helper);
};

class TestCallback : public BnThermalChangedCallback {
  public:
    ndk::ScopedAStatus notifyThrottling(const Temperature &t) override {
        std::lock_guard<std::mutex> lock_guard(mMutex);
        mTemperatures.emplace_back(t);
        return ndk::ScopedAStatus::ok();
    }

    std::vector<Temperature> getTemperatures() {
        std::lock_guard<std::mutex> lock_guard(mMutex);
        return mTemperatures;
    }

    void clear() {
        std::lock_guard<std::mutex> lock_guard(mMutex);
        mTemperatures.clear();
    }

  private:
    std::vector<Temperature> mTemperatures;
    std::mutex mMutex;
};

TEST_F(ThermalLooperTest, AsyncCallbackTest) {
    Temperature t1;
    t1.type = TemperatureType::SKIN;
    Temperature t2;
    t2.type = TemperatureType::UNKNOWN;
    ON_CALL(*helper, fillCurrentTemperatures)
            .WillByDefault([this, t1, t2](bool, bool, TemperatureType,
                                          std::vector<Temperature> *temperatures) {
                std::vector<Temperature> ret = {t1, t2};
                *temperatures = ret;
                sleep(1);
                return true;
            });
    std::shared_ptr<TestCallback> callback = ndk::SharedRefBase::make<TestCallback>();
    std::shared_ptr<TestCallback> callbackWithType = ndk::SharedRefBase::make<TestCallback>();

    // if callback immediately unregistered, no async callback should be scheduled
    ASSERT_TRUE(thermal->registerThermalChangedCallback(callback).isOk());
    ASSERT_TRUE(
            thermal->registerThermalChangedCallbackWithType(callbackWithType, TemperatureType::SKIN)
                    .isOk());
    ASSERT_TRUE(thermal->unregisterThermalChangedCallback(callback).isOk());
    ASSERT_TRUE(thermal->unregisterThermalChangedCallback(callbackWithType).isOk());
    sleep(3);
    ASSERT_TRUE(callback->getTemperatures().empty());
    ASSERT_TRUE(callbackWithType->getTemperatures().empty());

    // otherwise, async callback should be scheduled if registered
    ASSERT_TRUE(thermal->registerThermalChangedCallback(callback).isOk());
    ASSERT_TRUE(
            thermal->registerThermalChangedCallbackWithType(callbackWithType, TemperatureType::SKIN)
                    .isOk());
    sleep(3);
    ASSERT_THAT(callback->getTemperatures(), testing::UnorderedElementsAreArray({t1, t2}));
    ASSERT_THAT(callbackWithType->getTemperatures(), testing::UnorderedElementsAreArray({t1}));
}

}  // namespace aidl::android::hardware::thermal::implementation
