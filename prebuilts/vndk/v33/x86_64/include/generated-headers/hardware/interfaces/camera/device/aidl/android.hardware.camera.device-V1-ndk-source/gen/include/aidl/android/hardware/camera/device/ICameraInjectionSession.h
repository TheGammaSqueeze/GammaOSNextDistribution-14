#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/camera/device/CameraMetadata.h>
#include <aidl/android/hardware/camera/device/ICameraDeviceSession.h>
#include <aidl/android/hardware/camera/device/StreamConfiguration.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class ICameraInjectionSession : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ICameraInjectionSession();
  virtual ~ICameraInjectionSession();

  static const int32_t version = 1;
  static inline const std::string hash = "ef5889d8da1473ff5dfc481b9ce32a3f173ea048";
  static constexpr uint32_t TRANSACTION_configureInjectionStreams = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getCameraDeviceSession = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<ICameraInjectionSession> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraInjectionSession>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraInjectionSession>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraInjectionSession>& impl);
  static const std::shared_ptr<ICameraInjectionSession>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus configureInjectionStreams(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_requestedConfiguration, const ::aidl::android::hardware::camera::device::CameraMetadata& in_characteristics) = 0;
  virtual ::ndk::ScopedAStatus getCameraDeviceSession(std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceSession>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraInjectionSession> default_impl;
};
class ICameraInjectionSessionDefault : public ICameraInjectionSession {
public:
  ::ndk::ScopedAStatus configureInjectionStreams(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_requestedConfiguration, const ::aidl::android::hardware::camera::device::CameraMetadata& in_characteristics) override;
  ::ndk::ScopedAStatus getCameraDeviceSession(std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceSession>* _aidl_return) override;
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
