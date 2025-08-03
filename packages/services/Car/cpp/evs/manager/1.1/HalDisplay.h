/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef CPP_EVS_MANAGER_1_1_HALDISPLAY_H_
#define CPP_EVS_MANAGER_1_1_HALDISPLAY_H_

#include <android/hardware/automotive/evs/1.1/IEvsDisplay.h>
#include <android/hardware/automotive/evs/1.1/types.h>

#include <limits>

namespace android::automotive::evs::V1_1::implementation {

// TODO(129284474): This class has been defined to wrap the IEvsDisplay object the driver
// returns because of b/129284474 and represents an EVS display to the client
// application.  With a proper bug fix, we may remove this class and update the
// manager directly to use the IEvsDisplay object the driver provides.
class HalDisplay : public ::android::hardware::automotive::evs::V1_1::IEvsDisplay {
public:
    explicit HalDisplay(sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay> display,
                        int32_t port = std::numeric_limits<int32_t>::min());
    virtual ~HalDisplay();

    inline void shutdown();
    sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay> getHwDisplay();

    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsDisplay follow.
    ::android::hardware::Return<void> getDisplayInfo(getDisplayInfo_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>
    setDisplayState(::android::hardware::automotive::evs::V1_0::DisplayState state) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::DisplayState>
    getDisplayState() override;
    ::android::hardware::Return<void> getTargetBuffer(getTargetBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>
    returnTargetBufferForDisplay(
            const ::android::hardware::automotive::evs::V1_0::BufferDesc& buffer) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsDisplay follow.
    ::android::hardware::Return<void> getDisplayInfo_1_1(getDisplayInfo_1_1_cb _info_cb) override;

    // ::android::hardware::Returns a string showing the current status
    std::string toString(const char* indent = "");

private:
    sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>
            mHwDisplay;  // The low level display interface that backs this proxy
    int32_t mId;         // Display identifier
};

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_HALDISPLAY_H_
