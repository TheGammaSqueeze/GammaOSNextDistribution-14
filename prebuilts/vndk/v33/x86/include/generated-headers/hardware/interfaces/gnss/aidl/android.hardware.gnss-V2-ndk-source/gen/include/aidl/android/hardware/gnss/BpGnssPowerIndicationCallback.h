#pragma once

#include "aidl/android/hardware/gnss/IGnssPowerIndicationCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssPowerIndicationCallback : public ::ndk::BpCInterface<IGnssPowerIndicationCallback> {
public:
  explicit BpGnssPowerIndicationCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssPowerIndicationCallback();

  ::ndk::ScopedAStatus setCapabilitiesCb(int32_t in_capabilities) override;
  ::ndk::ScopedAStatus gnssPowerStatsCb(const ::aidl::android::hardware::gnss::GnssPowerStats& in_gnssPowerStats) override;
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
