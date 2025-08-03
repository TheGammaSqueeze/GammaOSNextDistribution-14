#pragma once

#include "aidl/android/hardware/gnss/IGnssCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssCallback : public ::ndk::BpCInterface<IGnssCallback> {
public:
  explicit BpGnssCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssCallback();

  ::ndk::ScopedAStatus gnssSetCapabilitiesCb(int32_t in_capabilities) override;
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
