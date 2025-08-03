#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/health/BatteryStatus.h>
#include <aidl/android/hardware/health/DiskStats.h>
#include <aidl/android/hardware/health/HealthInfo.h>
#include <aidl/android/hardware/health/IHealthInfoCallback.h>
#include <aidl/android/hardware/health/StorageInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace health {
class IHealth : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IHealth();
  virtual ~IHealth();

  enum : int32_t { STATUS_UNKNOWN = 2 };
  enum : int32_t { STATUS_CALLBACK_DIED = 4 };
  static const int32_t version = 1;
  static inline const std::string hash = "94e77215594f8ad98ab33a769263d48fdabed92e";
  static constexpr uint32_t TRANSACTION_registerCallback = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_unregisterCallback = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_update = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getChargeCounterUah = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getCurrentNowMicroamps = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getCurrentAverageMicroamps = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getCapacity = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getEnergyCounterNwh = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getChargeStatus = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getStorageInfo = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getDiskStats = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getHealthInfo = FIRST_CALL_TRANSACTION + 11;

  static std::shared_ptr<IHealth> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IHealth>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IHealth>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IHealth>& impl);
  static const std::shared_ptr<IHealth>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::health::IHealthInfoCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus unregisterCallback(const std::shared_ptr<::aidl::android::hardware::health::IHealthInfoCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus update() = 0;
  virtual ::ndk::ScopedAStatus getChargeCounterUah(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCurrentNowMicroamps(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCurrentAverageMicroamps(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCapacity(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEnergyCounterNwh(int64_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getChargeStatus(::aidl::android::hardware::health::BatteryStatus* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getStorageInfo(std::vector<::aidl::android::hardware::health::StorageInfo>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDiskStats(std::vector<::aidl::android::hardware::health::DiskStats>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getHealthInfo(::aidl::android::hardware::health::HealthInfo* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IHealth> default_impl;
};
class IHealthDefault : public IHealth {
public:
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::health::IHealthInfoCallback>& in_callback) override;
  ::ndk::ScopedAStatus unregisterCallback(const std::shared_ptr<::aidl::android::hardware::health::IHealthInfoCallback>& in_callback) override;
  ::ndk::ScopedAStatus update() override;
  ::ndk::ScopedAStatus getChargeCounterUah(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getCurrentNowMicroamps(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getCurrentAverageMicroamps(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getCapacity(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getEnergyCounterNwh(int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus getChargeStatus(::aidl::android::hardware::health::BatteryStatus* _aidl_return) override;
  ::ndk::ScopedAStatus getStorageInfo(std::vector<::aidl::android::hardware::health::StorageInfo>* _aidl_return) override;
  ::ndk::ScopedAStatus getDiskStats(std::vector<::aidl::android::hardware::health::DiskStats>* _aidl_return) override;
  ::ndk::ScopedAStatus getHealthInfo(::aidl::android::hardware::health::HealthInfo* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
