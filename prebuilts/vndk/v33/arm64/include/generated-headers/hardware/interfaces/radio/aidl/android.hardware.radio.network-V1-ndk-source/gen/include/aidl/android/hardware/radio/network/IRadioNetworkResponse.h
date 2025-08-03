#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioResponseInfo.h>
#include <aidl/android/hardware/radio/RadioTechnology.h>
#include <aidl/android/hardware/radio/RadioTechnologyFamily.h>
#include <aidl/android/hardware/radio/network/BarringInfo.h>
#include <aidl/android/hardware/radio/network/CdmaRoamingType.h>
#include <aidl/android/hardware/radio/network/CellIdentity.h>
#include <aidl/android/hardware/radio/network/CellInfo.h>
#include <aidl/android/hardware/radio/network/OperatorInfo.h>
#include <aidl/android/hardware/radio/network/RadioAccessSpecifier.h>
#include <aidl/android/hardware/radio/network/RadioBandMode.h>
#include <aidl/android/hardware/radio/network/RegStateResult.h>
#include <aidl/android/hardware/radio/network/SignalStrength.h>
#include <aidl/android/hardware/radio/network/UsageSetting.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class IRadioNetworkResponse : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioNetworkResponse();
  virtual ~IRadioNetworkResponse();

  static const int32_t version = 1;
  static inline const std::string hash = "57e8e923513d80a26102e450d335e89b4346be66";
  static constexpr uint32_t TRANSACTION_acknowledgeRequest = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getAllowedNetworkTypesBitmapResponse = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getAvailableBandModesResponse = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getAvailableNetworksResponse = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getBarringInfoResponse = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getCdmaRoamingPreferenceResponse = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getCellInfoListResponse = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getDataRegistrationStateResponse = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getImsRegistrationStateResponse = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getNetworkSelectionModeResponse = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getOperatorResponse = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getSignalStrengthResponse = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getSystemSelectionChannelsResponse = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_getVoiceRadioTechnologyResponse = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_getVoiceRegistrationStateResponse = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_isNrDualConnectivityEnabledResponse = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_setAllowedNetworkTypesBitmapResponse = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_setBandModeResponse = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_setBarringPasswordResponse = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_setCdmaRoamingPreferenceResponse = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_setCellInfoListRateResponse = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_setIndicationFilterResponse = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_setLinkCapacityReportingCriteriaResponse = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_setLocationUpdatesResponse = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_setNetworkSelectionModeAutomaticResponse = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_setNetworkSelectionModeManualResponse = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_setNrDualConnectivityStateResponse = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_setSignalStrengthReportingCriteriaResponse = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_setSuppServiceNotificationsResponse = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_setSystemSelectionChannelsResponse = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_startNetworkScanResponse = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_stopNetworkScanResponse = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_supplyNetworkDepersonalizationResponse = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_setUsageSettingResponse = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_getUsageSettingResponse = FIRST_CALL_TRANSACTION + 34;

  static std::shared_ptr<IRadioNetworkResponse> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioNetworkResponse>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioNetworkResponse>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioNetworkResponse>& impl);
  static const std::shared_ptr<IRadioNetworkResponse>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_networkTypeBitmap) = 0;
  virtual ::ndk::ScopedAStatus getAvailableBandModesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::RadioBandMode>& in_bandModes) = 0;
  virtual ::ndk::ScopedAStatus getAvailableNetworksResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::OperatorInfo>& in_networkInfos) = 0;
  virtual ::ndk::ScopedAStatus getBarringInfoResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::vector<::aidl::android::hardware::radio::network::BarringInfo>& in_barringInfos) = 0;
  virtual ::ndk::ScopedAStatus getCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::network::CdmaRoamingType in_type) = 0;
  virtual ::ndk::ScopedAStatus getCellInfoListResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::CellInfo>& in_cellInfo) = 0;
  virtual ::ndk::ScopedAStatus getDataRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::RegStateResult& in_dataRegResponse) = 0;
  virtual ::ndk::ScopedAStatus getImsRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isRegistered, ::aidl::android::hardware::radio::RadioTechnologyFamily in_ratFamily) = 0;
  virtual ::ndk::ScopedAStatus getNetworkSelectionModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_manual) = 0;
  virtual ::ndk::ScopedAStatus getOperatorResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_longName, const std::string& in_shortName, const std::string& in_numeric) = 0;
  virtual ::ndk::ScopedAStatus getSignalStrengthResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::SignalStrength& in_signalStrength) = 0;
  virtual ::ndk::ScopedAStatus getSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier>& in_specifiers) = 0;
  virtual ::ndk::ScopedAStatus getVoiceRadioTechnologyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::RadioTechnology in_rat) = 0;
  virtual ::ndk::ScopedAStatus getVoiceRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::RegStateResult& in_voiceRegResponse) = 0;
  virtual ::ndk::ScopedAStatus isNrDualConnectivityEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isEnabled) = 0;
  virtual ::ndk::ScopedAStatus setAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setBandModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setBarringPasswordResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setCellInfoListRateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setIndicationFilterResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setLinkCapacityReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setLocationUpdatesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setNetworkSelectionModeAutomaticResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setNetworkSelectionModeManualResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setNrDualConnectivityStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setSignalStrengthReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setSuppServiceNotificationsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus startNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus stopNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus supplyNetworkDepersonalizationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) = 0;
  virtual ::ndk::ScopedAStatus setUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::network::UsageSetting in_usageSetting) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioNetworkResponse> default_impl;
};
class IRadioNetworkResponseDefault : public IRadioNetworkResponse {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
