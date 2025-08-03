#pragma once

#include "aidl/android/hardware/gnss/IAGnssCallback.h"

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
class BnAGnssCallback : public ::ndk::BnCInterface<IAGnssCallback> {
public:
  BnAGnssCallback();
  virtual ~BnAGnssCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IAGnssCallbackDelegator : public BnAGnssCallback {
public:
  explicit IAGnssCallbackDelegator(const std::shared_ptr<IAGnssCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IAGnssCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus agnssStatusCb(::aidl::android::hardware::gnss::IAGnssCallback::AGnssType in_type, ::aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue in_status) override {
    return _impl->agnssStatusCb(in_type, in_status);
  }
protected:
private:
  std::shared_ptr<IAGnssCallback> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
