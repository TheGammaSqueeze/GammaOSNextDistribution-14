#pragma once

#include "aidl/android/hardware/power/IPower.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
class BpPower : public ::ndk::BpCInterface<IPower> {
public:
  explicit BpPower(const ::ndk::SpAIBinder& binder);
  virtual ~BpPower();

  ::ndk::ScopedAStatus setMode(::aidl::android::hardware::power::Mode in_type, bool in_enabled) override;
  ::ndk::ScopedAStatus isModeSupported(::aidl::android::hardware::power::Mode in_type, bool* _aidl_return) override;
  ::ndk::ScopedAStatus setBoost(::aidl::android::hardware::power::Boost in_type, int32_t in_durationMs) override;
  ::ndk::ScopedAStatus isBoostSupported(::aidl::android::hardware::power::Boost in_type, bool* _aidl_return) override;
  ::ndk::ScopedAStatus createHintSession(int32_t in_tgid, int32_t in_uid, const std::vector<int32_t>& in_threadIds, int64_t in_durationNanos, std::shared_ptr<::aidl::android::hardware::power::IPowerHintSession>* _aidl_return) override;
  ::ndk::ScopedAStatus getHintSessionPreferredRate(int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
