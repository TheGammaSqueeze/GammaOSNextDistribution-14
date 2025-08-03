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

#include "EnumeratorProxy.h"
#include "IEnumeratorManager.h"
#include "MockEnumeratorManager.h"
#include "MockEvsDisplay.h"
#include "MockEvsEnumerator.h"
#include "MockUltrasonicsArray.h"

#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

// EnumeratorProxy is temporarily taking an Enumerator instance and converting
// the interface piecemeal. When this is complete, the constructor will be
// flipped back and these tests will be restored.
#ifdef TEMPORARILY_DISABLE_SEE_B_206829268
using ::android::sp;
using ::android::automotive::evs::V1_1::implementation::EnumeratorProxy;
using ::android::automotive::evs::V1_1::implementation::MockEvsDisplay_1_0;
using ::android::automotive::evs::V1_1::implementation::MockEvsEnumerator;
using ::android::automotive::evs::V1_1::implementation::NiceMockEnumeratorManager;
using ::android::automotive::evs::V1_1::implementation::NiceMockEvsEnumerator;
using ::android::automotive::evs::V1_1::implementation::NiceMockUltrasonicsArray;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::testing::_;
using ::testing::ByMove;
using ::testing::Eq;

using CameraDesc_1_0 = ::android::hardware::automotive::evs::V1_0::CameraDesc;
using CameraDesc_1_1 = ::android::hardware::automotive::evs::V1_1::CameraDesc;
using DisplayState_1_0 = ::android::hardware::automotive::evs::V1_0::DisplayState;

////////////////////////////////////////////////////////////////////////////////
// These tests don't exercise any functional effects, instead they ensure that
// proxying from HIDL focused types proxy properly to x86 host compatible types.
////////////////////////////////////////////////////////////////////////////////

TEST(EnumeratorProxy, Constructs) {
    EnumeratorProxy enumeratorProxy{std::make_unique<NiceMockEnumeratorManager>()};
}

TEST(EnumeratorProxy, GetsCameraList_1_0) {
    std::vector<CameraDesc_1_0> expected_value{CameraDesc_1_0{.cameraId = "cam_123",
                                                              .vendorFlags = 123},
                                               CameraDesc_1_0{.cameraId = "cam_456",
                                                              .vendorFlags = 456}};

    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    ON_CALL(*mockEnumeratorManager, getCameraList).WillByDefault(::testing::Return(expected_value));

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    std::vector<CameraDesc_1_0> result;
    enumeratorProxy.getCameraList(std::function<void(const hidl_vec<CameraDesc_1_0>&)>{
            [&](const hidl_vec<CameraDesc_1_0>& cameras) {
                result = static_cast<std::vector<CameraDesc_1_0>>(cameras);
            }});

    EXPECT_EQ(result, expected_value);
}

TEST(EnumeratorProxy, OpensCamera) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    EXPECT_CALL(*mockEnumeratorManager, openCamera("cam_123"));

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    enumeratorProxy.openCamera(hidl_string{"cam_123"});
}

TEST(EnumeratorProxy, ClosesCamera) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    EXPECT_CALL(*mockEnumeratorManager, closeCamera);

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    enumeratorProxy.closeCamera({});
}

TEST(EnumeratorProxy, OpensDisplay) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    EXPECT_CALL(*mockEnumeratorManager, openDisplay);

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    enumeratorProxy.openDisplay();
}

TEST(EnumeratorProxy, ClosesDisplay) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    auto* mockEvsDisplay = new MockEvsDisplay_1_0();

    EXPECT_CALL(*mockEnumeratorManager, closeDisplay(Eq(mockEvsDisplay)));

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    enumeratorProxy.closeDisplay(
            sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>{mockEvsDisplay});
}

TEST(EnumeratorProxy, GetsDisplayState) {
    DisplayState_1_0 state{DisplayState_1_0::VISIBLE_ON_NEXT_FRAME};

    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    ON_CALL(*mockEnumeratorManager, getDisplayState).WillByDefault(::testing::Return(state));
    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    EXPECT_EQ(enumeratorProxy.getDisplayState(), DisplayState_1_0::VISIBLE_ON_NEXT_FRAME);
}

