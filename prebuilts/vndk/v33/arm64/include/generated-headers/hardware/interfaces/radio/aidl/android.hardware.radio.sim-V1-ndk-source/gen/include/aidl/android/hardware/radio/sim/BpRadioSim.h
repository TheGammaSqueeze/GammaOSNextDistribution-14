#pragma once

#include "aidl/android/hardware/radio/sim/IRadioSim.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class BpRadioSim : public ::ndk::BpCInterface<IRadioSim> {
public:
  explicit BpRadioSim(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioSim();

  ::ndk::ScopedAStatus areUiccApplicationsEnabled(int32_t in_serial) override;
  ::ndk::ScopedAStatus changeIccPin2ForApp(int32_t in_serial, const std::string& in_oldPin2, const std::string& in_newPin2, const std::string& in_aid) override;
  ::ndk::ScopedAStatus changeIccPinForApp(int32_t in_serial, const std::string& in_oldPin, const std::string& in_newPin, const std::string& in_aid) override;
  ::ndk::ScopedAStatus enableUiccApplications(int32_t in_serial, bool in_enable) override;
  ::ndk::ScopedAStatus getAllowedCarriers(int32_t in_serial) override;
  ::ndk::ScopedAStatus getCdmaSubscription(int32_t in_serial) override;
  ::ndk::ScopedAStatus getCdmaSubscriptionSource(int32_t in_serial) override;
  ::ndk::ScopedAStatus getFacilityLockForApp(int32_t in_serial, const std::string& in_facility, const std::string& in_password, int32_t in_serviceClass, const std::string& in_appId) override;
  ::ndk::ScopedAStatus getIccCardStatus(int32_t in_serial) override;
  ::ndk::ScopedAStatus getImsiForApp(int32_t in_serial, const std::string& in_aid) override;
  ::ndk::ScopedAStatus getSimPhonebookCapacity(int32_t in_serial) override;
  ::ndk::ScopedAStatus getSimPhonebookRecords(int32_t in_serial) override;
  ::ndk::ScopedAStatus iccCloseLogicalChannel(int32_t in_serial, int32_t in_channelId) override;
  ::ndk::ScopedAStatus iccIoForApp(int32_t in_serial, const ::aidl::android::hardware::radio::sim::IccIo& in_iccIo) override;
  ::ndk::ScopedAStatus iccOpenLogicalChannel(int32_t in_serial, const std::string& in_aid, int32_t in_p2) override;
  ::ndk::ScopedAStatus iccTransmitApduBasicChannel(int32_t in_serial, const ::aidl::android::hardware::radio::sim::SimApdu& in_message) override;
  ::ndk::ScopedAStatus iccTransmitApduLogicalChannel(int32_t in_serial, const ::aidl::android::hardware::radio::sim::SimApdu& in_message) override;
  ::ndk::ScopedAStatus reportStkServiceIsRunning(int32_t in_serial) override;
  ::ndk::ScopedAStatus requestIccSimAuthentication(int32_t in_serial, int32_t in_authContext, const std::string& in_authData, const std::string& in_aid) override;
  ::ndk::ScopedAStatus responseAcknowledgement() override;
  ::ndk::ScopedAStatus sendEnvelope(int32_t in_serial, const std::string& in_contents) override;
  ::ndk::ScopedAStatus sendEnvelopeWithStatus(int32_t in_serial, const std::string& in_contents) override;
  ::ndk::ScopedAStatus sendTerminalResponseToSim(int32_t in_serial, const std::string& in_contents) override;
  ::ndk::ScopedAStatus setAllowedCarriers(int32_t in_serial, const ::aidl::android::hardware::radio::sim::CarrierRestrictions& in_carriers, ::aidl::android::hardware::radio::sim::SimLockMultiSimPolicy in_multiSimPolicy) override;
  ::ndk::ScopedAStatus setCarrierInfoForImsiEncryption(int32_t in_serial, const ::aidl::android::hardware::radio::sim::ImsiEncryptionInfo& in_imsiEncryptionInfo) override;
  ::ndk::ScopedAStatus setCdmaSubscriptionSource(int32_t in_serial, ::aidl::android::hardware::radio::sim::CdmaSubscriptionSource in_cdmaSub) override;
  ::ndk::ScopedAStatus setFacilityLockForApp(int32_t in_serial, const std::string& in_facility, bool in_lockState, const std::string& in_password, int32_t in_serviceClass, const std::string& in_appId) override;
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::sim::IRadioSimResponse>& in_radioSimResponse, const std::shared_ptr<::aidl::android::hardware::radio::sim::IRadioSimIndication>& in_radioSimIndication) override;
  ::ndk::ScopedAStatus setSimCardPower(int32_t in_serial, ::aidl::android::hardware::radio::sim::CardPowerState in_powerUp) override;
  ::ndk::ScopedAStatus setUiccSubscription(int32_t in_serial, const ::aidl::android::hardware::radio::sim::SelectUiccSub& in_uiccSub) override;
  ::ndk::ScopedAStatus supplyIccPin2ForApp(int32_t in_serial, const std::string& in_pin2, const std::string& in_aid) override;
  ::ndk::ScopedAStatus supplyIccPinForApp(int32_t in_serial, const std::string& in_pin, const std::string& in_aid) override;
  ::ndk::ScopedAStatus supplyIccPuk2ForApp(int32_t in_serial, const std::string& in_puk2, const std::string& in_pin2, const std::string& in_aid) override;
  ::ndk::ScopedAStatus supplyIccPukForApp(int32_t in_serial, const std::string& in_puk, const std::string& in_pin, const std::string& in_aid) override;
  ::ndk::ScopedAStatus supplySimDepersonalization(int32_t in_serial, ::aidl::android::hardware::radio::sim::PersoSubstate in_persoType, const std::string& in_controlKey) override;
  ::ndk::ScopedAStatus updateSimPhonebookRecords(int32_t in_serial, const ::aidl::android::hardware::radio::sim::PhonebookRecordInfo& in_recordInfo) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
