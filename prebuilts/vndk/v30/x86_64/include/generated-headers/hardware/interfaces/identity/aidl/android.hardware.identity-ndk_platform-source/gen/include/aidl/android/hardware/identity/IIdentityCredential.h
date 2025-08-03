#pragma once

#include <android/binder_interface_utils.h>

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/identity/Certificate.h>
#include <aidl/android/hardware/identity/RequestNamespace.h>
#include <aidl/android/hardware/identity/SecureAccessControlProfile.h>
#include <aidl/android/hardware/keymaster/HardwareAuthToken.h>
#include <aidl/android/hardware/keymaster/VerificationToken.h>

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class IIdentityCredential : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IIdentityCredential();
  virtual ~IIdentityCredential();


  static const int32_t version = 2;
  static inline const std::string hash = "194e04be642728623d65ec8321a3764fdea52ae0";

  static std::shared_ptr<IIdentityCredential> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IIdentityCredential>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IIdentityCredential>* instance);
  static bool setDefaultImpl(std::shared_ptr<IIdentityCredential> impl);
  static const std::shared_ptr<IIdentityCredential>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus deleteCredential(std::vector<int8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus createEphemeralKeyPair(std::vector<int8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setReaderEphemeralPublicKey(const std::vector<int8_t>& in_publicKey) = 0;
  virtual ::ndk::ScopedAStatus createAuthChallenge(int64_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus startRetrieval(const std::vector<::aidl::android::hardware::identity::SecureAccessControlProfile>& in_accessControlProfiles, const ::aidl::android::hardware::keymaster::HardwareAuthToken& in_authToken, const std::vector<int8_t>& in_itemsRequest, const std::vector<int8_t>& in_signingKeyBlob, const std::vector<int8_t>& in_sessionTranscript, const std::vector<int8_t>& in_readerSignature, const std::vector<int32_t>& in_requestCounts) = 0;
  virtual ::ndk::ScopedAStatus startRetrieveEntryValue(const std::string& in_nameSpace, const std::string& in_name, int32_t in_entrySize, const std::vector<int32_t>& in_accessControlProfileIds) = 0;
  virtual ::ndk::ScopedAStatus retrieveEntryValue(const std::vector<int8_t>& in_encryptedContent, std::vector<int8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus finishRetrieval(std::vector<int8_t>* out_mac, std::vector<int8_t>* out_deviceNameSpaces) = 0;
  virtual ::ndk::ScopedAStatus generateSigningKeyPair(std::vector<int8_t>* out_signingKeyBlob, ::aidl::android::hardware::identity::Certificate* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setRequestedNamespaces(const std::vector<::aidl::android::hardware::identity::RequestNamespace>& in_requestNamespaces) = 0;
  virtual ::ndk::ScopedAStatus setVerificationToken(const ::aidl::android::hardware::keymaster::VerificationToken& in_verificationToken) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IIdentityCredential> default_impl;
};
class IIdentityCredentialDefault : public IIdentityCredential {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
