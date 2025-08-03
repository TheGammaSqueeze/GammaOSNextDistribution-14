/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKEVSENUMERATOR_H_
#define CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKEVSENUMERATOR_H_

#include "Enumerator.h"

#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace android::automotive::evs::V1_1::implementation {

class MockEvsEnumerator : public IEvsEnumerator {
public:
    MockEvsEnumerator() = default;
    ~MockEvsEnumerator() override = default;

    MOCK_METHOD(bool, isRemote, (), (const override));
    MOCK_METHOD(::android::hardware::Return<void>, getCameraList, (getCameraList_cb), (override));
    MOCK_METHOD(::android::hardware::Return<
                        ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCamera>>,
                openCamera, (const ::android::hardware::hidl_string&), (override));
    MOCK_METHOD(::android::hardware::Return<void>, closeCamera,
                (const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCamera>&),
                (override));
    MOCK_METHOD(::android::hardware::Return<
                        ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>>,
                openDisplay, (), (override));
    MOCK_METHOD(::android::hardware::Return<void>, closeDisplay,
                (const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>&),
                (override));
    MOCK_METHOD(
            ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::DisplayState>,
            getDisplayState, (), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getCameraList_1_1, (getCameraList_1_1_cb));
    MOCK_METHOD(::android::hardware::Return<
                        ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsCamera>>,
                openCamera_1_1,
                (const ::android::hardware::hidl_string&,
                 const ::android::hardware::camera::device::V3_2::Stream&));
    MOCK_METHOD(::android::hardware::Return<bool>, isHardware, (), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getDisplayIdList, (getDisplayIdList_cb));
    MOCK_METHOD(::android::hardware::Return<
                        ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsDisplay>>,
                openDisplay_1_1, (uint8_t id), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getUltrasonicsArrayList,
                (getUltrasonicsArrayList_cb), (override));
    MOCK_METHOD(::android::hardware::Return<::android::sp<
                        ::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>>,
                openUltrasonicsArray, (const ::android::hardware::hidl_string& ultrasonicsArrayId),
                (override));
    MOCK_METHOD(
            ::android::hardware::Return<void>, closeUltrasonicsArray,
            (const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>&
                     evsUltrasonicsArray));
    MOCK_METHOD(::android::hardware::Return<void>, interfaceChain, (interfaceChain_cb), (override));
    MOCK_METHOD(::android::hardware::Return<void>, debug,
                (const ::android::hardware::hidl_handle&,
                 const ::android::hardware::hidl_vec<::android::hardware::hidl_string>&),
                (override));
    MOCK_METHOD(::android::hardware::Return<void>, interfaceDescriptor, (interfaceDescriptor_cb),
                (override));
};

using NiceMockEvsEnumerator = ::testing::NiceMock<MockEvsEnumerator>;

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKEVSENUMERATOR_H_
