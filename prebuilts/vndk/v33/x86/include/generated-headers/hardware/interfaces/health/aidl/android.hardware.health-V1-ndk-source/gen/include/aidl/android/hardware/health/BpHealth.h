#pragma once

#include "aidl/android/hardware/health/IHealth.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace health {
class BpHealth : public ::ndk::BpCInterface<IHealth> {
public:
  explicit BpHealth(const ::ndk::SpAIBinder& binder);
  virtual ~BpHealth();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
