#pragma once

#include "aidl/android/hardware/security/keymint/IRemotelyProvisionedComponent.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class BpRemotelyProvisionedComponent : public ::ndk::BpCInterface<IRemotelyProvisionedComponent> {
public:
  explicit BpRemotelyProvisionedComponent(const ::ndk::SpAIBinder& binder);
  virtual ~BpRemotelyProvisionedComponent();

  ::ndk::ScopedAStatus getHardwareInfo(::aidl::android::hardware::security::keymint::RpcHardwareInfo* _aidl_return) override;
  ::ndk::ScopedAStatus generateEcdsaP256KeyPair(bool in_testMode, ::aidl::android::hardware::security::keymint::MacedPublicKey* out_macedPublicKey, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus generateCertificateRequest(bool in_testMode, const std::vector<::aidl::android::hardware::security::keymint::MacedPublicKey>& in_keysToSign, const std::vector<uint8_t>& in_endpointEncryptionCertChain, const std::vector<uint8_t>& in_challenge, ::aidl::android::hardware::security::keymint::DeviceInfo* out_deviceInfo, ::aidl::android::hardware::security::keymint::ProtectedData* out_protectedData, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
