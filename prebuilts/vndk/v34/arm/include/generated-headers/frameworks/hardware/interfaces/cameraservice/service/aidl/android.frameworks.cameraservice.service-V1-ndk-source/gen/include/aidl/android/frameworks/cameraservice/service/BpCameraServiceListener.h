#pragma once

#include "aidl/android/frameworks/cameraservice/service/ICameraServiceListener.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
class BpCameraServiceListener : public ::ndk::BpCInterface<ICameraServiceListener> {
public:
  explicit BpCameraServiceListener(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraServiceListener();

  ::ndk::ScopedAStatus onPhysicalCameraStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId, const std::string& in_physicalCameraId) override;
  ::ndk::ScopedAStatus onStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
