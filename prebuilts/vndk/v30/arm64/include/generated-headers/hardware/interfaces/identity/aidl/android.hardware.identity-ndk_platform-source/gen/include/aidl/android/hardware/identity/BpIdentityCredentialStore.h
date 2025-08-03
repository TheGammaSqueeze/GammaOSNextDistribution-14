#pragma once

#include "aidl/android/hardware/identity/IIdentityCredentialStore.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class BpIdentityCredentialStore : public ::ndk::BpCInterface<IIdentityCredentialStore> {
public:
  BpIdentityCredentialStore(const ::ndk::SpAIBinder& binder);
  virtual ~BpIdentityCredentialStore();

  ::ndk::ScopedAStatus getHardwareInformation(::aidl::android::hardware::identity::HardwareInformation* _aidl_return) override;
  ::ndk::ScopedAStatus createCredential(const std::string& in_docType, bool in_testCredential, std::shared_ptr<::aidl::android::hardware::identity::IWritableIdentityCredential>* _aidl_return) override;
  ::ndk::ScopedAStatus getCredential(::aidl::android::hardware::identity::CipherSuite in_cipherSuite, const std::vector<int8_t>& in_credentialData, std::shared_ptr<::aidl::android::hardware::identity::IIdentityCredential>* _aidl_return) override;
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
