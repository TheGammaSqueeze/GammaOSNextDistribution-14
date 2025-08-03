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

#include "HalDisplay.h"

#include "utils/include/Utils.h"

#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayState.h>
#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>

#include <cinttypes>

namespace aidl::android::automotive::evs::implementation {

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::DisplayDesc;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsDisplay;
using ::android::base::StringAppendF;
using ::ndk::ScopedAStatus;

HalDisplay::HalDisplay(std::shared_ptr<IEvsDisplay> display, int32_t id) :
      mHwDisplay(display), mId(id) {
    // nothing to do.
}

HalDisplay::~HalDisplay() {
    shutdown();
}

void HalDisplay::shutdown() {
    // simply release a shared pointer to remote display object.
    mHwDisplay.reset();
}

/**
 * Returns a shared pointer to remote display object.
 */
std::shared_ptr<IEvsDisplay> HalDisplay::getHwDisplay() {
    return mHwDisplay;
}

/**
 * Gets basic display information from a hardware display object
 * and returns.
 */
ScopedAStatus HalDisplay::getDisplayInfo(DisplayDesc* _aidl_return) {
    if (!mHwDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return mHwDisplay->getDisplayInfo(_aidl_return);
}

/**
 * Gets current display state from a hardware display object and return.
 */
ScopedAStatus HalDisplay::getDisplayState(DisplayState* _aidl_return) {
    if (!mHwDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return mHwDisplay->getDisplayState(_aidl_return);
}

/**
 * Returns a handle to a frame buffer associated with the display.
 */
ScopedAStatus HalDisplay::getTargetBuffer(BufferDesc* _aidl_return) {
    if (!mHwDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return mHwDisplay->getTargetBuffer(_aidl_return);
}

/**
 * Notifies the display that the buffer is ready to be used.
 */
ScopedAStatus HalDisplay::returnTargetBufferForDisplay(const BufferDesc& buffer) {
    if (!mHwDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return mHwDisplay->returnTargetBufferForDisplay(buffer);
}

/**
 * Sets the display state as what the clients wants.
 */
ScopedAStatus HalDisplay::setDisplayState(DisplayState state) {
    if (!mHwDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return mHwDisplay->setDisplayState(state);
}

std::string HalDisplay::toString(const char* indent) {
    std::string buffer;
    if (mId == kDisplayIdUnavailable) {
        // Display identifier has not set
        StringAppendF(&buffer, "HalDisplay: Display port is unknown.\n");
    } else {
        StringAppendF(&buffer, "HalDisplay: Display port %" PRId32 "\n", mId);
    }

    DisplayDesc displayDesc;
    auto status = getDisplayInfo(&displayDesc);
    if (status.isOk()) {
        StringAppendF(&buffer, "%sWidth: %" PRId32 "\n", indent, displayDesc.width);
        StringAppendF(&buffer, "%sHeight: %" PRId32 "\n", indent, displayDesc.height);
        StringAppendF(&buffer, "%sRotation: %" PRId32 "\n", indent,
                      static_cast<int32_t>(displayDesc.orientation));
    }

    return buffer;
}

}  // namespace aidl::android::automotive::evs::implementation
