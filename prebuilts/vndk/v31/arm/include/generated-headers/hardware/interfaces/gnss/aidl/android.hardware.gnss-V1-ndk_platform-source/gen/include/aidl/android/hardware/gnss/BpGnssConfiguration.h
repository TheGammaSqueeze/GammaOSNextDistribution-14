#pragma once

#include "aidl/android/hardware/gnss/IGnssConfiguration.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssConfiguration : public ::ndk::BpCInterface<IGnssConfiguration> {
public:
  explicit BpGnssConfiguration(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssConfiguration();

  ::ndk::ScopedAStatus setSuplVersion(int32_t in_version) override;
  ::ndk::ScopedAStatus setSuplMode(int32_t in_mode) override;
  ::ndk::ScopedAStatus setLppProfile(int32_t in_lppProfile) override;
  ::ndk::ScopedAStatus setGlonassPositioningProtocol(int32_t in_protocol) override;
  ::ndk::ScopedAStatus setEmergencySuplPdn(bool in_enable) override;
  ::ndk::ScopedAStatus setEsExtensionSec(int32_t in_emergencyExtensionSeconds) override;
  ::ndk::ScopedAStatus setBlocklist(const std::vector<::aidl::android::hardware::gnss::BlocklistedSource>& in_blocklist) override;
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
