#pragma once

#include "aidl/android/hardware/weaver/IWeaver.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace weaver {
class BpWeaver : public ::ndk::BpCInterface<IWeaver> {
public:
  explicit BpWeaver(const ::ndk::SpAIBinder& binder);
  virtual ~BpWeaver();

  ::ndk::ScopedAStatus getConfig(::aidl::android::hardware::weaver::WeaverConfig* _aidl_return) override;
  ::ndk::ScopedAStatus read(int32_t in_slotId, const std::vector<uint8_t>& in_key, ::aidl::android::hardware::weaver::WeaverReadResponse* _aidl_return) override;
  ::ndk::ScopedAStatus write(int32_t in_slotId, const std::vector<uint8_t>& in_key, const std::vector<uint8_t>& in_value) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace weaver
}  // namespace hardware
}  // namespace android
}  // namespace aidl
