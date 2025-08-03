#pragma once

#include "aidl/android/hardware/soundtrigger3/ISoundTriggerHwGlobalCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace soundtrigger3 {
class BpSoundTriggerHwGlobalCallback : public ::ndk::BpCInterface<ISoundTriggerHwGlobalCallback> {
public:
  explicit BpSoundTriggerHwGlobalCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpSoundTriggerHwGlobalCallback();

  ::ndk::ScopedAStatus onResourcesAvailable() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace soundtrigger3
}  // namespace hardware
}  // namespace android
}  // namespace aidl
