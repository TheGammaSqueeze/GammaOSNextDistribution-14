#pragma once

#include "aidl/android/hardware/camera/device/ICameraDeviceCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class BpCameraDeviceCallback : public ::ndk::BpCInterface<ICameraDeviceCallback> {
public:
  explicit BpCameraDeviceCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraDeviceCallback();

  ::ndk::ScopedAStatus notify(const std::vector<::aidl::android::hardware::camera::device::NotifyMsg>& in_msgs) override;
  ::ndk::ScopedAStatus processCaptureResult(const std::vector<::aidl::android::hardware::camera::device::CaptureResult>& in_results) override;
  ::ndk::ScopedAStatus requestStreamBuffers(const std::vector<::aidl::android::hardware::camera::device::BufferRequest>& in_bufReqs, std::vector<::aidl::android::hardware::camera::device::StreamBufferRet>* out_buffers, ::aidl::android::hardware::camera::device::BufferRequestStatus* _aidl_return) override;
  ::ndk::ScopedAStatus returnStreamBuffers(const std::vector<::aidl::android::hardware::camera::device::StreamBuffer>& in_buffers) override;
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
