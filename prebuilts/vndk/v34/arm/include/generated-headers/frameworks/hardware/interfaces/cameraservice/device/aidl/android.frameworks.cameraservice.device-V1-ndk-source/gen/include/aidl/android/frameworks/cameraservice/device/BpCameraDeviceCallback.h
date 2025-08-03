#pragma once

#include "aidl/android/frameworks/cameraservice/device/ICameraDeviceCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class BpCameraDeviceCallback : public ::ndk::BpCInterface<ICameraDeviceCallback> {
public:
  explicit BpCameraDeviceCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraDeviceCallback();

  ::ndk::ScopedAStatus onCaptureStarted(const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras, int64_t in_timestamp) override;
  ::ndk::ScopedAStatus onDeviceError(::aidl::android::frameworks::cameraservice::device::ErrorCode in_errorCode, const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras) override;
  ::ndk::ScopedAStatus onDeviceIdle() override;
  ::ndk::ScopedAStatus onPrepared(int32_t in_streamId) override;
  ::ndk::ScopedAStatus onRepeatingRequestError(int64_t in_lastFrameNumber, int32_t in_repeatingRequestId) override;
  ::ndk::ScopedAStatus onResultReceived(const ::aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo& in_result, const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras, const std::vector<::aidl::android::frameworks::cameraservice::device::PhysicalCaptureResultInfo>& in_physicalCaptureResultInfos) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
