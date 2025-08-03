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

#include "HalDisplay.h"

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>

#include <inttypes.h>

using ::android::base::StringAppendF;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::automotive::evs::V1_0::EvsResult;
using BufferDesc_1_0 = ::android::hardware::automotive::evs::V1_0::BufferDesc;
using EvsDisplayState = ::android::hardware::automotive::evs::V1_0::DisplayState;
using IEvsDisplay_1_0 = ::android::hardware::automotive::evs::V1_0::IEvsDisplay;
using IEvsDisplay_1_1 = ::android::hardware::automotive::evs::V1_1::IEvsDisplay;

namespace android::automotive::evs::V1_1::implementation {

HalDisplay::HalDisplay(sp<IEvsDisplay_1_0> display, int32_t id) : mHwDisplay(display), mId(id) {}

HalDisplay::~HalDisplay() {
    shutdown();
}

void HalDisplay::shutdown() {
    // simply release a strong pointer to remote display object.
    mHwDisplay = nullptr;
}

/**
 * Returns a strong pointer to remote display object.
 */
sp<IEvsDisplay_1_0> HalDisplay::getHwDisplay() {
    return mHwDisplay;
}

/**
 * Gets basic display information from a hardware display object
 * and returns.
 */
Return<void> HalDisplay::getDisplayInfo(getDisplayInfo_cb _hidl_cb) {
    if (mHwDisplay) {
        mHwDisplay->getDisplayInfo(_hidl_cb);
    }

    return Void();
}

/**
 * Sets the display state as what the clients wants.
 */
Return<EvsResult> HalDisplay::setDisplayState(EvsDisplayState state) {
    if (mHwDisplay) {
        return mHwDisplay->setDisplayState(state);
    } else {
        return EvsResult::UNDERLYING_SERVICE_ERROR;
    }
}

/**
 * Gets current display state from a hardware display object and return.
 */
Return<EvsDisplayState> HalDisplay::getDisplayState() {
    if (mHwDisplay) {
        return mHwDisplay->getDisplayState();
    } else {
        return EvsDisplayState::DEAD;
    }
}

/**
 * Returns a handle to a frame buffer associated with the display.
 */
Return<void> HalDisplay::getTargetBuffer(getTargetBuffer_cb _hidl_cb) {
    if (mHwDisplay) {
        mHwDisplay->getTargetBuffer(_hidl_cb);
    }

    return Void();
}

/**
 * Notifies the display that the buffer is ready to be used.
 */
Return<EvsResult> HalDisplay::returnTargetBufferForDisplay(const BufferDesc_1_0& buffer) {
    if (mHwDisplay) {
        return mHwDisplay->returnTargetBufferForDisplay(buffer);
    } else {
        return EvsResult::OWNERSHIP_LOST;
    }
}

/**
 * Gets basic display information from a hardware display object
 * and returns.
 */
Return<void> HalDisplay::getDisplayInfo_1_1(getDisplayInfo_1_1_cb _info_cb) {
    sp<IEvsDisplay_1_1> display = IEvsDisplay_1_1::castFrom(mHwDisplay).withDefault(nullptr);
    if (display != nullptr) {
        display->getDisplayInfo_1_1(_info_cb);
    }

    return Void();
}

std::string HalDisplay::toString(const char* indent) {
    std::string buffer;
    android::ui::DisplayMode displayMode;
    android::ui::DisplayState displayState;

    if (mId == std::numeric_limits<int32_t>::min()) {
        // Display identifier has not set
        StringAppendF(&buffer, "HalDisplay: Display port is unknown.\n");
    } else {
        StringAppendF(&buffer, "HalDisplay: Display port %" PRId32 "\n", mId);
    }

    getDisplayInfo_1_1([&](auto& config, auto& state) {
        displayMode = *(reinterpret_cast<const android::ui::DisplayMode*>(config.data()));
        displayState = *(reinterpret_cast<const android::ui::DisplayState*>(state.data()));
    });

    StringAppendF(&buffer, "%sWidth: %" PRId32 "\n", indent, displayMode.resolution.getWidth());
    StringAppendF(&buffer, "%sHeight: %" PRId32 "\n", indent, displayMode.resolution.getHeight());
    StringAppendF(&buffer, "%sRefresh rate: %f\n", indent, displayMode.peakRefreshRate);
    StringAppendF(&buffer, "%sRotation: %" PRId32 "\n", indent,
                  static_cast<int32_t>(displayState.orientation));

    return buffer;
}

}  // namespace android::automotive::evs::V1_1::implementation
