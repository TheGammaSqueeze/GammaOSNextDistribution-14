#pragma once

#include "aidl/android/hardware/uwb/IUwbClientCallback.h"

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
namespace uwb {
class BnUwbClientCallback : public ::ndk::BnCInterface<IUwbClientCallback> {
public:
  BnUwbClientCallback();
  virtual ~BnUwbClientCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IUwbClientCallbackDelegator : public BnUwbClientCallback {
public:
  explicit IUwbClientCallbackDelegator(const std::shared_ptr<IUwbClientCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IUwbClientCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onUciMessage(const std::vector<uint8_t>& in_data) override {
    return _impl->onUciMessage(in_data);
  }
  ::ndk::ScopedAStatus onHalEvent(::aidl::android::hardware::uwb::UwbEvent in_event, ::aidl::android::hardware::uwb::UwbStatus in_status) override {
    return _impl->onHalEvent(in_event, in_status);
  }
protected:
private:
  std::shared_ptr<IUwbClientCallback> _impl;
};

}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
