#pragma once

#include "aidl/android/hardware/gnss/visibility_control/IGnssVisibilityControlCallback.h"

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
namespace visibility_control {
class BnGnssVisibilityControlCallback : public ::ndk::BnCInterface<IGnssVisibilityControlCallback> {
public:
  BnGnssVisibilityControlCallback();
  virtual ~BnGnssVisibilityControlCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssVisibilityControlCallbackDelegator : public BnGnssVisibilityControlCallback {
public:
  explicit IGnssVisibilityControlCallbackDelegator(const std::shared_ptr<IGnssVisibilityControlCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssVisibilityControlCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus nfwNotifyCb(const ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwNotification& in_notification) override {
    return _impl->nfwNotifyCb(in_notification);
  }
  ::ndk::ScopedAStatus isInEmergencySession(bool* _aidl_return) override {
    return _impl->isInEmergencySession(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IGnssVisibilityControlCallback> _impl;
};

}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
