/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); * you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "utils/include/Utils.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/logging.h>
#include <android/hardware_buffer.h>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::CameraParam;
using ::aidl::android::hardware::automotive::evs::DisplayDesc;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventType;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::Rotation;
using ::aidl::android::hardware::automotive::evs::Stream;
using ::aidl::android::hardware::automotive::evs::StreamType;
using ::aidl::android::hardware::common::NativeHandle;
using ::aidl::android::hardware::graphics::common::BufferUsage;
using ::aidl::android::hardware::graphics::common::HardwareBuffer;
using ::aidl::android::hardware::graphics::common::HardwareBufferDescription;
using ::aidl::android::hardware::graphics::common::PixelFormat;
using ::android::hardware::Return;
using ::ndk::ScopedAStatus;

using HIDLBufferUsage = ::android::hardware::graphics::common::V1_0::BufferUsage;
using HIDLHardwareBuffer = ::android::hardware::graphics::common::V1_2::HardwareBuffer;
using HIDLHardwareBufferDescription =
        ::android::hardware::graphics::common::V1_2::HardwareBufferDescription;
using HIDLPixelFormat = ::android::hardware::graphics::common::V1_0::PixelFormat;
using HIDLStream = ::android::hardware::camera::device::V3_2::Stream;
using HIDLStreamType = ::android::hardware::camera::device::V3_2::StreamType;
using HIDLStreamRotation = ::android::hardware::camera::device::V3_2::StreamRotation;

DisplayState Utils::makeFromHidl(hidlevs::V1_0::DisplayState hidlState) {
    switch (hidlState) {
        case hidlevs::V1_0::DisplayState::NOT_OPEN:
            return DisplayState::NOT_OPEN;
        case hidlevs::V1_0::DisplayState::NOT_VISIBLE:
            return DisplayState::NOT_VISIBLE;
        case hidlevs::V1_0::DisplayState::VISIBLE_ON_NEXT_FRAME:
            return DisplayState::VISIBLE_ON_NEXT_FRAME;
        case hidlevs::V1_0::DisplayState::VISIBLE:
            return DisplayState::VISIBLE;
        case hidlevs::V1_0::DisplayState::DEAD:
            [[fallthrough]];
        default:
            return DisplayState::DEAD;
    }
}

hidlevs::V1_0::DisplayState Utils::makeToHidl(DisplayState aidlState) {
    switch (aidlState) {
        case DisplayState::NOT_OPEN:
            return hidlevs::V1_0::DisplayState::NOT_OPEN;
        case DisplayState::NOT_VISIBLE:
            return hidlevs::V1_0::DisplayState::NOT_VISIBLE;
        case DisplayState::VISIBLE_ON_NEXT_FRAME:
            return hidlevs::V1_0::DisplayState::VISIBLE_ON_NEXT_FRAME;
        case DisplayState::VISIBLE:
            return hidlevs::V1_0::DisplayState::VISIBLE;
        case DisplayState::DEAD:
            [[fallthrough]];
        default:
            return hidlevs::V1_0::DisplayState::DEAD;
    }
}

HardwareBuffer Utils::makeHwBufferFromHidlBuffer(const hidlevs::V1_0::BufferDesc& hidlBuffer,
                                                 bool doDup) {
    buffer_handle_t h = hidlBuffer.memHandle.getNativeHandle();
    if (h == nullptr) {
        LOG(WARNING) << "Buffer " << hidlBuffer.bufferId << " has an invalid native handle.";
        return {};
    }

    HardwareBuffer hwBuffer = {
            .description =
                    {
                            .width = static_cast<int>(hidlBuffer.width),
                            .height = static_cast<int>(hidlBuffer.height),
                            .layers = 1,
                            .format = static_cast<PixelFormat>(hidlBuffer.format),
                            .usage = static_cast<BufferUsage>(hidlBuffer.usage),
                            .stride = static_cast<int>(hidlBuffer.stride),
                    },
            .handle = doDup ? ::android::dupToAidl(h) : ::android::makeToAidl(h),
    };

    return std::move(hwBuffer);
}

