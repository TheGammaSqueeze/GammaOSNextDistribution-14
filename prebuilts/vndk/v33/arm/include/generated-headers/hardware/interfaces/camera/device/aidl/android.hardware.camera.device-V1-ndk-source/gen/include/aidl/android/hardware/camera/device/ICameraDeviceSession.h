#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/camera/device/BufferCache.h>
#include <aidl/android/hardware/camera/device/CameraMetadata.h>
#include <aidl/android/hardware/camera/device/CameraOfflineSessionInfo.h>
#include <aidl/android/hardware/camera/device/CaptureRequest.h>
#include <aidl/android/hardware/camera/device/HalStream.h>
#include <aidl/android/hardware/camera/device/ICameraOfflineSession.h>
#include <aidl/android/hardware/camera/device/RequestTemplate.h>
#include <aidl/android/hardware/camera/device/StreamConfiguration.h>
#include <aidl/android/hardware/common/fmq/MQDescriptor.h>
#include <aidl/android/hardware/common/fmq/SynchronizedReadWrite.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class ICameraDeviceSession : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ICameraDeviceSession();
  virtual ~ICameraDeviceSession();

  static const int32_t version = 1;
  static inline const std::string hash = "ef5889d8da1473ff5dfc481b9ce32a3f173ea048";
  static constexpr uint32_t TRANSACTION_close = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_configureStreams = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_constructDefaultRequestSettings = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_flush = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getCaptureRequestMetadataQueue = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getCaptureResultMetadataQueue = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_isReconfigurationRequired = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_processCaptureRequest = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_signalStreamFlush = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_switchToOffline = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_repeatingRequestEnd = FIRST_CALL_TRANSACTION + 10;

  static std::shared_ptr<ICameraDeviceSession> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraDeviceSession>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraDeviceSession>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraDeviceSession>& impl);
  static const std::shared_ptr<ICameraDeviceSession>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus close() = 0;
  virtual ::ndk::ScopedAStatus configureStreams(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_requestedConfiguration, std::vector<::aidl::android::hardware::camera::device::HalStream>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus constructDefaultRequestSettings(::aidl::android::hardware::camera::device::RequestTemplate in_type, ::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus flush() = 0;
  virtual ::ndk::ScopedAStatus getCaptureRequestMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isReconfigurationRequired(const ::aidl::android::hardware::camera::device::CameraMetadata& in_oldSessionParams, const ::aidl::android::hardware::camera::device::CameraMetadata& in_newSessionParams, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus processCaptureRequest(const std::vector<::aidl::android::hardware::camera::device::CaptureRequest>& in_requests, const std::vector<::aidl::android::hardware::camera::device::BufferCache>& in_cachesToRemove, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus signalStreamFlush(const std::vector<int32_t>& in_streamIds, int32_t in_streamConfigCounter) = 0;
  virtual ::ndk::ScopedAStatus switchToOffline(const std::vector<int32_t>& in_streamsToKeep, ::aidl::android::hardware::camera::device::CameraOfflineSessionInfo* out_offlineSessionInfo, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraOfflineSession>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus repeatingRequestEnd(int32_t in_frameNumber, const std::vector<int32_t>& in_streamIds) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraDeviceSession> default_impl;
};
class ICameraDeviceSessionDefault : public ICameraDeviceSession {
public:
  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus configureStreams(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_requestedConfiguration, std::vector<::aidl::android::hardware::camera::device::HalStream>* _aidl_return) override;
  ::ndk::ScopedAStatus constructDefaultRequestSettings(::aidl::android::hardware::camera::device::RequestTemplate in_type, ::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus flush() override;
  ::ndk::ScopedAStatus getCaptureRequestMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override;
  ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override;
  ::ndk::ScopedAStatus isReconfigurationRequired(const ::aidl::android::hardware::camera::device::CameraMetadata& in_oldSessionParams, const ::aidl::android::hardware::camera::device::CameraMetadata& in_newSessionParams, bool* _aidl_return) override;
  ::ndk::ScopedAStatus processCaptureRequest(const std::vector<::aidl::android::hardware::camera::device::CaptureRequest>& in_requests, const std::vector<::aidl::android::hardware::camera::device::BufferCache>& in_cachesToRemove, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus signalStreamFlush(const std::vector<int32_t>& in_streamIds, int32_t in_streamConfigCounter) override;
  ::ndk::ScopedAStatus switchToOffline(const std::vector<int32_t>& in_streamsToKeep, ::aidl::android::hardware::camera::device::CameraOfflineSessionInfo* out_offlineSessionInfo, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraOfflineSession>* _aidl_return) override;
  ::ndk::ScopedAStatus repeatingRequestEnd(int32_t in_frameNumber, const std::vector<int32_t>& in_streamIds) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
