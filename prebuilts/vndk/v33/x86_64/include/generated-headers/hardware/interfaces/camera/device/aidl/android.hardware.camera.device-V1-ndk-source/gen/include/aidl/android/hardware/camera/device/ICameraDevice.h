#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/camera/common/CameraResourceCost.h>
#include <aidl/android/hardware/camera/device/CameraMetadata.h>
#include <aidl/android/hardware/camera/device/ICameraDeviceCallback.h>
#include <aidl/android/hardware/camera/device/ICameraDeviceSession.h>
#include <aidl/android/hardware/camera/device/ICameraInjectionSession.h>
#include <aidl/android/hardware/camera/device/StreamConfiguration.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class ICameraDevice : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ICameraDevice();
  virtual ~ICameraDevice();

  static const int32_t version = 1;
  static inline const std::string hash = "ef5889d8da1473ff5dfc481b9ce32a3f173ea048";
  static constexpr uint32_t TRANSACTION_getCameraCharacteristics = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getPhysicalCameraCharacteristics = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getResourceCost = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_isStreamCombinationSupported = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_open = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_openInjectionSession = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_setTorchMode = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_turnOnTorchWithStrengthLevel = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getTorchStrengthLevel = FIRST_CALL_TRANSACTION + 8;

  static std::shared_ptr<ICameraDevice> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraDevice>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraDevice>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraDevice>& impl);
  static const std::shared_ptr<ICameraDevice>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getCameraCharacteristics(::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPhysicalCameraCharacteristics(const std::string& in_physicalCameraId, ::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getResourceCost(::aidl::android::hardware::camera::common::CameraResourceCost* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isStreamCombinationSupported(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_streams, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_callback, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceSession>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus openInjectionSession(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_callback, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraInjectionSession>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setTorchMode(bool in_on) = 0;
  virtual ::ndk::ScopedAStatus turnOnTorchWithStrengthLevel(int32_t in_torchStrength) = 0;
  virtual ::ndk::ScopedAStatus getTorchStrengthLevel(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraDevice> default_impl;
};
class ICameraDeviceDefault : public ICameraDevice {
public:
  ::ndk::ScopedAStatus getCameraCharacteristics(::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus getPhysicalCameraCharacteristics(const std::string& in_physicalCameraId, ::aidl::android::hardware::camera::device::CameraMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus getResourceCost(::aidl::android::hardware::camera::common::CameraResourceCost* _aidl_return) override;
  ::ndk::ScopedAStatus isStreamCombinationSupported(const ::aidl::android::hardware::camera::device::StreamConfiguration& in_streams, bool* _aidl_return) override;
  ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_callback, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceSession>* _aidl_return) override;
  ::ndk::ScopedAStatus openInjectionSession(const std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDeviceCallback>& in_callback, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraInjectionSession>* _aidl_return) override;
  ::ndk::ScopedAStatus setTorchMode(bool in_on) override;
  ::ndk::ScopedAStatus turnOnTorchWithStrengthLevel(int32_t in_torchStrength) override;
  ::ndk::ScopedAStatus getTorchStrengthLevel(int32_t* _aidl_return) override;
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
