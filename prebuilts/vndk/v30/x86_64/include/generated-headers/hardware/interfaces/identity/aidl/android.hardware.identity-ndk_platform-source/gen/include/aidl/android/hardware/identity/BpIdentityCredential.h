#pragma once

#include "aidl/android/hardware/identity/IIdentityCredential.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class BpIdentityCredential : public ::ndk::BpCInterface<IIdentityCredential> {
public:
  BpIdentityCredential(const ::ndk::SpAIBinder& binder);
  virtual ~BpIdentityCredential();

  ::ndk::ScopedAStatus deleteCredential(std::vector<int8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus createEphemeralKeyPair(std::vector<int8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus setReaderEphemeralPublicKey(const std::vector<int8_t>& in_publicKey) override;
  ::ndk::ScopedAStatus createAuthChallenge(int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus startRetrieval(const std::vector<::aidl::android::hardware::identity::SecureAccessControlProfile>& in_accessControlProfiles, const ::aidl::android::hardware::keymaster::HardwareAuthToken& in_authToken, const std::vector<int8_t>& in_itemsRequest, const std::vector<int8_t>& in_signingKeyBlob, const std::vector<int8_t>& in_sessionTranscript, const std::vector<int8_t>& in_readerSignature, const std::vector<int32_t>& in_requestCounts) override;
  ::ndk::ScopedAStatus startRetrieveEntryValue(const std::string& in_nameSpace, const std::string& in_name, int32_t in_entrySize, const std::vector<int32_t>& in_accessControlProfileIds) override;
  ::ndk::ScopedAStatus retrieveEntryValue(const std::vector<int8_t>& in_encryptedContent, std::vector<int8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus finishRetrieval(std::vector<int8_t>* out_mac, std::vector<int8_t>* out_deviceNameSpaces) override;
  ::ndk::ScopedAStatus generateSigningKeyPair(std::vector<int8_t>* out_signingKeyBlob, ::aidl::android::hardware::identity::Certificate* _aidl_return) override;
  ::ndk::ScopedAStatus setRequestedNamespaces(const std::vector<::aidl::android::hardware::identity::RequestNamespace>& in_requestNamespaces) override;
  ::ndk::ScopedAStatus setVerificationToken(const ::aidl::android::hardware::keymaster::VerificationToken& in_verificationToken) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
