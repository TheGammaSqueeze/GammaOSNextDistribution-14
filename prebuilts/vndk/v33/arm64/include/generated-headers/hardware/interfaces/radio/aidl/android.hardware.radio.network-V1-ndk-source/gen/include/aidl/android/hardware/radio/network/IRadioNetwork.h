#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/AccessNetwork.h>
#include <aidl/android/hardware/radio/network/CdmaRoamingType.h>
#include <aidl/android/hardware/radio/network/IRadioNetworkIndication.h>
#include <aidl/android/hardware/radio/network/IRadioNetworkResponse.h>
#include <aidl/android/hardware/radio/network/NetworkScanRequest.h>
#include <aidl/android/hardware/radio/network/NrDualConnectivityState.h>
#include <aidl/android/hardware/radio/network/RadioAccessSpecifier.h>
#include <aidl/android/hardware/radio/network/RadioBandMode.h>
#include <aidl/android/hardware/radio/network/SignalThresholdInfo.h>
#include <aidl/android/hardware/radio/network/UsageSetting.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class IRadioNetwork : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioNetwork();
  virtual ~IRadioNetwork();

  static const int32_t version = 1;
  static inline const std::string hash = "57e8e923513d80a26102e450d335e89b4346be66";
  static constexpr uint32_t TRANSACTION_getAllowedNetworkTypesBitmap = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getAvailableBandModes = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getAvailableNetworks = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getBarringInfo = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getCdmaRoamingPreference = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getCellInfoList = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getDataRegistrationState = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getImsRegistrationState = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getNetworkSelectionMode = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getOperator = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getSignalStrength = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getSystemSelectionChannels = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getVoiceRadioTechnology = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_getVoiceRegistrationState = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_isNrDualConnectivityEnabled = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_responseAcknowledgement = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_setAllowedNetworkTypesBitmap = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_setBandMode = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_setBarringPassword = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_setCdmaRoamingPreference = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_setCellInfoListRate = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_setIndicationFilter = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_setLinkCapacityReportingCriteria = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_setLocationUpdates = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_setNetworkSelectionModeAutomatic = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_setNetworkSelectionModeManual = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_setNrDualConnectivityState = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_setResponseFunctions = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_setSignalStrengthReportingCriteria = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_setSuppServiceNotifications = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_setSystemSelectionChannels = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_startNetworkScan = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_stopNetworkScan = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_supplyNetworkDepersonalization = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_setUsageSetting = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_getUsageSetting = FIRST_CALL_TRANSACTION + 35;

  static std::shared_ptr<IRadioNetwork> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioNetwork>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioNetwork>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioNetwork>& impl);
  static const std::shared_ptr<IRadioNetwork>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getAllowedNetworkTypesBitmap(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getAvailableBandModes(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getAvailableNetworks(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getBarringInfo(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getCdmaRoamingPreference(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getCellInfoList(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getDataRegistrationState(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getImsRegistrationState(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getNetworkSelectionMode(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getOperator(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getSignalStrength(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getSystemSelectionChannels(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getVoiceRadioTechnology(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getVoiceRegistrationState(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus isNrDualConnectivityEnabled(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus responseAcknowledgement() = 0;
  virtual ::ndk::ScopedAStatus setAllowedNetworkTypesBitmap(int32_t in_serial, int32_t in_networkTypeBitmap) = 0;
  virtual ::ndk::ScopedAStatus setBandMode(int32_t in_serial, ::aidl::android::hardware::radio::network::RadioBandMode in_mode) = 0;
  virtual ::ndk::ScopedAStatus setBarringPassword(int32_t in_serial, const std::string& in_facility, const std::string& in_oldPassword, const std::string& in_newPassword) = 0;
  virtual ::ndk::ScopedAStatus setCdmaRoamingPreference(int32_t in_serial, ::aidl::android::hardware::radio::network::CdmaRoamingType in_type) = 0;
  virtual ::ndk::ScopedAStatus setCellInfoListRate(int32_t in_serial, int32_t in_rate) = 0;
  virtual ::ndk::ScopedAStatus setIndicationFilter(int32_t in_serial, int32_t in_indicationFilter) = 0;
  virtual ::ndk::ScopedAStatus setLinkCapacityReportingCriteria(int32_t in_serial, int32_t in_hysteresisMs, int32_t in_hysteresisDlKbps, int32_t in_hysteresisUlKbps, const std::vector<int32_t>& in_thresholdsDownlinkKbps, const std::vector<int32_t>& in_thresholdsUplinkKbps, ::aidl::android::hardware::radio::AccessNetwork in_accessNetwork) = 0;
  virtual ::ndk::ScopedAStatus setLocationUpdates(int32_t in_serial, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setNetworkSelectionModeAutomatic(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus setNetworkSelectionModeManual(int32_t in_serial, const std::string& in_operatorNumeric, ::aidl::android::hardware::radio::AccessNetwork in_ran) = 0;
  virtual ::ndk::ScopedAStatus setNrDualConnectivityState(int32_t in_serial, ::aidl::android::hardware::radio::network::NrDualConnectivityState in_nrDualConnectivityState) = 0;
  virtual ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::network::IRadioNetworkResponse>& in_radioNetworkResponse, const std::shared_ptr<::aidl::android::hardware::radio::network::IRadioNetworkIndication>& in_radioNetworkIndication) = 0;
  virtual ::ndk::ScopedAStatus setSignalStrengthReportingCriteria(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::network::SignalThresholdInfo>& in_signalThresholdInfos) = 0;
  virtual ::ndk::ScopedAStatus setSuppServiceNotifications(int32_t in_serial, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setSystemSelectionChannels(int32_t in_serial, bool in_specifyChannels, const std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier>& in_specifiers) = 0;
  virtual ::ndk::ScopedAStatus startNetworkScan(int32_t in_serial, const ::aidl::android::hardware::radio::network::NetworkScanRequest& in_request) = 0;
  virtual ::ndk::ScopedAStatus stopNetworkScan(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus supplyNetworkDepersonalization(int32_t in_serial, const std::string& in_netPin) = 0;
  virtual ::ndk::ScopedAStatus setUsageSetting(int32_t in_serial, ::aidl::android::hardware::radio::network::UsageSetting in_usageSetting) = 0;
  virtual ::ndk::ScopedAStatus getUsageSetting(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioNetwork> default_impl;
};
class IRadioNetworkDefault : public IRadioNetwork {
public:
  ::ndk::ScopedAStatus getAllowedNetworkTypesBitmap(int32_t in_serial) override;
  ::ndk::ScopedAStatus getAvailableBandModes(int32_t in_serial) override;
  ::ndk::ScopedAStatus getAvailableNetworks(int32_t in_serial) override;
  ::ndk::ScopedAStatus getBarringInfo(int32_t in_serial) override;
  ::ndk::ScopedAStatus getCdmaRoamingPreference(int32_t in_serial) override;
  ::ndk::ScopedAStatus getCellInfoList(int32_t in_serial) override;
  ::ndk::ScopedAStatus getDataRegistrationState(int32_t in_serial) override;
  ::ndk::ScopedAStatus getImsRegistrationState(int32_t in_serial) override;
  ::ndk::ScopedAStatus getNetworkSelectionMode(int32_t in_serial) override;
  ::ndk::ScopedAStatus getOperator(int32_t in_serial) override;
  ::ndk::ScopedAStatus getSignalStrength(int32_t in_serial) override;
  ::ndk::ScopedAStatus getSystemSelectionChannels(int32_t in_serial) override;
  ::ndk::ScopedAStatus getVoiceRadioTechnology(int32_t in_serial) override;
  ::ndk::ScopedAStatus getVoiceRegistrationState(int32_t in_serial) override;
  ::ndk::ScopedAStatus isNrDualConnectivityEnabled(int32_t in_serial) override;
  ::ndk::ScopedAStatus responseAcknowledgement() override;
  ::ndk::ScopedAStatus setAllowedNetworkTypesBitmap(int32_t in_serial, int32_t in_networkTypeBitmap) override;
  ::ndk::ScopedAStatus setBandMode(int32_t in_serial, ::aidl::android::hardware::radio::network::RadioBandMode in_mode) override;
  ::ndk::ScopedAStatus setBarringPassword(int32_t in_serial, const std::string& in_facility, const std::string& in_oldPassword, const std::string& in_newPassword) override;
  ::ndk::ScopedAStatus setCdmaRoamingPreference(int32_t in_serial, ::aidl::android::hardware::radio::network::CdmaRoamingType in_type) override;
  ::ndk::ScopedAStatus setCellInfoListRate(int32_t in_serial, int32_t in_rate) override;
  ::ndk::ScopedAStatus setIndicationFilter(int32_t in_serial, int32_t in_indicationFilter) override;
  ::ndk::ScopedAStatus setLinkCapacityReportingCriteria(int32_t in_serial, int32_t in_hysteresisMs, int32_t in_hysteresisDlKbps, int32_t in_hysteresisUlKbps, const std::vector<int32_t>& in_thresholdsDownlinkKbps, const std::vector<int32_t>& in_thresholdsUplinkKbps, ::aidl::android::hardware::radio::AccessNetwork in_accessNetwork) override;
  ::ndk::ScopedAStatus setLocationUpdates(int32_t in_serial, bool in_enable) override;
  ::ndk::ScopedAStatus setNetworkSelectionModeAutomatic(int32_t in_serial) override;
  ::ndk::ScopedAStatus setNetworkSelectionModeManual(int32_t in_serial, const std::string& in_operatorNumeric, ::aidl::android::hardware::radio::AccessNetwork in_ran) override;
  ::ndk::ScopedAStatus setNrDualConnectivityState(int32_t in_serial, ::aidl::android::hardware::radio::network::NrDualConnectivityState in_nrDualConnectivityState) override;
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::network::IRadioNetworkResponse>& in_radioNetworkResponse, const std::shared_ptr<::aidl::android::hardware::radio::network::IRadioNetworkIndication>& in_radioNetworkIndication) override;
  ::ndk::ScopedAStatus setSignalStrengthReportingCriteria(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::network::SignalThresholdInfo>& in_signalThresholdInfos) override;
  ::ndk::ScopedAStatus setSuppServiceNotifications(int32_t in_serial, bool in_enable) override;
  ::ndk::ScopedAStatus setSystemSelectionChannels(int32_t in_serial, bool in_specifyChannels, const std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier>& in_specifiers) override;
  ::ndk::ScopedAStatus startNetworkScan(int32_t in_serial, const ::aidl::android::hardware::radio::network::NetworkScanRequest& in_request) override;
  ::ndk::ScopedAStatus stopNetworkScan(int32_t in_serial) override;
  ::ndk::ScopedAStatus supplyNetworkDepersonalization(int32_t in_serial, const std::string& in_netPin) override;
  ::ndk::ScopedAStatus setUsageSetting(int32_t in_serial, ::aidl::android::hardware::radio::network::UsageSetting in_usageSetting) override;
  ::ndk::ScopedAStatus getUsageSetting(int32_t in_serial) override;
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
