#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantP2pIface.h"

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
namespace wifi {
namespace supplicant {
class BnSupplicantP2pIface : public ::ndk::BnCInterface<ISupplicantP2pIface> {
public:
  BnSupplicantP2pIface();
  virtual ~BnSupplicantP2pIface();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISupplicantP2pIfaceDelegator : public BnSupplicantP2pIface {
public:
  explicit ISupplicantP2pIfaceDelegator(const std::shared_ptr<ISupplicantP2pIface> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISupplicantP2pIface::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus addBonjourService(const std::vector<uint8_t>& in_query, const std::vector<uint8_t>& in_response) override {
    return _impl->addBonjourService(in_query, in_response);
  }
  ::ndk::ScopedAStatus addGroup(bool in_persistent, int32_t in_persistentNetworkId) override {
    return _impl->addGroup(in_persistent, in_persistentNetworkId);
  }
  ::ndk::ScopedAStatus addGroupWithConfig(const std::vector<uint8_t>& in_ssid, const std::string& in_pskPassphrase, bool in_persistent, int32_t in_freq, const std::vector<uint8_t>& in_peerAddress, bool in_joinExistingGroup) override {
    return _impl->addGroupWithConfig(in_ssid, in_pskPassphrase, in_persistent, in_freq, in_peerAddress, in_joinExistingGroup);
  }
  ::ndk::ScopedAStatus addNetwork(std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pNetwork>* _aidl_return) override {
    return _impl->addNetwork(_aidl_return);
  }
  ::ndk::ScopedAStatus addUpnpService(int32_t in_version, const std::string& in_serviceName) override {
    return _impl->addUpnpService(in_version, in_serviceName);
  }
  ::ndk::ScopedAStatus cancelConnect() override {
    return _impl->cancelConnect();
  }
  ::ndk::ScopedAStatus cancelServiceDiscovery(int64_t in_identifier) override {
    return _impl->cancelServiceDiscovery(in_identifier);
  }
  ::ndk::ScopedAStatus cancelWps(const std::string& in_groupIfName) override {
    return _impl->cancelWps(in_groupIfName);
  }
  ::ndk::ScopedAStatus configureExtListen(int32_t in_periodInMillis, int32_t in_intervalInMillis) override {
    return _impl->configureExtListen(in_periodInMillis, in_intervalInMillis);
  }
  ::ndk::ScopedAStatus connect(const std::vector<uint8_t>& in_peerAddress, ::aidl::android::hardware::wifi::supplicant::WpsProvisionMethod in_provisionMethod, const std::string& in_preSelectedPin, bool in_joinExistingGroup, bool in_persistent, int32_t in_goIntent, std::string* _aidl_return) override {
    return _impl->connect(in_peerAddress, in_provisionMethod, in_preSelectedPin, in_joinExistingGroup, in_persistent, in_goIntent, _aidl_return);
  }
  ::ndk::ScopedAStatus createNfcHandoverRequestMessage(std::vector<uint8_t>* _aidl_return) override {
    return _impl->createNfcHandoverRequestMessage(_aidl_return);
  }
  ::ndk::ScopedAStatus createNfcHandoverSelectMessage(std::vector<uint8_t>* _aidl_return) override {
    return _impl->createNfcHandoverSelectMessage(_aidl_return);
  }
  ::ndk::ScopedAStatus enableWfd(bool in_enable) override {
    return _impl->enableWfd(in_enable);
  }
  ::ndk::ScopedAStatus find(int32_t in_timeoutInSec) override {
    return _impl->find(in_timeoutInSec);
  }
  ::ndk::ScopedAStatus flush() override {
    return _impl->flush();
  }
  ::ndk::ScopedAStatus flushServices() override {
    return _impl->flushServices();
  }
  ::ndk::ScopedAStatus getDeviceAddress(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getDeviceAddress(_aidl_return);
  }
  ::ndk::ScopedAStatus getEdmg(bool* _aidl_return) override {
    return _impl->getEdmg(_aidl_return);
  }
  ::ndk::ScopedAStatus getGroupCapability(const std::vector<uint8_t>& in_peerAddress, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask* _aidl_return) override {
    return _impl->getGroupCapability(in_peerAddress, _aidl_return);
  }
  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override {
    return _impl->getName(_aidl_return);
  }
  ::ndk::ScopedAStatus getNetwork(int32_t in_id, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pNetwork>* _aidl_return) override {
    return _impl->getNetwork(in_id, _aidl_return);
  }
  ::ndk::ScopedAStatus getSsid(const std::vector<uint8_t>& in_peerAddress, std::vector<uint8_t>* _aidl_return) override {
    return _impl->getSsid(in_peerAddress, _aidl_return);
  }
  ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) override {
    return _impl->getType(_aidl_return);
  }
  ::ndk::ScopedAStatus invite(const std::string& in_groupIfName, const std::vector<uint8_t>& in_goDeviceAddress, const std::vector<uint8_t>& in_peerAddress) override {
    return _impl->invite(in_groupIfName, in_goDeviceAddress, in_peerAddress);
  }
  ::ndk::ScopedAStatus listNetworks(std::vector<int32_t>* _aidl_return) override {
    return _impl->listNetworks(_aidl_return);
  }
  ::ndk::ScopedAStatus provisionDiscovery(const std::vector<uint8_t>& in_peerAddress, ::aidl::android::hardware::wifi::supplicant::WpsProvisionMethod in_provisionMethod) override {
    return _impl->provisionDiscovery(in_peerAddress, in_provisionMethod);
  }
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIfaceCallback>& in_callback) override {
    return _impl->registerCallback(in_callback);
  }
  ::ndk::ScopedAStatus reinvoke(int32_t in_persistentNetworkId, const std::vector<uint8_t>& in_peerAddress) override {
    return _impl->reinvoke(in_persistentNetworkId, in_peerAddress);
  }
  ::ndk::ScopedAStatus reject(const std::vector<uint8_t>& in_peerAddress) override {
    return _impl->reject(in_peerAddress);
  }
  ::ndk::ScopedAStatus removeBonjourService(const std::vector<uint8_t>& in_query) override {
    return _impl->removeBonjourService(in_query);
  }
  ::ndk::ScopedAStatus removeGroup(const std::string& in_groupIfName) override {
    return _impl->removeGroup(in_groupIfName);
  }
  ::ndk::ScopedAStatus removeNetwork(int32_t in_id) override {
    return _impl->removeNetwork(in_id);
  }
  ::ndk::ScopedAStatus removeUpnpService(int32_t in_version, const std::string& in_serviceName) override {
    return _impl->removeUpnpService(in_version, in_serviceName);
  }
  ::ndk::ScopedAStatus reportNfcHandoverInitiation(const std::vector<uint8_t>& in_select) override {
    return _impl->reportNfcHandoverInitiation(in_select);
  }
  ::ndk::ScopedAStatus reportNfcHandoverResponse(const std::vector<uint8_t>& in_request) override {
    return _impl->reportNfcHandoverResponse(in_request);
  }
  ::ndk::ScopedAStatus requestServiceDiscovery(const std::vector<uint8_t>& in_peerAddress, const std::vector<uint8_t>& in_query, int64_t* _aidl_return) override {
    return _impl->requestServiceDiscovery(in_peerAddress, in_query, _aidl_return);
  }
  ::ndk::ScopedAStatus saveConfig() override {
    return _impl->saveConfig();
  }
  ::ndk::ScopedAStatus setDisallowedFrequencies(const std::vector<::aidl::android::hardware::wifi::supplicant::FreqRange>& in_ranges) override {
    return _impl->setDisallowedFrequencies(in_ranges);
  }
  ::ndk::ScopedAStatus setEdmg(bool in_enable) override {
    return _impl->setEdmg(in_enable);
  }
  ::ndk::ScopedAStatus setGroupIdle(const std::string& in_groupIfName, int32_t in_timeoutInSec) override {
    return _impl->setGroupIdle(in_groupIfName, in_timeoutInSec);
  }
  ::ndk::ScopedAStatus setListenChannel(int32_t in_channel, int32_t in_operatingClass) override {
    return _impl->setListenChannel(in_channel, in_operatingClass);
  }
  ::ndk::ScopedAStatus setMacRandomization(bool in_enable) override {
    return _impl->setMacRandomization(in_enable);
  }
  ::ndk::ScopedAStatus setMiracastMode(::aidl::android::hardware::wifi::supplicant::MiracastMode in_mode) override {
    return _impl->setMiracastMode(in_mode);
  }
  ::ndk::ScopedAStatus setPowerSave(const std::string& in_groupIfName, bool in_enable) override {
    return _impl->setPowerSave(in_groupIfName, in_enable);
  }
  ::ndk::ScopedAStatus setSsidPostfix(const std::vector<uint8_t>& in_postfix) override {
    return _impl->setSsidPostfix(in_postfix);
  }
  ::ndk::ScopedAStatus setWfdDeviceInfo(const std::vector<uint8_t>& in_info) override {
    return _impl->setWfdDeviceInfo(in_info);
  }
  ::ndk::ScopedAStatus setWfdR2DeviceInfo(const std::vector<uint8_t>& in_info) override {
    return _impl->setWfdR2DeviceInfo(in_info);
  }
  ::ndk::ScopedAStatus removeClient(const std::vector<uint8_t>& in_peerAddress, bool in_isLegacyClient) override {
    return _impl->removeClient(in_peerAddress, in_isLegacyClient);
  }
  ::ndk::ScopedAStatus setWpsConfigMethods(::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods) override {
    return _impl->setWpsConfigMethods(in_configMethods);
  }
  ::ndk::ScopedAStatus setWpsDeviceName(const std::string& in_name) override {
    return _impl->setWpsDeviceName(in_name);
  }
  ::ndk::ScopedAStatus setWpsDeviceType(const std::vector<uint8_t>& in_type) override {
    return _impl->setWpsDeviceType(in_type);
  }
  ::ndk::ScopedAStatus setWpsManufacturer(const std::string& in_manufacturer) override {
    return _impl->setWpsManufacturer(in_manufacturer);
  }
  ::ndk::ScopedAStatus setWpsModelName(const std::string& in_modelName) override {
    return _impl->setWpsModelName(in_modelName);
  }
  ::ndk::ScopedAStatus setWpsModelNumber(const std::string& in_modelNumber) override {
    return _impl->setWpsModelNumber(in_modelNumber);
  }
  ::ndk::ScopedAStatus setWpsSerialNumber(const std::string& in_serialNumber) override {
    return _impl->setWpsSerialNumber(in_serialNumber);
  }
  ::ndk::ScopedAStatus startWpsPbc(const std::string& in_groupIfName, const std::vector<uint8_t>& in_bssid) override {
    return _impl->startWpsPbc(in_groupIfName, in_bssid);
  }
  ::ndk::ScopedAStatus startWpsPinDisplay(const std::string& in_groupIfName, const std::vector<uint8_t>& in_bssid, std::string* _aidl_return) override {
    return _impl->startWpsPinDisplay(in_groupIfName, in_bssid, _aidl_return);
  }
  ::ndk::ScopedAStatus startWpsPinKeypad(const std::string& in_groupIfName, const std::string& in_pin) override {
    return _impl->startWpsPinKeypad(in_groupIfName, in_pin);
  }
  ::ndk::ScopedAStatus stopFind() override {
    return _impl->stopFind();
  }
  ::ndk::ScopedAStatus findOnSocialChannels(int32_t in_timeoutInSec) override {
    return _impl->findOnSocialChannels(in_timeoutInSec);
  }
  ::ndk::ScopedAStatus findOnSpecificFrequency(int32_t in_freqInHz, int32_t in_timeoutInSec) override {
    return _impl->findOnSpecificFrequency(in_freqInHz, in_timeoutInSec);
  }
  ::ndk::ScopedAStatus setVendorElements(::aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask in_frameTypeMask, const std::vector<uint8_t>& in_vendorElemBytes) override {
    return _impl->setVendorElements(in_frameTypeMask, in_vendorElemBytes);
  }
protected:
private:
  std::shared_ptr<ISupplicantP2pIface> _impl;
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
