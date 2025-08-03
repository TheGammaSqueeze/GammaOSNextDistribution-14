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

#include "HidlDisplay.h"

#include "utils/include/Utils.h"

#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayState.h>
#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <android-base/logging.h>
#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::DisplayDesc;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::android::hardware::Return;

HidlDisplay::~HidlDisplay() {
    // simply release a shared pointer to remote display object.
    mAidlDisplay = nullptr;
}

Return<void> HidlDisplay::getDisplayInfo(getDisplayInfo_cb _hidl_cb) {
    if (!mAidlDisplay) {
        LOG(ERROR) << "A reference to AIDL IEvsDisplay is invalid.";
        _hidl_cb({});
        return {};
    }

    DisplayDesc aidlDesc;
    if (auto status = mAidlDisplay->getDisplayInfo(&aidlDesc); !status.isOk()) {
        LOG(ERROR) << "Failed to read a display information";
        _hidl_cb({});
        return {};
    }

    hidlevs::V1_0::DisplayDesc hidlDesc = {
            .displayId = aidlDesc.id,
            .vendorFlags = static_cast<uint32_t>(aidlDesc.vendorFlags),
    };
    _hidl_cb(std::move(hidlDesc));
    return {};
}

Return<hidlevs::V1_0::EvsResult> HidlDisplay::setDisplayState(hidlevs::V1_0::DisplayState state) {
    if (!mAidlDisplay) {
        LOG(ERROR) << "A reference to AIDL IEvsDisplay is invalid.";
        return hidlevs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR;
    }

    if (auto status = mAidlDisplay->setDisplayState(Utils::makeFromHidl(state)); !status.isOk()) {
        return Utils::makeToHidl(static_cast<EvsResult>(status.getServiceSpecificError()));
    }
    return hidlevs::V1_0::EvsResult::OK;
}

Return<hidlevs::V1_0::DisplayState> HidlDisplay::getDisplayState() {
    if (!mAidlDisplay) {
        LOG(ERROR) << "A reference to AIDL IEvsDisplay is invalid.";
        return Utils::makeToHidl(DisplayState::DEAD);
    }

    DisplayState aidlState;
    if (auto status = mAidlDisplay->getDisplayState(&aidlState); !status.isOk()) {
        return Utils::makeToHidl(DisplayState::DEAD);
    }

    return Utils::makeToHidl(aidlState);
}

Return<void> HidlDisplay::getTargetBuffer(getTargetBuffer_cb _hidl_cb) {
    if (!mAidlDisplay) {
        LOG(ERROR) << "A reference to AIDL IEvsDisplay is invalid.";
        _hidl_cb({});
        return {};
    }

    BufferDesc aidlBuffer;
    auto status = mAidlDisplay->getTargetBuffer(&aidlBuffer);
    if (!status.isOk()) {
        LOG(ERROR) << "Failed to get a target buffer";
        _hidl_cb({});
        return {};
    }

    // We already own a copy of a buffer handle so do not need to duplicate it
    // again.
    hidlevs::V1_0::BufferDesc hidlBuffer = Utils::makeToHidlV1_0(aidlBuffer, /* doDup = */ false);
    mHeldBuffer = std::move(aidlBuffer);
    _hidl_cb(hidlBuffer);
    return {};
}

Return<hidlevs::V1_0::EvsResult> HidlDisplay::returnTargetBufferForDisplay(
        const hidlevs::V1_0::BufferDesc& buffer) {
    if (!mAidlDisplay) {
        LOG(ERROR) << "A reference to AIDL IEvsDisplay is invalid.";
        return hidlevs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR;
    }

    if (buffer.bufferId != mHeldBuffer.bufferId) {
        LOG(WARNING) << "Ignores a request to return a buffer " << buffer.bufferId << "; a buffer "
                     << mHeldBuffer.bufferId << " is held.";
    } else {
        auto status = mAidlDisplay->returnTargetBufferForDisplay(std::move(mHeldBuffer));
        if (!status.isOk()) {
            LOG(WARNING) << "Failed to return a buffer " << mHeldBuffer.bufferId;
        }
    }

    return hidlevs::V1_0::EvsResult::OK;
}

Return<void> HidlDisplay::getDisplayInfo_1_1(getDisplayInfo_1_1_cb _hidl_cb) {
    ::android::hardware::hidl_vec<uint8_t> hidlMode(sizeof(::android::ui::DisplayMode));
    ::android::hardware::hidl_vec<uint8_t> hidlState(sizeof(::android::ui::DisplayState));

    if (!mAidlDisplay) {
        LOG(ERROR) << "A reference to AIDL IEvsDisplay is invalid.";
        _hidl_cb(hidlMode, hidlState);
        return {};
    }

    DisplayDesc aidlDesc;
    if (auto status = mAidlDisplay->getDisplayInfo(&aidlDesc); !status.isOk()) {
        LOG(ERROR) << "Failed to read a display information";
        _hidl_cb(hidlMode, hidlState);
        return {};
    }

    ::android::ui::DisplayMode* pMode =
            reinterpret_cast<::android::ui::DisplayMode*>(hidlMode.data());
    ::android::ui::DisplayState* pState =
            reinterpret_cast<::android::ui::DisplayState*>(hidlState.data());
    pMode->resolution.width = aidlDesc.width;
    pMode->resolution.height = aidlDesc.height;
    pState->orientation = static_cast<::android::ui::Rotation>(aidlDesc.orientation);

    _hidl_cb(hidlMode, hidlState);
    return {};
}

}  // namespace aidl::android::automotive::evs::implementation
