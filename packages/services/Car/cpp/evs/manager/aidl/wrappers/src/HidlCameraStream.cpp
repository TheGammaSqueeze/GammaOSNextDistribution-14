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

#include "HidlCameraStream.h"

#include "HidlCamera.h"
#include "utils/include/Utils.h"

#include <android-base/logging.h>
#include <utils/SystemClock.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Status;
using ::ndk::ScopedAStatus;

Return<void> HidlCameraStream::deliverFrame(const hidlevs::V1_0::BufferDesc& buffer) {
    if (!mAidlStream) {
        LOG(ERROR) << "A reference to AIDL IEvsCameraStream is invalid.";
        return {};
    }

    std::vector<BufferDesc> aidlBuffers(1);
    aidlBuffers[0] = std::move(Utils::makeFromHidl(buffer, /* doDup= */ true));

    // android::hardware::automotive::evs::V1_0::BufferDesc does not contain a
    // timestamp so we need to fill it here.
    aidlBuffers[0].timestamp = static_cast<int64_t>(::android::elapsedRealtimeNano() * 1e+3);
    aidlBuffers[0].deviceId = mSourceDeviceId;

    mHidlV0Buffers.push_back(buffer);
    auto aidlStatus = mAidlStream->deliverFrame(std::move(aidlBuffers));
    if (!aidlStatus.isOk()) {
        LOG(ERROR) << "Failed to forward frames to AIDL client";
    }

    return {};
}

Return<void> HidlCameraStream::deliverFrame_1_1(
        const hidl_vec<hidlevs::V1_1::BufferDesc>& buffers) {
    if (!mAidlStream) {
        LOG(ERROR) << "A reference to AIDL IEvsCameraStream is invalid.";
        return {};
    }

    std::vector<BufferDesc> aidlBuffers(buffers.size());
    for (auto i = 0; i < buffers.size(); ++i) {
        hidlevs::V1_1::BufferDesc buffer = std::move(buffers[i]);
        aidlBuffers[i] = std::move(Utils::makeFromHidl(buffer, /* doDup= */ true));
        mHidlV1Buffers.push_back(std::move(buffer));
    }

    if (!mAidlStream->deliverFrame(std::move(aidlBuffers)).isOk()) {
        LOG(ERROR) << "Failed to forward frames to AIDL client";
    }

    return {};
}

Return<void> HidlCameraStream::notify(const hidlevs::V1_1::EvsEventDesc& event) {
    if (!mAidlStream) {
        LOG(ERROR) << "A reference to AIDL IEvsCameraStream is invalid.";
        return {};
    }

    if (!mAidlStream->notify(std::move(Utils::makeFromHidl(event))).isOk()) {
        LOG(ERROR) << "Failed to forward events to AIDL client";
    }

    return {};
}

bool HidlCameraStream::getHidlBuffer(int id, hidlevs::V1_0::BufferDesc* _return) {
    auto it = std::find_if(mHidlV0Buffers.begin(), mHidlV0Buffers.end(),
                           [id](const hidlevs::V1_0::BufferDesc& buffer) {
                               return id == buffer.bufferId;
                           });
    if (it == mHidlV0Buffers.end()) {
        return false;
    }

    *_return = std::move(*it);
    mHidlV0Buffers.erase(it);
    return true;
}

bool HidlCameraStream::getHidlBuffer(int id, hidlevs::V1_1::BufferDesc* _return) {
    auto it = std::find_if(mHidlV1Buffers.begin(), mHidlV1Buffers.end(),
                           [id](const hidlevs::V1_1::BufferDesc& buffer) {
                               return id == buffer.bufferId;
                           });
    if (it == mHidlV1Buffers.end()) {
        return false;
    }

    *_return = std::move(*it);
    mHidlV1Buffers.erase(it);
    return true;
}

}  // namespace aidl::android::automotive::evs::implementation