HardwareBufferDescription Utils::makeFromHidl(const HIDLHardwareBuffer& hidlBuffer) {
    const AHardwareBuffer_Desc* pSrc =
            reinterpret_cast<const AHardwareBuffer_Desc*>(hidlBuffer.description.data());
    HardwareBufferDescription desc = {
            .width = static_cast<int>(pSrc->width),
            .height = static_cast<int>(pSrc->height),
            .layers = static_cast<int>(pSrc->layers),
            .format = static_cast<PixelFormat>(pSrc->format),
            .usage = static_cast<BufferUsage>(pSrc->usage),
            .stride = static_cast<int>(pSrc->stride),
    };

    return std::move(desc);
}

HardwareBuffer Utils::makeHwBufferFromHidlBuffer(const hidlevs::V1_1::BufferDesc& hidlBuffer,
                                                 bool doDup) {
    buffer_handle_t h = hidlBuffer.buffer.nativeHandle.getNativeHandle();
    if (h == nullptr) {
        LOG(WARNING) << "Buffer " << hidlBuffer.bufferId << " has an invalid native handle.";
        return {};
    }

    HardwareBuffer hwBuffer = {
            .description = makeFromHidl(hidlBuffer.buffer),
            .handle = doDup ? std::move(::android::dupToAidl(h))
                            : std::move(::android::makeToAidl(h)),
    };

    return std::move(hwBuffer);
}

BufferDesc Utils::makeFromHidl(const hidlevs::V1_0::BufferDesc& hidlBuffer, bool doDup) {
    BufferDesc aidlBuffer = {
            .buffer = Utils::makeHwBufferFromHidlBuffer(hidlBuffer, doDup),
            .pixelSizeBytes = static_cast<int>(hidlBuffer.pixelSize),
            .bufferId = static_cast<int>(hidlBuffer.bufferId),
            // EVS v1.0 BufferDesc does not contain deviceId, timestamp, and
            // metadata.
    };

    return std::move(aidlBuffer);
}

BufferDesc Utils::makeFromHidl(const hidlevs::V1_1::BufferDesc& hidlBuffer, bool doDup) {
    BufferDesc aidlBuffer = {
            .buffer = Utils::makeHwBufferFromHidlBuffer(hidlBuffer, doDup),
            .pixelSizeBytes = static_cast<int>(hidlBuffer.pixelSize),
            .bufferId = static_cast<int>(hidlBuffer.bufferId),
            .deviceId = hidlBuffer.deviceId,
            .timestamp = hidlBuffer.timestamp,
            .metadata = hidlBuffer.metadata,
    };

    return std::move(aidlBuffer);
}

HIDLHardwareBufferDescription Utils::makeToHidl(const HardwareBufferDescription& aidlDesc) {
    HIDLHardwareBufferDescription hidlDesc;
    AHardwareBuffer_Desc* pDesc = reinterpret_cast<AHardwareBuffer_Desc*>(hidlDesc.data());
    pDesc->width = aidlDesc.width;
    pDesc->height = aidlDesc.height;
    pDesc->layers = aidlDesc.layers;
    pDesc->format = static_cast<uint32_t>(aidlDesc.format);
    pDesc->usage = static_cast<uint64_t>(aidlDesc.usage);
    pDesc->stride = aidlDesc.stride;

    return std::move(hidlDesc);
}

HIDLHardwareBuffer Utils::makeToHidl(const HardwareBuffer& aidlBuffer, bool doDup) {
    HIDLHardwareBuffer hidlBuffer = {
            .description = makeToHidl(aidlBuffer.description),
            .nativeHandle = doDup ? ::android::dupFromAidl(aidlBuffer.handle)
                                  : ::android::makeFromAidl(aidlBuffer.handle),
    };

    return std::move(hidlBuffer);
}

hidlevs::V1_0::BufferDesc Utils::makeToHidlV1_0(const BufferDesc& aidlBuffer, bool doDup) {
    hidlevs::V1_0::BufferDesc hidlBuffer = {
            .width = static_cast<uint32_t>(aidlBuffer.buffer.description.width),
            .height = static_cast<uint32_t>(aidlBuffer.buffer.description.height),
            .stride = static_cast<uint32_t>(aidlBuffer.buffer.description.stride),
            .pixelSize = static_cast<uint32_t>(aidlBuffer.pixelSizeBytes),
            .format = static_cast<uint32_t>(aidlBuffer.buffer.description.format),
            .usage = static_cast<uint32_t>(aidlBuffer.buffer.description.usage),
            .bufferId = static_cast<uint32_t>(aidlBuffer.bufferId),
            .memHandle = doDup ? ::android::dupFromAidl(aidlBuffer.buffer.handle)
                               : ::android::makeFromAidl(aidlBuffer.buffer.handle),
    };

    return std::move(hidlBuffer);
}

