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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_PROVIDER_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_PROVIDER_H_

#include <aidl/android/hardware/camera/provider/BnCameraProvider.h>
#include <aidl/android/hardware/camera/provider/ICameraProviderCallback.h>

#include <regex>

#include "camera_provider.h"

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

using ::android::google_camera_hal::CameraProvider;
using ndk::ScopedAStatus;

// AidlCameraProvider implements the AIDL camera provider interface,
// ICameraProvider, to enumerate the available individual camera devices
// in the system, and provide updates about changes to device status.
class AidlCameraProvider : public BnCameraProvider {
 public:
  static const std::string kProviderName;
  static std::shared_ptr<AidlCameraProvider> Create();
  virtual ~AidlCameraProvider() = default;

  // Override functions in ICameraProvider.

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

  // End of override functions in ICameraProvider.
  AidlCameraProvider() = default;

 private:
  static const std::regex kDeviceNameRegex;

  status_t Initialize();

  // Parse device version and camera ID.
  bool ParseDeviceName(const std::string& device_name,
                       std::string* device_version, std::string* camera_id);

  std::mutex callbacks_lock_;
  std::shared_ptr<ICameraProviderCallback> callbacks_;

  std::unique_ptr<CameraProvider> google_camera_provider_;
  google_camera_hal::CameraProviderCallback camera_provider_callback_;
};

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_PROVIDER_H_
