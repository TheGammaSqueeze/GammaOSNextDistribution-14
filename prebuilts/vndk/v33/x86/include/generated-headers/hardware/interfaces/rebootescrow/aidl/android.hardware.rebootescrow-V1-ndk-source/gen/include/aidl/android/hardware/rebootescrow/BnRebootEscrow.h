#pragma once

#include "aidl/android/hardware/rebootescrow/IRebootEscrow.h"

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
namespace rebootescrow {
class BnRebootEscrow : public ::ndk::BnCInterface<IRebootEscrow> {
public:
  BnRebootEscrow();
  virtual ~BnRebootEscrow();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRebootEscrowDelegator : public BnRebootEscrow {
public:
  explicit IRebootEscrowDelegator(const std::shared_ptr<IRebootEscrow> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRebootEscrow::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus storeKey(const std::vector<uint8_t>& in_kek) override {
    return _impl->storeKey(in_kek);
  }
  ::ndk::ScopedAStatus retrieveKey(std::vector<uint8_t>* _aidl_return) override {
    return _impl->retrieveKey(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IRebootEscrow> _impl;
};

}  // namespace rebootescrow
}  // namespace hardware
}  // namespace android
}  // namespace aidl
