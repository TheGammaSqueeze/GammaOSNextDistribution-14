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

#include <aidl/android/hardware/camera/provider/BnCameraProvider.h>
#include <aidl/android/hardware/camera/provider/ICameraProviderCallback.h>

#include "HwCamera.h"
#include "Span.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

using aidl::android::hardware::camera::common::VendorTagSection;
using aidl::android::hardware::camera::device::ICameraDevice;
using aidl::android::hardware::camera::provider::BnCameraProvider;
using aidl::android::hardware::camera::provider::CameraIdAndStreamCombination;
using aidl::android::hardware::camera::provider::ConcurrentCameraIdCombination;
using aidl::android::hardware::camera::provider::ICameraProviderCallback;
using ndk::ScopedAStatus;

struct CameraProvider : public BnCameraProvider {
    CameraProvider(int deviceIdBase, Span<const hw::HwCameraFactory> availableCameras);
    ~CameraProvider() override;

    ScopedAStatus setCallback(
            const std::shared_ptr<ICameraProviderCallback>& callback) override;
    ScopedAStatus getVendorTags(std::vector<VendorTagSection>* vts) override;
    ScopedAStatus getCameraIdList(std::vector<std::string>* camera_ids) override;
    ScopedAStatus getCameraDeviceInterface(
            const std::string& in_cameraDeviceName,
            std::shared_ptr<ICameraDevice>* device) override;
    ScopedAStatus notifyDeviceStateChange(int64_t in_deviceState) override;
    ScopedAStatus getConcurrentCameraIds(
            std::vector<ConcurrentCameraIdCombination>* concurrent_camera_ids) override;
    ScopedAStatus isConcurrentStreamCombinationSupported(
            const std::vector<CameraIdAndStreamCombination>& in_configs,
            bool* support) override;

private:
    const int mDeviceIdBase;
    const Span<const hw::HwCameraFactory> mAvailableCameras;
    std::shared_ptr<ICameraProviderCallback> mCallback;
};

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
