#pragma once

#include "aidl/android/hardware/security/sharedsecret/ISharedSecret.h"

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
namespace security {
namespace sharedsecret {
class BnSharedSecret : public ::ndk::BnCInterface<ISharedSecret> {
public:
  BnSharedSecret();
  virtual ~BnSharedSecret();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISharedSecretDelegator : public BnSharedSecret {
public:
  explicit ISharedSecretDelegator(const std::shared_ptr<ISharedSecret> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISharedSecret::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getSharedSecretParameters(::aidl::android::hardware::security::sharedsecret::SharedSecretParameters* _aidl_return) override {
    return _impl->getSharedSecretParameters(_aidl_return);
  }
  ::ndk::ScopedAStatus computeSharedSecret(const std::vector<::aidl::android::hardware::security::sharedsecret::SharedSecretParameters>& in_params, std::vector<uint8_t>* _aidl_return) override {
    return _impl->computeSharedSecret(in_params, _aidl_return);
  }
protected:
private:
  std::shared_ptr<ISharedSecret> _impl;
};

}  // namespace sharedsecret
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
