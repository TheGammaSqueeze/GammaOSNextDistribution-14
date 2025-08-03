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
  ::ndk::ScopedAStatus gnssStatusCb(::aidl::android::hardware::gnss::IGnssCallback::GnssStatusValue in_status) override;
  ::ndk::ScopedAStatus gnssSvStatusCb(const std::vector<::aidl::android::hardware::gnss::IGnssCallback::GnssSvInfo>& in_svInfoList) override;
  ::ndk::ScopedAStatus gnssLocationCb(const ::aidl::android::hardware::gnss::GnssLocation& in_location) override;
  ::ndk::ScopedAStatus gnssNmeaCb(int64_t in_timestamp, const std::string& in_nmea) override;
  ::ndk::ScopedAStatus gnssAcquireWakelockCb() override;
  ::ndk::ScopedAStatus gnssReleaseWakelockCb() override;
  ::ndk::ScopedAStatus gnssSetSystemInfoCb(const ::aidl::android::hardware::gnss::IGnssCallback::GnssSystemInfo& in_info) override;
  ::ndk::ScopedAStatus gnssRequestTimeCb() override;
  ::ndk::ScopedAStatus gnssRequestLocationCb(bool in_independentFromGnss, bool in_isUserEmergency) override;
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
