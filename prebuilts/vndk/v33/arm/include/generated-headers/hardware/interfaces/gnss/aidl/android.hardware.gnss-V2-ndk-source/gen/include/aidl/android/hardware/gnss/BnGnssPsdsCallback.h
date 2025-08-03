#pragma once

#include "aidl/android/hardware/gnss/IGnssPsdsCallback.h"

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
class BnGnssPsdsCallback : public ::ndk::BnCInterface<IGnssPsdsCallback> {
public:
  BnGnssPsdsCallback();
  virtual ~BnGnssPsdsCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssPsdsCallbackDelegator : public BnGnssPsdsCallback {
public:
  explicit IGnssPsdsCallbackDelegator(const std::shared_ptr<IGnssPsdsCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssPsdsCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus downloadRequestCb(::aidl::android::hardware::gnss::PsdsType in_psdsType) override {
    return _impl->downloadRequestCb(in_psdsType);
  }
protected:
private:
  std::shared_ptr<IGnssPsdsCallback> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
