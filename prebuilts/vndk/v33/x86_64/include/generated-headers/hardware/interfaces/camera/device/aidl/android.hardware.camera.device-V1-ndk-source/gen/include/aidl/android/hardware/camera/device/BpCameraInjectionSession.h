#pragma once

#include "aidl/android/hardware/camera/device/ICameraInjectionSession.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class BpCameraInjectionSession : public ::ndk::BpCInterface<ICameraInjectionSession> {
public:
  explicit BpCameraInjectionSession(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraInjectionSession();

  ::ndk::ScopedAStatus configureInjectionStreams(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_requestedConfiguration, const ::aidl::android::hardware::camera::device::CameraMetadata& in_characteristics) override;
  ::ndk::ScopedAStatus getCameraDeviceSession(std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceSession>* _aidl_return) override;
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
