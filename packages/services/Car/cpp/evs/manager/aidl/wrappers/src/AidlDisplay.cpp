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

#include "AidlDisplay.h"

#include "utils/include/Utils.h"

#include <aidl/android/hardware/automotive/evs/Rotation.h>
#include <android-base/logging.h>
#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::DisplayDesc;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::Rotation;
using ::ndk::ScopedAStatus;

AidlDisplay::~AidlDisplay() {
    // simply release a shared pointer to remote display object.
    mHidlDisplay = nullptr;
}

/**
 * Gets basic display information from a hardware display object and returns.
 */
ScopedAStatus AidlDisplay::getDisplayInfo(DisplayDesc* _aidl_return) {
    if (!mHidlDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    mHidlDisplay->getDisplayInfo([_aidl_return](const hidlevs::V1_0::DisplayDesc& info) {
        _aidl_return->id = info.displayId;
        _aidl_return->vendorFlags = info.vendorFlags;
    });

    auto halV1_1 = hidlevs::V1_1::IEvsDisplay::castFrom(mHidlDisplay).withDefault(nullptr);
    if (!halV1_1) {
        // Additional display information is not available if the system runs
        // HIDL EVS v1.0 implementation.
        return ScopedAStatus::ok();
    }

    halV1_1->getDisplayInfo_1_1([_aidl_return](const auto& hidlMode, const auto& hidlState) {
        const ::android::ui::DisplayMode* pMode =
                reinterpret_cast<const ::android::ui::DisplayMode*>(hidlMode.data());
        const ::android::ui::DisplayState* pState =
                reinterpret_cast<const ::android::ui::DisplayState*>(hidlState.data());
        _aidl_return->width = pMode->resolution.getWidth();
        _aidl_return->height = pMode->resolution.getHeight();
        _aidl_return->orientation = static_cast<Rotation>(pState->orientation);
    });
    return ScopedAStatus::ok();
}

/**
 * Gets current display state from a hardware display object and return.
 */
ScopedAStatus AidlDisplay::getDisplayState(DisplayState* _aidl_return) {
    if (!mHidlDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    *_aidl_return = std::move(Utils::makeFromHidl(mHidlDisplay->getDisplayState()));
    return ScopedAStatus::ok();
}

/**
 * Returns a handle to a frame buffer associated with the display.
 */
ScopedAStatus AidlDisplay::getTargetBuffer(BufferDesc* _aidl_return) {
    if (!mHidlDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    mHidlDisplay->getTargetBuffer([this, _aidl_return](auto& hidlBuffer) {
        *_aidl_return = std::move(Utils::makeFromHidl(hidlBuffer, /* doDup= */ true));
        mHeldBuffer = std::move(hidlBuffer);
    });
    return ScopedAStatus::ok();
}

/**
 * Notifies the display that the buffer is ready to be used.
 */
ScopedAStatus AidlDisplay::returnTargetBufferForDisplay(const BufferDesc& buffer) {
    if (!mHidlDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    if (buffer.bufferId != mHeldBuffer.bufferId) {
        LOG(WARNING) << "Ignores a request to return a buffer " << buffer.bufferId << "; a buffer "
                     << mHeldBuffer.bufferId << " is held.";
        return ScopedAStatus::ok();
    }

    return Utils::buildScopedAStatusFromEvsResult(
            mHidlDisplay->returnTargetBufferForDisplay(mHeldBuffer));
}

/**
 * Sets the display state as what the clients wants.
 */
ScopedAStatus AidlDisplay::setDisplayState(DisplayState state) {
    if (!mHidlDisplay) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return Utils::buildScopedAStatusFromEvsResult(
            mHidlDisplay->setDisplayState(std::move(Utils::makeToHidl(state))));
}

}  // namespace aidl::android::automotive::evs::implementation
