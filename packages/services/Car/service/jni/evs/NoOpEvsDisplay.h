/*
 * Copyright 2023 The Android Open Source Project
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

#include <aidl/android/hardware/automotive/evs/BnEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayState.h>

namespace android::automotive::evs {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;

class NoOpEvsDisplay final : public ::aidl::android::hardware::automotive::evs::BnEvsDisplay {
public:
    // Methods from ::aidl::android::hardware::automotive::evs::IEvsDisplay follow.
    ndk::ScopedAStatus getDisplayInfo(aidlevs::DisplayDesc*) override {
        return ndk::ScopedAStatus::ok();
    };
    ndk::ScopedAStatus getDisplayState(aidlevs::DisplayState*) override {
        return ndk::ScopedAStatus::ok();
    };
    ndk::ScopedAStatus getTargetBuffer(aidlevs::BufferDesc*) override {
        return ndk::ScopedAStatus::ok();
    };
    ndk::ScopedAStatus returnTargetBufferForDisplay(const aidlevs::BufferDesc&) override {
        return ndk::ScopedAStatus::ok();
    };
    ndk::ScopedAStatus setDisplayState(aidlevs::DisplayState) override {
        return ndk::ScopedAStatus::ok();
    };

    // Implementation details
    virtual ~NoOpEvsDisplay() override = default;
};

}  // namespace android::automotive::evs
