#pragma once

#include "aidl/android/hardware/radio/modem/IRadioModem.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class BpRadioModem : public ::ndk::BpCInterface<IRadioModem> {
public:
  explicit BpRadioModem(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioModem();

  ::ndk::ScopedAStatus enableModem(int32_t in_serial, bool in_on) override;
  ::ndk::ScopedAStatus getBasebandVersion(int32_t in_serial) override;
  ::ndk::ScopedAStatus getDeviceIdentity(int32_t in_serial) override;
  ::ndk::ScopedAStatus getHardwareConfig(int32_t in_serial) override;
  ::ndk::ScopedAStatus getModemActivityInfo(int32_t in_serial) override;
  ::ndk::ScopedAStatus getModemStackStatus(int32_t in_serial) override;
  ::ndk::ScopedAStatus getRadioCapability(int32_t in_serial) override;
  ::ndk::ScopedAStatus nvReadItem(int32_t in_serial, ::aidl::android::hardware::radio::modem::NvItem in_itemId) override;
  ::ndk::ScopedAStatus nvResetConfig(int32_t in_serial, ::aidl::android::hardware::radio::modem::ResetNvType in_resetType) override;
  ::ndk::ScopedAStatus nvWriteCdmaPrl(int32_t in_serial, const std::vector<uint8_t>& in_prl) override;
  ::ndk::ScopedAStatus nvWriteItem(int32_t in_serial, const ::aidl::android::hardware::radio::modem::NvWriteItem& in_item) override;
  ::ndk::ScopedAStatus requestShutdown(int32_t in_serial) override;
  ::ndk::ScopedAStatus responseAcknowledgement() override;
  ::ndk::ScopedAStatus sendDeviceState(int32_t in_serial, ::aidl::android::hardware::radio::modem::DeviceStateType in_deviceStateType, bool in_state) override;
  ::ndk::ScopedAStatus setRadioCapability(int32_t in_serial, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) override;
  ::ndk::ScopedAStatus setRadioPower(int32_t in_serial, bool in_powerOn, bool in_forEmergencyCall, bool in_preferredForEmergencyCall) override;
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::modem::IRadioModemResponse>& in_radioModemResponse, const std::shared_ptr<::aidl::android::hardware::radio::modem::IRadioModemIndication>& in_radioModemIndication) override;
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
