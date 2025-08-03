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

#include <android/hardware/automotive/evs/1.0/IEvsDisplay.h>
#include <android/hardware/automotive/evs/1.0/IEvsEnumerator.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

class MockHidlEvsEnumerator_1_0 : public hidlevs::V1_0::IEvsEnumerator {
public:
    MockHidlEvsEnumerator_1_0() = default;
    virtual ~MockHidlEvsEnumerator_1_0() = default;

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
};

using NiceMockHidlEvsEnumerator_1_0 = ::testing::NiceMock<MockHidlEvsEnumerator_1_0>;

}  // namespace aidl::android::automotive::evs::implementation
