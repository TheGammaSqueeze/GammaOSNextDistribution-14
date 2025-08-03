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

#include "HidlEnumerator.h"

#include "Constants.h"
#include "HidlCamera.h"
#include "HidlDisplay.h"
#include "utils/include/Utils.h"

#include <aidl/android/hardware/automotive/evs/CameraDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayState.h>
#include <aidl/android/hardware/automotive/evs/Rotation.h>
#include <aidl/android/hardware/automotive/evs/Stream.h>
#include <aidl/android/hardware/automotive/evs/StreamType.h>
#include <android-base/logging.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::IEvsCamera;
using ::aidl::android::hardware::automotive::evs::IEvsDisplay;
using ::aidl::android::hardware::automotive::evs::Stream;
using ::aidl::android::hardware::graphics::common::PixelFormat;
using ::android::hardware::hidl_string;
using ::android::hardware::Return;
using ::android::hardware::Status;

HidlEnumerator::~HidlEnumerator() {
    mEnumerator = nullptr;
}

Return<void> HidlEnumerator::getCameraList(getCameraList_cb _hidl_cb) {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        _hidl_cb({});
        return {};
    }

    std::vector<CameraDesc> aidlCameras;
    if (auto status = mEnumerator->getCameraList(&aidlCameras); !status.isOk()) {
        LOG(ERROR) << "Failed to get a list of cameras, status = "
                   << status.getServiceSpecificError();
        _hidl_cb({});
        return {};
    }

    ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> hidlCameras(aidlCameras.size());
    for (auto i = 0; i < aidlCameras.size(); ++i) {
        hidlCameras[i] = Utils::makeToHidlV1_0(aidlCameras[i]);
    }

    _hidl_cb(hidlCameras);
    return {};
}

Return<::android::sp<hidlevs::V1_0::IEvsCamera>> HidlEnumerator::openCamera(
        const hidl_string& cameraId) {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        return nullptr;
    }

    std::shared_ptr<IEvsCamera> aidlCamera;
    // IEvsEnumerator will open a camera with its default configuration.
    auto status = mEnumerator->openCamera(cameraId, {}, &aidlCamera);
    if (!status.isOk()) {
        LOG(ERROR) << "Failed to open a camera " << cameraId;
        return nullptr;
    }

    auto hidlCamera = new (std::nothrow) HidlCamera(aidlCamera);
    if (hidlCamera == nullptr) {
        LOG(ERROR) << "Failed to open a camera " << cameraId;
        return nullptr;
    }

    return hidlCamera;
}

Return<void> HidlEnumerator::closeCamera(
        const ::android::sp<hidlevs::V1_0::IEvsCamera>& cameraObj) {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        return {};
    }

    if (!cameraObj) {
        LOG(WARNING) << "Ignoring a call with an invalid camera object";
        return {};
    }

    auto hidlCamera = reinterpret_cast<HidlCamera*>(cameraObj.get());
    mEnumerator->closeCamera(hidlCamera->getAidlCamera());
    return {};
}

Return<::android::sp<hidlevs::V1_0::IEvsDisplay>> HidlEnumerator::openDisplay() {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        return nullptr;
    }

    auto displayId = kDisplayIdUnavailable;
    if (mAidlDisplayIds.empty()) {
        if (auto status = mEnumerator->getDisplayIdList(&mAidlDisplayIds);
            !status.isOk() || mAidlDisplayIds.empty()) {
            LOG(WARNING) << "Use the default display ID because we failed to get a display list.";
        } else {
            displayId = mAidlDisplayIds[0];
        }
    }

    std::shared_ptr<IEvsDisplay> aidlDisplay;
    if (auto status = mEnumerator->openDisplay(displayId, &aidlDisplay); !status.isOk()) {
        LOG(ERROR) << "Failed to open a display " << displayId;
        return nullptr;
    }

    HidlDisplay* hidlDisplay = new (std::nothrow) HidlDisplay(aidlDisplay);
    if (hidlDisplay == nullptr) {
        LOG(ERROR) << "Failed to open a display " << displayId;
        return nullptr;
    }

    mAidlDisplay = aidlDisplay;
    mHidlDisplay = hidlDisplay;
    return hidlDisplay;
}

Return<void> HidlEnumerator::closeDisplay(
        const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        return {};
    }

    if (display != mHidlDisplay.promote()) {
        LOG(DEBUG) << "Ignores an invalid request to close the display";
        return {};
    }

    mEnumerator->closeDisplay(mAidlDisplay.lock());
    return {};
}

