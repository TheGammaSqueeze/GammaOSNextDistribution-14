#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantP2pNetwork.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BpSupplicantP2pNetwork : public ::ndk::BpCInterface<ISupplicantP2pNetwork> {
public:
  explicit BpSupplicantP2pNetwork(const ::ndk::SpAIBinder& binder);
  virtual ~BpSupplicantP2pNetwork();

  ::ndk::ScopedAStatus getBssid(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getClientList(std::vector<::aidl::android::hardware::wifi::supplicant::MacAddress>* _aidl_return) override;
  ::ndk::ScopedAStatus getId(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceName(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getSsid(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) override;
  ::ndk::ScopedAStatus isCurrent(bool* _aidl_return) override;
  ::ndk::ScopedAStatus isGroupOwner(bool* _aidl_return) override;
  ::ndk::ScopedAStatus isPersistent(bool* _aidl_return) override;
  ::ndk::ScopedAStatus setClientList(const std::vector<::aidl::android::hardware::wifi::supplicant::MacAddress>& in_clients) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
