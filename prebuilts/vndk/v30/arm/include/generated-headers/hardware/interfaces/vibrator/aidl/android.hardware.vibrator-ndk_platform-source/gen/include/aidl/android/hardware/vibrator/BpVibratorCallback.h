#pragma once

#include "aidl/android/hardware/vibrator/IVibratorCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class BpVibratorCallback : public ::ndk::BpCInterface<IVibratorCallback> {
public:
  BpVibratorCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpVibratorCallback();

  ::ndk::ScopedAStatus onComplete() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
