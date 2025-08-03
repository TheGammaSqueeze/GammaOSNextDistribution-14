#pragma once

#include "aidl/android/hardware/gnss/visibility_control/IGnssVisibilityControl.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace visibility_control {
class BpGnssVisibilityControl : public ::ndk::BpCInterface<IGnssVisibilityControl> {
public:
  explicit BpGnssVisibilityControl(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssVisibilityControl();

  ::ndk::ScopedAStatus enableNfwLocationAccess(const std::vector<std::string>& in_proxyApps) override;
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::visibility_control::IGnssVisibilityControlCallback>& in_callback) override;
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
