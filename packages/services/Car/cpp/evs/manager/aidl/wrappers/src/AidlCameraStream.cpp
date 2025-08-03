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

#include "AidlCameraStream.h"

#include "utils/include/Utils.h"

#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsEventType.h>
#include <android-base/logging.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventType;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::ndk::ScopedAStatus;

AidlCameraStream::AidlCameraStream(
        const ::android::sp<hidlevs::V1_0::IEvsCameraStream>& hidlStream) {
    auto hidlStreamV1 = hidlevs::V1_1::IEvsCameraStream::castFrom(hidlStream).withDefault(nullptr);
    if (!hidlStreamV1) {
        mImpl = std::make_shared<ImplV0>(hidlStream);
    } else {
        mImpl = std::make_shared<ImplV1>(hidlStreamV1);
    }
}

ScopedAStatus AidlCameraStream::deliverFrame(const std::vector<BufferDesc>& buffers) {
    return mImpl->deliverFrame(buffers);
}

ScopedAStatus AidlCameraStream::notify(const EvsEventDesc& event) {
    return mImpl->notify(event);
}

bool AidlCameraStream::getBuffer(int id, BufferDesc* _return) {
    return mImpl->getBuffer(id, _return);
}

bool AidlCameraStream::IHidlCameraStream::getBuffer(int id, BufferDesc* _return) {
    auto it = std::find_if(mBuffers.begin(), mBuffers.end(),
                           [id](const BufferDesc& buffer) { return id == buffer.bufferId; });
    if (it == mBuffers.end()) {
        return false;
    }

    *_return = std::move(*it);
    mBuffers.erase(it);
    return true;
}

AidlCameraStream::ImplV0::ImplV0(const ::android::sp<hidlevs::V1_0::IEvsCameraStream>& stream) :
      IHidlCameraStream(stream) {}

ScopedAStatus AidlCameraStream::ImplV0::deliverFrame(const std::vector<BufferDesc>& buffers) {
    if (!mStream) {
        return ScopedAStatus::fromServiceSpecificError(
                static_cast<int>(EvsResult::RESOURCE_NOT_AVAILABLE));
    }

    auto hidlBuffer = Utils::makeToHidlV1_0(buffers[0], /* doDup= */ false);
    mBuffers.push_back(std::move(Utils::dupBufferDesc(buffers[0], /* doDup= */ true)));
    if (auto status = mStream->deliverFrame(std::move(hidlBuffer)); !status.isOk()) {
        LOG(ERROR) << "Failed to forward a frame to HIDL v1.0 client";
        return ScopedAStatus::fromStatus(STATUS_FAILED_TRANSACTION);
    }

    return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraStream::ImplV0::notify(const EvsEventDesc& event) {
    if (!mStream) {
        return ScopedAStatus::fromServiceSpecificError(
                static_cast<int>(EvsResult::RESOURCE_NOT_AVAILABLE));
    }

    switch (event.aType) {
        case EvsEventType::STREAM_STOPPED:
            if (auto status = mStream->deliverFrame({}); !status.isOk()) {
                LOG(ERROR) << "Error delivering the end of stream marker";
                return ScopedAStatus::fromStatus(STATUS_FAILED_TRANSACTION);
            }
            break;

        default:
            // HIDL v1.0 interface does not support events
            LOG(INFO) << "Event " << toString(event.aType)
                      << " is received but ignored for HIDL v1.0 client";
            break;
    }

    return ScopedAStatus::ok();
}

AidlCameraStream::ImplV1::ImplV1(const ::android::sp<hidlevs::V1_1::IEvsCameraStream>& stream) :
      IHidlCameraStream(stream), mStream(stream) {}

ScopedAStatus AidlCameraStream::ImplV1::deliverFrame(const std::vector<BufferDesc>& buffers) {
    const auto n = buffers.size();
    ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc> hidlBuffers(n);
    for (auto i = 0; i < n; ++i) {
        BufferDesc buffer = std::move(Utils::dupBufferDesc(buffers[i], /* doDup= */ true));
        hidlBuffers[i] = std::move(Utils::makeToHidlV1_1(buffer, /* doDup= */ false));
        mBuffers.push_back(std::move(buffer));
    }

    if (auto status = mStream->deliverFrame_1_1(hidlBuffers); !status.isOk()) {
        LOG(ERROR) << "Failed to forward a frame to HIDL v1.1 client";
        return ScopedAStatus::fromStatus(STATUS_FAILED_TRANSACTION);
    }

    return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraStream::ImplV1::notify(const EvsEventDesc& event) {
    if (!mStream) {
        return ScopedAStatus::fromServiceSpecificError(
                static_cast<int>(EvsResult::RESOURCE_NOT_AVAILABLE));
    }

    hidlevs::V1_1::EvsEventDesc hidlEvent;
    if (!Utils::makeToHidl(event, &hidlEvent)) {
        return ScopedAStatus::fromServiceSpecificError(static_cast<int>(EvsResult::INVALID_ARG));
    }

    if (auto status = mStream->notify(hidlEvent); !status.isOk()) {
        LOG(ERROR) << "Failed to forward an event, " << toString(event.aType);
        return ScopedAStatus::fromStatus(STATUS_FAILED_TRANSACTION);
    }

    return ScopedAStatus::ok();
}

}  // namespace aidl::android::automotive::evs::implementation
