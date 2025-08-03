#pragma once

#include "aidl/android/hardware/wifi/hostapd/IHostapdCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
class BpHostapdCallback : public ::ndk::BpCInterface<IHostapdCallback> {
public:
  explicit BpHostapdCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpHostapdCallback();

  ::ndk::ScopedAStatus onApInstanceInfoChanged(const ::aidl::android::hardware::wifi::hostapd::ApInfo& in_apInfo) override;
  ::ndk::ScopedAStatus onConnectedClientsChanged(const ::aidl::android::hardware::wifi::hostapd::ClientInfo& in_clientInfo) override;
  ::ndk::ScopedAStatus onFailure(const std::string& in_ifaceName, const std::string& in_instanceName) override;
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
