#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/identity/Certificate.h>
#include <aidl/android/hardware/identity/SecureAccessControlProfile.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class IWritableIdentityCredential : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IWritableIdentityCredential();
  virtual ~IWritableIdentityCredential();

  static const int32_t version = 4;
  static inline const std::string hash = "2d63b17eb0f860c87b3564eda281342fb362e38d";
  static constexpr uint32_t TRANSACTION_getAttestationCertificate = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_startPersonalization = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_addAccessControlProfile = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_beginAddEntry = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_addEntryValue = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_finishAddingEntries = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_setExpectedProofOfProvisioningSize = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_setRemotelyProvisionedAttestationKey = FIRST_CALL_TRANSACTION + 7;

  static std::shared_ptr<IWritableIdentityCredential> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IWritableIdentityCredential>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IWritableIdentityCredential>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IWritableIdentityCredential>& impl);
  static const std::shared_ptr<IWritableIdentityCredential>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getAttestationCertificate(const std::vector<uint8_t>& in_attestationApplicationId, const std::vector<uint8_t>& in_attestationChallenge, std::vector<::aidl::android::hardware::identity::Certificate>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus startPersonalization(int32_t in_accessControlProfileCount, const std::vector<int32_t>& in_entryCounts) = 0;
  virtual ::ndk::ScopedAStatus addAccessControlProfile(int32_t in_id, const ::aidl::android::hardware::identity::Certificate& in_readerCertificate, bool in_userAuthenticationRequired, int64_t in_timeoutMillis, int64_t in_secureUserId, ::aidl::android::hardware::identity::SecureAccessControlProfile* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus beginAddEntry(const std::vector<int32_t>& in_accessControlProfileIds, const std::string& in_nameSpace, const std::string& in_name, int32_t in_entrySize) = 0;
  virtual ::ndk::ScopedAStatus addEntryValue(const std::vector<uint8_t>& in_content, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus finishAddingEntries(std::vector<uint8_t>* out_credentialData, std::vector<uint8_t>* out_proofOfProvisioningSignature) = 0;
  virtual ::ndk::ScopedAStatus setExpectedProofOfProvisioningSize(int32_t in_expectedProofOfProvisioningSize) = 0;
  virtual ::ndk::ScopedAStatus setRemotelyProvisionedAttestationKey(const std::vector<uint8_t>& in_attestationKeyBlob, const std::vector<uint8_t>& in_attestationCertificate) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IWritableIdentityCredential> default_impl;
};
class IWritableIdentityCredentialDefault : public IWritableIdentityCredential {
public:
  ::ndk::ScopedAStatus getAttestationCertificate(const std::vector<uint8_t>& in_attestationApplicationId, const std::vector<uint8_t>& in_attestationChallenge, std::vector<::aidl::android::hardware::identity::Certificate>* _aidl_return) override;
  ::ndk::ScopedAStatus startPersonalization(int32_t in_accessControlProfileCount, const std::vector<int32_t>& in_entryCounts) override;
  ::ndk::ScopedAStatus addAccessControlProfile(int32_t in_id, const ::aidl::android::hardware::identity::Certificate& in_readerCertificate, bool in_userAuthenticationRequired, int64_t in_timeoutMillis, int64_t in_secureUserId, ::aidl::android::hardware::identity::SecureAccessControlProfile* _aidl_return) override;
  ::ndk::ScopedAStatus beginAddEntry(const std::vector<int32_t>& in_accessControlProfileIds, const std::string& in_nameSpace, const std::string& in_name, int32_t in_entrySize) override;
  ::ndk::ScopedAStatus addEntryValue(const std::vector<uint8_t>& in_content, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus finishAddingEntries(std::vector<uint8_t>* out_credentialData, std::vector<uint8_t>* out_proofOfProvisioningSignature) override;
  ::ndk::ScopedAStatus setExpectedProofOfProvisioningSize(int32_t in_expectedProofOfProvisioningSize) override;
  ::ndk::ScopedAStatus setRemotelyProvisionedAttestationKey(const std::vector<uint8_t>& in_attestationKeyBlob, const std::vector<uint8_t>& in_attestationCertificate) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
