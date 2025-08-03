#pragma once

#include "aidl/android/hardware/oemlock/IOemLock.h"

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
namespace oemlock {
class BnOemLock : public ::ndk::BnCInterface<IOemLock> {
public:
  BnOemLock();
  virtual ~BnOemLock();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IOemLockDelegator : public BnOemLock {
public:
  explicit IOemLockDelegator(const std::shared_ptr<IOemLock> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IOemLock::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override {
    return _impl->getName(_aidl_return);
  }
  ::ndk::ScopedAStatus isOemUnlockAllowedByCarrier(bool* _aidl_return) override {
    return _impl->isOemUnlockAllowedByCarrier(_aidl_return);
  }
  ::ndk::ScopedAStatus isOemUnlockAllowedByDevice(bool* _aidl_return) override {
    return _impl->isOemUnlockAllowedByDevice(_aidl_return);
  }
  ::ndk::ScopedAStatus setOemUnlockAllowedByCarrier(bool in_allowed, const std::vector<uint8_t>& in_signature, ::aidl::android::hardware::oemlock::OemLockSecureStatus* _aidl_return) override {
    return _impl->setOemUnlockAllowedByCarrier(in_allowed, in_signature, _aidl_return);
  }
  ::ndk::ScopedAStatus setOemUnlockAllowedByDevice(bool in_allowed) override {
    return _impl->setOemUnlockAllowedByDevice(in_allowed);
  }
protected:
private:
  std::shared_ptr<IOemLock> _impl;
};

}  // namespace oemlock
}  // namespace hardware
}  // namespace android
}  // namespace aidl
