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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_DEVICE_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_DEVICE_H_

#include <aidl/android/hardware/camera/device/BnCameraDevice.h>
#include <aidl/android/hardware/camera/device/ICameraDeviceCallback.h>

#include "aidl_profiler.h"
#include "camera_device.h"

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace implementation {

using aidl::android::hardware::camera::common::CameraResourceCost;
using aidl::android::hardware::camera::device::BnCameraDevice;
using aidl::android::hardware::camera::device::CameraMetadata;
using aidl::android::hardware::camera::device::ICameraDevice;
using aidl::android::hardware::camera::device::ICameraDeviceCallback;
using aidl::android::hardware::camera::device::ICameraDeviceSession;
using aidl::android::hardware::camera::device::ICameraInjectionSession;
using aidl::android::hardware::camera::device::RequestTemplate;
using aidl::android::hardware::camera::device::StreamConfiguration;
using ::android::hardware::camera::implementation::AidlProfiler;
using ndk::ScopedAStatus;
using ndk::ScopedFileDescriptor;

using ::android::google_camera_hal::CameraDevice;

// AidlCameraDevice implements the AIDL camera device interface, ICameraDevice,
// using Google Camera HAL to provide information about the associated camera
// device.
class AidlCameraDevice : public BnCameraDevice {
 public:
  static const std::string kDeviceVersion;

  // Create a AidlCameraDevice.
  // google_camera_device is a google camera device that AidlCameraDevice
  // is going to manage. Creating a AidlCameraDevice will fail if
  // google_camera_device is nullptr.
  static std::shared_ptr<AidlCameraDevice> Create(
      std::unique_ptr<CameraDevice> google_camera_device);
  virtual ~AidlCameraDevice() = default;

  binder_status_t dump(int fd, const char**, uint32_t) override;

  // Override functions in ICameraDevice
  ScopedAStatus getCameraCharacteristics(
      CameraMetadata* characteristics) override;

  ScopedAStatus getPhysicalCameraCharacteristics(
      const std::string& in_physicalCameraId,
      CameraMetadata* characteristics) override;

  ScopedAStatus getResourceCost(CameraResourceCost* resource_cost) override;

  ScopedAStatus isStreamCombinationSupported(
      const StreamConfiguration& in_streams, bool* supported) override;

  ScopedAStatus open(const std::shared_ptr<ICameraDeviceCallback>& in_callback,
                     std::shared_ptr<ICameraDeviceSession>* session) override;

  ScopedAStatus openInjectionSession(
      const std::shared_ptr<ICameraDeviceCallback>& in_callback,
      std::shared_ptr<ICameraInjectionSession>* session) override;

  ScopedAStatus setTorchMode(bool on) override;

  ScopedAStatus turnOnTorchWithStrengthLevel(int32_t torchStrength) override;

  ScopedAStatus getTorchStrengthLevel(int32_t* strength_level) override;

  ScopedAStatus constructDefaultRequestSettings(
      RequestTemplate type, CameraMetadata* request_settings) override;

  ScopedAStatus isStreamCombinationWithSettingsSupported(
      const StreamConfiguration& streamConfiguration, bool* supported) override;

  ScopedAStatus getSessionCharacteristics(
      const StreamConfiguration& session_config,
      CameraMetadata* characteristics) override;

  // End of override functions in ICameraDevice
  AidlCameraDevice() = default;

 private:
  status_t Initialize(std::unique_ptr<CameraDevice> google_camera_device);

  std::unique_ptr<CameraDevice> google_camera_device_;
  uint32_t camera_id_ = 0;
  std::shared_ptr<AidlProfiler> aidl_profiler_;

  ScopedAStatus isStreamCombinationSupportedInternal(
      const StreamConfiguration& streamConfiguration, bool* supported,
      bool checkSettings);
};

}  // namespace implementation
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_DEVICE_H_
