#pragma once

#include "aidl/android/hardware/gnss/IGnssPowerIndicationCallback.h"

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
namespace gnss {
class BnGnssPowerIndicationCallback : public ::ndk::BnCInterface<IGnssPowerIndicationCallback> {
public:
  BnGnssPowerIndicationCallback();
  virtual ~BnGnssPowerIndicationCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssPowerIndicationCallbackDelegator : public BnGnssPowerIndicationCallback {
public:
  explicit IGnssPowerIndicationCallbackDelegator(const std::shared_ptr<IGnssPowerIndicationCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssPowerIndicationCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setCapabilitiesCb(int32_t in_capabilities) override {
    return _impl->setCapabilitiesCb(in_capabilities);
  }
  ::ndk::ScopedAStatus gnssPowerStatsCb(const ::aidl::android::hardware::gnss::GnssPowerStats& in_gnssPowerStats) override {
    return _impl->gnssPowerStatsCb(in_gnssPowerStats);
  }
protected:
private:
  std::shared_ptr<IGnssPowerIndicationCallback> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
