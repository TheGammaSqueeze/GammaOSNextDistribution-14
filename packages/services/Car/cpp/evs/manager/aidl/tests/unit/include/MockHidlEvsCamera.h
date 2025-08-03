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

#include <android/hardware/automotive/evs/1.1/IEvsCamera.h>
#include <android/hardware/automotive/evs/1.1/IEvsCameraStream.h>
#include <android/hardware/automotive/evs/1.1/IEvsDisplay.h>
#include <android/hardware/automotive/evs/1.1/types.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

class MockHidlEvsCamera : public hidlevs::V1_1::IEvsCamera {
public:
    MockHidlEvsCamera(const std::string& deviceId) : mDeviceId(deviceId) {}
    virtual ~MockHidlEvsCamera() = default;

    // Methods from hardware::automotive::evs::V1_0::IEvsCamera follow.
    MOCK_METHOD(::android::hardware::Return<void>, getCameraInfo, (getCameraInfo_cb), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, setMaxFramesInFlight,
                (uint32_t), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, startVideoStream,
                (const ::android::sp<hidlevs::V1_0::IEvsCameraStream>&), (override));
    MOCK_METHOD(::android::hardware::Return<void>, doneWithFrame,
                (const hidlevs::V1_0::BufferDesc&), (override));
    MOCK_METHOD(::android::hardware::Return<void>, stopVideoStream, (), (override));
    MOCK_METHOD(::android::hardware::Return<int32_t>, getExtendedInfo, (uint32_t), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, setExtendedInfo,
                (uint32_t, int32_t), (override));

    // Methods from hardware::automotive::evs::V1_1::IEvsCamera follow.
    MOCK_METHOD(::android::hardware::Return<void>, getCameraInfo_1_1, (getCameraInfo_1_1_cb),
                (override));
    MOCK_METHOD(::android::hardware::Return<void>, getPhysicalCameraInfo,
                (const ::android::hardware::hidl_string&, getPhysicalCameraInfo_cb), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, doneWithFrame_1_1,
                (const ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc>&), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, pauseVideoStream, (),
                (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, resumeVideoStream, (),
                (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, setMaster, (), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, forceMaster,
                (const ::android::sp<hidlevs::V1_0::IEvsDisplay>&), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, unsetMaster, (), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getParameterList, (getParameterList_cb),
                (override));
    MOCK_METHOD(::android::hardware::Return<void>, getIntParameterRange,
                (hidlevs::V1_1::CameraParam, getIntParameterRange_cb), (override));
    MOCK_METHOD(::android::hardware::Return<void>, setIntParameter,
                (hidlevs::V1_1::CameraParam, int32_t, setIntParameter_cb), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getIntParameter,
                (hidlevs::V1_1::CameraParam, getIntParameter_cb), (override));
    MOCK_METHOD(::android::hardware::Return<hidlevs::V1_0::EvsResult>, setExtendedInfo_1_1,
                (uint32_t, const ::android::hardware::hidl_vec<uint8_t>&), (override));
    MOCK_METHOD(::android::hardware::Return<void>, getExtendedInfo_1_1,
                (uint32_t, getExtendedInfo_1_1_cb), (override));
    MOCK_METHOD(::android::hardware::Return<void>, importExternalBuffers,
                (const ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc>&,
                 importExternalBuffers_cb),
                (override));

    std::string getId() const { return mDeviceId; }

private:
    std::string mDeviceId;
};

using NiceMockHidlEvsCamera = ::testing::NiceMock<MockHidlEvsCamera>;

}  // namespace aidl::android::automotive::evs::implementation
