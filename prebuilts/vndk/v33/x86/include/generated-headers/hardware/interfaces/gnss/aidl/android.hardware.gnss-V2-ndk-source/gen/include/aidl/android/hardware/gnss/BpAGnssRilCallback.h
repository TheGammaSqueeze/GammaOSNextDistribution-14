#pragma once

#include "aidl/android/hardware/gnss/IAGnssRilCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpAGnssRilCallback : public ::ndk::BpCInterface<IAGnssRilCallback> {
public:
  explicit BpAGnssRilCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpAGnssRilCallback();

  ::ndk::ScopedAStatus requestSetIdCb(int32_t in_setIdflag) override;
  ::ndk::ScopedAStatus requestRefLocCb() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
