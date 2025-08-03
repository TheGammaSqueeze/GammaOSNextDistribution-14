/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_EVS_MANAGER_AIDL_UTILS_INCLUDE_UTILS_H
#define CPP_EVS_MANAGER_AIDL_UTILS_INCLUDE_UTILS_H

#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraParam.h>
#include <aidl/android/hardware/automotive/evs/DisplayDesc.h>
#include <aidl/android/hardware/automotive/evs/DisplayState.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsEventType.h>
#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <aidl/android/hardware/automotive/evs/Rotation.h>
#include <aidl/android/hardware/automotive/evs/Stream.h>
#include <aidl/android/hardware/automotive/evs/StreamType.h>
#include <aidl/android/hardware/common/NativeHandle.h>
#include <android-base/macros.h>
#include <android/hardware/automotive/evs/1.1/types.h>
#include <android/hardware/camera/device/3.2/ICameraDevice.h>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;
namespace aidlgfx = ::aidl::android::hardware::graphics;
namespace hidlevs = ::android::hardware::automotive::evs;
namespace hidlgfx = ::android::hardware::graphics;

class Utils final {
public:
    static aidlevs::DisplayState makeFromHidl(hidlevs::V1_0::DisplayState hidlState);

    static hidlevs::V1_0::DisplayState makeToHidl(aidlevs::DisplayState aidlState);

    static aidlgfx::common::HardwareBuffer makeHwBufferFromHidlBuffer(
            const hidlevs::V1_0::BufferDesc& hidlBuffer, bool doDup);

    static aidlgfx::common::HardwareBuffer makeHwBufferFromHidlBuffer(
            const hidlevs::V1_1::BufferDesc& hidlBuffer, bool doDup);

    static aidlevs::BufferDesc makeFromHidl(const hidlevs::V1_0::BufferDesc& hidlBuffer,
                                            bool doDup = true);

    static aidlevs::BufferDesc makeFromHidl(const hidlevs::V1_1::BufferDesc& hidlBuffer,
                                            bool doDup = true);

    static hidlevs::V1_0::BufferDesc makeToHidlV1_0(const aidlevs::BufferDesc& hidlBuffer,
                                                    bool doDup = true);

    static hidlevs::V1_1::BufferDesc makeToHidlV1_1(const aidlevs::BufferDesc& hidlBuffer,
                                                    bool doDup = true);

    static aidlgfx::common::HardwareBufferDescription makeFromHidl(
            const hidlgfx::common::V1_2::HardwareBuffer& hidlBuffer);

    static aidlevs::EvsResult makeFromHidl(hidlevs::V1_0::EvsResult result);

    static hidlevs::V1_0::EvsResult makeToHidl(aidlevs::EvsResult result);

    static ::ndk::ScopedAStatus buildScopedAStatusFromEvsResult(aidlevs::EvsResult result);

    static ::ndk::ScopedAStatus buildScopedAStatusFromEvsResult(
            ::android::hardware::Return<aidlevs::EvsResult>& result);

    static ::ndk::ScopedAStatus buildScopedAStatusFromEvsResult(hidlevs::V1_0::EvsResult result);

    static ::ndk::ScopedAStatus buildScopedAStatusFromEvsResult(
            ::android::hardware::Return<hidlevs::V1_0::EvsResult>& result);

    static aidlevs::CameraDesc makeFromHidl(const hidlevs::V1_0::CameraDesc& desc);

    static aidlevs::CameraDesc makeFromHidl(const hidlevs::V1_1::CameraDesc& desc);

    static hidlevs::V1_0::CameraDesc makeToHidlV1_0(const aidlevs::CameraDesc& desc);

    static hidlevs::V1_1::CameraDesc makeToHidlV1_1(const aidlevs::CameraDesc& desc);

    static hidlevs::V1_1::CameraParam makeToHidl(aidlevs::CameraParam id);

    static aidlevs::CameraParam makeFromHidl(hidlevs::V1_1::CameraParam id);

    static aidlevs::DisplayDesc makeFromHidl(const hidlevs::V1_0::DisplayDesc& desc);

    static hidlevs::V1_1::EvsEventType makeToHidl(const aidlevs::EvsEventType& type);

    static aidlevs::EvsEventType makeFromHidl(const hidlevs::V1_1::EvsEventType& type);

    static bool makeToHidl(const aidlevs::EvsEventDesc& in, hidlevs::V1_1::EvsEventDesc* out);

    static aidlevs::EvsEventDesc makeFromHidl(const hidlevs::V1_1::EvsEventDesc& desc);

    static hidlgfx::common::V1_2::HardwareBuffer makeToHidl(
            const aidlgfx::common::HardwareBuffer& aidlDesc, bool doDup = true);

    static hidlgfx::common::V1_2::HardwareBufferDescription makeToHidl(
            const aidlgfx::common::HardwareBufferDescription& aidlDesc);

    static aidlevs::Stream makeFromHidl(
            const ::android::hardware::camera::device::V3_2::Stream& hidlConfig);

    static aidlevs::StreamType makeFromHidl(
            ::android::hardware::camera::device::V3_2::StreamType hidlType);

    static aidlevs::Rotation makeFromHidl(
            ::android::hardware::camera::device::V3_2::StreamRotation hidlRotation);

    static ::android::hardware::camera::device::V3_2::Stream makeToHidl(
            const aidlevs::Stream& aidlConfig);

    static ::android::hardware::camera::device::V3_2::StreamType makeToHidl(
            aidlevs::StreamType aidlType);

    static ::android::hardware::camera::device::V3_2::StreamRotation makeToHidl(
            aidlevs::Rotation aidlRotation);

    static bool validateNativeHandle(const ::aidl::android::hardware::common::NativeHandle& handle);

    static ::aidl::android::hardware::common::NativeHandle dupNativeHandle(
            const ::aidl::android::hardware::common::NativeHandle& handle, bool doDup);

    static aidlgfx::common::HardwareBuffer dupHardwareBuffer(
            const aidlgfx::common::HardwareBuffer& buffer, bool doDup);

    static aidlevs::BufferDesc dupBufferDesc(const aidlevs::BufferDesc& src, bool doDup);

    DISALLOW_IMPLICIT_CONSTRUCTORS(Utils);
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_UTILS_INCLUDE_UTILS_H
