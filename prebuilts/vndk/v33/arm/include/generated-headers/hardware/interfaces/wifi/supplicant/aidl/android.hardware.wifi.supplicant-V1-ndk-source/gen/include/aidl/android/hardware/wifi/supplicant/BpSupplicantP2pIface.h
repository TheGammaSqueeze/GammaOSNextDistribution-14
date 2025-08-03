#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantP2pIface.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BpSupplicantP2pIface : public ::ndk::BpCInterface<ISupplicantP2pIface> {
public:
  explicit BpSupplicantP2pIface(const ::ndk::SpAIBinder& binder);
  virtual ~BpSupplicantP2pIface();

  ::ndk::ScopedAStatus addBonjourService(const std::vector<uint8_t>& in_query, const std::vector<uint8_t>& in_response) override;
  ::ndk::ScopedAStatus addGroup(bool in_persistent, int32_t in_persistentNetworkId) override;
  ::ndk::ScopedAStatus addGroupWithConfig(const std::vector<uint8_t>& in_ssid, const std::string& in_pskPassphrase, bool in_persistent, int32_t in_freq, const std::vector<uint8_t>& in_peerAddress, bool in_joinExistingGroup) override;
  ::ndk::ScopedAStatus addNetwork(std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pNetwork>* _aidl_return) override;
  ::ndk::ScopedAStatus addUpnpService(int32_t in_version, const std::string& in_serviceName) override;
  ::ndk::ScopedAStatus cancelConnect() override;
  ::ndk::ScopedAStatus cancelServiceDiscovery(int64_t in_identifier) override;
  ::ndk::ScopedAStatus cancelWps(const std::string& in_groupIfName) override;
  ::ndk::ScopedAStatus configureExtListen(int32_t in_periodInMillis, int32_t in_intervalInMillis) override;
  ::ndk::ScopedAStatus connect(const std::vector<uint8_t>& in_peerAddress, ::aidl::android::hardware::wifi::supplicant::WpsProvisionMethod in_provisionMethod, const std::string& in_preSelectedPin, bool in_joinExistingGroup, bool in_persistent, int32_t in_goIntent, std::string* _aidl_return) override;
  ::ndk::ScopedAStatus createNfcHandoverRequestMessage(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus createNfcHandoverSelectMessage(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus enableWfd(bool in_enable) override;
  ::ndk::ScopedAStatus find(int32_t in_timeoutInSec) override;
  ::ndk::ScopedAStatus flush() override;
  ::ndk::ScopedAStatus flushServices() override;
  ::ndk::ScopedAStatus getDeviceAddress(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getEdmg(bool* _aidl_return) override;
  ::ndk::ScopedAStatus getGroupCapability(const std::vector<uint8_t>& in_peerAddress, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask* _aidl_return) override;
  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getNetwork(int32_t in_id, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pNetwork>* _aidl_return) override;
  ::ndk::ScopedAStatus getSsid(const std::vector<uint8_t>& in_peerAddress, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) override;
  ::ndk::ScopedAStatus invite(const std::string& in_groupIfName, const std::vector<uint8_t>& in_goDeviceAddress, const std::vector<uint8_t>& in_peerAddress) override;
  ::ndk::ScopedAStatus listNetworks(std::vector<int32_t>* _aidl_return) override;
  ::ndk::ScopedAStatus provisionDiscovery(const std::vector<uint8_t>& in_peerAddress, ::aidl::android::hardware::wifi::supplicant::WpsProvisionMethod in_provisionMethod) override;
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIfaceCallback>& in_callback) override;
  ::ndk::ScopedAStatus reinvoke(int32_t in_persistentNetworkId, const std::vector<uint8_t>& in_peerAddress) override;
  ::ndk::ScopedAStatus reject(const std::vector<uint8_t>& in_peerAddress) override;
  ::ndk::ScopedAStatus removeBonjourService(const std::vector<uint8_t>& in_query) override;
  ::ndk::ScopedAStatus removeGroup(const std::string& in_groupIfName) override;
  ::ndk::ScopedAStatus removeNetwork(int32_t in_id) override;
  ::ndk::ScopedAStatus removeUpnpService(int32_t in_version, const std::string& in_serviceName) override;
  ::ndk::ScopedAStatus reportNfcHandoverInitiation(const std::vector<uint8_t>& in_select) override;
  ::ndk::ScopedAStatus reportNfcHandoverResponse(const std::vector<uint8_t>& in_request) override;
  ::ndk::ScopedAStatus requestServiceDiscovery(const std::vector<uint8_t>& in_peerAddress, const std::vector<uint8_t>& in_query, int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus saveConfig() override;
  ::ndk::ScopedAStatus setDisallowedFrequencies(const std::vector<::aidl::android::hardware::wifi::supplicant::FreqRange>& in_ranges) override;
  ::ndk::ScopedAStatus setEdmg(bool in_enable) override;
  ::ndk::ScopedAStatus setGroupIdle(const std::string& in_groupIfName, int32_t in_timeoutInSec) override;
  ::ndk::ScopedAStatus setListenChannel(int32_t in_channel, int32_t in_operatingClass) override;
  ::ndk::ScopedAStatus setMacRandomization(bool in_enable) override;
  ::ndk::ScopedAStatus setMiracastMode(::aidl::android::hardware::wifi::supplicant::MiracastMode in_mode) override;
  ::ndk::ScopedAStatus setPowerSave(const std::string& in_groupIfName, bool in_enable) override;
  ::ndk::ScopedAStatus setSsidPostfix(const std::vector<uint8_t>& in_postfix) override;
  ::ndk::ScopedAStatus setWfdDeviceInfo(const std::vector<uint8_t>& in_info) override;
  ::ndk::ScopedAStatus setWfdR2DeviceInfo(const std::vector<uint8_t>& in_info) override;
  ::ndk::ScopedAStatus removeClient(const std::vector<uint8_t>& in_peerAddress, bool in_isLegacyClient) override;
  ::ndk::ScopedAStatus setWpsConfigMethods(::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods) override;
  ::ndk::ScopedAStatus setWpsDeviceName(const std::string& in_name) override;
  ::ndk::ScopedAStatus setWpsDeviceType(const std::vector<uint8_t>& in_type) override;
  ::ndk::ScopedAStatus setWpsManufacturer(const std::string& in_manufacturer) override;
  ::ndk::ScopedAStatus setWpsModelName(const std::string& in_modelName) override;
  ::ndk::ScopedAStatus setWpsModelNumber(const std::string& in_modelNumber) override;
  ::ndk::ScopedAStatus setWpsSerialNumber(const std::string& in_serialNumber) override;
  ::ndk::ScopedAStatus startWpsPbc(const std::string& in_groupIfName, const std::vector<uint8_t>& in_bssid) override;
  ::ndk::ScopedAStatus startWpsPinDisplay(const std::string& in_groupIfName, const std::vector<uint8_t>& in_bssid, std::string* _aidl_return) override;
  ::ndk::ScopedAStatus startWpsPinKeypad(const std::string& in_groupIfName, const std::string& in_pin) override;
  ::ndk::ScopedAStatus stopFind() override;
  ::ndk::ScopedAStatus findOnSocialChannels(int32_t in_timeoutInSec) override;
  ::ndk::ScopedAStatus findOnSpecificFrequency(int32_t in_freqInHz, int32_t in_timeoutInSec) override;
  ::ndk::ScopedAStatus setVendorElements(::aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask in_frameTypeMask, const std::vector<uint8_t>& in_vendorElemBytes) override;
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
