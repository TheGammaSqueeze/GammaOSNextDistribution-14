#pragma once

#include "aidl/android/frameworks/cameraservice/device/ICameraDeviceUser.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class BpCameraDeviceUser : public ::ndk::BpCInterface<ICameraDeviceUser> {
public:
  explicit BpCameraDeviceUser(const ::ndk::SpAIBinder& binder);
  virtual ~BpCameraDeviceUser();

  ::ndk::ScopedAStatus beginConfigure() override;
  ::ndk::ScopedAStatus cancelRepeatingRequest(int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus createDefaultRequest(::aidl::android::frameworks::cameraservice::device::TemplateId in_templateId, ::aidl::android::frameworks::cameraservice::device::CameraMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus createStream(const ::aidl::android::frameworks::cameraservice::device::OutputConfiguration& in_outputConfiguration, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus deleteStream(int32_t in_streamId) override;
  ::ndk::ScopedAStatus disconnect() override;
  ::ndk::ScopedAStatus endConfigure(::aidl::android::frameworks::cameraservice::device::StreamConfigurationMode in_operatingMode, const ::aidl::android::frameworks::cameraservice::device::CameraMetadata& in_sessionParams, int64_t in_startTimeNs) override;
  ::ndk::ScopedAStatus flush(int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus getCaptureRequestMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override;
  ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override;
  ::ndk::ScopedAStatus isSessionConfigurationSupported(const ::aidl::android::frameworks::cameraservice::device::SessionConfiguration& in_sessionConfiguration, bool* _aidl_return) override;
  ::ndk::ScopedAStatus prepare(int32_t in_streamId) override;
  ::ndk::ScopedAStatus submitRequestList(const std::vector<::aidl::android::frameworks::cameraservice::device::CaptureRequest>& in_requestList, bool in_isRepeating, ::aidl::android::frameworks::cameraservice::device::SubmitInfo* _aidl_return) override;
  ::ndk::ScopedAStatus updateOutputConfiguration(int32_t in_streamId, const ::aidl::android::frameworks::cameraservice::device::OutputConfiguration& in_outputConfiguration) override;
  ::ndk::ScopedAStatus waitUntilIdle() override;
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
