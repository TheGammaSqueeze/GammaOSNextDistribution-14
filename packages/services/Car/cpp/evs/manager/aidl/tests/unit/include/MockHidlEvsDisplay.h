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
#include <android/hardware/automotive/evs/1.1/types.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

class MockHidlEvsDisplay : public hidlevs::V1_1::IEvsDisplay {
public:
    MockHidlEvsDisplay() = default;
    virtual ~MockHidlEvsDisplay() = default;

    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsDisplay follow.
    MOCK_METHOD(::android::hardware::Return<void>, getDisplayInfo, (getDisplayInfo_cb), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, setDisplayState,
                (hidlevs::V1_0::DisplayState), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::DisplayState>, getDisplayState, (),
                (override));
    MOCK_METHOD(::android::hardware::Return<void>, getTargetBuffer, (getTargetBuffer_cb),
                (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, returnTargetBufferForDisplay,
                (const hidlevs::V1_0::BufferDesc&), (override));

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsDisplay follow.
    MOCK_METHOD(::android::hardware::Return<void>, getDisplayInfo_1_1, (getDisplayInfo_1_1_cb),
                (override));
};

using NiceMockHidlEvsDisplay = ::testing::NiceMock<MockHidlEvsDisplay>;

}  // namespace aidl::android::automotive::evs::implementation
