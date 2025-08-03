#pragma once

#include "aidl/android/hardware/camera/device/ICameraOfflineSession.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class BpCameraOfflineSession : public ::ndk::BpCInterface<ICameraOfflineSession> {
public:
  explicit BpCameraOfflineSession(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraOfflineSession();

  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override;
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_cb) override;
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