hidlevs::V1_1::BufferDesc Utils::makeToHidlV1_1(const BufferDesc& aidlBuffer, bool doDup) {
    hidlevs::V1_1::BufferDesc hidlBuffer = {
            .buffer = Utils::makeToHidl(aidlBuffer.buffer, doDup),
            .pixelSize = static_cast<uint32_t>(aidlBuffer.pixelSizeBytes),
            .bufferId = static_cast<uint32_t>(aidlBuffer.bufferId),
            .deviceId = aidlBuffer.deviceId,
            .timestamp = aidlBuffer.timestamp,
            .metadata = aidlBuffer.metadata,
    };

    return std::move(hidlBuffer);
}

EvsResult Utils::makeFromHidl(hidlevs::V1_0::EvsResult result) {
    switch (result) {
        case hidlevs::V1_0::EvsResult::OK:
            return EvsResult::OK;
        case hidlevs::V1_0::EvsResult::INVALID_ARG:
            return EvsResult::INVALID_ARG;
        case hidlevs::V1_0::EvsResult::STREAM_ALREADY_RUNNING:
            return EvsResult::STREAM_ALREADY_RUNNING;
        case hidlevs::V1_0::EvsResult::BUFFER_NOT_AVAILABLE:
            return EvsResult::BUFFER_NOT_AVAILABLE;
        case hidlevs::V1_0::EvsResult::OWNERSHIP_LOST:
            return EvsResult::OWNERSHIP_LOST;
        case hidlevs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR:
            [[fallthrough]];
        default:
            return EvsResult::UNDERLYING_SERVICE_ERROR;
    }
}

hidlevs::V1_0::EvsResult Utils::makeToHidl(EvsResult result) {
    switch (result) {
        case EvsResult::OK:
            return hidlevs::V1_0::EvsResult::OK;
        case EvsResult::INVALID_ARG:
            return hidlevs::V1_0::EvsResult::INVALID_ARG;
        case EvsResult::STREAM_ALREADY_RUNNING:
            return hidlevs::V1_0::EvsResult::STREAM_ALREADY_RUNNING;
        case EvsResult::BUFFER_NOT_AVAILABLE:
            return hidlevs::V1_0::EvsResult::BUFFER_NOT_AVAILABLE;
        case EvsResult::OWNERSHIP_LOST:
            return hidlevs::V1_0::EvsResult::OWNERSHIP_LOST;
        default:
            LOG(WARNING) << "Received " << toString(result)
                         << ", which is not recognized by EVS HIDL version";
            return hidlevs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR;
    }
}

CameraDesc Utils::makeFromHidl(const hidlevs::V1_0::CameraDesc& hidlDesc) {
    CameraDesc aidlDesc = {
            .id = hidlDesc.cameraId,
            .vendorFlags = static_cast<int32_t>(hidlDesc.vendorFlags),
    };

    return std::move(aidlDesc);
}

CameraDesc Utils::makeFromHidl(const hidlevs::V1_1::CameraDesc& hidlDesc) {
    CameraDesc aidlDesc = {
            .id = hidlDesc.v1.cameraId,
            .vendorFlags = static_cast<int32_t>(hidlDesc.v1.vendorFlags),
            .metadata = hidlDesc.metadata,
    };

    return std::move(aidlDesc);
}

hidlevs::V1_0::CameraDesc Utils::makeToHidlV1_0(const CameraDesc& aidlDesc) {
    hidlevs::V1_0::CameraDesc hidlDesc = {
            .cameraId = aidlDesc.id,
            .vendorFlags = static_cast<uint32_t>(aidlDesc.vendorFlags),
    };

    return std::move(hidlDesc);
}

hidlevs::V1_1::CameraDesc Utils::makeToHidlV1_1(const CameraDesc& aidlDesc) {
    hidlevs::V1_1::CameraDesc hidlDesc = {
            .v1 =
                    {
                            .cameraId = aidlDesc.id,
                            .vendorFlags = static_cast<uint32_t>(aidlDesc.vendorFlags),
                    },
    };

    if (!aidlDesc.metadata.empty()) {
        const auto n = aidlDesc.metadata.size() * sizeof(decltype(aidlDesc.metadata)::value_type);
        hidlDesc.metadata.resize(n);
        memcpy(hidlDesc.metadata.data(), aidlDesc.metadata.data(), n);
    }

    return std::move(hidlDesc);
}

