#pragma once

#include "aidl/android/frameworks/cameraservice/service/ICameraService.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
class BpCameraService : public ::ndk::BpCInterface<ICameraService> {
public:
  explicit BpCameraService(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraService();

  ::ndk::ScopedAStatus addListener(const std::shared_ptr<::aidl::android::frameworks::cameraservice::service::ICameraServiceListener>& in_listener, std::vector<::aidl::android::frameworks::cameraservice::service::CameraStatusAndId>* _aidl_return) override;
  ::ndk::ScopedAStatus connectDevice(const std::shared_ptr<::aidl::android::frameworks::cameraservice::device::ICameraDeviceCallback>& in_callback, const std::string& in_cameraId, std::shared_ptr<::aidl::android::frameworks::cameraservice::device::ICameraDeviceUser>* _aidl_return) override;
  ::ndk::ScopedAStatus getCameraCharacteristics(const std::string& in_cameraId, ::aidl::android::frameworks::cameraservice::device::CameraMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus getCameraVendorTagSections(std::vector<::aidl::android::frameworks::cameraservice::common::ProviderIdAndVendorTagSections>* _aidl_return) override;
  ::ndk::ScopedAStatus removeListener(const std::shared_ptr<::aidl::android::frameworks::cameraservice::service::ICameraServiceListener>& in_listener) override;
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
