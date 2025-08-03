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

#pragma once

#include <android/hardware/automotive/evs/1.1/IEvsDisplay.h>
#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>
#include <android/hardware/camera/device/3.2/ICameraDevice.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

class MockHidlEvsEnumerator : public hidlevs::V1_1::IEvsEnumerator {
public:
    MockHidlEvsEnumerator() = default;
    virtual ~MockHidlEvsEnumerator() = default;

    // Methods from hardware::automotive::evs::V1_0::IEvsEnumerator follow.
    MOCK_METHOD(::android::hardware::Return<void>, getCameraList, (getCameraList_cb), (override));
    MOCK_METHOD(::android::hardware::Return<::android::sp<hidlevs::V1_0::IEvsCamera>>, openCamera,
                (const ::android::hardware::hidl_string&), (override));
    MOCK_METHOD(::android::hardware::Return<void>, closeCamera,
                (const ::android::sp<hidlevs::V1_0::IEvsCamera>&), (override));
    MOCK_METHOD(::android::hardware::Return<::android::sp<hidlevs::V1_0::IEvsDisplay>>, openDisplay,
                (), (override));
    MOCK_METHOD(::android::hardware::Return<void>, closeDisplay,
                (const ::android::sp<hidlevs::V1_0::IEvsDisplay>&), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::DisplayState>, getDisplayState, (),
                (override));

    // Methods from hardware::automotive::evs::V1_1::IEvsEnumerator follow.
    MOCK_METHOD(::android::hardware::Return<void>, getCameraList_1_1, (getCameraList_1_1_cb),
                (override));
    MOCK_METHOD(::android::hardware::Return<::android::sp<hidlevs::V1_1::IEvsCamera>>,
                openCamera_1_1,
                (const ::android::hardware::hidl_string&,
                 const ::android::hardware::camera::device::V3_2::Stream&),
                (override));
    MOCK_METHOD(::android::hardware::Return<bool>, isHardware, (), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getDisplayIdList, (getDisplayIdList_cb),
                (override));
    MOCK_METHOD(::android::hardware::Return<::android::sp<hidlevs::V1_1::IEvsDisplay>>,
                openDisplay_1_1, (uint8_t), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getUltrasonicsArrayList,
                (getUltrasonicsArrayList_cb), (override));
    MOCK_METHOD(::android::hardware::Return<::android::sp<hidlevs::V1_1::IEvsUltrasonicsArray>>,
                openUltrasonicsArray, (const ::android::hardware::hidl_string&), (override));
    MOCK_METHOD(::android::hardware::Return<void>, closeUltrasonicsArray,
                (const ::android::sp<hidlevs::V1_1::IEvsUltrasonicsArray>&), (override));
};

using NiceMockHidlEvsEnumerator = ::testing::NiceMock<MockHidlEvsEnumerator>;

}  // namespace aidl::android::automotive::evs::implementation
