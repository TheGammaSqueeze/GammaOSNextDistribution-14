#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantStaIface.h"

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
class BnSupplicantStaIface : public ::ndk::BnCInterface<ISupplicantStaIface> {
public:
  BnSupplicantStaIface();
  virtual ~BnSupplicantStaIface();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISupplicantStaIfaceDelegator : public BnSupplicantStaIface {
public:
  explicit ISupplicantStaIfaceDelegator(const std::shared_ptr<ISupplicantStaIface> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISupplicantStaIface::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus addDppPeerUri(const std::string& in_uri, int32_t* _aidl_return) override {
    return _impl->addDppPeerUri(in_uri, _aidl_return);
  }
  ::ndk::ScopedAStatus addExtRadioWork(const std::string& in_name, int32_t in_freqInMhz, int32_t in_timeoutInSec, int32_t* _aidl_return) override {
    return _impl->addExtRadioWork(in_name, in_freqInMhz, in_timeoutInSec, _aidl_return);
  }
  ::ndk::ScopedAStatus addNetwork(std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaNetwork>* _aidl_return) override {
    return _impl->addNetwork(_aidl_return);
  }
  ::ndk::ScopedAStatus addRxFilter(::aidl::android::hardware::wifi::supplicant::RxFilterType in_type) override {
    return _impl->addRxFilter(in_type);
  }
  ::ndk::ScopedAStatus cancelWps() override {
    return _impl->cancelWps();
  }
  ::ndk::ScopedAStatus disconnect() override {
    return _impl->disconnect();
  }
  ::ndk::ScopedAStatus enableAutoReconnect(bool in_enable) override {
    return _impl->enableAutoReconnect(in_enable);
  }
  ::ndk::ScopedAStatus filsHlpAddRequest(const std::vector<uint8_t>& in_dst_mac, const std::vector<uint8_t>& in_pkt) override {
    return _impl->filsHlpAddRequest(in_dst_mac, in_pkt);
  }
  ::ndk::ScopedAStatus filsHlpFlushRequest() override {
    return _impl->filsHlpFlushRequest();
  }
  ::ndk::ScopedAStatus generateDppBootstrapInfoForResponder(const std::vector<uint8_t>& in_macAddress, const std::string& in_deviceInfo, ::aidl::android::hardware::wifi::supplicant::DppCurve in_curve, ::aidl::android::hardware::wifi::supplicant::DppResponderBootstrapInfo* _aidl_return) override {
    return _impl->generateDppBootstrapInfoForResponder(in_macAddress, in_deviceInfo, in_curve, _aidl_return);
  }
  ::ndk::ScopedAStatus generateSelfDppConfiguration(const std::string& in_ssid, const std::vector<uint8_t>& in_privEcKey) override {
    return _impl->generateSelfDppConfiguration(in_ssid, in_privEcKey);
  }
  ::ndk::ScopedAStatus getConnectionCapabilities(::aidl::android::hardware::wifi::supplicant::ConnectionCapabilities* _aidl_return) override {
    return _impl->getConnectionCapabilities(_aidl_return);
  }
  ::ndk::ScopedAStatus getConnectionMloLinksInfo(::aidl::android::hardware::wifi::supplicant::MloLinksInfo* _aidl_return) override {
    return _impl->getConnectionMloLinksInfo(_aidl_return);
  }
  ::ndk::ScopedAStatus getKeyMgmtCapabilities(::aidl::android::hardware::wifi::supplicant::KeyMgmtMask* _aidl_return) override {
    return _impl->getKeyMgmtCapabilities(_aidl_return);
  }
  ::ndk::ScopedAStatus getMacAddress(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getMacAddress(_aidl_return);
  }
  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override {
    return _impl->getName(_aidl_return);
  }
  ::ndk::ScopedAStatus getNetwork(int32_t in_id, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaNetwork>* _aidl_return) override {
    return _impl->getNetwork(in_id, _aidl_return);
  }
  ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) override {
    return _impl->getType(_aidl_return);
  }
  ::ndk::ScopedAStatus getWpaDriverCapabilities(::aidl::android::hardware::wifi::supplicant::WpaDriverCapabilitiesMask* _aidl_return) override {
    return _impl->getWpaDriverCapabilities(_aidl_return);
  }
  ::ndk::ScopedAStatus initiateAnqpQuery(const std::vector<uint8_t>& in_macAddress, const std::vector<::aidl::android::hardware::wifi::supplicant::AnqpInfoId>& in_infoElements, const std::vector<::aidl::android::hardware::wifi::supplicant::Hs20AnqpSubtypes>& in_subTypes) override {
    return _impl->initiateAnqpQuery(in_macAddress, in_infoElements, in_subTypes);
  }
  ::ndk::ScopedAStatus initiateHs20IconQuery(const std::vector<uint8_t>& in_macAddress, const std::string& in_fileName) override {
    return _impl->initiateHs20IconQuery(in_macAddress, in_fileName);
  }
  ::ndk::ScopedAStatus initiateTdlsDiscover(const std::vector<uint8_t>& in_macAddress) override {
    return _impl->initiateTdlsDiscover(in_macAddress);
  }
  ::ndk::ScopedAStatus initiateTdlsSetup(const std::vector<uint8_t>& in_macAddress) override {
    return _impl->initiateTdlsSetup(in_macAddress);
  }
  ::ndk::ScopedAStatus initiateTdlsTeardown(const std::vector<uint8_t>& in_macAddress) override {
    return _impl->initiateTdlsTeardown(in_macAddress);
  }
  ::ndk::ScopedAStatus initiateVenueUrlAnqpQuery(const std::vector<uint8_t>& in_macAddress) override {
    return _impl->initiateVenueUrlAnqpQuery(in_macAddress);
  }
  ::ndk::ScopedAStatus listNetworks(std::vector<int32_t>* _aidl_return) override {
    return _impl->listNetworks(_aidl_return);
  }
  ::ndk::ScopedAStatus reassociate() override {
    return _impl->reassociate();
  }
  ::ndk::ScopedAStatus reconnect() override {
    return _impl->reconnect();
  }
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIfaceCallback>& in_callback) override {
    return _impl->registerCallback(in_callback);
  }
  ::ndk::ScopedAStatus setQosPolicyFeatureEnabled(bool in_enable) override {
    return _impl->setQosPolicyFeatureEnabled(in_enable);
  }
  ::ndk::ScopedAStatus sendQosPolicyResponse(int32_t in_qosPolicyRequestId, bool in_morePolicies, const std::vector<::aidl::android::hardware::wifi::supplicant::QosPolicyStatus>& in_qosPolicyStatusList) override {
    return _impl->sendQosPolicyResponse(in_qosPolicyRequestId, in_morePolicies, in_qosPolicyStatusList);
  }
  ::ndk::ScopedAStatus removeAllQosPolicies() override {
    return _impl->removeAllQosPolicies();
  }
  ::ndk::ScopedAStatus removeDppUri(int32_t in_id) override {
    return _impl->removeDppUri(in_id);
  }
  ::ndk::ScopedAStatus removeExtRadioWork(int32_t in_id) override {
    return _impl->removeExtRadioWork(in_id);
  }
  ::ndk::ScopedAStatus removeNetwork(int32_t in_id) override {
    return _impl->removeNetwork(in_id);
  }
  ::ndk::ScopedAStatus removeRxFilter(::aidl::android::hardware::wifi::supplicant::RxFilterType in_type) override {
    return _impl->removeRxFilter(in_type);
  }
  ::ndk::ScopedAStatus setBtCoexistenceMode(::aidl::android::hardware::wifi::supplicant::BtCoexistenceMode in_mode) override {
    return _impl->setBtCoexistenceMode(in_mode);
  }
  ::ndk::ScopedAStatus setBtCoexistenceScanModeEnabled(bool in_enable) override {
    return _impl->setBtCoexistenceScanModeEnabled(in_enable);
  }
  ::ndk::ScopedAStatus setCountryCode(const std::vector<uint8_t>& in_code) override {
    return _impl->setCountryCode(in_code);
  }
  ::ndk::ScopedAStatus setExternalSim(bool in_useExternalSim) override {
    return _impl->setExternalSim(in_useExternalSim);
  }
  ::ndk::ScopedAStatus setMboCellularDataStatus(bool in_available) override {
    return _impl->setMboCellularDataStatus(in_available);
  }
  ::ndk::ScopedAStatus setPowerSave(bool in_enable) override {
    return _impl->setPowerSave(in_enable);
  }
  ::ndk::ScopedAStatus setSuspendModeEnabled(bool in_enable) override {
    return _impl->setSuspendModeEnabled(in_enable);
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
  ::ndk::ScopedAStatus startDppConfiguratorInitiator(int32_t in_peerBootstrapId, int32_t in_ownBootstrapId, const std::string& in_ssid, const std::string& in_password, const std::string& in_psk, ::aidl::android::hardware::wifi::supplicant::DppNetRole in_netRole, ::aidl::android::hardware::wifi::supplicant::DppAkm in_securityAkm, const std::vector<uint8_t>& in_privEcKey, std::vector<uint8_t>* _aidl_return) override {
    return _impl->startDppConfiguratorInitiator(in_peerBootstrapId, in_ownBootstrapId, in_ssid, in_password, in_psk, in_netRole, in_securityAkm, in_privEcKey, _aidl_return);
  }
  ::ndk::ScopedAStatus startDppEnrolleeInitiator(int32_t in_peerBootstrapId, int32_t in_ownBootstrapId) override {
    return _impl->startDppEnrolleeInitiator(in_peerBootstrapId, in_ownBootstrapId);
  }
  ::ndk::ScopedAStatus startDppEnrolleeResponder(int32_t in_listenChannel) override {
    return _impl->startDppEnrolleeResponder(in_listenChannel);
  }
  ::ndk::ScopedAStatus startRxFilter() override {
    return _impl->startRxFilter();
  }
  ::ndk::ScopedAStatus startWpsPbc(const std::vector<uint8_t>& in_bssid) override {
    return _impl->startWpsPbc(in_bssid);
  }
  ::ndk::ScopedAStatus startWpsPinDisplay(const std::vector<uint8_t>& in_bssid, std::string* _aidl_return) override {
    return _impl->startWpsPinDisplay(in_bssid, _aidl_return);
  }
  ::ndk::ScopedAStatus startWpsPinKeypad(const std::string& in_pin) override {
    return _impl->startWpsPinKeypad(in_pin);
  }
  ::ndk::ScopedAStatus startWpsRegistrar(const std::vector<uint8_t>& in_bssid, const std::string& in_pin) override {
    return _impl->startWpsRegistrar(in_bssid, in_pin);
  }
  ::ndk::ScopedAStatus stopDppInitiator() override {
    return _impl->stopDppInitiator();
  }
  ::ndk::ScopedAStatus stopDppResponder(int32_t in_ownBootstrapId) override {
    return _impl->stopDppResponder(in_ownBootstrapId);
  }
  ::ndk::ScopedAStatus stopRxFilter() override {
    return _impl->stopRxFilter();
  }
protected:
private:
  std::shared_ptr<ISupplicantStaIface> _impl;
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
