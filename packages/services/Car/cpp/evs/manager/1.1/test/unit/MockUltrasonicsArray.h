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

#ifndef CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKULTRASONICSARRAY_H_
#define CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKULTRASONICSARRAY_H_

#include <android/hardware/automotive/evs/1.1/IEvsUltrasonicsArray.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace android::automotive::evs::V1_1::implementation {

class MockUltrasonicsArray :
      public ::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray {
public:
    virtual ~MockUltrasonicsArray() = default;

    MOCK_METHOD(bool, isRemote, (), (const, override));
    MOCK_METHOD(::android::hardware::Return<void>, getUltrasonicArrayInfo,
                (getUltrasonicArrayInfo_cb), (override));
    MOCK_METHOD(::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>,
                setMaxFramesInFlight, (uint32_t), (override));
    MOCK_METHOD(::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>,
                startStream,
                (const ::android::sp<
                        ::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArrayStream>&),
                (override));
    MOCK_METHOD(::android::hardware::Return<void>, stopStream, (), (override));
    MOCK_METHOD(::android::hardware::Return<void>, doneWithDataFrame,
                (const ::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc&),
                (override));
    MOCK_METHOD(::android::hardware::Return<void>, interfaceChain, (interfaceChain_cb), (override));
    MOCK_METHOD(::android::hardware::Return<void>, debug,
                (const ::android::hardware::hidl_handle&,
                 const ::android::hardware::hidl_vec<::android::hardware::hidl_string>&),
                (override));
    MOCK_METHOD(::android::hardware::Return<void>, interfaceDescriptor,
                (IEvsUltrasonicsArray::interfaceDescriptor_cb), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getHashChain, (getHashChain_cb), (override));
    MOCK_METHOD(::android::hardware::Return<void>, setHALInstrumentation, (), (override));
    MOCK_METHOD(::android::hardware::Return<bool>, linkToDeath,
                (const ::android::sp<::android::hardware::hidl_death_recipient>&, uint64_t),
                (override));
    MOCK_METHOD(::android::hardware::Return<void>, ping, (), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getDebugInfo, (getDebugInfo_cb), (override));
    MOCK_METHOD(::android::hardware::Return<void>, notifySyspropsChanged, (), (override));
    MOCK_METHOD(::android::hardware::Return<bool>, unlinkToDeath,
                (const ::android::sp<::android::hardware::hidl_death_recipient>&), (override));
};

using NiceMockUltrasonicsArray = ::testing::NiceMock<MockUltrasonicsArray>;

}  // namespace android::automotive::evs::V1_1::implementation

#endif  //  CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKULTRASONICSARRAY_H_
