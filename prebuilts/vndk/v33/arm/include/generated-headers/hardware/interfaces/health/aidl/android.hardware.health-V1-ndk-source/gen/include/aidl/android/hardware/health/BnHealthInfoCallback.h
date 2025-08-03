#pragma once

#include "aidl/android/hardware/health/IHealthInfoCallback.h"

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
class BnHealthInfoCallback : public ::ndk::BnCInterface<IHealthInfoCallback> {
public:
  BnHealthInfoCallback();
  virtual ~BnHealthInfoCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IHealthInfoCallbackDelegator : public BnHealthInfoCallback {
public:
  explicit IHealthInfoCallbackDelegator(const std::shared_ptr<IHealthInfoCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IHealthInfoCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus healthInfoChanged(const ::aidl::android::hardware::health::HealthInfo& in_info) override {
    return _impl->healthInfoChanged(in_info);
  }
protected:
private:
  std::shared_ptr<IHealthInfoCallback> _impl;
};

}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