Return<hidlevs::V1_0::DisplayState> HidlEnumerator::getDisplayState() {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        return hidlevs::V1_0::DisplayState::DEAD;
    }

    DisplayState aidlState;
    if (auto status = mEnumerator->getDisplayState(&aidlState); !status.isOk()) {
        return hidlevs::V1_0::DisplayState::NOT_OPEN;
    }

    return Utils::makeToHidl(aidlState);
}

// Methods from hardware::automotive::evs::V1_1::IEvsEnumerator follow.
Return<void> HidlEnumerator::getCameraList_1_1(getCameraList_1_1_cb _hidl_cb) {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        _hidl_cb({});
        return {};
    }

    std::vector<CameraDesc> aidlCameras;
    if (auto status = mEnumerator->getCameraList(&aidlCameras); !status.isOk()) {
        LOG(ERROR) << "Failed to get a list of cameras, status = "
                   << status.getServiceSpecificError();
        _hidl_cb({});
        return {};
    }

    ::android::hardware::hidl_vec<hidlevs::V1_1::CameraDesc> hidlCameras(aidlCameras.size());
    for (auto i = 0; i < aidlCameras.size(); ++i) {
        hidlCameras[i] = Utils::makeToHidlV1_1(aidlCameras[i]);
    }

    _hidl_cb(hidlCameras);
    return {};
}

Return<::android::sp<hidlevs::V1_1::IEvsCamera>> HidlEnumerator::openCamera_1_1(
        const hidl_string& cameraId,
        const ::android::hardware::camera::device::V3_2::Stream& hidlCfg) {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        return nullptr;
    }

    Stream cfg = std::move(Utils::makeFromHidl(hidlCfg));
    std::shared_ptr<IEvsCamera> aidlCamera;
    if (auto status = mEnumerator->openCamera(cameraId, cfg, &aidlCamera); !status.isOk()) {
        LOG(ERROR) << "Failed to open a camera " << cameraId
                   << ", error = " << status.getServiceSpecificError();
        return nullptr;
    }

    auto hidlCamera = new (std::nothrow) HidlCamera(aidlCamera);
    if (hidlCamera == nullptr) {
        LOG(ERROR) << "Failed to create a HidlCamera object " << cameraId;
        return nullptr;
    }

    return hidlCamera;
}

Return<void> HidlEnumerator::getDisplayIdList(getDisplayIdList_cb _list_cb) {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        _list_cb({});
        return {};
    }

    if (auto status = mEnumerator->getDisplayIdList(&mAidlDisplayIds); !status.isOk()) {
        LOG(ERROR) << "Failed to get a display list";
        _list_cb({});
        return {};
    }

    _list_cb(mAidlDisplayIds);
    return {};
}

Return<::android::sp<hidlevs::V1_1::IEvsDisplay>> HidlEnumerator::openDisplay_1_1(uint8_t id) {
    if (!mEnumerator) {
        LOG(ERROR) << "A reference to AIDL IEvsEnumerator is invalid.";
        return nullptr;
    }

    std::shared_ptr<IEvsDisplay> aidlDisplay;
    auto status = mEnumerator->openDisplay(id, &aidlDisplay);
    if (!status.isOk()) {
        LOG(ERROR) << "Failed to open a display " << id;
        return nullptr;
    }

    HidlDisplay* hidlDisplay = new (std::nothrow) HidlDisplay(aidlDisplay);
    if (hidlDisplay == nullptr) {
        LOG(ERROR) << "Failed to open a display " << id;
        return nullptr;
    }

    mAidlDisplay = aidlDisplay;
    mHidlDisplay = hidlDisplay;
    return hidlDisplay;
}

Return<void> HidlEnumerator::getUltrasonicsArrayList(
        [[maybe_unused]] getUltrasonicsArrayList_cb _hidl_cb) {
    // TODO(b/149874793): Add implementation for EVS Manager and Sample driver
    _hidl_cb({});
    return {};
}

Return<::android::sp<hidlevs::V1_1::IEvsUltrasonicsArray>> HidlEnumerator::openUltrasonicsArray(
        [[maybe_unused]] const hidl_string& ultrasonicsArrayId) {
    // TODO(b/149874793): Add implementation for EVS Manager and Sample driver
    return nullptr;
}

Return<void> HidlEnumerator::closeUltrasonicsArray(
        [[maybe_unused]] const ::android::sp<hidlevs::V1_1::IEvsUltrasonicsArray>&
                evsUltrasonicsArray) {
    // TODO(b/149874793): Add implementation for EVS Manager and Sample driver
    return {};
}

}  // namespace aidl::android::automotive::evs::implementation
