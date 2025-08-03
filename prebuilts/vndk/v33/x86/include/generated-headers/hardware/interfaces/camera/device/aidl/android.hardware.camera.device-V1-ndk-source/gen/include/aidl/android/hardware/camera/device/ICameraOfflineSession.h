#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/camera/device/ICameraDeviceCallback.h>
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
class ICameraOfflineSession : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ICameraOfflineSession();
  virtual ~ICameraOfflineSession();

  static const int32_t version = 1;
  static inline const std::string hash = "ef5889d8da1473ff5dfc481b9ce32a3f173ea048";
  static constexpr uint32_t TRANSACTION_close = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getCaptureResultMetadataQueue = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 2;

  static std::shared_ptr<ICameraOfflineSession> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraOfflineSession>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraOfflineSession>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraOfflineSession>& impl);
  static const std::shared_ptr<ICameraOfflineSession>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus close() = 0;
  virtual ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_cb) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraOfflineSession> default_impl;
};
class ICameraOfflineSessionDefault : public ICameraOfflineSession {
public:
  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus getCaptureResultMetadataQueue(::aidl::android::hardware::common::fmq::MQDescriptor<int8_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>* _aidl_return) override;
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_cb) override;
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
