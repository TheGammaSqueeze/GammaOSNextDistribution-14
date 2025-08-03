#pragma once

#include "aidl/android/system/keystore2/IKeystoreOperation.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class BpKeystoreOperation : public ::ndk::BpCInterface<IKeystoreOperation> {
public:
  explicit BpKeystoreOperation(const ::ndk::SpAIBinder& binder);
  virtual ~BpKeystoreOperation();

  ::ndk::ScopedAStatus updateAad(const std::vector<uint8_t>& in_aadInput) override;
  ::ndk::ScopedAStatus update(const std::vector<uint8_t>& in_input, std::optional<std::vector<uint8_t>>* _aidl_return) override;
  ::ndk::ScopedAStatus finish(const std::optional<std::vector<uint8_t>>& in_input, const std::optional<std::vector<uint8_t>>& in_signature, std::optional<std::vector<uint8_t>>* _aidl_return) override;
  ::ndk::ScopedAStatus abort() override;
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
