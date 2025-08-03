#pragma once

#include "aidl/android/system/suspend/IWakeLock.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace system {
namespace suspend {
class BnWakeLock : public ::ndk::BnCInterface<IWakeLock> {
public:
  BnWakeLock();
  virtual ~BnWakeLock();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IWakeLockDelegator : public BnWakeLock {
public:
  explicit IWakeLockDelegator(const std::shared_ptr<IWakeLock> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IWakeLock::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus release() override {
    return _impl->release();
  }
protected:
private:
  std::shared_ptr<IWakeLock> _impl;
};

}  // namespace suspend
}  // namespace system
}  // namespace android
}  // namespace aidl