hidlevs::V1_1::CameraParam Utils::makeToHidl(CameraParam id) {
    switch (id) {
        case CameraParam::BRIGHTNESS:
            return hidlevs::V1_1::CameraParam::BRIGHTNESS;
        case CameraParam::CONTRAST:
            return hidlevs::V1_1::CameraParam::CONTRAST;
        case CameraParam::AUTOGAIN:
            return hidlevs::V1_1::CameraParam::AUTOGAIN;
        case CameraParam::GAIN:
            return hidlevs::V1_1::CameraParam::GAIN;
        case CameraParam::AUTO_WHITE_BALANCE:
            return hidlevs::V1_1::CameraParam::AUTO_WHITE_BALANCE;
        case CameraParam::WHITE_BALANCE_TEMPERATURE:
            return hidlevs::V1_1::CameraParam::WHITE_BALANCE_TEMPERATURE;
        case CameraParam::SHARPNESS:
            return hidlevs::V1_1::CameraParam::SHARPNESS;
        case CameraParam::AUTO_EXPOSURE:
            return hidlevs::V1_1::CameraParam::AUTO_EXPOSURE;
        case CameraParam::ABSOLUTE_EXPOSURE:
            return hidlevs::V1_1::CameraParam::ABSOLUTE_EXPOSURE;
        case CameraParam::ABSOLUTE_FOCUS:
            return hidlevs::V1_1::CameraParam::ABSOLUTE_FOCUS;
        case CameraParam::AUTO_FOCUS:
            return hidlevs::V1_1::CameraParam::AUTO_FOCUS;
        case CameraParam::ABSOLUTE_ZOOM:
            return hidlevs::V1_1::CameraParam::ABSOLUTE_ZOOM;
    }
}

CameraParam Utils::makeFromHidl(hidlevs::V1_1::CameraParam id) {
    switch (id) {
        case hidlevs::V1_1::CameraParam::BRIGHTNESS:
            return CameraParam::BRIGHTNESS;
        case hidlevs::V1_1::CameraParam::CONTRAST:
            return CameraParam::CONTRAST;
        case hidlevs::V1_1::CameraParam::AUTOGAIN:
            return CameraParam::AUTOGAIN;
        case hidlevs::V1_1::CameraParam::GAIN:
            return CameraParam::GAIN;
        case hidlevs::V1_1::CameraParam::AUTO_WHITE_BALANCE:
            return CameraParam::AUTO_WHITE_BALANCE;
        case hidlevs::V1_1::CameraParam::WHITE_BALANCE_TEMPERATURE:
            return CameraParam::WHITE_BALANCE_TEMPERATURE;
        case hidlevs::V1_1::CameraParam::SHARPNESS:
            return CameraParam::SHARPNESS;
        case hidlevs::V1_1::CameraParam::AUTO_EXPOSURE:
            return CameraParam::AUTO_EXPOSURE;
        case hidlevs::V1_1::CameraParam::ABSOLUTE_EXPOSURE:
            return CameraParam::ABSOLUTE_EXPOSURE;
        case hidlevs::V1_1::CameraParam::ABSOLUTE_FOCUS:
            return CameraParam::ABSOLUTE_FOCUS;
        case hidlevs::V1_1::CameraParam::AUTO_FOCUS:
            return CameraParam::AUTO_FOCUS;
        case hidlevs::V1_1::CameraParam::ABSOLUTE_ZOOM:
            return CameraParam::ABSOLUTE_ZOOM;
    }
}

DisplayDesc Utils::makeFromHidl(const hidlevs::V1_0::DisplayDesc& hidlDesc) {
    DisplayDesc aidlDesc = {
            .id = hidlDesc.displayId,
            .vendorFlags = static_cast<int>(hidlDesc.vendorFlags),
    };

    return std::move(aidlDesc);
}

