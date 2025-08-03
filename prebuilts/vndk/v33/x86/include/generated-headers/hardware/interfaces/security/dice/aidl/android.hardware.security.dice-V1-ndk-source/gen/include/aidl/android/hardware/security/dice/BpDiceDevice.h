#pragma once

#include "aidl/android/hardware/security/dice/IDiceDevice.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace dice {
class BpDiceDevice : public ::ndk::BpCInterface<IDiceDevice> {
public:
  explicit BpDiceDevice(const ::ndk::SpAIBinder& binder);
  virtual ~BpDiceDevice();

  ::ndk::ScopedAStatus sign(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_id, const std::vector<uint8_t>& in_payload, ::aidl::android::hardware::security::dice::Signature* _aidl_return) override;
  ::ndk::ScopedAStatus getAttestationChain(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues, ::aidl::android::hardware::security::dice::Bcc* _aidl_return) override;
  ::ndk::ScopedAStatus derive(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues, ::aidl::android::hardware::security::dice::BccHandover* _aidl_return) override;
  ::ndk::ScopedAStatus demote(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace dice
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
