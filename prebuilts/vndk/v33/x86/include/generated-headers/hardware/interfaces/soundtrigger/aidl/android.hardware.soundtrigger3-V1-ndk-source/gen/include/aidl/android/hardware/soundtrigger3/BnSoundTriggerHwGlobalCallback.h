#pragma once

#include "aidl/android/hardware/soundtrigger3/ISoundTriggerHwGlobalCallback.h"

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
namespace soundtrigger3 {
class BnSoundTriggerHwGlobalCallback : public ::ndk::BnCInterface<ISoundTriggerHwGlobalCallback> {
public:
  BnSoundTriggerHwGlobalCallback();
  virtual ~BnSoundTriggerHwGlobalCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISoundTriggerHwGlobalCallbackDelegator : public BnSoundTriggerHwGlobalCallback {
public:
  explicit ISoundTriggerHwGlobalCallbackDelegator(const std::shared_ptr<ISoundTriggerHwGlobalCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISoundTriggerHwGlobalCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onResourcesAvailable() override {
    return _impl->onResourcesAvailable();
  }
protected:
private:
  std::shared_ptr<ISoundTriggerHwGlobalCallback> _impl;
};

}  // namespace soundtrigger3
}  // namespace hardware
}  // namespace android
}  // namespace aidl
