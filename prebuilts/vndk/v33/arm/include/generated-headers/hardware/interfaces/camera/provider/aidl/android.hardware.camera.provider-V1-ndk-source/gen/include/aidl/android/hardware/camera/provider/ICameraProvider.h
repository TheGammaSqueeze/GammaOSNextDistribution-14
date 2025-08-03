#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/camera/common/VendorTagSection.h>
#include <aidl/android/hardware/camera/device/ICameraDevice.h>
#include <aidl/android/hardware/camera/provider/CameraIdAndStreamCombination.h>
#include <aidl/android/hardware/camera/provider/ConcurrentCameraIdCombination.h>
#include <aidl/android/hardware/camera/provider/ICameraProviderCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace provider {
class ICameraProvider : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ICameraProvider();
  virtual ~ICameraProvider();

  enum : int64_t { DEVICE_STATE_NORMAL = 0L };
  enum : int64_t { DEVICE_STATE_BACK_COVERED = 1L };
  enum : int64_t { DEVICE_STATE_FRONT_COVERED = 2L };
  enum : int64_t { DEVICE_STATE_FOLDED = 4L };
  static const int32_t version = 1;
  static inline const std::string hash = "5904a53ea55472ca9b45b731cb148c65d1090ba5";
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getVendorTags = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getCameraIdList = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getCameraDeviceInterface = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_notifyDeviceStateChange = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getConcurrentCameraIds = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_isConcurrentStreamCombinationSupported = FIRST_CALL_TRANSACTION + 6;

  static std::shared_ptr<ICameraProvider> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICameraProvider>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICameraProvider>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICameraProvider>& impl);
  static const std::shared_ptr<ICameraProvider>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::camera::provider::ICameraProviderCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus getVendorTags(std::vector<::aidl::android::hardware::camera::common::VendorTagSection>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCameraIdList(std::vector<std::string>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCameraDeviceInterface(const std::string& in_cameraDeviceName, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDevice>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus notifyDeviceStateChange(int64_t in_deviceState) = 0;
  virtual ::ndk::ScopedAStatus getConcurrentCameraIds(std::vector<::aidl::android::hardware::camera::provider::ConcurrentCameraIdCombination>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isConcurrentStreamCombinationSupported(const std::vector<::aidl::android::hardware::camera::provider::CameraIdAndStreamCombination>& in_configs, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICameraProvider> default_impl;
};
class ICameraProviderDefault : public ICameraProvider {
public:
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::camera::provider::ICameraProviderCallback>& in_callback) override;
  ::ndk::ScopedAStatus getVendorTags(std::vector<::aidl::android::hardware::camera::common::VendorTagSection>* _aidl_return) override;
  ::ndk::ScopedAStatus getCameraIdList(std::vector<std::string>* _aidl_return) override;
  ::ndk::ScopedAStatus getCameraDeviceInterface(const std::string& in_cameraDeviceName, std::shared_ptr<::aidl::android::hardware::camera::device::ICameraDevice>* _aidl_return) override;
  ::ndk::ScopedAStatus notifyDeviceStateChange(int64_t in_deviceState) override;
  ::ndk::ScopedAStatus getConcurrentCameraIds(std::vector<::aidl::android::hardware::camera::provider::ConcurrentCameraIdCombination>* _aidl_return) override;
  ::ndk::ScopedAStatus isConcurrentStreamCombinationSupported(const std::vector<::aidl::android::hardware::camera::provider::CameraIdAndStreamCombination>& in_configs, bool* _aidl_return) override;
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
