#pragma once

#include "aidl/android/hardware/camera/provider/ICameraProvider.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace provider {
class BpCameraProvider : public ::ndk::BpCInterface<ICameraProvider> {
public:
  explicit BpCameraProvider(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraProvider();

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::camera::provider::ICameraProviderCallback>& in_callback) override;
  ::ndk::ScopedAStatus getVendorTags(std::vector<::aidl::android::hardware::camera::common::VendorTagSection>* _aidl_return) override;
  ::ndk::ScopedAStatus getCameraIdList(std::vector<std::string>* _aidl_return) override;
  ::ndk::ScopedAStatus getCameraDeviceInterface(const std::string& in_cameraDeviceName, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDevice>* _aidl_return) override;
  ::ndk::ScopedAStatus notifyDeviceStateChange(int64_t in_deviceState) override;
  ::ndk::ScopedAStatus getConcurrentCameraIds(std::vector<::aidl::android::hardware::camera::provider::ConcurrentCameraIdCombination>* _aidl_return) override;
  ::ndk::ScopedAStatus isConcurrentStreamCombinationSupported(const std::vector<::aidl::android::hardware::camera::provider::CameraIdAndStreamCombination>& in_configs, bool* _aidl_return) override;
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
