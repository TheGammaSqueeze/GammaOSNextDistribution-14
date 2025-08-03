#pragma once

#include "aidl/android/hardware/radio/modem/IRadioModemIndication.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class BpRadioModemIndication : public ::ndk::BpCInterface<IRadioModemIndication> {
public:
  explicit BpRadioModemIndication(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioModemIndication();

  ::ndk::ScopedAStatus hardwareConfigChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::modem::HardwareConfig>& in_configs) override;
  ::ndk::ScopedAStatus modemReset(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_reason) override;
  ::ndk::ScopedAStatus radioCapabilityIndication(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) override;
  ::ndk::ScopedAStatus radioStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::modem::RadioState in_radioState) override;
  ::ndk::ScopedAStatus rilConnected(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
