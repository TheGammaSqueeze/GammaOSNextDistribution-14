#pragma once

#include "aidl/android/hardware/authsecret/IAuthSecret.h"

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
namespace authsecret {
class BnAuthSecret : public ::ndk::BnCInterface<IAuthSecret> {
public:
  BnAuthSecret();
  virtual ~BnAuthSecret();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IAuthSecretDelegator : public BnAuthSecret {
public:
  explicit IAuthSecretDelegator(const std::shared_ptr<IAuthSecret> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IAuthSecret::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus setPrimaryUserCredential(const std::vector<uint8_t>& in_secret) override {
    return _impl->setPrimaryUserCredential(in_secret);
  }
protected:
private:
  std::shared_ptr<IAuthSecret> _impl;
};

}  // namespace authsecret
}  // namespace hardware
}  // namespace android
}  // namespace aidl
