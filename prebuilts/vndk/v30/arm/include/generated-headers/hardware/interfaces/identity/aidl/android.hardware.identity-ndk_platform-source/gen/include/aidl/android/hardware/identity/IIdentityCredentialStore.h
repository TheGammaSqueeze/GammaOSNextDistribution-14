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
#include <aidl/android/hardware/identity/CipherSuite.h>
#include <aidl/android/hardware/identity/HardwareInformation.h>
#include <aidl/android/hardware/identity/IIdentityCredential.h>
#include <aidl/android/hardware/identity/IWritableIdentityCredential.h>

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class IIdentityCredentialStore : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IIdentityCredentialStore();
  virtual ~IIdentityCredentialStore();


  enum : int32_t {
    STATUS_OK = 0,
    STATUS_FAILED = 1,
    STATUS_CIPHER_SUITE_NOT_SUPPORTED = 2,
    STATUS_INVALID_DATA = 3,
    STATUS_INVALID_AUTH_TOKEN = 4,
    STATUS_INVALID_ITEMS_REQUEST_MESSAGE = 5,
    STATUS_READER_SIGNATURE_CHECK_FAILED = 6,
    STATUS_EPHEMERAL_PUBLIC_KEY_NOT_FOUND = 7,
    STATUS_USER_AUTHENTICATION_FAILED = 8,
    STATUS_READER_AUTHENTICATION_FAILED = 9,
    STATUS_NO_ACCESS_CONTROL_PROFILES = 10,
    STATUS_NOT_IN_REQUEST_MESSAGE = 11,
    STATUS_SESSION_TRANSCRIPT_MISMATCH = 12,
  };
  static const int32_t version = 2;
  static inline const std::string hash = "194e04be642728623d65ec8321a3764fdea52ae0";

  static std::shared_ptr<IIdentityCredentialStore> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IIdentityCredentialStore>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IIdentityCredentialStore>* instance);
  static bool setDefaultImpl(std::shared_ptr<IIdentityCredentialStore> impl);
  static const std::shared_ptr<IIdentityCredentialStore>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getHardwareInformation(::aidl::android::hardware::identity::HardwareInformation* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus createCredential(const std::string& in_docType, bool in_testCredential, std::shared_ptr<::aidl::android::hardware::identity::IWritableIdentityCredential>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCredential(::aidl::android::hardware::identity::CipherSuite in_cipherSuite, const std::vector<int8_t>& in_credentialData, std::shared_ptr<::aidl::android::hardware::identity::IIdentityCredential>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IIdentityCredentialStore> default_impl;
};
class IIdentityCredentialStoreDefault : public IIdentityCredentialStore {
public:
  ::ndk::ScopedAStatus getHardwareInformation(::aidl::android::hardware::identity::HardwareInformation* _aidl_return) override;
  ::ndk::ScopedAStatus createCredential(const std::string& in_docType, bool in_testCredential, std::shared_ptr<::aidl::android::hardware::identity::IWritableIdentityCredential>* _aidl_return) override;
  ::ndk::ScopedAStatus getCredential(::aidl::android::hardware::identity::CipherSuite in_cipherSuite, const std::vector<int8_t>& in_credentialData, std::shared_ptr<::aidl::android::hardware::identity::IIdentityCredential>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
