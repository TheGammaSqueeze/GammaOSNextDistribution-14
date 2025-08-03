#pragma once

#include "aidl/android/hardware/identity/IIdentityCredential.h"

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
class BnIdentityCredential : public ::ndk::BnCInterface<IIdentityCredential> {
public:
  BnIdentityCredential();
  virtual ~BnIdentityCredential();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IIdentityCredentialDelegator : public BnIdentityCredential {
public:
  explicit IIdentityCredentialDelegator(const std::shared_ptr<IIdentityCredential> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IIdentityCredential::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus deleteCredential(std::vector<uint8_t>* _aidl_return) override __attribute__((deprecated("use deleteCredentalWithChallenge() instead."))) {
    return _impl->deleteCredential(_aidl_return);
  }
  ::ndk::ScopedAStatus createEphemeralKeyPair(std::vector<uint8_t>* _aidl_return) override {
    return _impl->createEphemeralKeyPair(_aidl_return);
  }
  ::ndk::ScopedAStatus setReaderEphemeralPublicKey(const std::vector<uint8_t>& in_publicKey) override {
    return _impl->setReaderEphemeralPublicKey(in_publicKey);
  }
  ::ndk::ScopedAStatus createAuthChallenge(int64_t* _aidl_return) override {
    return _impl->createAuthChallenge(_aidl_return);
  }
  ::ndk::ScopedAStatus startRetrieval(const std::vector<::aidl::android::hardware::identity::SecureAccessControlProfile>& in_accessControlProfiles, const ::aidl::android::hardware::keymaster::HardwareAuthToken& in_authToken, const std::vector<uint8_t>& in_itemsRequest, const std::vector<uint8_t>& in_signingKeyBlob, const std::vector<uint8_t>& in_sessionTranscript, const std::vector<uint8_t>& in_readerSignature, const std::vector<int32_t>& in_requestCounts) override {
    return _impl->startRetrieval(in_accessControlProfiles, in_authToken, in_itemsRequest, in_signingKeyBlob, in_sessionTranscript, in_readerSignature, in_requestCounts);
  }
  ::ndk::ScopedAStatus startRetrieveEntryValue(const std::string& in_nameSpace, const std::string& in_name, int32_t in_entrySize, const std::vector<int32_t>& in_accessControlProfileIds) override {
    return _impl->startRetrieveEntryValue(in_nameSpace, in_name, in_entrySize, in_accessControlProfileIds);
  }
  ::ndk::ScopedAStatus retrieveEntryValue(const std::vector<uint8_t>& in_encryptedContent, std::vector<uint8_t>* _aidl_return) override {
    return _impl->retrieveEntryValue(in_encryptedContent, _aidl_return);
  }
  ::ndk::ScopedAStatus finishRetrieval(std::vector<uint8_t>* out_mac, std::vector<uint8_t>* out_deviceNameSpaces) override {
    return _impl->finishRetrieval(out_mac, out_deviceNameSpaces);
  }
  ::ndk::ScopedAStatus generateSigningKeyPair(std::vector<uint8_t>* out_signingKeyBlob, ::aidl::android::hardware::identity::Certificate* _aidl_return) override {
    return _impl->generateSigningKeyPair(out_signingKeyBlob, _aidl_return);
  }
  ::ndk::ScopedAStatus setRequestedNamespaces(const std::vector<::aidl::android::hardware::identity::RequestNamespace>& in_requestNamespaces) override {
    return _impl->setRequestedNamespaces(in_requestNamespaces);
  }
  ::ndk::ScopedAStatus setVerificationToken(const ::aidl::android::hardware::keymaster::VerificationToken& in_verificationToken) override {
    return _impl->setVerificationToken(in_verificationToken);
  }
  ::ndk::ScopedAStatus deleteCredentialWithChallenge(const std::vector<uint8_t>& in_challenge, std::vector<uint8_t>* _aidl_return) override {
    return _impl->deleteCredentialWithChallenge(in_challenge, _aidl_return);
  }
  ::ndk::ScopedAStatus proveOwnership(const std::vector<uint8_t>& in_challenge, std::vector<uint8_t>* _aidl_return) override {
    return _impl->proveOwnership(in_challenge, _aidl_return);
  }
  ::ndk::ScopedAStatus updateCredential(std::shared_ptr<::aidl::android::hardware::identity::IWritableIdentityCredential>* _aidl_return) override {
    return _impl->updateCredential(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IIdentityCredential> _impl;
};

}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
