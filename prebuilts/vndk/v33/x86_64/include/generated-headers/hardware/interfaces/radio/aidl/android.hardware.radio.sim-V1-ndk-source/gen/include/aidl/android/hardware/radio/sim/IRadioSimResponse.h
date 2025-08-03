#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioResponseInfo.h>
#include <aidl/android/hardware/radio/sim/CardStatus.h>
#include <aidl/android/hardware/radio/sim/CarrierRestrictions.h>
#include <aidl/android/hardware/radio/sim/CdmaSubscriptionSource.h>
#include <aidl/android/hardware/radio/sim/IccIoResult.h>
#include <aidl/android/hardware/radio/sim/PersoSubstate.h>
#include <aidl/android/hardware/radio/sim/PhonebookCapacity.h>
#include <aidl/android/hardware/radio/sim/SimLockMultiSimPolicy.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class IRadioSimResponse : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioSimResponse();
  virtual ~IRadioSimResponse();

  static const int32_t version = 1;
  static inline const std::string hash = "01cea196fdf8f5e41fda8dc41125f1cc2b96f757";
  static constexpr uint32_t TRANSACTION_acknowledgeRequest = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_areUiccApplicationsEnabledResponse = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_changeIccPin2ForAppResponse = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_changeIccPinForAppResponse = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_enableUiccApplicationsResponse = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getAllowedCarriersResponse = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getCdmaSubscriptionResponse = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getCdmaSubscriptionSourceResponse = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getFacilityLockForAppResponse = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getIccCardStatusResponse = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getImsiForAppResponse = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getSimPhonebookCapacityResponse = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getSimPhonebookRecordsResponse = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_iccCloseLogicalChannelResponse = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_iccIoForAppResponse = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_iccOpenLogicalChannelResponse = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_iccTransmitApduBasicChannelResponse = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_iccTransmitApduLogicalChannelResponse = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_reportStkServiceIsRunningResponse = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_requestIccSimAuthenticationResponse = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_sendEnvelopeResponse = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_sendEnvelopeWithStatusResponse = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_sendTerminalResponseToSimResponse = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_setAllowedCarriersResponse = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_setCarrierInfoForImsiEncryptionResponse = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_setCdmaSubscriptionSourceResponse = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_setFacilityLockForAppResponse = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_setSimCardPowerResponse = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_setUiccSubscriptionResponse = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_supplyIccPin2ForAppResponse = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_supplyIccPinForAppResponse = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_supplyIccPuk2ForAppResponse = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_supplyIccPukForAppResponse = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_supplySimDepersonalizationResponse = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_updateSimPhonebookRecordsResponse = FIRST_CALL_TRANSACTION + 34;

  static std::shared_ptr<IRadioSimResponse> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioSimResponse>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioSimResponse>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioSimResponse>& impl);
  static const std::shared_ptr<IRadioSimResponse>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus areUiccApplicationsEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enabled) = 0;
  virtual ::ndk::ScopedAStatus changeIccPin2ForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) = 0;
  virtual ::ndk::ScopedAStatus changeIccPinForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) = 0;
  virtual ::ndk::ScopedAStatus enableUiccApplicationsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getAllowedCarriersResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::CarrierRestrictions& in_carriers, ::aidl::android::hardware::radio::sim::SimLockMultiSimPolicy in_multiSimPolicy) = 0;
  virtual ::ndk::ScopedAStatus getCdmaSubscriptionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_mdn, const std::string& in_hSid, const std::string& in_hNid, const std::string& in_min, const std::string& in_prl) = 0;
  virtual ::ndk::ScopedAStatus getCdmaSubscriptionSourceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::sim::CdmaSubscriptionSource in_source) = 0;
  virtual ::ndk::ScopedAStatus getFacilityLockForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_response) = 0;
  virtual ::ndk::ScopedAStatus getIccCardStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::CardStatus& in_cardStatus) = 0;
  virtual ::ndk::ScopedAStatus getImsiForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_imsi) = 0;
  virtual ::ndk::ScopedAStatus getSimPhonebookCapacityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::PhonebookCapacity& in_capacity) = 0;
  virtual ::ndk::ScopedAStatus getSimPhonebookRecordsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus iccCloseLogicalChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus iccIoForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_iccIo) = 0;
  virtual ::ndk::ScopedAStatus iccOpenLogicalChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_channelId, const std::vector<uint8_t>& in_selectResponse) = 0;
  virtual ::ndk::ScopedAStatus iccTransmitApduBasicChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_result) = 0;
  virtual ::ndk::ScopedAStatus iccTransmitApduLogicalChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_result) = 0;
  virtual ::ndk::ScopedAStatus reportStkServiceIsRunningResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus requestIccSimAuthenticationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_result) = 0;
  virtual ::ndk::ScopedAStatus sendEnvelopeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_commandResponse) = 0;
  virtual ::ndk::ScopedAStatus sendEnvelopeWithStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_iccIo) = 0;
  virtual ::ndk::ScopedAStatus sendTerminalResponseToSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setAllowedCarriersResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setCarrierInfoForImsiEncryptionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setCdmaSubscriptionSourceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setFacilityLockForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_retry) = 0;
  virtual ::ndk::ScopedAStatus setSimCardPowerResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setUiccSubscriptionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus supplyIccPin2ForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) = 0;
  virtual ::ndk::ScopedAStatus supplyIccPinForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) = 0;
  virtual ::ndk::ScopedAStatus supplyIccPuk2ForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) = 0;
  virtual ::ndk::ScopedAStatus supplyIccPukForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) = 0;
  virtual ::ndk::ScopedAStatus supplySimDepersonalizationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::sim::PersoSubstate in_persoType, int32_t in_remainingRetries) = 0;
  virtual ::ndk::ScopedAStatus updateSimPhonebookRecordsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_updatedRecordIndex) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioSimResponse> default_impl;
};
class IRadioSimResponseDefault : public IRadioSimResponse {
public:
  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override;
  ::ndk::ScopedAStatus areUiccApplicationsEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enabled) override;
  ::ndk::ScopedAStatus changeIccPin2ForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override;
  ::ndk::ScopedAStatus changeIccPinForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override;
  ::ndk::ScopedAStatus enableUiccApplicationsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus getAllowedCarriersResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::CarrierRestrictions& in_carriers, ::aidl::android::hardware::radio::sim::SimLockMultiSimPolicy in_multiSimPolicy) override;
  ::ndk::ScopedAStatus getCdmaSubscriptionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_mdn, const std::string& in_hSid, const std::string& in_hNid, const std::string& in_min, const std::string& in_prl) override;
  ::ndk::ScopedAStatus getCdmaSubscriptionSourceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::sim::CdmaSubscriptionSource in_source) override;
  ::ndk::ScopedAStatus getFacilityLockForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_response) override;
  ::ndk::ScopedAStatus getIccCardStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::CardStatus& in_cardStatus) override;
  ::ndk::ScopedAStatus getImsiForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_imsi) override;
  ::ndk::ScopedAStatus getSimPhonebookCapacityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::PhonebookCapacity& in_capacity) override;
  ::ndk::ScopedAStatus getSimPhonebookRecordsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus iccCloseLogicalChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus iccIoForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_iccIo) override;
  ::ndk::ScopedAStatus iccOpenLogicalChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_channelId, const std::vector<uint8_t>& in_selectResponse) override;
  ::ndk::ScopedAStatus iccTransmitApduBasicChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_result) override;
  ::ndk::ScopedAStatus iccTransmitApduLogicalChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_result) override;
  ::ndk::ScopedAStatus reportStkServiceIsRunningResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus requestIccSimAuthenticationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_result) override;
  ::ndk::ScopedAStatus sendEnvelopeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_commandResponse) override;
  ::ndk::ScopedAStatus sendEnvelopeWithStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_iccIo) override;
  ::ndk::ScopedAStatus sendTerminalResponseToSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setAllowedCarriersResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setCarrierInfoForImsiEncryptionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setCdmaSubscriptionSourceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setFacilityLockForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_retry) override;
  ::ndk::ScopedAStatus setSimCardPowerResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setUiccSubscriptionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus supplyIccPin2ForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override;
  ::ndk::ScopedAStatus supplyIccPinForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override;
  ::ndk::ScopedAStatus supplyIccPuk2ForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override;
  ::ndk::ScopedAStatus supplyIccPukForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override;
  ::ndk::ScopedAStatus supplySimDepersonalizationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::sim::PersoSubstate in_persoType, int32_t in_remainingRetries) override;
  ::ndk::ScopedAStatus updateSimPhonebookRecordsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_updatedRecordIndex) override;
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
