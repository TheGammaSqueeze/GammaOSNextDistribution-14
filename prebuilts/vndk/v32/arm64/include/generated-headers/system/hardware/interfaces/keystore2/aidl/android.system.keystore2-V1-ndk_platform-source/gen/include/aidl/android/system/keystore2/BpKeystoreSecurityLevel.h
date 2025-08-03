#pragma once

#include "aidl/android/system/keystore2/IKeystoreSecurityLevel.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class BpKeystoreSecurityLevel : public ::ndk::BpCInterface<IKeystoreSecurityLevel> {
public:
  explicit BpKeystoreSecurityLevel(const ::ndk::SpAIBinder& binder);
  virtual ~BpKeystoreSecurityLevel();

  ::ndk::ScopedAStatus createOperation(const ::aidl::android::system::keystore2::KeyDescriptor& in_key, const std::vector<::aidl::android::hardware::security::keymint::KeyParameter>& in_operationParameters, bool in_forced, ::aidl::android::system::keystore2::CreateOperationResponse* _aidl_return) override;
  ::ndk::ScopedAStatus generateKey(const ::aidl::android::system::keystore2::KeyDescriptor& in_key, const std::optional<::aidl::android::system::keystore2::KeyDescriptor>& in_attestationKey, const std::vector<::aidl::android::hardware::security::keymint::KeyParameter>& in_params, int32_t in_flags, const std::vector<uint8_t>& in_entropy, ::aidl::android::system::keystore2::KeyMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus importKey(const ::aidl::android::system::keystore2::KeyDescriptor& in_key, const std::optional<::aidl::android::system::keystore2::KeyDescriptor>& in_attestationKey, const std::vector<::aidl::android::hardware::security::keymint::KeyParameter>& in_params, int32_t in_flags, const std::vector<uint8_t>& in_keyData, ::aidl::android::system::keystore2::KeyMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus importWrappedKey(const ::aidl::android::system::keystore2::KeyDescriptor& in_key, const ::aidl::android::system::keystore2::KeyDescriptor& in_wrappingKey, const std::optional<std::vector<uint8_t>>& in_maskingKey, const std::vector<::aidl::android::hardware::security::keymint::KeyParameter>& in_params, const std::vector<::aidl::android::system::keystore2::AuthenticatorSpec>& in_authenticators, ::aidl::android::system::keystore2::KeyMetadata* _aidl_return) override;
  ::ndk::ScopedAStatus convertStorageKeyToEphemeral(const ::aidl::android::system::keystore2::KeyDescriptor& in_storageKey, ::aidl::android::system::keystore2::EphemeralStorageKeyResponse* _aidl_return) override;
  ::ndk::ScopedAStatus deleteKey(const ::aidl::android::system::keystore2::KeyDescriptor& in_key) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
