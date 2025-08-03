#pragma once

#include "aidl/android/hardware/identity/IIdentityCredentialStore.h"

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
namespace identity {
class BnIdentityCredentialStore : public ::ndk::BnCInterface<IIdentityCredentialStore> {
public:
  BnIdentityCredentialStore();
  virtual ~BnIdentityCredentialStore();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IIdentityCredentialStoreDelegator : public BnIdentityCredentialStore {
public:
  explicit IIdentityCredentialStoreDelegator(const std::shared_ptr<IIdentityCredentialStore> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IIdentityCredentialStore::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getHardwareInformation(::aidl::android::hardware::identity::HardwareInformation* _aidl_return) override {
    return _impl->getHardwareInformation(_aidl_return);
  }
  ::ndk::ScopedAStatus createCredential(const std::string& in_docType, bool in_testCredential, std::shared_ptr<::aidl::android::hardware::identity::IWritableIdentityCredential>* _aidl_return) override {
    return _impl->createCredential(in_docType, in_testCredential, _aidl_return);
  }
  ::ndk::ScopedAStatus getCredential(::aidl::android::hardware::identity::CipherSuite in_cipherSuite, const std::vector<uint8_t>& in_credentialData, std::shared_ptr<::aidl::android::hardware::identity::IIdentityCredential>* _aidl_return) override {
    return _impl->getCredential(in_cipherSuite, in_credentialData, _aidl_return);
  }
  ::ndk::ScopedAStatus createPresentationSession(::aidl::android::hardware::identity::CipherSuite in_cipherSuite, std::shared_ptr<::aidl::android::hardware::identity::IPresentationSession>* _aidl_return) override {
    return _impl->createPresentationSession(in_cipherSuite, _aidl_return);
  }
  ::ndk::ScopedAStatus getRemotelyProvisionedComponent(std::shared_ptr<::aidl::android::hardware::security::keymint::IRemotelyProvisionedComponent>* _aidl_return) override {
    return _impl->getRemotelyProvisionedComponent(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IIdentityCredentialStore> _impl;
};

}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
