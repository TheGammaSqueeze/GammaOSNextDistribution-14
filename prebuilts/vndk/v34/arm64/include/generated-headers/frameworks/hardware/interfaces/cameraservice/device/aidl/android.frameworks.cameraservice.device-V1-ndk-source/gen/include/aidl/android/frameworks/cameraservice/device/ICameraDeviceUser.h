#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/frameworks/cameraservice/device/CameraMetadata.h>
#include <aidl/android/frameworks/cameraservice/device/CaptureRequest.h>
#include <aidl/android/frameworks/cameraservice/device/OutputConfiguration.h>
#include <aidl/android/frameworks/cameraservice/device/SessionConfiguration.h>
#include <aidl/android/frameworks/cameraservice/device/StreamConfigurationMode.h>
#include <aidl/android/frameworks/cameraservice/device/SubmitInfo.h>
#include <aidl/android/frameworks/cameraservice/device/TemplateId.h>
#include <aidl/android/hardware/common/fmq/MQDescriptor.h>
#include <aidl/android/hardware/common/fmq/SynchronizedReadWrite.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::frameworks::cameraservice::device {
class CameraMetadata;
class CaptureRequest;
class OutputConfiguration;
class SessionConfiguration;
class SubmitInfo;
}  // namespace aidl::android::frameworks::cameraservice::device
namespace aidl::android::hardware::common::fmq {
template <typename T, typename Flavor>
class MQDescriptor;
}  // namespace aidl::android::hardware::common::fmq
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class ICameraDeviceUserDelegator;

class ICameraDeviceUser : public ::ndk::ICInterface {
public:
  typedef ICameraDeviceUserDelegator DefaultDelegator;
  static const char* descriptor;
  ICameraDeviceUser();
  virtual ~ICameraDeviceUser();

  static const int32_t version = 1;
  static inline const std::string hash = "acf6819da94dc452c4fa1e831c96f324a2be805a";
  static constexpr uint32_t TRANSACTION_beginConfigure = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_cancelRepeatingRequest = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_createDefaultRequest = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_createStream = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_deleteStream = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_disconnect = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_endConfigure = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_flush = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getCaptureRequestMetadataQueue = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getCaptureResultMetadataQueue = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_isSessionConfigurationSupported = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_prepare = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_submitRequestList = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_updateOutputConfiguration = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_waitUntilIdle = FIRST_CALL_TRANSACTION + 14;

  static std::shared_ptr<ICameraDeviceUser> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraDeviceUser>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraDeviceUser>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraDeviceUser>& impl);
  static const std::shared_ptr<ICameraDeviceUser>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus beginConfigure() = 0;
  virtual ::ndk::ScopedAStatus cancelRepeatingRequest(int64_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus createDefaultRequest(::aidl::android::frameworks::cameraservice::device::TemplateId in_templateId, ::aidl::android::frameworks::cameraservice::device::CameraMetadata* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus createStream(const ::aidl::android::frameworks::cameraservice::device::OutputConfiguration& in_outputConfiguration, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus deleteStream(int32_t in_streamId) = 0;
  virtual ::ndk::ScopedAStatus disconnect() = 0;
  virtual ::ndk::ScopedAStatus endConfigure(::aidl::android::frameworks::cameraservice::device::StreamConfigurationMode in_operatingMode, const ::aidl::android::frameworks::cameraservice::device::CameraMetadata& in_sessionParams, int64_t in_startTimeNs) = 0;
  virtual ::ndk::ScopedAStatus flush(int64_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCaptureRequestMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isSessionConfigurationSupported(const ::aidl::android::frameworks::cameraservice::device::SessionConfiguration& in_sessionConfiguration, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus prepare(int32_t in_streamId) = 0;
  virtual ::ndk::ScopedAStatus submitRequestList(const std::vector<::aidl::android::frameworks::cameraservice::device::CaptureRequest>& in_requestList, bool in_isRepeating, ::aidl::android::frameworks::cameraservice::device::SubmitInfo* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus updateOutputConfiguration(int32_t in_streamId, const ::aidl::android::frameworks::cameraservice::device::OutputConfiguration& in_outputConfiguration) = 0;
  virtual ::ndk::ScopedAStatus waitUntilIdle() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraDeviceUser> default_impl;
};
class ICameraDeviceUserDefault : public ICameraDeviceUser {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
