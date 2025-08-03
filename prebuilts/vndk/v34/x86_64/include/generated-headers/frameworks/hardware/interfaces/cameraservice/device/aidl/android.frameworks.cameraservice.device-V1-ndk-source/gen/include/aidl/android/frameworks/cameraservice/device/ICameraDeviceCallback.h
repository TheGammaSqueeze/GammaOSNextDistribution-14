#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/frameworks/cameraservice/device/CaptureMetadataInfo.h>
#include <aidl/android/frameworks/cameraservice/device/CaptureResultExtras.h>
#include <aidl/android/frameworks/cameraservice/device/ErrorCode.h>
#include <aidl/android/frameworks/cameraservice/device/PhysicalCaptureResultInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::frameworks::cameraservice::device {
class CaptureResultExtras;
class PhysicalCaptureResultInfo;
}  // namespace aidl::android::frameworks::cameraservice::device
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class ICameraDeviceCallbackDelegator;

class ICameraDeviceCallback : public ::ndk::ICInterface {
public:
  typedef ICameraDeviceCallbackDelegator DefaultDelegator;
  static const char* descriptor;
  ICameraDeviceCallback();
  virtual ~ICameraDeviceCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "acf6819da94dc452c4fa1e831c96f324a2be805a";
  static constexpr uint32_t TRANSACTION_onCaptureStarted = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onDeviceError = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_onDeviceIdle = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_onPrepared = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_onRepeatingRequestError = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_onResultReceived = FIRST_CALL_TRANSACTION + 5;

  static std::shared_ptr<ICameraDeviceCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraDeviceCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraDeviceCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraDeviceCallback>& impl);
  static const std::shared_ptr<ICameraDeviceCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onCaptureStarted(const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras, int64_t in_timestamp) = 0;
  virtual ::ndk::ScopedAStatus onDeviceError(::aidl::android::frameworks::cameraservice::device::ErrorCode in_errorCode, const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras) = 0;
  virtual ::ndk::ScopedAStatus onDeviceIdle() = 0;
  virtual ::ndk::ScopedAStatus onPrepared(int32_t in_streamId) = 0;
  virtual ::ndk::ScopedAStatus onRepeatingRequestError(int64_t in_lastFrameNumber, int32_t in_repeatingRequestId) = 0;
  virtual ::ndk::ScopedAStatus onResultReceived(const ::aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo& in_result, const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras, const std::vector<::aidl::android::frameworks::cameraservice::device::PhysicalCaptureResultInfo>& in_physicalCaptureResultInfos) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraDeviceCallback> default_impl;
};
class ICameraDeviceCallbackDefault : public ICameraDeviceCallback {
public:
  ::ndk::ScopedAStatus onCaptureStarted(const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras, int64_t in_timestamp) override;
  ::ndk::ScopedAStatus onDeviceError(::aidl::android::frameworks::cameraservice::device::ErrorCode in_errorCode, const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras) override;
  ::ndk::ScopedAStatus onDeviceIdle() override;
  ::ndk::ScopedAStatus onPrepared(int32_t in_streamId) override;
  ::ndk::ScopedAStatus onRepeatingRequestError(int64_t in_lastFrameNumber, int32_t in_repeatingRequestId) override;
  ::ndk::ScopedAStatus onResultReceived(const ::aidl::android::frameworks::cameraservice::device::CaptureMetadataInfo& in_result, const ::aidl::android::frameworks::cameraservice::device::CaptureResultExtras& in_resultExtras, const std::vector<::aidl::android::frameworks::cameraservice::device::PhysicalCaptureResultInfo>& in_physicalCaptureResultInfos) override;
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
