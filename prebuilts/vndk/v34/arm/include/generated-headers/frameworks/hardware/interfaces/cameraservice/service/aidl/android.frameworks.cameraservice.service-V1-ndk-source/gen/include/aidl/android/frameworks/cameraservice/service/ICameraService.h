#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/frameworks/cameraservice/common/ProviderIdAndVendorTagSections.h>
#include <aidl/android/frameworks/cameraservice/device/CameraMetadata.h>
#include <aidl/android/frameworks/cameraservice/device/ICameraDeviceCallback.h>
#include <aidl/android/frameworks/cameraservice/device/ICameraDeviceUser.h>
#include <aidl/android/frameworks/cameraservice/service/CameraStatusAndId.h>
#include <aidl/android/frameworks/cameraservice/service/ICameraServiceListener.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::frameworks::cameraservice::common {
class ProviderIdAndVendorTagSections;
}  // namespace aidl::android::frameworks::cameraservice::common
namespace aidl::android::frameworks::cameraservice::device {
class CameraMetadata;
class ICameraDeviceCallback;
class ICameraDeviceUser;
}  // namespace aidl::android::frameworks::cameraservice::device
namespace aidl::android::frameworks::cameraservice::service {
class CameraStatusAndId;
class ICameraServiceListener;
}  // namespace aidl::android::frameworks::cameraservice::service
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
class ICameraServiceDelegator;

class ICameraService : public ::ndk::ICInterface {
public:
  typedef ICameraServiceDelegator DefaultDelegator;
  static const char* descriptor;
  ICameraService();
  virtual ~ICameraService();

  static const int32_t version = 1;
  static inline const std::string hash = "9af5caea9ed28139c7005ead2e2c851351a4caa1";
  static constexpr uint32_t TRANSACTION_addListener = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_connectDevice = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getCameraCharacteristics = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getCameraVendorTagSections = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_removeListener = FIRST_CALL_TRANSACTION + 4;

  static std::shared_ptr<ICameraService> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraService>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraService>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraService>& impl);
  static const std::shared_ptr<ICameraService>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus addListener(const std::shared_ptr<::aidl::android::frameworks::cameraservice::service::ICameraServiceListener>& in_listener, std::vector<::aidl::android::frameworks::cameraservice::service::CameraStatusAndId>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus connectDevice(const std::shared_ptr<::aidl::android::frameworks::cameraservice::device::ICameraDeviceCallback>& in_callback, const std::string& in_cameraId, std::shared_ptr<::aidl::android::frameworks::cameraservice::device::ICameraDeviceUser>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCameraCharacteristics(const std::string& in_cameraId, ::aidl::android::frameworks::cameraservice::device::CameraMetadata* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCameraVendorTagSections(std::vector<::aidl::android::frameworks::cameraservice::common::ProviderIdAndVendorTagSections>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus removeListener(const std::shared_ptr<::aidl::android::frameworks::cameraservice::service::ICameraServiceListener>& in_listener) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraService> default_impl;
};
class ICameraServiceDefault : public ICameraService {
public:
  ::ndk::ScopedAStatus addListener(const std::shared_ptr<::aidl::android::frameworks::cameraservice::service::ICameraServiceListener>& in_listener, std::vector<::aidl::android::frameworks::cameraservice::service::CameraStatusAndId>* _aidl_return) override;
  ::ndk::ScopedAStatus connectDevice(const std::shared_ptr<::aidl::android::frameworks::cameraservice::device::ICameraDeviceCallback>& in_callback, const std::string& in_cameraId, std::shared_ptr<::aidl::android::frameworks::cameraservice::device::ICameraDeviceUser>* _aidl_return) override;
  ::ndk::ScopedAStatus getCameraCharacteristics(const std::string& in_cameraId, ::aidl::android::frameworks::cameraservice::device::CameraMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus getCameraVendorTagSections(std::vector<::aidl::android::frameworks::cameraservice::common::ProviderIdAndVendorTagSections>* _aidl_return) override;
  ::ndk::ScopedAStatus removeListener(const std::shared_ptr<::aidl::android::frameworks::cameraservice::service::ICameraServiceListener>& in_listener) override;
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
