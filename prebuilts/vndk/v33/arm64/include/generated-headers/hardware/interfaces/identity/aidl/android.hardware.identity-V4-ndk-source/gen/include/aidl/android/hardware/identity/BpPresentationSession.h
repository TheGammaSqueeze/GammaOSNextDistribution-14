#pragma once

#include "aidl/android/hardware/identity/IPresentationSession.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class BpPresentationSession : public ::ndk::BpCInterface<IPresentationSession> {
public:
  explicit BpPresentationSession(const ::ndk::SpAIBinder& binder);
  virtual ~BpPresentationSession();

  ::ndk::ScopedAStatus getEphemeralKeyPair(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getAuthChallenge(int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus setReaderEphemeralPublicKey(const std::vector<uint8_t>& in_publicKey) override;
  ::ndk::ScopedAStatus setSessionTranscript(const std::vector<uint8_t>& in_sessionTranscript) override;
  ::ndk::ScopedAStatus getCredential(const std::vector<uint8_t>& in_credentialData, std::shared_ptr<::aidl::android::hardware::identity::IIdentityCredential>* _aidl_return) override;
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
