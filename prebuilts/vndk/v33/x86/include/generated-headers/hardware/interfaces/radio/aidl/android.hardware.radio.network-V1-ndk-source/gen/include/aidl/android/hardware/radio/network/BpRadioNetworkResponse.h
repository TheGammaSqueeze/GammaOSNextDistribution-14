#pragma once

#include "aidl/android/hardware/radio/network/IRadioNetworkResponse.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class BpRadioNetworkResponse : public ::ndk::BpCInterface<IRadioNetworkResponse> {
public:
  explicit BpRadioNetworkResponse(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioNetworkResponse();

  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override;
  ::ndk::ScopedAStatus getAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_networkTypeBitmap) override;
  ::ndk::ScopedAStatus getAvailableBandModesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::RadioBandMode>& in_bandModes) override;
  ::ndk::ScopedAStatus getAvailableNetworksResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::OperatorInfo>& in_networkInfos) override;
  ::ndk::ScopedAStatus getBarringInfoResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::vector<::aidl::android::hardware::radio::network::BarringInfo>& in_barringInfos) override;
  ::ndk::ScopedAStatus getCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::network::CdmaRoamingType in_type) override;
  ::ndk::ScopedAStatus getCellInfoListResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::CellInfo>& in_cellInfo) override;
  ::ndk::ScopedAStatus getDataRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::RegStateResult& in_dataRegResponse) override;
  ::ndk::ScopedAStatus getImsRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isRegistered, ::aidl::android::hardware::radio::RadioTechnologyFamily in_ratFamily) override;
  ::ndk::ScopedAStatus getNetworkSelectionModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_manual) override;
  ::ndk::ScopedAStatus getOperatorResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_longName, const std::string& in_shortName, const std::string& in_numeric) override;
  ::ndk::ScopedAStatus getSignalStrengthResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::SignalStrength& in_signalStrength) override;
  ::ndk::ScopedAStatus getSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier>& in_specifiers) override;
  ::ndk::ScopedAStatus getVoiceRadioTechnologyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::RadioTechnology in_rat) override;
  ::ndk::ScopedAStatus getVoiceRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::RegStateResult& in_voiceRegResponse) override;
  ::ndk::ScopedAStatus isNrDualConnectivityEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isEnabled) override;
  ::ndk::ScopedAStatus setAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setBandModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setBarringPasswordResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setCellInfoListRateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setIndicationFilterResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setLinkCapacityReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setLocationUpdatesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setNetworkSelectionModeAutomaticResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setNetworkSelectionModeManualResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setNrDualConnectivityStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setSignalStrengthReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setSuppServiceNotificationsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus startNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus stopNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus supplyNetworkDepersonalizationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) override;
  ::ndk::ScopedAStatus setUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus getUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::network::UsageSetting in_usageSetting) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
