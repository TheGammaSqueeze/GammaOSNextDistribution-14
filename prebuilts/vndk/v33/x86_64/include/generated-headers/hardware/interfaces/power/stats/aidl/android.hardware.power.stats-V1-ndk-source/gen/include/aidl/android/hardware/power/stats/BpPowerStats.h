#pragma once

#include "aidl/android/hardware/power/stats/IPowerStats.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {
class BpPowerStats : public ::ndk::BpCInterface<IPowerStats> {
public:
  explicit BpPowerStats(const ::ndk::SpAIBinder& binder);
  virtual ~BpPowerStats();

  ::ndk::ScopedAStatus getPowerEntityInfo(std::vector<::aidl::android::hardware::power::stats::PowerEntity>* _aidl_return) override;
  ::ndk::ScopedAStatus getStateResidency(const std::vector<int32_t>& in_powerEntityIds, std::vector<::aidl::android::hardware::power::stats::StateResidencyResult>* _aidl_return) override;
  ::ndk::ScopedAStatus getEnergyConsumerInfo(std::vector<::aidl::android::hardware::power::stats::EnergyConsumer>* _aidl_return) override;
  ::ndk::ScopedAStatus getEnergyConsumed(const std::vector<int32_t>& in_energyConsumerIds, std::vector<::aidl::android::hardware::power::stats::EnergyConsumerResult>* _aidl_return) override;
  ::ndk::ScopedAStatus getEnergyMeterInfo(std::vector<::aidl::android::hardware::power::stats::Channel>* _aidl_return) override;
  ::ndk::ScopedAStatus readEnergyMeter(const std::vector<int32_t>& in_channelIds, std::vector<::aidl::android::hardware::power::stats::EnergyMeasurement>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
