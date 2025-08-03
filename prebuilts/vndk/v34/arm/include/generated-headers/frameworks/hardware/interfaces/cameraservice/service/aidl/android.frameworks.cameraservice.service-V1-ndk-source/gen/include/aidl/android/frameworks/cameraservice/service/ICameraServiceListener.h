#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/frameworks/cameraservice/service/CameraDeviceStatus.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
class ICameraServiceListenerDelegator;

class ICameraServiceListener : public ::ndk::ICInterface {
public:
  typedef ICameraServiceListenerDelegator DefaultDelegator;
  static const char* descriptor;
  ICameraServiceListener();
  virtual ~ICameraServiceListener();

  static const int32_t version = 1;
  static inline const std::string hash = "9af5caea9ed28139c7005ead2e2c851351a4caa1";
  static constexpr uint32_t TRANSACTION_onPhysicalCameraStatusChanged = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onStatusChanged = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<ICameraServiceListener> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraServiceListener>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraServiceListener>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraServiceListener>& impl);
  static const std::shared_ptr<ICameraServiceListener>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onPhysicalCameraStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId, const std::string& in_physicalCameraId) = 0;
  virtual ::ndk::ScopedAStatus onStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraServiceListener> default_impl;
};
class ICameraServiceListenerDefault : public ICameraServiceListener {
public:
  ::ndk::ScopedAStatus onPhysicalCameraStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId, const std::string& in_physicalCameraId) override;
  ::ndk::ScopedAStatus onStatusChanged(::aidl::android::frameworks::cameraservice::service::CameraDeviceStatus in_status, const std::string& in_cameraId) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
