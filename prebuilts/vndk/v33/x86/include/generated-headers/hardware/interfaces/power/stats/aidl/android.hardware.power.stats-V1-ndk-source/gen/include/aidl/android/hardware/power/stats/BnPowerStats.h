#pragma once

#include "aidl/android/hardware/power/stats/IPowerStats.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {
class BnPowerStats : public ::ndk::BnCInterface<IPowerStats> {
public:
  BnPowerStats();
  virtual ~BnPowerStats();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IPowerStatsDelegator : public BnPowerStats {
public:
  explicit IPowerStatsDelegator(const std::shared_ptr<IPowerStats> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IPowerStats::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getPowerEntityInfo(std::vector<::aidl::android::hardware::power::stats::PowerEntity>* _aidl_return) override {
    return _impl->getPowerEntityInfo(_aidl_return);
  }
  ::ndk::ScopedAStatus getStateResidency(const std::vector<int32_t>& in_powerEntityIds, std::vector<::aidl::android::hardware::power::stats::StateResidencyResult>* _aidl_return) override {
    return _impl->getStateResidency(in_powerEntityIds, _aidl_return);
  }
  ::ndk::ScopedAStatus getEnergyConsumerInfo(std::vector<::aidl::android::hardware::power::stats::EnergyConsumer>* _aidl_return) override {
    return _impl->getEnergyConsumerInfo(_aidl_return);
  }
  ::ndk::ScopedAStatus getEnergyConsumed(const std::vector<int32_t>& in_energyConsumerIds, std::vector<::aidl::android::hardware::power::stats::EnergyConsumerResult>* _aidl_return) override {
    return _impl->getEnergyConsumed(in_energyConsumerIds, _aidl_return);
  }
  ::ndk::ScopedAStatus getEnergyMeterInfo(std::vector<::aidl::android::hardware::power::stats::Channel>* _aidl_return) override {
    return _impl->getEnergyMeterInfo(_aidl_return);
  }
  ::ndk::ScopedAStatus readEnergyMeter(const std::vector<int32_t>& in_channelIds, std::vector<::aidl::android::hardware::power::stats::EnergyMeasurement>* _aidl_return) override {
    return _impl->readEnergyMeter(in_channelIds, _aidl_return);
  }
protected:
private:
  std::shared_ptr<IPowerStats> _impl;
};

}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
