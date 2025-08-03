#pragma once

#include "aidl/android/hardware/identity/IWritableIdentityCredential.h"

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
class BnWritableIdentityCredential : public ::ndk::BnCInterface<IWritableIdentityCredential> {
public:
  BnWritableIdentityCredential();
  virtual ~BnWritableIdentityCredential();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IWritableIdentityCredentialDelegator : public BnWritableIdentityCredential {
public:
  explicit IWritableIdentityCredentialDelegator(const std::shared_ptr<IWritableIdentityCredential> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IWritableIdentityCredential::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getAttestationCertificate(const std::vector<uint8_t>& in_attestationApplicationId, const std::vector<uint8_t>& in_attestationChallenge, std::vector<::aidl::android::hardware::identity::Certificate>* _aidl_return) override {
    return _impl->getAttestationCertificate(in_attestationApplicationId, in_attestationChallenge, _aidl_return);
  }
  ::ndk::ScopedAStatus startPersonalization(int32_t in_accessControlProfileCount, const std::vector<int32_t>& in_entryCounts) override {
    return _impl->startPersonalization(in_accessControlProfileCount, in_entryCounts);
  }
  ::ndk::ScopedAStatus addAccessControlProfile(int32_t in_id, const ::aidl::android::hardware::identity::Certificate& in_readerCertificate, bool in_userAuthenticationRequired, int64_t in_timeoutMillis, int64_t in_secureUserId, ::aidl::android::hardware::identity::SecureAccessControlProfile* _aidl_return) override {
    return _impl->addAccessControlProfile(in_id, in_readerCertificate, in_userAuthenticationRequired, in_timeoutMillis, in_secureUserId, _aidl_return);
  }
  ::ndk::ScopedAStatus beginAddEntry(const std::vector<int32_t>& in_accessControlProfileIds, const std::string& in_nameSpace, const std::string& in_name, int32_t in_entrySize) override {
    return _impl->beginAddEntry(in_accessControlProfileIds, in_nameSpace, in_name, in_entrySize);
  }
  ::ndk::ScopedAStatus addEntryValue(const std::vector<uint8_t>& in_content, std::vector<uint8_t>* _aidl_return) override {
    return _impl->addEntryValue(in_content, _aidl_return);
  }
  ::ndk::ScopedAStatus finishAddingEntries(std::vector<uint8_t>* out_credentialData, std::vector<uint8_t>* out_proofOfProvisioningSignature) override {
    return _impl->finishAddingEntries(out_credentialData, out_proofOfProvisioningSignature);
  }
  ::ndk::ScopedAStatus setExpectedProofOfProvisioningSize(int32_t in_expectedProofOfProvisioningSize) override {
    return _impl->setExpectedProofOfProvisioningSize(in_expectedProofOfProvisioningSize);
  }
  ::ndk::ScopedAStatus setRemotelyProvisionedAttestationKey(const std::vector<uint8_t>& in_attestationKeyBlob, const std::vector<uint8_t>& in_attestationCertificate) override {
    return _impl->setRemotelyProvisionedAttestationKey(in_attestationKeyBlob, in_attestationCertificate);
  }
protected:
private:
  std::shared_ptr<IWritableIdentityCredential> _impl;
};

}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
