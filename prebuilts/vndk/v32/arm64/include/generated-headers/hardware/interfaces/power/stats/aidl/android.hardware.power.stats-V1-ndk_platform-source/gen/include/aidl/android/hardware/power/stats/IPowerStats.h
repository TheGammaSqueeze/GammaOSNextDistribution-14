#pragma once

#include <android/binder_interface_utils.h>

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/power/stats/Channel.h>
#include <aidl/android/hardware/power/stats/EnergyConsumer.h>
#include <aidl/android/hardware/power/stats/EnergyConsumerResult.h>
#include <aidl/android/hardware/power/stats/EnergyMeasurement.h>
#include <aidl/android/hardware/power/stats/PowerEntity.h>
#include <aidl/android/hardware/power/stats/StateResidencyResult.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {
class IPowerStats : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IPowerStats();
  virtual ~IPowerStats();

  static const int32_t version = 1;
  static inline const std::string hash = "93253458fae451cf1187db6120a59fab428f7d02";
  static constexpr uint32_t TRANSACTION_getPowerEntityInfo = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getStateResidency = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getEnergyConsumerInfo = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getEnergyConsumed = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getEnergyMeterInfo = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_readEnergyMeter = FIRST_CALL_TRANSACTION + 5;

  static std::shared_ptr<IPowerStats> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IPowerStats>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IPowerStats>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IPowerStats>& impl);
  static const std::shared_ptr<IPowerStats>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getPowerEntityInfo(std::vector<::aidl::android::hardware::power::stats::PowerEntity>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getStateResidency(const std::vector<int32_t>& in_powerEntityIds, std::vector<::aidl::android::hardware::power::stats::StateResidencyResult>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEnergyConsumerInfo(std::vector<::aidl::android::hardware::power::stats::EnergyConsumer>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEnergyConsumed(const std::vector<int32_t>& in_energyConsumerIds, std::vector<::aidl::android::hardware::power::stats::EnergyConsumerResult>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEnergyMeterInfo(std::vector<::aidl::android::hardware::power::stats::Channel>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus readEnergyMeter(const std::vector<int32_t>& in_channelIds, std::vector<::aidl::android::hardware::power::stats::EnergyMeasurement>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IPowerStats> default_impl;
};
class IPowerStatsDefault : public IPowerStats {
public:
  ::ndk::ScopedAStatus getPowerEntityInfo(std::vector<::aidl::android::hardware::power::stats::PowerEntity>* _aidl_return) override;
  ::ndk::ScopedAStatus getStateResidency(const std::vector<int32_t>& in_powerEntityIds, std::vector<::aidl::android::hardware::power::stats::StateResidencyResult>* _aidl_return) override;
  ::ndk::ScopedAStatus getEnergyConsumerInfo(std::vector<::aidl::android::hardware::power::stats::EnergyConsumer>* _aidl_return) override;
  ::ndk::ScopedAStatus getEnergyConsumed(const std::vector<int32_t>& in_energyConsumerIds, std::vector<::aidl::android::hardware::power::stats::EnergyConsumerResult>* _aidl_return) override;
  ::ndk::ScopedAStatus getEnergyMeterInfo(std::vector<::aidl::android::hardware::power::stats::Channel>* _aidl_return) override;
  ::ndk::ScopedAStatus readEnergyMeter(const std::vector<int32_t>& in_channelIds, std::vector<::aidl::android::hardware::power::stats::EnergyMeasurement>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
