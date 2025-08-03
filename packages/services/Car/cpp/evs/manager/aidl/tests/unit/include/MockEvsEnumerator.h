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

#include <aidl/android/hardware/automotive/evs/BnEvsEnumerator.h>
#include <aidl/android/hardware/automotive/evs/BnEvsEnumeratorStatusCallback.h>
#include <aidl/android/hardware/automotive/evs/IEvsDisplay.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;

class MockEvsEnumerator : public aidlevs::BnEvsEnumerator {
public:
    MockEvsEnumerator() = default;
    virtual ~MockEvsEnumerator() = default;

    MOCK_METHOD(::ndk::ScopedAStatus, isHardware, (bool*), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, openCamera,
                (const std::string&, const aidlevs::Stream&, std::shared_ptr<aidlevs::IEvsCamera>*),
                (override));
    MOCK_METHOD(::ndk::ScopedAStatus, closeCamera, (const std::shared_ptr<aidlevs::IEvsCamera>&),
                (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getCameraList, (std::vector<aidlevs::CameraDesc>*),
                (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getStreamList,
                (const aidlevs::CameraDesc&, std::vector<aidlevs::Stream>*), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, openDisplay,
                (int32_t, std::shared_ptr<aidlevs::IEvsDisplay>*), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, closeDisplay, (const std::shared_ptr<aidlevs::IEvsDisplay>&),
                (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getDisplayIdList, (std::vector<uint8_t>*), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getDisplayState, (aidlevs::DisplayState*), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getDisplayStateById, (int32_t, aidlevs::DisplayState*),
                (override));
    MOCK_METHOD(::ndk::ScopedAStatus, registerStatusCallback,
                (const std::shared_ptr<aidlevs::IEvsEnumeratorStatusCallback>&), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, openUltrasonicsArray,
                (const std::string&, std::shared_ptr<aidlevs::IEvsUltrasonicsArray>*), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, closeUltrasonicsArray,
                (const std::shared_ptr<aidlevs::IEvsUltrasonicsArray>&), (override));
    MOCK_METHOD(::ndk::ScopedAStatus, getUltrasonicsArrayList,
                (std::vector<aidlevs::UltrasonicsArrayDesc>*), (override));
};

using NiceMockEvsEnumerator = ::testing::NiceMock<MockEvsEnumerator>;

}  // namespace aidl::android::automotive::evs::implementation
