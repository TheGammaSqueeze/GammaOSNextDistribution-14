#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/camera/device/BufferRequest.h>
#include <aidl/android/hardware/camera/device/BufferRequestStatus.h>
#include <aidl/android/hardware/camera/device/CaptureResult.h>
#include <aidl/android/hardware/camera/device/NotifyMsg.h>
#include <aidl/android/hardware/camera/device/StreamBuffer.h>
#include <aidl/android/hardware/camera/device/StreamBufferRet.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class ICameraDeviceCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ICameraDeviceCallback();
  virtual ~ICameraDeviceCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "ef5889d8da1473ff5dfc481b9ce32a3f173ea048";
  static constexpr uint32_t TRANSACTION_notify = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_processCaptureResult = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_requestStreamBuffers = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_returnStreamBuffers = FIRST_CALL_TRANSACTION + 3;

  static std::shared_ptr<ICameraDeviceCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraDeviceCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraDeviceCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraDeviceCallback>& impl);
  static const std::shared_ptr<ICameraDeviceCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus notify(const std::vector<::aidl::android::hardware::camera::device::NotifyMsg>& in_msgs) = 0;
  virtual ::ndk::ScopedAStatus processCaptureResult(const std::vector<::aidl::android::hardware::camera::device::CaptureResult>& in_results) = 0;
  virtual ::ndk::ScopedAStatus requestStreamBuffers(const std::vector<::aidl::android::hardware::camera::device::BufferRequest>& in_bufReqs, std::vector<::aidl::android::hardware::camera::device::StreamBufferRet>* out_buffers, ::aidl::android::hardware::camera::device::BufferRequestStatus* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus returnStreamBuffers(const std::vector<::aidl::android::hardware::camera::device::StreamBuffer>& in_buffers) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraDeviceCallback> default_impl;
};
class ICameraDeviceCallbackDefault : public ICameraDeviceCallback {
public:
  ::ndk::ScopedAStatus notify(const std::vector<::aidl::android::hardware::camera::device::NotifyMsg>& in_msgs) override;
  ::ndk::ScopedAStatus processCaptureResult(const std::vector<::aidl::android::hardware::camera::device::CaptureResult>& in_results) override;
  ::ndk::ScopedAStatus requestStreamBuffers(const std::vector<::aidl::android::hardware::camera::device::BufferRequest>& in_bufReqs, std::vector<::aidl::android::hardware::camera::device::StreamBufferRet>* out_buffers, ::aidl::android::hardware::camera::device::BufferRequestStatus* _aidl_return) override;
  ::ndk::ScopedAStatus returnStreamBuffers(const std::vector<::aidl::android::hardware::camera::device::StreamBuffer>& in_buffers) override;
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
