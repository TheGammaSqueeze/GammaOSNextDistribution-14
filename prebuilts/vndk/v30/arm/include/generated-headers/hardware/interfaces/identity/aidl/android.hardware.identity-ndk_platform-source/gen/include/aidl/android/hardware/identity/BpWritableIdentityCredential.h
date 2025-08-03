#pragma once

#include "aidl/android/hardware/identity/IWritableIdentityCredential.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class BpWritableIdentityCredential : public ::ndk::BpCInterface<IWritableIdentityCredential> {
public:
  BpWritableIdentityCredential(const ::ndk::SpAIBinder& binder);
  virtual ~BpWritableIdentityCredential();

  ::ndk::ScopedAStatus getAttestationCertificate(const std::vector<int8_t>& in_attestationApplicationId, const std::vector<int8_t>& in_attestationChallenge, std::vector<::aidl::android::hardware::identity::Certificate>* _aidl_return) override;
  ::ndk::ScopedAStatus startPersonalization(int32_t in_accessControlProfileCount, const std::vector<int32_t>& in_entryCounts) override;
  ::ndk::ScopedAStatus addAccessControlProfile(int32_t in_id, const ::aidl::android::hardware::identity::Certificate& in_readerCertificate, bool in_userAuthenticationRequired, int64_t in_timeoutMillis, int64_t in_secureUserId, ::aidl::android::hardware::identity::SecureAccessControlProfile* _aidl_return) override;
  ::ndk::ScopedAStatus beginAddEntry(const std::vector<int32_t>& in_accessControlProfileIds, const std::string& in_nameSpace, const std::string& in_name, int32_t in_entrySize) override;
  ::ndk::ScopedAStatus addEntryValue(const std::vector<int8_t>& in_content, std::vector<int8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus finishAddingEntries(std::vector<int8_t>* out_credentialData, std::vector<int8_t>* out_proofOfProvisioningSignature) override;
  ::ndk::ScopedAStatus setExpectedProofOfProvisioningSize(int32_t in_expectedProofOfProvisioningSize) override;
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