Stream Utils::makeFromHidl(const HIDLStream& config) {
    Stream aidlStreamConfig = {
            .id = config.id,
            .streamType = makeFromHidl(config.streamType),
            .width = static_cast<int32_t>(config.width),
            .height = static_cast<int32_t>(config.height),
            .format = static_cast<PixelFormat>(config.format),
            .usage = static_cast<BufferUsage>(config.usage),
            .rotation = makeFromHidl(config.rotation),
    };

    return std::move(aidlStreamConfig);
}

StreamType Utils::makeFromHidl(HIDLStreamType hidlType) {
    switch (hidlType) {
        case HIDLStreamType::OUTPUT:
            return StreamType::OUTPUT;
        case HIDLStreamType::INPUT:
            return StreamType::INPUT;
    }
}

Rotation Utils::makeFromHidl(HIDLStreamRotation hidlRotation) {
    switch (hidlRotation) {
        case HIDLStreamRotation::ROTATION_0:
            return Rotation::ROTATION_0;
        case HIDLStreamRotation::ROTATION_90:
            return Rotation::ROTATION_90;
        case HIDLStreamRotation::ROTATION_180:
            return Rotation::ROTATION_180;
        case HIDLStreamRotation::ROTATION_270:
            return Rotation::ROTATION_270;
    }
}

HIDLStreamType Utils::makeToHidl(StreamType aidlType) {
    switch (aidlType) {
        case StreamType::OUTPUT:
            return HIDLStreamType::OUTPUT;
        case StreamType::INPUT:
            return HIDLStreamType::INPUT;
    }
}

HIDLStreamRotation Utils::makeToHidl(Rotation aidlRotation) {
    switch (aidlRotation) {
        case Rotation::ROTATION_0:
            return HIDLStreamRotation::ROTATION_0;
        case Rotation::ROTATION_90:
            return HIDLStreamRotation::ROTATION_90;
        case Rotation::ROTATION_180:
            return HIDLStreamRotation::ROTATION_180;
        case Rotation::ROTATION_270:
            return HIDLStreamRotation::ROTATION_270;
    }
}

::android::hardware::camera::device::V3_2::Stream Utils::makeToHidl(
        const ::aidl::android::hardware::automotive::evs::Stream& config) {
    HIDLStream hidlStreamConfig = {
            .id = config.id,
            .streamType = makeToHidl(config.streamType),
            .width = static_cast<uint32_t>(config.width),
            .height = static_cast<uint32_t>(config.height),
            .format = static_cast<HIDLPixelFormat>(config.format),
            .usage = static_cast<::android::hardware::hidl_bitfield<HIDLBufferUsage>>(config.usage),
            // dataSpace is opaque to EVS and therefore we don't fill it.
            .rotation = makeToHidl(config.rotation),
    };

    return std::move(hidlStreamConfig);
}

EvsEventType Utils::makeFromHidl(const hidlevs::V1_1::EvsEventType& hidlType) {
    switch (hidlType) {
        case hidlevs::V1_1::EvsEventType::STREAM_STARTED:
            return EvsEventType::STREAM_STARTED;
        case hidlevs::V1_1::EvsEventType::STREAM_STOPPED:
            return EvsEventType::STREAM_STOPPED;
        case hidlevs::V1_1::EvsEventType::FRAME_DROPPED:
            return EvsEventType::FRAME_DROPPED;
        case hidlevs::V1_1::EvsEventType::TIMEOUT:
            return EvsEventType::TIMEOUT;
        case hidlevs::V1_1::EvsEventType::PARAMETER_CHANGED:
            return EvsEventType::PARAMETER_CHANGED;
        case hidlevs::V1_1::EvsEventType::MASTER_RELEASED:
            return EvsEventType::MASTER_RELEASED;
        case hidlevs::V1_1::EvsEventType::STREAM_ERROR:
            return EvsEventType::STREAM_ERROR;
    }
}

hidlevs::V1_1::EvsEventType Utils::makeToHidl(const EvsEventType& aidlType) {
    switch (aidlType) {
        case EvsEventType::STREAM_STARTED:
            return hidlevs::V1_1::EvsEventType::STREAM_STARTED;
        case EvsEventType::STREAM_STOPPED:
            return hidlevs::V1_1::EvsEventType::STREAM_STOPPED;
        case EvsEventType::FRAME_DROPPED:
            return hidlevs::V1_1::EvsEventType::FRAME_DROPPED;
        case EvsEventType::TIMEOUT:
            return hidlevs::V1_1::EvsEventType::TIMEOUT;
        case EvsEventType::PARAMETER_CHANGED:
            return hidlevs::V1_1::EvsEventType::PARAMETER_CHANGED;
        case EvsEventType::MASTER_RELEASED:
            return hidlevs::V1_1::EvsEventType::MASTER_RELEASED;
        case EvsEventType::STREAM_ERROR:
            return hidlevs::V1_1::EvsEventType::STREAM_ERROR;
    }
}

