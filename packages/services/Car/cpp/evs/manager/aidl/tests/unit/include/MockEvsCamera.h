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

#include <aidl/android/hardware/automotive/evs/BnEvsCamera.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraParam.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <aidl/android/hardware/automotive/evs/IEvsCameraStream.h>
#include <aidl/android/hardware/automotive/evs/IEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/ParameterRange.h>
#include <aidl/android/hardware/automotive/evs/Stream.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;

class MockEvsCamera : public aidlevs::BnEvsCamera {
public:
    MockEvsCamera(const std::string& deviceId) : mDeviceId(deviceId) {}
    virtual ~MockEvsCamera() = default;

    MOCK_METHOD(::ndk::ScopedAStatus, doneWithFrame,
                (const std::vector<aidlevs::BufferDesc>& buffers), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, forcePrimaryClient,
                (const std::shared_ptr<aidlevs::IEvsDisplay>& display), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getCameraInfo, (aidlevs::CameraDesc * _aidl_return),
                (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getExtendedInfo,
                (int32_t opaqueIdentifier, std::vector<uint8_t>* value), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getIntParameter,
                (aidlevs::CameraParam id, std::vector<int32_t>* value), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getIntParameterRange,
                (aidlevs::CameraParam id, aidlevs::ParameterRange* _aidl_return), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getParameterList,
                (std::vector<aidlevs::CameraParam> * _aidl_return), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getPhysicalCameraInfo,
                (const std::string& deviceId, aidlevs::CameraDesc* _aidl_return), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, importExternalBuffers,
                (const std::vector<aidlevs::BufferDesc>& buffers, int32_t* _aidl_return),
                (override));
    MOCK_METHOD(::ndk::ScopedAStatus, pauseVideoStream, (), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, resumeVideoStream, (), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, setExtendedInfo,
                (int32_t opaqueIdentifier, const std::vector<uint8_t>& opaqueValue), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, setIntParameter,
                (aidlevs::CameraParam id, int32_t value, std::vector<int32_t>* effectiveValue),
                (override));
    MOCK_METHOD(::ndk::ScopedAStatus, setPrimaryClient, (), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, setMaxFramesInFlight, (int32_t bufferCount), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, startVideoStream,
                (const std::shared_ptr<aidlevs::IEvsCameraStream>& receiver), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, stopVideoStream, (), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, unsetPrimaryClient, (), (override));

    std::string getId() const { return mDeviceId; }

private:
    std::string mDeviceId;
};

using NiceMockEvsCamera = ::testing::NiceMock<MockEvsCamera>;

}  // namespace aidl::android::automotive::evs::implementation
