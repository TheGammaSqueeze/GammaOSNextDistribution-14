#pragma once

#include "aidl/android/hardware/wifi/hostapd/IHostapd.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
class BpHostapd : public ::ndk::BpCInterface<IHostapd> {
public:
  explicit BpHostapd(const ::ndk::SpAIBinder& binder);
  virtual ~BpHostapd();

  ::ndk::ScopedAStatus addAccessPoint(const ::aidl::android::hardware::wifi::hostapd::IfaceParams& in_ifaceParams, const ::aidl::android::hardware::wifi::hostapd::NetworkParams& in_nwParams) override;
  ::ndk::ScopedAStatus forceClientDisconnect(const std::string& in_ifaceName, const std::vector<uint8_t>& in_clientAddress, ::aidl::android::hardware::wifi::hostapd::Ieee80211ReasonCode in_reasonCode) override;
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::hostapd::IHostapdCallback>& in_callback) override;
  ::ndk::ScopedAStatus removeAccessPoint(const std::string& in_ifaceName) override;
  ::ndk::ScopedAStatus setDebugParams(::aidl::android::hardware::wifi::hostapd::DebugLevel in_level) override;
  ::ndk::ScopedAStatus terminate() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
