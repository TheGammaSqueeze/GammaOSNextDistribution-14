#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/supplicant/AnqpInfoId.h>
#include <aidl/android/hardware/wifi/supplicant/BtCoexistenceMode.h>
#include <aidl/android/hardware/wifi/supplicant/ConnectionCapabilities.h>
#include <aidl/android/hardware/wifi/supplicant/DppAkm.h>
#include <aidl/android/hardware/wifi/supplicant/DppCurve.h>
#include <aidl/android/hardware/wifi/supplicant/DppNetRole.h>
#include <aidl/android/hardware/wifi/supplicant/DppResponderBootstrapInfo.h>
#include <aidl/android/hardware/wifi/supplicant/Hs20AnqpSubtypes.h>
#include <aidl/android/hardware/wifi/supplicant/ISupplicantStaIfaceCallback.h>
#include <aidl/android/hardware/wifi/supplicant/ISupplicantStaNetwork.h>
#include <aidl/android/hardware/wifi/supplicant/IfaceType.h>
#include <aidl/android/hardware/wifi/supplicant/KeyMgmtMask.h>
#include <aidl/android/hardware/wifi/supplicant/MloLinksInfo.h>
#include <aidl/android/hardware/wifi/supplicant/QosPolicyStatus.h>
#include <aidl/android/hardware/wifi/supplicant/RxFilterType.h>
#include <aidl/android/hardware/wifi/supplicant/WpaDriverCapabilitiesMask.h>
#include <aidl/android/hardware/wifi/supplicant/WpsConfigMethods.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class ISupplicantStaIface : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISupplicantStaIface();
  virtual ~ISupplicantStaIface();

  static const int32_t version = 1;
  static inline const std::string hash = "5b8bcab6b43177dffdec5873e84205b04757cc9d";
  static constexpr uint32_t TRANSACTION_addDppPeerUri = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_addExtRadioWork = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_addNetwork = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_addRxFilter = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_cancelWps = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_disconnect = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_enableAutoReconnect = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_filsHlpAddRequest = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_filsHlpFlushRequest = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_generateDppBootstrapInfoForResponder = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_generateSelfDppConfiguration = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getConnectionCapabilities = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getConnectionMloLinksInfo = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_getKeyMgmtCapabilities = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_getMacAddress = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_getName = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_getNetwork = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_getType = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_getWpaDriverCapabilities = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_initiateAnqpQuery = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_initiateHs20IconQuery = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_initiateTdlsDiscover = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_initiateTdlsSetup = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_initiateTdlsTeardown = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_initiateVenueUrlAnqpQuery = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_listNetworks = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_reassociate = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_reconnect = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_registerCallback = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_setQosPolicyFeatureEnabled = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_sendQosPolicyResponse = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_removeAllQosPolicies = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_removeDppUri = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_removeExtRadioWork = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_removeNetwork = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_removeRxFilter = FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_setBtCoexistenceMode = FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_setBtCoexistenceScanModeEnabled = FIRST_CALL_TRANSACTION + 37;
  static constexpr uint32_t TRANSACTION_setCountryCode = FIRST_CALL_TRANSACTION + 38;
  static constexpr uint32_t TRANSACTION_setExternalSim = FIRST_CALL_TRANSACTION + 39;
  static constexpr uint32_t TRANSACTION_setMboCellularDataStatus = FIRST_CALL_TRANSACTION + 40;
  static constexpr uint32_t TRANSACTION_setPowerSave = FIRST_CALL_TRANSACTION + 41;
  static constexpr uint32_t TRANSACTION_setSuspendModeEnabled = FIRST_CALL_TRANSACTION + 42;
  static constexpr uint32_t TRANSACTION_setWpsConfigMethods = FIRST_CALL_TRANSACTION + 43;
  static constexpr uint32_t TRANSACTION_setWpsDeviceName = FIRST_CALL_TRANSACTION + 44;
  static constexpr uint32_t TRANSACTION_setWpsDeviceType = FIRST_CALL_TRANSACTION + 45;
  static constexpr uint32_t TRANSACTION_setWpsManufacturer = FIRST_CALL_TRANSACTION + 46;
  static constexpr uint32_t TRANSACTION_setWpsModelName = FIRST_CALL_TRANSACTION + 47;
  static constexpr uint32_t TRANSACTION_setWpsModelNumber = FIRST_CALL_TRANSACTION + 48;
  static constexpr uint32_t TRANSACTION_setWpsSerialNumber = FIRST_CALL_TRANSACTION + 49;
  static constexpr uint32_t TRANSACTION_startDppConfiguratorInitiator = FIRST_CALL_TRANSACTION + 50;
  static constexpr uint32_t TRANSACTION_startDppEnrolleeInitiator = FIRST_CALL_TRANSACTION + 51;
  static constexpr uint32_t TRANSACTION_startDppEnrolleeResponder = FIRST_CALL_TRANSACTION + 52;
  static constexpr uint32_t TRANSACTION_startRxFilter = FIRST_CALL_TRANSACTION + 53;
  static constexpr uint32_t TRANSACTION_startWpsPbc = FIRST_CALL_TRANSACTION + 54;
  static constexpr uint32_t TRANSACTION_startWpsPinDisplay = FIRST_CALL_TRANSACTION + 55;
  static constexpr uint32_t TRANSACTION_startWpsPinKeypad = FIRST_CALL_TRANSACTION + 56;
  static constexpr uint32_t TRANSACTION_startWpsRegistrar = FIRST_CALL_TRANSACTION + 57;
  static constexpr uint32_t TRANSACTION_stopDppInitiator = FIRST_CALL_TRANSACTION + 58;
  static constexpr uint32_t TRANSACTION_stopDppResponder = FIRST_CALL_TRANSACTION + 59;
  static constexpr uint32_t TRANSACTION_stopRxFilter = FIRST_CALL_TRANSACTION + 60;

  static std::shared_ptr<ISupplicantStaIface> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantStaIface>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantStaIface>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISupplicantStaIface>& impl);
  static const std::shared_ptr<ISupplicantStaIface>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus addDppPeerUri(const std::string& in_uri, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus addExtRadioWork(const std::string& in_name, int32_t in_freqInMhz, int32_t in_timeoutInSec, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus addNetwork(std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaNetwork>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus addRxFilter(::aidl::android::hardware::wifi::supplicant::RxFilterType in_type) = 0;
  virtual ::ndk::ScopedAStatus cancelWps() = 0;
  virtual ::ndk::ScopedAStatus disconnect() = 0;
  virtual ::ndk::ScopedAStatus enableAutoReconnect(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus filsHlpAddRequest(const std::vector<uint8_t>& in_dst_mac, const std::vector<uint8_t>& in_pkt) = 0;
  virtual ::ndk::ScopedAStatus filsHlpFlushRequest() = 0;
  virtual ::ndk::ScopedAStatus generateDppBootstrapInfoForResponder(const std::vector<uint8_t>& in_macAddress, const std::string& in_deviceInfo, ::aidl::android::hardware::wifi::supplicant::DppCurve in_curve, ::aidl::android::hardware::wifi::supplicant::DppResponderBootstrapInfo* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus generateSelfDppConfiguration(const std::string& in_ssid, const std::vector<uint8_t>& in_privEcKey) = 0;
  virtual ::ndk::ScopedAStatus getConnectionCapabilities(::aidl::android::hardware::wifi::supplicant::ConnectionCapabilities* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getConnectionMloLinksInfo(::aidl::android::hardware::wifi::supplicant::MloLinksInfo* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getKeyMgmtCapabilities(::aidl::android::hardware::wifi::supplicant::KeyMgmtMask* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getMacAddress(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getName(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getNetwork(int32_t in_id, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaNetwork>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getWpaDriverCapabilities(::aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus initiateAnqpQuery(const std::vector<uint8_t>& in_macAddress, const std::vector<::aidl::android::hardware::wifi::supplicant::AnqpInfoId>& in_infoElements, const std::vector<::aidl::android::hardware::wifi::supplicant::Hs20AnqpSubtypes>& in_subTypes) = 0;
  virtual ::ndk::ScopedAStatus initiateHs20IconQuery(const std::vector<uint8_t>& in_macAddress, const std::string& in_fileName) = 0;
  virtual ::ndk::ScopedAStatus initiateTdlsDiscover(const std::vector<uint8_t>& in_macAddress) = 0;
  virtual ::ndk::ScopedAStatus initiateTdlsSetup(const std::vector<uint8_t>& in_macAddress) = 0;
  virtual ::ndk::ScopedAStatus initiateTdlsTeardown(const std::vector<uint8_t>& in_macAddress) = 0;
  virtual ::ndk::ScopedAStatus initiateVenueUrlAnqpQuery(const std::vector<uint8_t>& in_macAddress) = 0;
  virtual ::ndk::ScopedAStatus listNetworks(std::vector<int32_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus reassociate() = 0;
  virtual ::ndk::ScopedAStatus reconnect() = 0;
  virtual ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIfaceCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus setQosPolicyFeatureEnabled(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus sendQosPolicyResponse(int32_t in_qosPolicyRequestId, bool in_morePolicies, const std::vector<::aidl::android::hardware::wifi::supplicant::QosPolicyStatus>& in_qosPolicyStatusList) = 0;
  virtual ::ndk::ScopedAStatus removeAllQosPolicies() = 0;
  virtual ::ndk::ScopedAStatus removeDppUri(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus removeExtRadioWork(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus removeNetwork(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus removeRxFilter(::aidl::android::hardware::wifi::supplicant::RxFilterType in_type) = 0;
  virtual ::ndk::ScopedAStatus setBtCoexistenceMode(::aidl::android::hardware::wifi::supplicant::BtCoexistenceMode in_mode) = 0;
  virtual ::ndk::ScopedAStatus setBtCoexistenceScanModeEnabled(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setCountryCode(const std::vector<uint8_t>& in_code) = 0;
  virtual ::ndk::ScopedAStatus setExternalSim(bool in_useExternalSim) = 0;
  virtual ::ndk::ScopedAStatus setMboCellularDataStatus(bool in_available) = 0;
  virtual ::ndk::ScopedAStatus setPowerSave(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setSuspendModeEnabled(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setWpsConfigMethods(::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods) = 0;
  virtual ::ndk::ScopedAStatus setWpsDeviceName(const std::string& in_name) = 0;
  virtual ::ndk::ScopedAStatus setWpsDeviceType(const std::vector<uint8_t>& in_type) = 0;
  virtual ::ndk::ScopedAStatus setWpsManufacturer(const std::string& in_manufacturer) = 0;
  virtual ::ndk::ScopedAStatus setWpsModelName(const std::string& in_modelName) = 0;
  virtual ::ndk::ScopedAStatus setWpsModelNumber(const std::string& in_modelNumber) = 0;
  virtual ::ndk::ScopedAStatus setWpsSerialNumber(const std::string& in_serialNumber) = 0;
  virtual ::ndk::ScopedAStatus startDppConfiguratorInitiator(int32_t in_peerBootstrapId, int32_t in_ownBootstrapId, const std::string& in_ssid, const std::string& in_password, const std::string& in_psk, ::aidl::android::hardware::wifi::supplicant::DppNetRole in_netRole, ::aidl::android::hardware::wifi::supplicant::DppAkm in_securityAkm, const std::vector<uint8_t>& in_privEcKey, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus startDppEnrolleeInitiator(int32_t in_peerBootstrapId, int32_t in_ownBootstrapId) = 0;
  virtual ::ndk::ScopedAStatus startDppEnrolleeResponder(int32_t in_listenChannel) = 0;
  virtual ::ndk::ScopedAStatus startRxFilter() = 0;
  virtual ::ndk::ScopedAStatus startWpsPbc(const std::vector<uint8_t>& in_bssid) = 0;
  virtual ::ndk::ScopedAStatus startWpsPinDisplay(const std::vector<uint8_t>& in_bssid, std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus startWpsPinKeypad(const std::string& in_pin) = 0;
  virtual ::ndk::ScopedAStatus startWpsRegistrar(const std::vector<uint8_t>& in_bssid, const std::string& in_pin) = 0;
  virtual ::ndk::ScopedAStatus stopDppInitiator() = 0;
  virtual ::ndk::ScopedAStatus stopDppResponder(int32_t in_ownBootstrapId) = 0;
  virtual ::ndk::ScopedAStatus stopRxFilter() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISupplicantStaIface> default_impl;
};
class ISupplicantStaIfaceDefault : public ISupplicantStaIface {
public:
  ::ndk::ScopedAStatus addDppPeerUri(const std::string& in_uri, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus addExtRadioWork(const std::string& in_name, int32_t in_freqInMhz, int32_t in_timeoutInSec, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus addNetwork(std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaNetwork>* _aidl_return) override;
  ::ndk::ScopedAStatus addRxFilter(::aidl::android::hardware::wifi::supplicant::RxFilterType in_type) override;
  ::ndk::ScopedAStatus cancelWps() override;
  ::ndk::ScopedAStatus disconnect() override;
  ::ndk::ScopedAStatus enableAutoReconnect(bool in_enable) override;
  ::ndk::ScopedAStatus filsHlpAddRequest(const std::vector<uint8_t>& in_dst_mac, const std::vector<uint8_t>& in_pkt) override;
  ::ndk::ScopedAStatus filsHlpFlushRequest() override;
  ::ndk::ScopedAStatus generateDppBootstrapInfoForResponder(const std::vector<uint8_t>& in_macAddress, const std::string& in_deviceInfo, ::aidl::android::hardware::wifi::supplicant::DppCurve in_curve, ::aidl::android::hardware::wifi::supplicant::DppResponderBootstrapInfo* _aidl_return) override;
  ::ndk::ScopedAStatus generateSelfDppConfiguration(const std::string& in_ssid, const std::vector<uint8_t>& in_privEcKey) override;
  ::ndk::ScopedAStatus getConnectionCapabilities(::aidl::android::hardware::wifi::supplicant::ConnectionCapabilities* _aidl_return) override;
  ::ndk::ScopedAStatus getConnectionMloLinksInfo(::aidl::android::hardware::wifi::supplicant::MloLinksInfo* _aidl_return) override;
  ::ndk::ScopedAStatus getKeyMgmtCapabilities(::aidl::android::hardware::wifi::supplicant::KeyMgmtMask* _aidl_return) override;
  ::ndk::ScopedAStatus getMacAddress(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getNetwork(int32_t in_id, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaNetwork>* _aidl_return) override;
  ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) override;
  ::ndk::ScopedAStatus getWpaDriverCapabilities(::aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask* _aidl_return) override;
  ::ndk::ScopedAStatus initiateAnqpQuery(const std::vector<uint8_t>& in_macAddress, const std::vector<::aidl::android::hardware::wifi::supplicant::AnqpInfoId>& in_infoElements, const std::vector<::aidl::android::hardware::wifi::supplicant::Hs20AnqpSubtypes>& in_subTypes) override;
  ::ndk::ScopedAStatus initiateHs20IconQuery(const std::vector<uint8_t>& in_macAddress, const std::string& in_fileName) override;
  ::ndk::ScopedAStatus initiateTdlsDiscover(const std::vector<uint8_t>& in_macAddress) override;
  ::ndk::ScopedAStatus initiateTdlsSetup(const std::vector<uint8_t>& in_macAddress) override;
  ::ndk::ScopedAStatus initiateTdlsTeardown(const std::vector<uint8_t>& in_macAddress) override;
  ::ndk::ScopedAStatus initiateVenueUrlAnqpQuery(const std::vector<uint8_t>& in_macAddress) override;
  ::ndk::ScopedAStatus listNetworks(std::vector<int32_t>* _aidl_return) override;
  ::ndk::ScopedAStatus reassociate() override;
  ::ndk::ScopedAStatus reconnect() override;
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIfaceCallback>& in_callback) override;
  ::ndk::ScopedAStatus setQosPolicyFeatureEnabled(bool in_enable) override;
  ::ndk::ScopedAStatus sendQosPolicyResponse(int32_t in_qosPolicyRequestId, bool in_morePolicies, const std::vector<::aidl::android::hardware::wifi::supplicant::QosPolicyStatus>& in_qosPolicyStatusList) override;
  ::ndk::ScopedAStatus removeAllQosPolicies() override;
  ::ndk::ScopedAStatus removeDppUri(int32_t in_id) override;
  ::ndk::ScopedAStatus removeExtRadioWork(int32_t in_id) override;
  ::ndk::ScopedAStatus removeNetwork(int32_t in_id) override;
  ::ndk::ScopedAStatus removeRxFilter(::aidl::android::hardware::wifi::supplicant::RxFilterType in_type) override;
  ::ndk::ScopedAStatus setBtCoexistenceMode(::aidl::android::hardware::wifi::supplicant::BtCoexistenceMode in_mode) override;
  ::ndk::ScopedAStatus setBtCoexistenceScanModeEnabled(bool in_enable) override;
  ::ndk::ScopedAStatus setCountryCode(const std::vector<uint8_t>& in_code) override;
  ::ndk::ScopedAStatus setExternalSim(bool in_useExternalSim) override;
  ::ndk::ScopedAStatus setMboCellularDataStatus(bool in_available) override;
  ::ndk::ScopedAStatus setPowerSave(bool in_enable) override;
  ::ndk::ScopedAStatus setSuspendModeEnabled(bool in_enable) override;
  ::ndk::ScopedAStatus setWpsConfigMethods(::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods) override;
  ::ndk::ScopedAStatus setWpsDeviceName(const std::string& in_name) override;
  ::ndk::ScopedAStatus setWpsDeviceType(const std::vector<uint8_t>& in_type) override;
  ::ndk::ScopedAStatus setWpsManufacturer(const std::string& in_manufacturer) override;
  ::ndk::ScopedAStatus setWpsModelName(const std::string& in_modelName) override;
  ::ndk::ScopedAStatus setWpsModelNumber(const std::string& in_modelNumber) override;
  ::ndk::ScopedAStatus setWpsSerialNumber(const std::string& in_serialNumber) override;
  ::ndk::ScopedAStatus startDppConfiguratorInitiator(int32_t in_peerBootstrapId, int32_t in_ownBootstrapId, const std::string& in_ssid, const std::string& in_password, const std::string& in_psk, ::aidl::android::hardware::wifi::supplicant::DppNetRole in_netRole, ::aidl::android::hardware::wifi::supplicant::DppAkm in_securityAkm, const std::vector<uint8_t>& in_privEcKey, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus startDppEnrolleeInitiator(int32_t in_peerBootstrapId, int32_t in_ownBootstrapId) override;
  ::ndk::ScopedAStatus startDppEnrolleeResponder(int32_t in_listenChannel) override;
  ::ndk::ScopedAStatus startRxFilter() override;
  ::ndk::ScopedAStatus startWpsPbc(const std::vector<uint8_t>& in_bssid) override;
  ::ndk::ScopedAStatus startWpsPinDisplay(const std::vector<uint8_t>& in_bssid, std::string* _aidl_return) override;
  ::ndk::ScopedAStatus startWpsPinKeypad(const std::string& in_pin) override;
  ::ndk::ScopedAStatus startWpsRegistrar(const std::vector<uint8_t>& in_bssid, const std::string& in_pin) override;
  ::ndk::ScopedAStatus stopDppInitiator() override;
  ::ndk::ScopedAStatus stopDppResponder(int32_t in_ownBootstrapId) override;
  ::ndk::ScopedAStatus stopRxFilter() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
