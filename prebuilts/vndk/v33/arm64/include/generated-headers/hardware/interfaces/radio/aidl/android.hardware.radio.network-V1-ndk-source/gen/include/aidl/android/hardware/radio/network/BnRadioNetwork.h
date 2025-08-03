#pragma once

#include "aidl/android/hardware/radio/network/IRadioNetwork.h"

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
namespace radio {
namespace network {
class BnRadioNetwork : public ::ndk::BnCInterface<IRadioNetwork> {
public:
  BnRadioNetwork();
  virtual ~BnRadioNetwork();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioNetworkDelegator : public BnRadioNetwork {
public:
  explicit IRadioNetworkDelegator(const std::shared_ptr<IRadioNetwork> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioNetwork::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getAllowedNetworkTypesBitmap(int32_t in_serial) override {
    return _impl->getAllowedNetworkTypesBitmap(in_serial);
  }
  ::ndk::ScopedAStatus getAvailableBandModes(int32_t in_serial) override {
    return _impl->getAvailableBandModes(in_serial);
  }
  ::ndk::ScopedAStatus getAvailableNetworks(int32_t in_serial) override {
    return _impl->getAvailableNetworks(in_serial);
  }
  ::ndk::ScopedAStatus getBarringInfo(int32_t in_serial) override {
    return _impl->getBarringInfo(in_serial);
  }
  ::ndk::ScopedAStatus getCdmaRoamingPreference(int32_t in_serial) override {
    return _impl->getCdmaRoamingPreference(in_serial);
  }
  ::ndk::ScopedAStatus getCellInfoList(int32_t in_serial) override {
    return _impl->getCellInfoList(in_serial);
  }
  ::ndk::ScopedAStatus getDataRegistrationState(int32_t in_serial) override {
    return _impl->getDataRegistrationState(in_serial);
  }
  ::ndk::ScopedAStatus getImsRegistrationState(int32_t in_serial) override {
    return _impl->getImsRegistrationState(in_serial);
  }
  ::ndk::ScopedAStatus getNetworkSelectionMode(int32_t in_serial) override {
    return _impl->getNetworkSelectionMode(in_serial);
  }
  ::ndk::ScopedAStatus getOperator(int32_t in_serial) override {
    return _impl->getOperator(in_serial);
  }
  ::ndk::ScopedAStatus getSignalStrength(int32_t in_serial) override {
    return _impl->getSignalStrength(in_serial);
  }
  ::ndk::ScopedAStatus getSystemSelectionChannels(int32_t in_serial) override {
    return _impl->getSystemSelectionChannels(in_serial);
  }
  ::ndk::ScopedAStatus getVoiceRadioTechnology(int32_t in_serial) override {
    return _impl->getVoiceRadioTechnology(in_serial);
  }
  ::ndk::ScopedAStatus getVoiceRegistrationState(int32_t in_serial) override {
    return _impl->getVoiceRegistrationState(in_serial);
  }
  ::ndk::ScopedAStatus isNrDualConnectivityEnabled(int32_t in_serial) override {
    return _impl->isNrDualConnectivityEnabled(in_serial);
  }
  ::ndk::ScopedAStatus responseAcknowledgement() override {
    return _impl->responseAcknowledgement();
  }
  ::ndk::ScopedAStatus setAllowedNetworkTypesBitmap(int32_t in_serial, int32_t in_networkTypeBitmap) override {
    return _impl->setAllowedNetworkTypesBitmap(in_serial, in_networkTypeBitmap);
  }
  ::ndk::ScopedAStatus setBandMode(int32_t in_serial, ::aidl::android::hardware::radio::network::RadioBandMode in_mode) override {
    return _impl->setBandMode(in_serial, in_mode);
  }
  ::ndk::ScopedAStatus setBarringPassword(int32_t in_serial, const std::string& in_facility, const std::string& in_oldPassword, const std::string& in_newPassword) override {
    return _impl->setBarringPassword(in_serial, in_facility, in_oldPassword, in_newPassword);
  }
  ::ndk::ScopedAStatus setCdmaRoamingPreference(int32_t in_serial, ::aidl::android::hardware::radio::network::CdmaRoamingType in_type) override {
    return _impl->setCdmaRoamingPreference(in_serial, in_type);
  }
  ::ndk::ScopedAStatus setCellInfoListRate(int32_t in_serial, int32_t in_rate) override {
    return _impl->setCellInfoListRate(in_serial, in_rate);
  }
  ::ndk::ScopedAStatus setIndicationFilter(int32_t in_serial, int32_t in_indicationFilter) override {
    return _impl->setIndicationFilter(in_serial, in_indicationFilter);
  }
  ::ndk::ScopedAStatus setLinkCapacityReportingCriteria(int32_t in_serial, int32_t in_hysteresisMs, int32_t in_hysteresisDlKbps, int32_t in_hysteresisUlKbps, const std::vector<int32_t>& in_thresholdsDownlinkKbps, const std::vector<int32_t>& in_thresholdsUplinkKbps, ::aidl::android::hardware::radio::AccessNetwork in_accessNetwork) override {
    return _impl->setLinkCapacityReportingCriteria(in_serial, in_hysteresisMs, in_hysteresisDlKbps, in_hysteresisUlKbps, in_thresholdsDownlinkKbps, in_thresholdsUplinkKbps, in_accessNetwork);
  }
  ::ndk::ScopedAStatus setLocationUpdates(int32_t in_serial, bool in_enable) override {
    return _impl->setLocationUpdates(in_serial, in_enable);
  }
  ::ndk::ScopedAStatus setNetworkSelectionModeAutomatic(int32_t in_serial) override {
    return _impl->setNetworkSelectionModeAutomatic(in_serial);
  }
  ::ndk::ScopedAStatus setNetworkSelectionModeManual(int32_t in_serial, const std::string& in_operatorNumeric, ::aidl::android::hardware::radio::AccessNetwork in_ran) override {
    return _impl->setNetworkSelectionModeManual(in_serial, in_operatorNumeric, in_ran);
  }
  ::ndk::ScopedAStatus setNrDualConnectivityState(int32_t in_serial, ::aidl::android::hardware::radio::network::NrDualConnectivityState in_nrDualConnectivityState) override {
    return _impl->setNrDualConnectivityState(in_serial, in_nrDualConnectivityState);
  }
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::network::IRadioNetworkResponse>& in_radioNetworkResponse, const std::shared_ptr<::aidl::android::hardware::radio::network::IRadioNetworkIndication>& in_radioNetworkIndication) override {
    return _impl->setResponseFunctions(in_radioNetworkResponse, in_radioNetworkIndication);
  }
  ::ndk::ScopedAStatus setSignalStrengthReportingCriteria(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::network::SignalThresholdInfo>& in_signalThresholdInfos) override {
    return _impl->setSignalStrengthReportingCriteria(in_serial, in_signalThresholdInfos);
  }
  ::ndk::ScopedAStatus setSuppServiceNotifications(int32_t in_serial, bool in_enable) override {
    return _impl->setSuppServiceNotifications(in_serial, in_enable);
  }
  ::ndk::ScopedAStatus setSystemSelectionChannels(int32_t in_serial, bool in_specifyChannels, const std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier>& in_specifiers) override {
    return _impl->setSystemSelectionChannels(in_serial, in_specifyChannels, in_specifiers);
  }
  ::ndk::ScopedAStatus startNetworkScan(int32_t in_serial, const ::aidl::android::hardware::radio::network::NetworkScanRequest& in_request) override {
    return _impl->startNetworkScan(in_serial, in_request);
  }
  ::ndk::ScopedAStatus stopNetworkScan(int32_t in_serial) override {
    return _impl->stopNetworkScan(in_serial);
  }
  ::ndk::ScopedAStatus supplyNetworkDepersonalization(int32_t in_serial, const std::string& in_netPin) override {
    return _impl->supplyNetworkDepersonalization(in_serial, in_netPin);
  }
  ::ndk::ScopedAStatus setUsageSetting(int32_t in_serial, ::aidl::android::hardware::radio::network::UsageSetting in_usageSetting) override {
    return _impl->setUsageSetting(in_serial, in_usageSetting);
  }
  ::ndk::ScopedAStatus getUsageSetting(int32_t in_serial) override {
    return _impl->getUsageSetting(in_serial);
  }
protected:
private:
  std::shared_ptr<IRadioNetwork> _impl;
};

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
