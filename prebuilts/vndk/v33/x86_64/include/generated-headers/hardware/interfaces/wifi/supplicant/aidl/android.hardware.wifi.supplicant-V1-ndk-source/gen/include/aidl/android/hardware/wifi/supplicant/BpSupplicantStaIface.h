#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantStaIface.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BpSupplicantStaIface : public ::ndk::BpCInterface<ISupplicantStaIface> {
public:
  explicit BpSupplicantStaIface(const ::ndk::SpAIBinder& binder);
  virtual ~BpSupplicantStaIface();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
