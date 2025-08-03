#pragma once

#include "aidl/android/hardware/power/IPowerHintSession.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
class BpPowerHintSession : public ::ndk::BpCInterface<IPowerHintSession> {
public:
  explicit BpPowerHintSession(const ::ndk::SpAIBinder& binder);
  virtual ~BpPowerHintSession();

  ::ndk::ScopedAStatus updateTargetWorkDuration(int64_t in_targetDurationNanos) override;
  ::ndk::ScopedAStatus reportActualWorkDuration(const std::vector<::aidl::android::hardware::power::WorkDuration>& in_durations) override;
  ::ndk::ScopedAStatus pause() override;
  ::ndk::ScopedAStatus resume() override;
  ::ndk::ScopedAStatus close() override;
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
