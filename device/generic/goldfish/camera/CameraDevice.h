/*
 * Copyright (C) 2023 The Android Open Source Project
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

#pragma once

#include <memory>
#include <optional>

#include <aidl/android/hardware/camera/device/BnCameraDevice.h>

#include "HwCamera.h"
#include "metadata_utils.h"
#include "utils.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

using aidl::android::hardware::camera::device::BnCameraDevice;
using aidl::android::hardware::camera::device::CameraMetadata;
using aidl::android::hardware::camera::common::CameraResourceCost;
using aidl::android::hardware::camera::device::ICameraDeviceCallback;
using aidl::android::hardware::camera::device::ICameraDeviceSession;
using aidl::android::hardware::camera::device::ICameraInjectionSession;
using aidl::android::hardware::camera::device::RequestTemplate;
using aidl::android::hardware::camera::device::StreamConfiguration;

using ndk::ScopedAStatus;

struct CameraProvider;

struct CameraDevice : public BnCameraDevice {
    CameraDevice(hw::HwCameraFactoryProduct hwCamera);
    ~CameraDevice() override;

    ScopedAStatus getCameraCharacteristics(CameraMetadata* metadata) override;
    ScopedAStatus getPhysicalCameraCharacteristics(
            const std::string& in_physicalCameraId, CameraMetadata* metadata) override;
    ScopedAStatus getResourceCost(CameraResourceCost* cost) override;
    ScopedAStatus isStreamCombinationSupported(
            const StreamConfiguration& in_streams, bool* support) override;
    ScopedAStatus open(const std::shared_ptr<ICameraDeviceCallback>& in_callback,
                       std::shared_ptr<ICameraDeviceSession>* session) override;
    ScopedAStatus openInjectionSession(
            const std::shared_ptr<ICameraDeviceCallback>& in_callback,
            std::shared_ptr<ICameraInjectionSession>* session) override;
    ScopedAStatus setTorchMode(bool on) override;
    ScopedAStatus turnOnTorchWithStrengthLevel(int32_t strength) override;
    ScopedAStatus getTorchStrengthLevel(int32_t* strength) override;

    CameraMetadataMap constructDefaultRequestSettings(RequestTemplate tpl) const;

private:
    friend struct CameraProvider;

    hw::HwCameraFactoryProduct mHwCamera;
    std::weak_ptr<CameraDevice> mSelf;
};

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
