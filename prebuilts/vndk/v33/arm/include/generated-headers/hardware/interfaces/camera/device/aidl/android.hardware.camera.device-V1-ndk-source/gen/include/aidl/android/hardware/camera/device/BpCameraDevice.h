#pragma once

#include "aidl/android/hardware/camera/device/ICameraDevice.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class BpCameraDevice : public ::ndk::BpCInterface<ICameraDevice> {
public:
  explicit BpCameraDevice(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraDevice();

  ::ndk::ScopedAStatus getCameraCharacteristics(::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus getPhysicalCameraCharacteristics(const std::string& in_physicalCameraId, ::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus getResourceCost(::aidl::android::hardware::camera::common::CameraResourceCost* _aidl_return) override;
  ::ndk::ScopedAStatus isStreamCombinationSupported(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_streams, bool* _aidl_return) override;
  ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_callback, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceSession>* _aidl_return) override;
  ::ndk::ScopedAStatus openInjectionSession(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_callback, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraInjectionSession>* _aidl_return) override;
  ::ndk::ScopedAStatus setTorchMode(bool in_on) override;
  ::ndk::ScopedAStatus turnOnTorchWithStrengthLevel(int32_t in_torchStrength) override;
  ::ndk::ScopedAStatus getTorchStrengthLevel(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
