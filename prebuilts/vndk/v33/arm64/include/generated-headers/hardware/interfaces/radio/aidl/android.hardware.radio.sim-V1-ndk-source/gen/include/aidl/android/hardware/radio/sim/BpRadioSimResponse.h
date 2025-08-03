#pragma once

#include "aidl/android/hardware/radio/sim/IRadioSimResponse.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class BpRadioSimResponse : public ::ndk::BpCInterface<IRadioSimResponse> {
public:
  explicit BpRadioSimResponse(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioSimResponse();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
