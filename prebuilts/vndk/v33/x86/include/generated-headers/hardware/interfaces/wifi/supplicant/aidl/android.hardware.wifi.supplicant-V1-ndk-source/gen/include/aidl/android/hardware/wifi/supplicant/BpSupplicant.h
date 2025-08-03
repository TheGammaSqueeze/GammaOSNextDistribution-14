#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicant.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BpSupplicant : public ::ndk::BpCInterface<ISupplicant> {
public:
  explicit BpSupplicant(const ::ndk::SpAIBinder& binder);
  virtual ~BpSupplicant();

  ::ndk::ScopedAStatus addP2pInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIface>* _aidl_return) override;
  ::ndk::ScopedAStatus addStaInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIface>* _aidl_return) override;
  ::ndk::ScopedAStatus getDebugLevel(::aidl::android::hardware::wifi::supplicant::DebugLevel* _aidl_return) override;
  ::ndk::ScopedAStatus getP2pInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIface>* _aidl_return) override;
  ::ndk::ScopedAStatus getStaInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIface>* _aidl_return) override;
  ::ndk::ScopedAStatus isDebugShowKeysEnabled(bool* _aidl_return) override;
  ::ndk::ScopedAStatus isDebugShowTimestampEnabled(bool* _aidl_return) override;
  ::ndk::ScopedAStatus listInterfaces(std::vector<::aidl::android::hardware::wifi::supplicant::IfaceInfo>* _aidl_return) override;
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantCallback>& in_callback) override;
  ::ndk::ScopedAStatus removeInterface(const ::aidl::android::hardware::wifi::supplicant::IfaceInfo& in_ifaceInfo) override;
  ::ndk::ScopedAStatus setConcurrencyPriority(::aidl::android::hardware::wifi::supplicant::IfaceType in_type) override;
  ::ndk::ScopedAStatus setDebugParams(::aidl::android::hardware::wifi::supplicant::DebugLevel in_level, bool in_showTimestamp, bool in_showKeys) override;
  ::ndk::ScopedAStatus terminate() override;
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
