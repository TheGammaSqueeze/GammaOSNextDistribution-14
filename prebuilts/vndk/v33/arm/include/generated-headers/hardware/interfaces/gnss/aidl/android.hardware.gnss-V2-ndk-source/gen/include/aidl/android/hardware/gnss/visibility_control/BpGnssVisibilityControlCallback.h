#pragma once

#include "aidl/android/hardware/gnss/visibility_control/IGnssVisibilityControlCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace visibility_control {
class BpGnssVisibilityControlCallback : public ::ndk::BpCInterface<IGnssVisibilityControlCallback> {
public:
  explicit BpGnssVisibilityControlCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssVisibilityControlCallback();

  ::ndk::ScopedAStatus nfwNotifyCb(const ::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback::NfwNotification& in_notification) override;
  ::ndk::ScopedAStatus isInEmergencySession(bool* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace visibility_control
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
