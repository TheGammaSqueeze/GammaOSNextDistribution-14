#pragma once

#include "aidl/android/hardware/health/IHealth.h"

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
namespace health {
class BnHealth : public ::ndk::BnCInterface<IHealth> {
public:
  BnHealth();
  virtual ~BnHealth();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IHealthDelegator : public BnHealth {
public:
  explicit IHealthDelegator(const std::shared_ptr<IHealth> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IHealth::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::health::IHealthInfoCallback>& in_callback) override {
    return _impl->registerCallback(in_callback);
  }
  ::ndk::ScopedAStatus unregisterCallback(const std::shared_ptr<::aidl::android::hardware::health::IHealthInfoCallback>& in_callback) override {
    return _impl->unregisterCallback(in_callback);
  }
  ::ndk::ScopedAStatus update() override {
    return _impl->update();
  }
  ::ndk::ScopedAStatus getChargeCounterUah(int32_t* _aidl_return) override {
    return _impl->getChargeCounterUah(_aidl_return);
  }
  ::ndk::ScopedAStatus getCurrentNowMicroamps(int32_t* _aidl_return) override {
    return _impl->getCurrentNowMicroamps(_aidl_return);
  }
  ::ndk::ScopedAStatus getCurrentAverageMicroamps(int32_t* _aidl_return) override {
    return _impl->getCurrentAverageMicroamps(_aidl_return);
  }
  ::ndk::ScopedAStatus getCapacity(int32_t* _aidl_return) override {
    return _impl->getCapacity(_aidl_return);
  }
  ::ndk::ScopedAStatus getEnergyCounterNwh(int64_t* _aidl_return) override {
    return _impl->getEnergyCounterNwh(_aidl_return);
  }
  ::ndk::ScopedAStatus getChargeStatus(::aidl::android::hardware::health::BatteryStatus* _aidl_return) override {
    return _impl->getChargeStatus(_aidl_return);
  }
  ::ndk::ScopedAStatus getStorageInfo(std::vector<::aidl::android::hardware::health::StorageInfo>* _aidl_return) override {
    return _impl->getStorageInfo(_aidl_return);
  }
  ::ndk::ScopedAStatus getDiskStats(std::vector<::aidl::android::hardware::health::DiskStats>* _aidl_return) override {
    return _impl->getDiskStats(_aidl_return);
  }
  ::ndk::ScopedAStatus getHealthInfo(::aidl::android::hardware::health::HealthInfo* _aidl_return) override {
    return _impl->getHealthInfo(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IHealth> _impl;
};

}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
