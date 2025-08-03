#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/sim/CardPowerState.h>
#include <aidl/android/hardware/radio/sim/CarrierRestrictions.h>
#include <aidl/android/hardware/radio/sim/CdmaSubscriptionSource.h>
#include <aidl/android/hardware/radio/sim/IRadioSimIndication.h>
#include <aidl/android/hardware/radio/sim/IRadioSimResponse.h>
#include <aidl/android/hardware/radio/sim/IccIo.h>
#include <aidl/android/hardware/radio/sim/ImsiEncryptionInfo.h>
#include <aidl/android/hardware/radio/sim/PersoSubstate.h>
#include <aidl/android/hardware/radio/sim/PhonebookRecordInfo.h>
#include <aidl/android/hardware/radio/sim/SelectUiccSub.h>
#include <aidl/android/hardware/radio/sim/SimApdu.h>
#include <aidl/android/hardware/radio/sim/SimLockMultiSimPolicy.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class IRadioSim : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioSim();
  virtual ~IRadioSim();

  static const int32_t version = 1;
  static inline const std::string hash = "01cea196fdf8f5e41fda8dc41125f1cc2b96f757";
  static constexpr uint32_t TRANSACTION_areUiccApplicationsEnabled = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_changeIccPin2ForApp = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_changeIccPinForApp = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_enableUiccApplications = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getAllowedCarriers = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getCdmaSubscription = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getCdmaSubscriptionSource = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getFacilityLockForApp = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getIccCardStatus = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getImsiForApp = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getSimPhonebookCapacity = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getSimPhonebookRecords = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_iccCloseLogicalChannel = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_iccIoForApp = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_iccOpenLogicalChannel = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_iccTransmitApduBasicChannel = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_iccTransmitApduLogicalChannel = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_reportStkServiceIsRunning = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_requestIccSimAuthentication = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_responseAcknowledgement = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_sendEnvelope = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_sendEnvelopeWithStatus = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_sendTerminalResponseToSim = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_setAllowedCarriers = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_setCarrierInfoForImsiEncryption = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_setCdmaSubscriptionSource = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_setFacilityLockForApp = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_setResponseFunctions = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_setSimCardPower = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_setUiccSubscription = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_supplyIccPin2ForApp = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_supplyIccPinForApp = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_supplyIccPuk2ForApp = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_supplyIccPukForApp = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_supplySimDepersonalization = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_updateSimPhonebookRecords = FIRST_CALL_TRANSACTION + 35;

  static std::shared_ptr<IRadioSim> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioSim>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioSim>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioSim>& impl);
  static const std::shared_ptr<IRadioSim>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus areUiccApplicationsEnabled(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus changeIccPin2ForApp(int32_t in_serial, const std::string& in_oldPin2, const std::string& in_newPin2, const std::string& in_aid) = 0;
  virtual ::ndk::ScopedAStatus changeIccPinForApp(int32_t in_serial, const std::string& in_oldPin, const std::string& in_newPin, const std::string& in_aid) = 0;
  virtual ::ndk::ScopedAStatus enableUiccApplications(int32_t in_serial, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus getAllowedCarriers(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getCdmaSubscription(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getCdmaSubscriptionSource(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getFacilityLockForApp(int32_t in_serial, const std::string& in_facility, const std::string& in_password, int32_t in_serviceClass, const std::string& in_appId) = 0;
  virtual ::ndk::ScopedAStatus getIccCardStatus(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getImsiForApp(int32_t in_serial, const std::string& in_aid) = 0;
  virtual ::ndk::ScopedAStatus getSimPhonebookCapacity(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getSimPhonebookRecords(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus iccCloseLogicalChannel(int32_t in_serial, int32_t in_channelId) = 0;
  virtual ::ndk::ScopedAStatus iccIoForApp(int32_t in_serial, const ::aidl::android::hardware::radio::sim::IccIo& in_iccIo) = 0;
  virtual ::ndk::ScopedAStatus iccOpenLogicalChannel(int32_t in_serial, const std::string& in_aid, int32_t in_p2) = 0;
  virtual ::ndk::ScopedAStatus iccTransmitApduBasicChannel(int32_t in_serial, const ::aidl::android::hardware::radio::sim::SimApdu& in_message) = 0;
  virtual ::ndk::ScopedAStatus iccTransmitApduLogicalChannel(int32_t in_serial, const ::aidl::android::hardware::radio::sim::SimApdu& in_message) = 0;
  virtual ::ndk::ScopedAStatus reportStkServiceIsRunning(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus requestIccSimAuthentication(int32_t in_serial, int32_t in_authContext, const std::string& in_authData, const std::string& in_aid) = 0;
  virtual ::ndk::ScopedAStatus responseAcknowledgement() = 0;
  virtual ::ndk::ScopedAStatus sendEnvelope(int32_t in_serial, const std::string& in_contents) = 0;
  virtual ::ndk::ScopedAStatus sendEnvelopeWithStatus(int32_t in_serial, const std::string& in_contents) = 0;
  virtual ::ndk::ScopedAStatus sendTerminalResponseToSim(int32_t in_serial, const std::string& in_contents) = 0;
  virtual ::ndk::ScopedAStatus setAllowedCarriers(int32_t in_serial, const ::aidl::android::hardware::radio::sim::CarrierRestrictions& in_carriers, ::aidl::android::hardware::radio::sim::SimLockMultiSimPolicy in_multiSimPolicy) = 0;
  virtual ::ndk::ScopedAStatus setCarrierInfoForImsiEncryption(int32_t in_serial, const ::aidl::android::hardware::radio::sim::ImsiEncryptionInfo& in_imsiEncryptionInfo) = 0;
  virtual ::ndk::ScopedAStatus setCdmaSubscriptionSource(int32_t in_serial, ::aidl::android::hardware::radio::sim::CdmaSubscriptionSource in_cdmaSub) = 0;
  virtual ::ndk::ScopedAStatus setFacilityLockForApp(int32_t in_serial, const std::string& in_facility, bool in_lockState, const std::string& in_password, int32_t in_serviceClass, const std::string& in_appId) = 0;
  virtual ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::sim::IRadioSimResponse>& in_radioSimResponse, const std::shared_ptr<::aidl::android::hardware::radio::sim::IRadioSimIndication>& in_radioSimIndication) = 0;
  virtual ::ndk::ScopedAStatus setSimCardPower(int32_t in_serial, ::aidl::android::hardware::radio::sim::CardPowerState in_powerUp) = 0;
  virtual ::ndk::ScopedAStatus setUiccSubscription(int32_t in_serial, const ::aidl::android::hardware::radio::sim::SelectUiccSub& in_uiccSub) = 0;
  virtual ::ndk::ScopedAStatus supplyIccPin2ForApp(int32_t in_serial, const std::string& in_pin2, const std::string& in_aid) = 0;
  virtual ::ndk::ScopedAStatus supplyIccPinForApp(int32_t in_serial, const std::string& in_pin, const std::string& in_aid) = 0;
  virtual ::ndk::ScopedAStatus supplyIccPuk2ForApp(int32_t in_serial, const std::string& in_puk2, const std::string& in_pin2, const std::string& in_aid) = 0;
  virtual ::ndk::ScopedAStatus supplyIccPukForApp(int32_t in_serial, const std::string& in_puk, const std::string& in_pin, const std::string& in_aid) = 0;
  virtual ::ndk::ScopedAStatus supplySimDepersonalization(int32_t in_serial, ::aidl::android::hardware::radio::sim::PersoSubstate in_persoType, const std::string& in_controlKey) = 0;
  virtual ::ndk::ScopedAStatus updateSimPhonebookRecords(int32_t in_serial, const ::aidl::android::hardware::radio::sim::PhonebookRecordInfo& in_recordInfo) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioSim> default_impl;
};
class IRadioSimDefault : public IRadioSim {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
