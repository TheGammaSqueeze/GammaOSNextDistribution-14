#pragma once

#include "aidl/android/hardware/power/IPower.h"

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
class BnPower : public ::ndk::BnCInterface<IPower> {
public:
  BnPower();
  virtual ~BnPower();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IPowerDelegator : public BnPower {
public:
  explicit IPowerDelegator(const std::shared_ptr<IPower> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IPower::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setMode(::aidl::android::hardware::power::Mode in_type, bool in_enabled) override {
    return _impl->setMode(in_type, in_enabled);
  }
  ::ndk::ScopedAStatus isModeSupported(::aidl::android::hardware::power::Mode in_type, bool* _aidl_return) override {
    return _impl->isModeSupported(in_type, _aidl_return);
  }
  ::ndk::ScopedAStatus setBoost(::aidl::android::hardware::power::Boost in_type, int32_t in_durationMs) override {
    return _impl->setBoost(in_type, in_durationMs);
  }
  ::ndk::ScopedAStatus isBoostSupported(::aidl::android::hardware::power::Boost in_type, bool* _aidl_return) override {
    return _impl->isBoostSupported(in_type, _aidl_return);
  }
  ::ndk::ScopedAStatus createHintSession(int32_t in_tgid, int32_t in_uid, const std::vector<int32_t>& in_threadIds, int64_t in_durationNanos, std::shared_ptr<::aidl::android::hardware::power::IPowerHintSession>* _aidl_return) override {
    return _impl->createHintSession(in_tgid, in_uid, in_threadIds, in_durationNanos, _aidl_return);
  }
  ::ndk::ScopedAStatus getHintSessionPreferredRate(int64_t* _aidl_return) override {
    return _impl->getHintSessionPreferredRate(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IPower> _impl;
};

}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
