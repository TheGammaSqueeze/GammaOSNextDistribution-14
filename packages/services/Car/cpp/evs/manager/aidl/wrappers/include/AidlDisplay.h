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

#ifndef CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLHWDISPLAY_H
#define CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLHWDISPLAY_H

#include <aidl/android/hardware/automotive/evs/BnEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayState.h>
#include <android/hardware/automotive/evs/1.1/IEvsDisplay.h>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;
namespace hidlevs = ::android::hardware::automotive::evs;

class AidlDisplay final : public ::aidl::android::hardware::automotive::evs::BnEvsDisplay {
public:
    // Methods from ::aidl::android::hardware::automotive::evs::IEvsDisplay follow.
    ::ndk::ScopedAStatus getDisplayInfo(aidlevs::DisplayDesc* _aidl_return) override;
    ::ndk::ScopedAStatus getDisplayState(aidlevs::DisplayState* _aidl_return) override;
    ::ndk::ScopedAStatus getTargetBuffer(aidlevs::BufferDesc* _aidl_return) override;
    ::ndk::ScopedAStatus returnTargetBufferForDisplay(const aidlevs::BufferDesc& buffer) override;
    ::ndk::ScopedAStatus setDisplayState(aidlevs::DisplayState state) override;

    explicit AidlDisplay(const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) :
          mHidlDisplay(display){};
    virtual ~AidlDisplay();

    const ::android::sp<hidlevs::V1_0::IEvsDisplay> getHidlDisplay() const { return mHidlDisplay; }

private:
    // The low level display interface that backs this proxy
    ::android::sp<hidlevs::V1_0::IEvsDisplay> mHidlDisplay;
    hidlevs::V1_0::BufferDesc mHeldBuffer;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLHWDISPLAY_H
