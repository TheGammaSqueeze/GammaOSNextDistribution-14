#pragma once

#include "aidl/android/hardware/gnss/IAGnssRilCallback.h"

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
class BnAGnssRilCallback : public ::ndk::BnCInterface<IAGnssRilCallback> {
public:
  BnAGnssRilCallback();
  virtual ~BnAGnssRilCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IAGnssRilCallbackDelegator : public BnAGnssRilCallback {
public:
  explicit IAGnssRilCallbackDelegator(const std::shared_ptr<IAGnssRilCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IAGnssRilCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus requestSetIdCb(int32_t in_setIdflag) override {
    return _impl->requestSetIdCb(in_setIdflag);
  }
  ::ndk::ScopedAStatus requestRefLocCb() override {
    return _impl->requestRefLocCb();
  }
protected:
private:
  std::shared_ptr<IAGnssRilCallback> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
