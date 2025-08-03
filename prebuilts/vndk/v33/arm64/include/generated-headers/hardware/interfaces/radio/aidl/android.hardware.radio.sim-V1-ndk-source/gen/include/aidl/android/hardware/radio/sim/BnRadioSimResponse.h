#pragma once

#include "aidl/android/hardware/radio/sim/IRadioSimResponse.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class BnRadioSimResponse : public ::ndk::BnCInterface<IRadioSimResponse> {
public:
  BnRadioSimResponse();
  virtual ~BnRadioSimResponse();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioSimResponseDelegator : public BnRadioSimResponse {
public:
  explicit IRadioSimResponseDelegator(const std::shared_ptr<IRadioSimResponse> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioSimResponse::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override {
    return _impl->acknowledgeRequest(in_serial);
  }
  ::ndk::ScopedAStatus areUiccApplicationsEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enabled) override {
    return _impl->areUiccApplicationsEnabledResponse(in_info, in_enabled);
  }
  ::ndk::ScopedAStatus changeIccPin2ForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override {
    return _impl->changeIccPin2ForAppResponse(in_info, in_remainingRetries);
  }
  ::ndk::ScopedAStatus changeIccPinForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override {
    return _impl->changeIccPinForAppResponse(in_info, in_remainingRetries);
  }
  ::ndk::ScopedAStatus enableUiccApplicationsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->enableUiccApplicationsResponse(in_info);
  }
  ::ndk::ScopedAStatus getAllowedCarriersResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::CarrierRestrictions& in_carriers, ::aidl::android::hardware::radio::sim::SimLockMultiSimPolicy in_multiSimPolicy) override {
    return _impl->getAllowedCarriersResponse(in_info, in_carriers, in_multiSimPolicy);
  }
  ::ndk::ScopedAStatus getCdmaSubscriptionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_mdn, const std::string& in_hSid, const std::string& in_hNid, const std::string& in_min, const std::string& in_prl) override {
    return _impl->getCdmaSubscriptionResponse(in_info, in_mdn, in_hSid, in_hNid, in_min, in_prl);
  }
  ::ndk::ScopedAStatus getCdmaSubscriptionSourceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::sim::CdmaSubscriptionSource in_source) override {
    return _impl->getCdmaSubscriptionSourceResponse(in_info, in_source);
  }
  ::ndk::ScopedAStatus getFacilityLockForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_response) override {
    return _impl->getFacilityLockForAppResponse(in_info, in_response);
  }
  ::ndk::ScopedAStatus getIccCardStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::CardStatus& in_cardStatus) override {
    return _impl->getIccCardStatusResponse(in_info, in_cardStatus);
  }
  ::ndk::ScopedAStatus getImsiForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_imsi) override {
    return _impl->getImsiForAppResponse(in_info, in_imsi);
  }
  ::ndk::ScopedAStatus getSimPhonebookCapacityResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::PhonebookCapacity& in_capacity) override {
    return _impl->getSimPhonebookCapacityResponse(in_info, in_capacity);
  }
  ::ndk::ScopedAStatus getSimPhonebookRecordsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->getSimPhonebookRecordsResponse(in_info);
  }
  ::ndk::ScopedAStatus iccCloseLogicalChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->iccCloseLogicalChannelResponse(in_info);
  }
  ::ndk::ScopedAStatus iccIoForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_iccIo) override {
    return _impl->iccIoForAppResponse(in_info, in_iccIo);
  }
  ::ndk::ScopedAStatus iccOpenLogicalChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_channelId, const std::vector<uint8_t>& in_selectResponse) override {
    return _impl->iccOpenLogicalChannelResponse(in_info, in_channelId, in_selectResponse);
  }
  ::ndk::ScopedAStatus iccTransmitApduBasicChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_result) override {
    return _impl->iccTransmitApduBasicChannelResponse(in_info, in_result);
  }
  ::ndk::ScopedAStatus iccTransmitApduLogicalChannelResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_result) override {
    return _impl->iccTransmitApduLogicalChannelResponse(in_info, in_result);
  }
  ::ndk::ScopedAStatus reportStkServiceIsRunningResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->reportStkServiceIsRunningResponse(in_info);
  }
  ::ndk::ScopedAStatus requestIccSimAuthenticationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_result) override {
    return _impl->requestIccSimAuthenticationResponse(in_info, in_result);
  }
  ::ndk::ScopedAStatus sendEnvelopeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_commandResponse) override {
    return _impl->sendEnvelopeResponse(in_info, in_commandResponse);
  }
  ::ndk::ScopedAStatus sendEnvelopeWithStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::sim::IccIoResult& in_iccIo) override {
    return _impl->sendEnvelopeWithStatusResponse(in_info, in_iccIo);
  }
  ::ndk::ScopedAStatus sendTerminalResponseToSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->sendTerminalResponseToSimResponse(in_info);
  }
  ::ndk::ScopedAStatus setAllowedCarriersResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setAllowedCarriersResponse(in_info);
  }
  ::ndk::ScopedAStatus setCarrierInfoForImsiEncryptionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setCarrierInfoForImsiEncryptionResponse(in_info);
  }
  ::ndk::ScopedAStatus setCdmaSubscriptionSourceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setCdmaSubscriptionSourceResponse(in_info);
  }
  ::ndk::ScopedAStatus setFacilityLockForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_retry) override {
    return _impl->setFacilityLockForAppResponse(in_info, in_retry);
  }
  ::ndk::ScopedAStatus setSimCardPowerResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setSimCardPowerResponse(in_info);
  }
  ::ndk::ScopedAStatus setUiccSubscriptionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setUiccSubscriptionResponse(in_info);
  }
  ::ndk::ScopedAStatus supplyIccPin2ForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override {
    return _impl->supplyIccPin2ForAppResponse(in_info, in_remainingRetries);
  }
  ::ndk::ScopedAStatus supplyIccPinForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override {
    return _impl->supplyIccPinForAppResponse(in_info, in_remainingRetries);
  }
  ::ndk::ScopedAStatus supplyIccPuk2ForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override {
    return _impl->supplyIccPuk2ForAppResponse(in_info, in_remainingRetries);
  }
  ::ndk::ScopedAStatus supplyIccPukForAppResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override {
    return _impl->supplyIccPukForAppResponse(in_info, in_remainingRetries);
  }
  ::ndk::ScopedAStatus supplySimDepersonalizationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::sim::PersoSubstate in_persoType, int32_t in_remainingRetries) override {
    return _impl->supplySimDepersonalizationResponse(in_info, in_persoType, in_remainingRetries);
  }
  ::ndk::ScopedAStatus updateSimPhonebookRecordsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_updatedRecordIndex) override {
    return _impl->updateSimPhonebookRecordsResponse(in_info, in_updatedRecordIndex);
  }
protected:
private:
  std::shared_ptr<IRadioSimResponse> _impl;
};

}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
