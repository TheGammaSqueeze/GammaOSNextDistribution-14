// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef CPP_EVS_MANAGER_1_1_TEST_FUZZER_MOCKHWDISPLAY_H_
#define CPP_EVS_MANAGER_1_1_TEST_FUZZER_MOCKHWDISPLAY_H_

#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace android::automotive::evs::V1_1::implementation {

class MockHWDisplay : public ::android::hardware::automotive::evs::V1_1::IEvsDisplay {
public:
    MockHWDisplay() = default;

    ::android::hardware::Return<void> getDisplayInfo(getDisplayInfo_cb _hidl_cb) override {
        return {};
    }
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>
    setDisplayState(::android::hardware::automotive::evs::V1_0::DisplayState state) override {
        return ::android::hardware::automotive::evs::V1_0::EvsResult::OK;
    }
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::DisplayState>
    getDisplayState() override {
        return ::android::hardware::automotive::evs::V1_0::DisplayState::VISIBLE;
    }
    ::android::hardware::Return<void> getTargetBuffer(getTargetBuffer_cb _hidl_cb) override {
        return {};
    }
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>
    returnTargetBufferForDisplay(
            const ::android::hardware::automotive::evs::V1_0::BufferDesc& buffer) override {
        return ::android::hardware::automotive::evs::V1_0::EvsResult::OK;
    }
    ::android::hardware::Return<void> getDisplayInfo_1_1(getDisplayInfo_1_1_cb _info_cb) override {
        return {};
    }
};

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_TEST_FUZZER_MOCKHWDISPLAY_H_