EvsEventDesc Utils::makeFromHidl(const hidlevs::V1_1::EvsEventDesc& hidlDesc) {
    EvsEventDesc aidlDesc = {
            .aType = makeFromHidl(hidlDesc.aType),
            .deviceId = hidlDesc.deviceId,
    };

    for (auto i = 0; i < hidlDesc.payload.size(); ++i) {
        aidlDesc.payload.push_back(hidlDesc.payload[i]);
    }

    return std::move(aidlDesc);
}

bool Utils::makeToHidl(const EvsEventDesc& in, hidlevs::V1_1::EvsEventDesc* out) {
    if (in.payload.size() > out->payload.size()) {
        LOG(ERROR) << "The size of the event payload must not exceed "
                   << out->payload.size() * sizeof(out->payload[0]) << " bytes.";

        return false;
    }

    out->aType = makeToHidl(in.aType);
    out->deviceId = in.deviceId;

    for (int i = 0; i < in.payload.size(); ++i) {
        out->payload[i] = in.payload[i];
    }

    return true;
}

bool Utils::validateNativeHandle(const NativeHandle& handle) {
    return handle.fds.size() > 0 && handle.ints.size() > 0 &&
            std::all_of(handle.fds.begin(), handle.fds.end(),
                        [](const ::ndk::ScopedFileDescriptor& fd) { return fd.get() > 0; });
}

NativeHandle Utils::dupNativeHandle(const NativeHandle& handle, bool doDup) {
    NativeHandle dup;

    dup.fds = std::vector<::ndk::ScopedFileDescriptor>(handle.fds.size());
    if (!doDup) {
        for (auto i = 0; i < handle.fds.size(); ++i) {
            dup.fds.at(i).set(handle.fds[i].get());
        }
    } else {
        for (auto i = 0; i < handle.fds.size(); ++i) {
            dup.fds[i] = std::move(handle.fds[i].dup());
        }
    }
    dup.ints = handle.ints;

    return std::move(dup);
}

HardwareBuffer Utils::dupHardwareBuffer(const HardwareBuffer& buffer, bool doDup) {
    HardwareBuffer dup = {
            .description = buffer.description,
            .handle = dupNativeHandle(buffer.handle, doDup),
    };

    return std::move(dup);
}

BufferDesc Utils::dupBufferDesc(const BufferDesc& src, bool doDup) {
    BufferDesc dup = {
            .buffer = dupHardwareBuffer(src.buffer, doDup),
            .pixelSizeBytes = src.pixelSizeBytes,
            .bufferId = src.bufferId,
            .deviceId = src.deviceId,
            .timestamp = src.timestamp,
            .metadata = src.metadata,
    };

    return std::move(dup);
}

ScopedAStatus Utils::buildScopedAStatusFromEvsResult(EvsResult result) {
    if (result != EvsResult::OK) {
        return ScopedAStatus::fromServiceSpecificError(static_cast<int>(result));
    } else {
        return ScopedAStatus::ok();
    }
}

ScopedAStatus Utils::buildScopedAStatusFromEvsResult(hidlevs::V1_0::EvsResult result) {
    if (result != hidlevs::V1_0::EvsResult::OK) {
        return ScopedAStatus::fromServiceSpecificError(static_cast<int>(makeFromHidl(result)));
    } else {
        return ScopedAStatus::ok();
    }
}

ScopedAStatus Utils::buildScopedAStatusFromEvsResult(Return<hidlevs::V1_0::EvsResult>& result) {
    if (!result.isOk()) {
        return ScopedAStatus::fromServiceSpecificError(
                static_cast<int>(EvsResult::UNDERLYING_SERVICE_ERROR));
    }

    return Utils::buildScopedAStatusFromEvsResult(static_cast<hidlevs::V1_0::EvsResult>(result));
}

}  // namespace aidl::android::automotive::evs::implementation
