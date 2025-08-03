#pragma once

#include "aidl/android/hardware/radio/network/IRadioNetwork.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class BpRadioNetwork : public ::ndk::BpCInterface<IRadioNetwork> {
public:
  explicit BpRadioNetwork(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioNetwork();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
