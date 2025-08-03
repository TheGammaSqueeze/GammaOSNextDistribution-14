#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/supplicant/FreqRange.h>
#include <aidl/android/hardware/wifi/supplicant/ISupplicantP2pIfaceCallback.h>
#include <aidl/android/hardware/wifi/supplicant/ISupplicantP2pNetwork.h>
#include <aidl/android/hardware/wifi/supplicant/IfaceType.h>
#include <aidl/android/hardware/wifi/supplicant/MiracastMode.h>
#include <aidl/android/hardware/wifi/supplicant/P2pFrameTypeMask.h>
#include <aidl/android/hardware/wifi/supplicant/P2pGroupCapabilityMask.h>
#include <aidl/android/hardware/wifi/supplicant/WpsConfigMethods.h>
#include <aidl/android/hardware/wifi/supplicant/WpsProvisionMethod.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class ISupplicantP2pIface : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISupplicantP2pIface();
  virtual ~ISupplicantP2pIface();

  static const int32_t version = 1;
  static inline const std::string hash = "5b8bcab6b43177dffdec5873e84205b04757cc9d";
  static constexpr uint32_t TRANSACTION_addBonjourService = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_addGroup = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_addGroupWithConfig = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_addNetwork = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_addUpnpService = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_cancelConnect = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_cancelServiceDiscovery = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_cancelWps = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_configureExtListen = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_connect = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_createNfcHandoverRequestMessage = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_createNfcHandoverSelectMessage = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_enableWfd = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_find = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_flush = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_flushServices = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_getDeviceAddress = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_getEdmg = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_getGroupCapability = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_getName = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_getNetwork = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_getSsid = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_getType = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_invite = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_listNetworks = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_provisionDiscovery = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_registerCallback = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_reinvoke = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_reject = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_removeBonjourService = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_removeGroup = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_removeNetwork = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_removeUpnpService = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_reportNfcHandoverInitiation = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_reportNfcHandoverResponse = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_requestServiceDiscovery = FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_saveConfig = FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_setDisallowedFrequencies = FIRST_CALL_TRANSACTION + 37;
  static constexpr uint32_t TRANSACTION_setEdmg = FIRST_CALL_TRANSACTION + 38;
  static constexpr uint32_t TRANSACTION_setGroupIdle = FIRST_CALL_TRANSACTION + 39;
  static constexpr uint32_t TRANSACTION_setListenChannel = FIRST_CALL_TRANSACTION + 40;
  static constexpr uint32_t TRANSACTION_setMacRandomization = FIRST_CALL_TRANSACTION + 41;
  static constexpr uint32_t TRANSACTION_setMiracastMode = FIRST_CALL_TRANSACTION + 42;
  static constexpr uint32_t TRANSACTION_setPowerSave = FIRST_CALL_TRANSACTION + 43;
  static constexpr uint32_t TRANSACTION_setSsidPostfix = FIRST_CALL_TRANSACTION + 44;
  static constexpr uint32_t TRANSACTION_setWfdDeviceInfo = FIRST_CALL_TRANSACTION + 45;
  static constexpr uint32_t TRANSACTION_setWfdR2DeviceInfo = FIRST_CALL_TRANSACTION + 46;
  static constexpr uint32_t TRANSACTION_removeClient = FIRST_CALL_TRANSACTION + 47;
  static constexpr uint32_t TRANSACTION_setWpsConfigMethods = FIRST_CALL_TRANSACTION + 48;
  static constexpr uint32_t TRANSACTION_setWpsDeviceName = FIRST_CALL_TRANSACTION + 49;
  static constexpr uint32_t TRANSACTION_setWpsDeviceType = FIRST_CALL_TRANSACTION + 50;
  static constexpr uint32_t TRANSACTION_setWpsManufacturer = FIRST_CALL_TRANSACTION + 51;
  static constexpr uint32_t TRANSACTION_setWpsModelName = FIRST_CALL_TRANSACTION + 52;
  static constexpr uint32_t TRANSACTION_setWpsModelNumber = FIRST_CALL_TRANSACTION + 53;
  static constexpr uint32_t TRANSACTION_setWpsSerialNumber = FIRST_CALL_TRANSACTION + 54;
  static constexpr uint32_t TRANSACTION_startWpsPbc = FIRST_CALL_TRANSACTION + 55;
  static constexpr uint32_t TRANSACTION_startWpsPinDisplay = FIRST_CALL_TRANSACTION + 56;
  static constexpr uint32_t TRANSACTION_startWpsPinKeypad = FIRST_CALL_TRANSACTION + 57;
  static constexpr uint32_t TRANSACTION_stopFind = FIRST_CALL_TRANSACTION + 58;
  static constexpr uint32_t TRANSACTION_findOnSocialChannels = FIRST_CALL_TRANSACTION + 59;
  static constexpr uint32_t TRANSACTION_findOnSpecificFrequency = FIRST_CALL_TRANSACTION + 60;
  static constexpr uint32_t TRANSACTION_setVendorElements = FIRST_CALL_TRANSACTION + 61;

  static std::shared_ptr<ISupplicantP2pIface> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantP2pIface>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantP2pIface>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISupplicantP2pIface>& impl);
  static const std::shared_ptr<ISupplicantP2pIface>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus addBonjourService(const std::vector<uint8_t>& in_query, const std::vector<uint8_t>& in_response) = 0;
  virtual ::ndk::ScopedAStatus addGroup(bool in_persistent, int32_t in_persistentNetworkId) = 0;
  virtual ::ndk::ScopedAStatus addGroupWithConfig(const std::vector<uint8_t>& in_ssid, const std::string& in_pskPassphrase, bool in_persistent, int32_t in_freq, const std::vector<uint8_t>& in_peerAddress, bool in_joinExistingGroup) = 0;
  virtual ::ndk::ScopedAStatus addNetwork(std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pNetwork>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus addUpnpService(int32_t in_version, const std::string& in_serviceName) = 0;
  virtual ::ndk::ScopedAStatus cancelConnect() = 0;
  virtual ::ndk::ScopedAStatus cancelServiceDiscovery(int64_t in_identifier) = 0;
  virtual ::ndk::ScopedAStatus cancelWps(const std::string& in_groupIfName) = 0;
  virtual ::ndk::ScopedAStatus configureExtListen(int32_t in_periodInMillis, int32_t in_intervalInMillis) = 0;
  virtual ::ndk::ScopedAStatus connect(const std::vector<uint8_t>& in_peerAddress, ::aidl::android::hardware::wifi::supplicant::WpsProvisionMethod in_provisionMethod, const std::string& in_preSelectedPin, bool in_joinExistingGroup, bool in_persistent, int32_t in_goIntent, std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus createNfcHandoverRequestMessage(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus createNfcHandoverSelectMessage(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus enableWfd(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus find(int32_t in_timeoutInSec) = 0;
  virtual ::ndk::ScopedAStatus flush() = 0;
  virtual ::ndk::ScopedAStatus flushServices() = 0;
  virtual ::ndk::ScopedAStatus getDeviceAddress(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEdmg(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getGroupCapability(const std::vector<uint8_t>& in_peerAddress, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getName(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getNetwork(int32_t in_id, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pNetwork>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSsid(const std::vector<uint8_t>& in_peerAddress, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus invite(const std::string& in_groupIfName, const std::vector<uint8_t>& in_goDeviceAddress, const std::vector<uint8_t>& in_peerAddress) = 0;
  virtual ::ndk::ScopedAStatus listNetworks(std::vector<int32_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus provisionDiscovery(const std::vector<uint8_t>& in_peerAddress, ::aidl::android::hardware::wifi::supplicant::WpsProvisionMethod in_provisionMethod) = 0;
  virtual ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIfaceCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus reinvoke(int32_t in_persistentNetworkId, const std::vector<uint8_t>& in_peerAddress) = 0;
  virtual ::ndk::ScopedAStatus reject(const std::vector<uint8_t>& in_peerAddress) = 0;
  virtual ::ndk::ScopedAStatus removeBonjourService(const std::vector<uint8_t>& in_query) = 0;
  virtual ::ndk::ScopedAStatus removeGroup(const std::string& in_groupIfName) = 0;
  virtual ::ndk::ScopedAStatus removeNetwork(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus removeUpnpService(int32_t in_version, const std::string& in_serviceName) = 0;
  virtual ::ndk::ScopedAStatus reportNfcHandoverInitiation(const std::vector<uint8_t>& in_select) = 0;
  virtual ::ndk::ScopedAStatus reportNfcHandoverResponse(const std::vector<uint8_t>& in_request) = 0;
  virtual ::ndk::ScopedAStatus requestServiceDiscovery(const std::vector<uint8_t>& in_peerAddress, const std::vector<uint8_t>& in_query, int64_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus saveConfig() = 0;
  virtual ::ndk::ScopedAStatus setDisallowedFrequencies(const std::vector<::aidl::android::hardware::wifi::supplicant::FreqRange>& in_ranges) = 0;
  virtual ::ndk::ScopedAStatus setEdmg(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setGroupIdle(const std::string& in_groupIfName, int32_t in_timeoutInSec) = 0;
  virtual ::ndk::ScopedAStatus setListenChannel(int32_t in_channel, int32_t in_operatingClass) = 0;
  virtual ::ndk::ScopedAStatus setMacRandomization(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setMiracastMode(::aidl::android::hardware::wifi::supplicant::MiracastMode in_mode) = 0;
  virtual ::ndk::ScopedAStatus setPowerSave(const std::string& in_groupIfName, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setSsidPostfix(const std::vector<uint8_t>& in_postfix) = 0;
  virtual ::ndk::ScopedAStatus setWfdDeviceInfo(const std::vector<uint8_t>& in_info) = 0;
  virtual ::ndk::ScopedAStatus setWfdR2DeviceInfo(const std::vector<uint8_t>& in_info) = 0;
  virtual ::ndk::ScopedAStatus removeClient(const std::vector<uint8_t>& in_peerAddress, bool in_isLegacyClient) = 0;
  virtual ::ndk::ScopedAStatus setWpsConfigMethods(::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods) = 0;
  virtual ::ndk::ScopedAStatus setWpsDeviceName(const std::string& in_name) = 0;
  virtual ::ndk::ScopedAStatus setWpsDeviceType(const std::vector<uint8_t>& in_type) = 0;
  virtual ::ndk::ScopedAStatus setWpsManufacturer(const std::string& in_manufacturer) = 0;
  virtual ::ndk::ScopedAStatus setWpsModelName(const std::string& in_modelName) = 0;
  virtual ::ndk::ScopedAStatus setWpsModelNumber(const std::string& in_modelNumber) = 0;
  virtual ::ndk::ScopedAStatus setWpsSerialNumber(const std::string& in_serialNumber) = 0;
  virtual ::ndk::ScopedAStatus startWpsPbc(const std::string& in_groupIfName, const std::vector<uint8_t>& in_bssid) = 0;
  virtual ::ndk::ScopedAStatus startWpsPinDisplay(const std::string& in_groupIfName, const std::vector<uint8_t>& in_bssid, std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus startWpsPinKeypad(const std::string& in_groupIfName, const std::string& in_pin) = 0;
  virtual ::ndk::ScopedAStatus stopFind() = 0;
  virtual ::ndk::ScopedAStatus findOnSocialChannels(int32_t in_timeoutInSec) = 0;
  virtual ::ndk::ScopedAStatus findOnSpecificFrequency(int32_t in_freqInHz, int32_t in_timeoutInSec) = 0;
  virtual ::ndk::ScopedAStatus setVendorElements(::aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask in_frameTypeMask, const std::vector<uint8_t>& in_vendorElemBytes) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISupplicantP2pIface> default_impl;
};
class ISupplicantP2pIfaceDefault : public ISupplicantP2pIface {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
