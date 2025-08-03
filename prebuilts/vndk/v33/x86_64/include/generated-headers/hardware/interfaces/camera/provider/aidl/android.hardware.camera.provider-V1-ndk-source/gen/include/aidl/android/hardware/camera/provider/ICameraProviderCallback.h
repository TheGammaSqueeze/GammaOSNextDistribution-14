#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/camera/common/CameraDeviceStatus.h>
#include <aidl/android/hardware/camera/common/TorchModeStatus.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace provider {
class ICameraProviderCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ICameraProviderCallback();
  virtual ~ICameraProviderCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "5904a53ea55472ca9b45b731cb148c65d1090ba5";
  static constexpr uint32_t TRANSACTION_cameraDeviceStatusChange = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_torchModeStatusChange = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_physicalCameraDeviceStatusChange = FIRST_CALL_TRANSACTION + 2;

  static std::shared_ptr<ICameraProviderCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraProviderCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraProviderCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraProviderCallback>& impl);
  static const std::shared_ptr<ICameraProviderCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus cameraDeviceStatusChange(const std::string& in_cameraDeviceName, ::aidl::android::hardware::camera::common::CameraDeviceStatus in_newStatus) = 0;
  virtual ::ndk::ScopedAStatus torchModeStatusChange(const std::string& in_cameraDeviceName, ::aidl::android::hardware::camera::common::TorchModeStatus in_newStatus) = 0;
  virtual ::ndk::ScopedAStatus physicalCameraDeviceStatusChange(const std::string& in_cameraDeviceName, const std::string& in_physicalCameraDeviceName, ::aidl::android::hardware::camera::common::CameraDeviceStatus in_newStatus) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraProviderCallback> default_impl;
};
class ICameraProviderCallbackDefault : public ICameraProviderCallback {
public:
  ::ndk::ScopedAStatus cameraDeviceStatusChange(const std::string& in_cameraDeviceName, ::aidl::android::hardware::camera::common::CameraDeviceStatus in_newStatus) override;
  ::ndk::ScopedAStatus torchModeStatusChange(const std::string& in_cameraDeviceName, ::aidl::android::hardware::camera::common::TorchModeStatus in_newStatus) override;
  ::ndk::ScopedAStatus physicalCameraDeviceStatusChange(const std::string& in_cameraDeviceName, const std::string& in_physicalCameraDeviceName, ::aidl::android::hardware::camera::common::CameraDeviceStatus in_newStatus) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
