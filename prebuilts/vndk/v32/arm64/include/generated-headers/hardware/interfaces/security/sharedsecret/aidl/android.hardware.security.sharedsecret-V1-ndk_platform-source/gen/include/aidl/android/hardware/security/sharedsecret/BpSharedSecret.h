#pragma once

#include "aidl/android/hardware/security/sharedsecret/ISharedSecret.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace sharedsecret {
class BpSharedSecret : public ::ndk::BpCInterface<ISharedSecret> {
public:
  explicit BpSharedSecret(const ::ndk::SpAIBinder& binder);
  virtual ~BpSharedSecret();

  ::ndk::ScopedAStatus getSharedSecretParameters(::aidl::android::hardware::security::sharedsecret::SharedSecretParameters* _aidl_return) override;
  ::ndk::ScopedAStatus computeSharedSecret(const std::vector<::aidl::android::hardware::security::sharedsecret::SharedSecretParameters>& in_params, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace sharedsecret
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
