#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantCallback.h"

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
namespace wifi {
namespace supplicant {
class BnSupplicantCallback : public ::ndk::BnCInterface<ISupplicantCallback> {
public:
  BnSupplicantCallback();
  virtual ~BnSupplicantCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISupplicantCallbackDelegator : public BnSupplicantCallback {
public:
  explicit ISupplicantCallbackDelegator(const std::shared_ptr<ISupplicantCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISupplicantCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onInterfaceCreated(const std::string& in_ifaceName) override {
    return _impl->onInterfaceCreated(in_ifaceName);
  }
  ::ndk::ScopedAStatus onInterfaceRemoved(const std::string& in_ifaceName) override {
    return _impl->onInterfaceRemoved(in_ifaceName);
  }
protected:
private:
  std::shared_ptr<ISupplicantCallback> _impl;
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
