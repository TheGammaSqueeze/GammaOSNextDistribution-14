/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKENUMERATORMANAGER_H_
#define CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKENUMERATORMANAGER_H_

#include "IEnumeratorManager.h"

#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstdint>
#include <memory>
#include <string_view>
#include <vector>

namespace android::automotive::evs::V1_1::implementation {

class MockEnumeratorManager : public IEnumeratorManager {
public:
    MockEnumeratorManager() = default;
    virtual ~MockEnumeratorManager() = default;

    MOCK_METHOD(std::vector<::android::hardware::automotive::evs::V1_0::CameraDesc>, getCameraList,
                (), (override));
    MOCK_METHOD(std::unique_ptr<::android::hardware::automotive::evs::V1_0::IEvsCamera>, openCamera,
                (std::string_view), (override));
    MOCK_METHOD(void, closeCamera, (const ::android::hardware::automotive::evs::V1_0::IEvsCamera&),
                (override));
    MOCK_METHOD(std::unique_ptr<::android::hardware::automotive::evs::V1_0::IEvsDisplay>,
                openDisplay, (), (override));

    MOCK_METHOD(void, closeDisplay, (::android::hardware::automotive::evs::V1_0::IEvsDisplay*),
                (override));
    MOCK_METHOD(::android::hardware::automotive::evs::V1_0::DisplayState, getDisplayState, (),
                (override));
    MOCK_METHOD(std::vector<::android::hardware::automotive::evs::V1_1::CameraDesc>,
                getCameraList_1_1, (), (override));
    MOCK_METHOD(std::unique_ptr<::android::hardware::automotive::evs::V1_1::IEvsCamera>,
                openCamera_1_1, (std::string, const hardware::camera::device::V3_2::Stream&),
                (override));
    MOCK_METHOD(bool, isHardware, (), (override));
    MOCK_METHOD(std::vector<std::uint8_t>, getDisplayIdList, (), (override));
    MOCK_METHOD(std::unique_ptr<::android::hardware::automotive::evs::V1_1::IEvsDisplay>,
                openDisplay_1_1, (std::uint8_t), (override));
    MOCK_METHOD(std::vector<::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc>,
                getUltrasonicsArrayList, (), (override));
    MOCK_METHOD(std::unique_ptr<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>,
                openUltrasonicsArray, (std::string), (override));
    MOCK_METHOD(void, closeUltrasonicsArray,
                (const ::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray&),
                (override));
    MOCK_METHOD(std::string, debug, (::android::hardware::hidl_handle, std::vector<std::string>),
                (override));
};

using NiceMockEnumeratorManager = ::testing::NiceMock<MockEnumeratorManager>;

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKENUMERATORMANAGER_H_