TEST(EnumeratorProxy, GetsCameraList_1_1) {
    std::vector<CameraDesc_1_1> expected_value{CameraDesc_1_1{
                                                       .v1 = {CameraDesc_1_0{.cameraId = "cam_123",
                                                                             .vendorFlags = 123}}},
                                               CameraDesc_1_1{
                                                       .v1 = {CameraDesc_1_0{.cameraId = "cam_456",
                                                                             .vendorFlags = 456}}}};

    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    ON_CALL(*mockEnumeratorManager, getCameraList_1_1)
            .WillByDefault(::testing::Return(expected_value));

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    std::vector<CameraDesc_1_1> result;
    enumeratorProxy.getCameraList_1_1(std::function<void(const hidl_vec<CameraDesc_1_1>&)>{
            [&](const hidl_vec<CameraDesc_1_1>& cameras) {
                result = static_cast<std::vector<CameraDesc_1_1>>(cameras);
            }});

    EXPECT_EQ(result, expected_value);
}

TEST(EnumeratorProxy, OpensCamera_1_1) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    std::unique_ptr<::android::hardware::automotive::evs::V1_1::IEvsCamera> camera;

    EXPECT_CALL(*mockEnumeratorManager, openCamera_1_1("cam_123", _))
            .WillOnce(::testing::Return(ByMove(std::move(camera))));

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    enumeratorProxy.openCamera_1_1(hidl_string{"cam_123"},
                                   ::android::hardware::camera::device::V3_2::Stream{});
}

TEST(EnumeratorProxy, CallsIsHardware) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    ON_CALL(*mockEnumeratorManager, isHardware).WillByDefault(::testing::Return(false));

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    EXPECT_EQ(false, enumeratorProxy.isHardware());
}

TEST(EnumeratorProxy, GetsDisplayIdList) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    ON_CALL(*mockEnumeratorManager, getDisplayIdList)
            .WillByDefault(::testing::Return(std::vector<uint8_t>{1, 2, 3, 4}));

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    std::vector<uint8_t> result;
    enumeratorProxy.getDisplayIdList(
            std::function<void(const hidl_vec<uint8_t>&)>{[&](const hidl_vec<std::uint8_t>& ids) {
                result = static_cast<std::vector<std::uint8_t>>(ids);
            }});

    EXPECT_EQ(result, (std::vector<uint8_t>{1, 2, 3, 4}));
}

TEST(EnumeratorProxy, OpensDisplay_1_1) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    EXPECT_CALL(*mockEnumeratorManager, openDisplay_1_1(123));

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    enumeratorProxy.openDisplay_1_1(123);
}

TEST(EnumeratorProxy, GetsUltrasonicsArrayList) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();
    std::vector<::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc>
            ultrasonicsArrayDescriptions;
    ON_CALL(*mockEnumeratorManager, getUltrasonicsArrayList)
            .WillByDefault(::testing::Return(ultrasonicsArrayDescriptions));
    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};

    std::vector<::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc> result;
    enumeratorProxy.getUltrasonicsArrayList(
            std::function<void(const ::android::hardware::hidl_vec<
                               ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc>&)>(
                    [](const ::android::hardware::hidl_vec<
                            ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc>&) {
                    }));

    EXPECT_EQ(result, ultrasonicsArrayDescriptions);
}

TEST(EnumeratorProxy, OpensUltrasonicsArrayList) {
    auto mockEnumeratorManager = std::make_unique<NiceMockEnumeratorManager>();

    std::unique_ptr<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>
            ultrasonicsArray{std::make_unique<NiceMockUltrasonicsArray>()};
    EXPECT_CALL(*mockEnumeratorManager, openUltrasonicsArray)
            .WillOnce(::testing::Return(ByMove(std::move(ultrasonicsArray))));

    EnumeratorProxy enumeratorProxy{std::move(mockEnumeratorManager)};
    enumeratorProxy.openUltrasonicsArray("ultrasonics_id");
}
#endif  // TEMPORARILY_DISABLE_SEE_B_206829268
