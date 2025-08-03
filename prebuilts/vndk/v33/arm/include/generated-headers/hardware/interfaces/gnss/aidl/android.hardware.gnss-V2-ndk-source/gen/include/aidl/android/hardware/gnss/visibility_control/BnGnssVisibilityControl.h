#pragma once

#include "aidl/android/hardware/gnss/visibility_control/IGnssVisibilityControl.h"

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
class BnGnssVisibilityControl : public ::ndk::BnCInterface<IGnssVisibilityControl> {
public:
  BnGnssVisibilityControl();
  virtual ~BnGnssVisibilityControl();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssVisibilityControlDelegator : public BnGnssVisibilityControl {
public:
  explicit IGnssVisibilityControlDelegator(const std::shared_ptr<IGnssVisibilityControl> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssVisibilityControl::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus enableNfwLocationAccess(const std::vector<std::string>& in_proxyApps) override {
    return _impl->enableNfwLocationAccess(in_proxyApps);
  }
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
protected:
private:
  std::shared_ptr<IGnssVisibilityControl> _impl;
};

}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
