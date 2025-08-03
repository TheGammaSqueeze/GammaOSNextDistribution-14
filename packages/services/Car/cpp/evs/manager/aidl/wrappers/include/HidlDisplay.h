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

#ifndef CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLDISPLAY_H
#define CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLDISPLAY_H

#include <aidl/android/hardware/automotive/evs/BnEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayState.h>
#include <android/hardware/automotive/evs/1.1/IEvsDisplay.h>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;
namespace hidlevs = ::android::hardware::automotive::evs;

class HidlDisplay final : public hidlevs::V1_1::IEvsDisplay {
public:
    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsDisplay follow.
    ::android::hardware::Return<void> getDisplayInfo(getDisplayInfo_cb _hidl_cb) override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> setDisplayState(
            hidlevs::V1_0::DisplayState state) override;
    ::android::hardware::Return<hidlevs::V1_0::DisplayState> getDisplayState() override;
    ::android::hardware::Return<void> getTargetBuffer(getTargetBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> returnTargetBufferForDisplay(
            const hidlevs::V1_0::BufferDesc& buffer) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsDisplay follow.
    ::android::hardware::Return<void> getDisplayInfo_1_1(getDisplayInfo_1_1_cb _hidl_cb) override;

    explicit HidlDisplay(const std::shared_ptr<aidlevs::IEvsDisplay>& display) :
          mAidlDisplay(display){};
    virtual ~HidlDisplay();

    const std::shared_ptr<aidlevs::IEvsDisplay> getAidlDisplay() const { return mAidlDisplay; }

private:
    // The low level display interface that backs this proxy
    std::shared_ptr<aidlevs::IEvsDisplay> mAidlDisplay;
    aidlevs::BufferDesc mHeldBuffer;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLDISPLAY_H
