#pragma once

#include "aidl/android/hardware/gnss/IAGnss.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpAGnss : public ::ndk::BpCInterface<IAGnss> {
public:
  explicit BpAGnss(const ::ndk::SpAIBinder& binder);
  virtual ~BpAGnss();

  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IAGnssCallback>& in_callback) override;
  ::ndk::ScopedAStatus dataConnClosed() override;
  ::ndk::ScopedAStatus dataConnFailed() override;
  ::ndk::ScopedAStatus setServer(::aidl::android::hardware::gnss::IAGnssCallback::AGnssType in_type, const std::string& in_hostname, int32_t in_port) override;
  ::ndk::ScopedAStatus dataConnOpen(int64_t in_networkHandle, const std::string& in_apn, ::aidl::android::hardware::gnss::IAGnss::ApnIpType in_apnIpType) override;
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
