#pragma once

#include "aidl/android/hardware/authsecret/IAuthSecret.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace authsecret {
class BpAuthSecret : public ::ndk::BpCInterface<IAuthSecret> {
public:
  explicit BpAuthSecret(const ::ndk::SpAIBinder& binder);
  virtual ~BpAuthSecret();

  ::ndk::ScopedAStatus setPrimaryUserCredential(const std::vector<uint8_t>& in_secret) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace authsecret
}  // namespace hardware
}  // namespace android
}  // namespace aidl
