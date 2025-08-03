#pragma once

#include "aidl/android/hardware/camera/provider/ICameraProviderCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace provider {
class BpCameraProviderCallback : public ::ndk::BpCInterface<ICameraProviderCallback> {
public:
  explicit BpCameraProviderCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraProviderCallback();

  ::ndk::ScopedAStatus cameraDeviceStatusChange(const std::string& in_cameraDeviceName, ::aidl::android::hardware::camera::common::CameraDeviceStatus in_newStatus) override;
  ::ndk::ScopedAStatus torchModeStatusChange(const std::string& in_cameraDeviceName, ::aidl::android::hardware::camera::common::TorchModeStatus in_newStatus) override;
  ::ndk::ScopedAStatus physicalCameraDeviceStatusChange(const std::string& in_cameraDeviceName, const std::string& in_physicalCameraDeviceName, ::aidl::android::hardware::camera::common::CameraDeviceStatus in_newStatus) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
