#pragma once

#include "aidl/android/hardware/radio/network/IRadioNetworkResponse.h"

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
namespace network {
class BnRadioNetworkResponse : public ::ndk::BnCInterface<IRadioNetworkResponse> {
public:
  BnRadioNetworkResponse();
  virtual ~BnRadioNetworkResponse();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioNetworkResponseDelegator : public BnRadioNetworkResponse {
public:
  explicit IRadioNetworkResponseDelegator(const std::shared_ptr<IRadioNetworkResponse> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioNetworkResponse::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override {
    return _impl->acknowledgeRequest(in_serial);
  }
  ::ndk::ScopedAStatus getAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_networkTypeBitmap) override {
    return _impl->getAllowedNetworkTypesBitmapResponse(in_info, in_networkTypeBitmap);
  }
  ::ndk::ScopedAStatus getAvailableBandModesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::RadioBandMode>& in_bandModes) override {
    return _impl->getAvailableBandModesResponse(in_info, in_bandModes);
  }
  ::ndk::ScopedAStatus getAvailableNetworksResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::OperatorInfo>& in_networkInfos) override {
    return _impl->getAvailableNetworksResponse(in_info, in_networkInfos);
  }
  ::ndk::ScopedAStatus getBarringInfoResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::vector<::aidl::android::hardware::radio::network::BarringInfo>& in_barringInfos) override {
    return _impl->getBarringInfoResponse(in_info, in_cellIdentity, in_barringInfos);
  }
  ::ndk::ScopedAStatus getCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::network::CdmaRoamingType in_type) override {
    return _impl->getCdmaRoamingPreferenceResponse(in_info, in_type);
  }
  ::ndk::ScopedAStatus getCellInfoListResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::CellInfo>& in_cellInfo) override {
    return _impl->getCellInfoListResponse(in_info, in_cellInfo);
  }
  ::ndk::ScopedAStatus getDataRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::RegStateResult& in_dataRegResponse) override {
    return _impl->getDataRegistrationStateResponse(in_info, in_dataRegResponse);
  }
  ::ndk::ScopedAStatus getImsRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isRegistered, ::aidl::android::hardware::radio::RadioTechnologyFamily in_ratFamily) override {
    return _impl->getImsRegistrationStateResponse(in_info, in_isRegistered, in_ratFamily);
  }
  ::ndk::ScopedAStatus getNetworkSelectionModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_manual) override {
    return _impl->getNetworkSelectionModeResponse(in_info, in_manual);
  }
  ::ndk::ScopedAStatus getOperatorResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_longName, const std::string& in_shortName, const std::string& in_numeric) override {
    return _impl->getOperatorResponse(in_info, in_longName, in_shortName, in_numeric);
  }
  ::ndk::ScopedAStatus getSignalStrengthResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::SignalStrength& in_signalStrength) override {
    return _impl->getSignalStrengthResponse(in_info, in_signalStrength);
  }
  ::ndk::ScopedAStatus getSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier>& in_specifiers) override {
    return _impl->getSystemSelectionChannelsResponse(in_info, in_specifiers);
  }
  ::ndk::ScopedAStatus getVoiceRadioTechnologyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::RadioTechnology in_rat) override {
    return _impl->getVoiceRadioTechnologyResponse(in_info, in_rat);
  }
  ::ndk::ScopedAStatus getVoiceRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::RegStateResult& in_voiceRegResponse) override {
    return _impl->getVoiceRegistrationStateResponse(in_info, in_voiceRegResponse);
  }
  ::ndk::ScopedAStatus isNrDualConnectivityEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isEnabled) override {
    return _impl->isNrDualConnectivityEnabledResponse(in_info, in_isEnabled);
  }
  ::ndk::ScopedAStatus setAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setAllowedNetworkTypesBitmapResponse(in_info);
  }
  ::ndk::ScopedAStatus setBandModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setBandModeResponse(in_info);
  }
  ::ndk::ScopedAStatus setBarringPasswordResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setBarringPasswordResponse(in_info);
  }
  ::ndk::ScopedAStatus setCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setCdmaRoamingPreferenceResponse(in_info);
  }
  ::ndk::ScopedAStatus setCellInfoListRateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setCellInfoListRateResponse(in_info);
  }
  ::ndk::ScopedAStatus setIndicationFilterResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setIndicationFilterResponse(in_info);
  }
  ::ndk::ScopedAStatus setLinkCapacityReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setLinkCapacityReportingCriteriaResponse(in_info);
  }
  ::ndk::ScopedAStatus setLocationUpdatesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setLocationUpdatesResponse(in_info);
  }
  ::ndk::ScopedAStatus setNetworkSelectionModeAutomaticResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setNetworkSelectionModeAutomaticResponse(in_info);
  }
  ::ndk::ScopedAStatus setNetworkSelectionModeManualResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setNetworkSelectionModeManualResponse(in_info);
  }
  ::ndk::ScopedAStatus setNrDualConnectivityStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setNrDualConnectivityStateResponse(in_info);
  }
  ::ndk::ScopedAStatus setSignalStrengthReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setSignalStrengthReportingCriteriaResponse(in_info);
  }
  ::ndk::ScopedAStatus setSuppServiceNotificationsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setSuppServiceNotificationsResponse(in_info);
  }
  ::ndk::ScopedAStatus setSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setSystemSelectionChannelsResponse(in_info);
  }
  ::ndk::ScopedAStatus startNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->startNetworkScanResponse(in_info);
  }
  ::ndk::ScopedAStatus stopNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->stopNetworkScanResponse(in_info);
  }
  ::ndk::ScopedAStatus supplyNetworkDepersonalizationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override {
    return _impl->supplyNetworkDepersonalizationResponse(in_info, in_remainingRetries);
  }
  ::ndk::ScopedAStatus setUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setUsageSettingResponse(in_info);
  }
  ::ndk::ScopedAStatus getUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::network::UsageSetting in_usageSetting) override {
    return _impl->getUsageSettingResponse(in_info, in_usageSetting);
  }
protected:
private:
  std::shared_ptr<IRadioNetworkResponse> _impl;
};

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
