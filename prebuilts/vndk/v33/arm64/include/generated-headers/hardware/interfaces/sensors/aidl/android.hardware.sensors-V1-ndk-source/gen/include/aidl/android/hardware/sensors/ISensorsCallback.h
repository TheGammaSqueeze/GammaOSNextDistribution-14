#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/sensors/SensorInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
class ISensorsCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISensorsCallback();
  virtual ~ISensorsCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "c38c6b75e592ba5a6400b6348ad7f27db3064410";
  static constexpr uint32_t TRANSACTION_onDynamicSensorsConnected = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onDynamicSensorsDisconnected = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<ISensorsCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISensorsCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISensorsCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISensorsCallback>& impl);
  static const std::shared_ptr<ISensorsCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onDynamicSensorsConnected(const std::vector<::aidl::android::hardware::sensors::SensorInfo>& in_sensorInfos) = 0;
  virtual ::ndk::ScopedAStatus onDynamicSensorsDisconnected(const std::vector<int32_t>& in_sensorHandles) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISensorsCallback> default_impl;
};
class ISensorsCallbackDefault : public ISensorsCallback {
public:
  ::ndk::ScopedAStatus onDynamicSensorsConnected(const std::vector<::aidl::android::hardware::sensors::SensorInfo>& in_sensorInfos) override;
  ::ndk::ScopedAStatus onDynamicSensorsDisconnected(const std::vector<int32_t>& in_sensorHandles) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
