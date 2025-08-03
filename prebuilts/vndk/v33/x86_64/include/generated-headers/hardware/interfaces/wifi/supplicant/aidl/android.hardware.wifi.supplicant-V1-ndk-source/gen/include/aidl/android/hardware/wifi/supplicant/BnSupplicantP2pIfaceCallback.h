#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantP2pIfaceCallback.h"

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
class BnSupplicantP2pIfaceCallback : public ::ndk::BnCInterface<ISupplicantP2pIfaceCallback> {
public:
  BnSupplicantP2pIfaceCallback();
  virtual ~BnSupplicantP2pIfaceCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISupplicantP2pIfaceCallbackDelegator : public BnSupplicantP2pIfaceCallback {
public:
  explicit ISupplicantP2pIfaceCallbackDelegator(const std::shared_ptr<ISupplicantP2pIfaceCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISupplicantP2pIfaceCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onDeviceFound(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo) override {
    return _impl->onDeviceFound(in_srcAddress, in_p2pDeviceAddress, in_primaryDeviceType, in_deviceName, in_configMethods, in_deviceCapabilities, in_groupCapabilities, in_wfdDeviceInfo);
  }
  ::ndk::ScopedAStatus onDeviceLost(const std::vector<uint8_t>& in_p2pDeviceAddress) override {
    return _impl->onDeviceLost(in_p2pDeviceAddress);
  }
  ::ndk::ScopedAStatus onFindStopped() override {
    return _impl->onFindStopped();
  }
  ::ndk::ScopedAStatus onGoNegotiationCompleted(::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status) override {
    return _impl->onGoNegotiationCompleted(in_status);
  }
  ::ndk::ScopedAStatus onGoNegotiationRequest(const std::vector<uint8_t>& in_srcAddress, ::aidl::android::hardware::wifi::supplicant::WpsDevPasswordId in_passwordId) override {
    return _impl->onGoNegotiationRequest(in_srcAddress, in_passwordId);
  }
  ::ndk::ScopedAStatus onGroupFormationFailure(const std::string& in_failureReason) override {
    return _impl->onGroupFormationFailure(in_failureReason);
  }
  ::ndk::ScopedAStatus onGroupFormationSuccess() override {
    return _impl->onGroupFormationSuccess();
  }
  ::ndk::ScopedAStatus onGroupRemoved(const std::string& in_groupIfname, bool in_isGroupOwner) override {
    return _impl->onGroupRemoved(in_groupIfname, in_isGroupOwner);
  }
  ::ndk::ScopedAStatus onGroupStarted(const std::string& in_groupIfname, bool in_isGroupOwner, const std::vector<uint8_t>& in_ssid, int32_t in_frequency, const std::vector<uint8_t>& in_psk, const std::string& in_passphrase, const std::vector<uint8_t>& in_goDeviceAddress, bool in_isPersistent) override {
    return _impl->onGroupStarted(in_groupIfname, in_isGroupOwner, in_ssid, in_frequency, in_psk, in_passphrase, in_goDeviceAddress, in_isPersistent);
  }
  ::ndk::ScopedAStatus onInvitationReceived(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_goDeviceAddress, const std::vector<uint8_t>& in_bssid, int32_t in_persistentNetworkId, int32_t in_operatingFrequency) override {
    return _impl->onInvitationReceived(in_srcAddress, in_goDeviceAddress, in_bssid, in_persistentNetworkId, in_operatingFrequency);
  }
  ::ndk::ScopedAStatus onInvitationResult(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status) override {
    return _impl->onInvitationResult(in_bssid, in_status);
  }
  ::ndk::ScopedAStatus onProvisionDiscoveryCompleted(const std::vector<uint8_t>& in_p2pDeviceAddress, bool in_isRequest, ::aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode in_status, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, const std::string& in_generatedPin) override {
    return _impl->onProvisionDiscoveryCompleted(in_p2pDeviceAddress, in_isRequest, in_status, in_configMethods, in_generatedPin);
  }
  ::ndk::ScopedAStatus onR2DeviceFound(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo, const std::vector<uint8_t>& in_wfdR2DeviceInfo) override {
    return _impl->onR2DeviceFound(in_srcAddress, in_p2pDeviceAddress, in_primaryDeviceType, in_deviceName, in_configMethods, in_deviceCapabilities, in_groupCapabilities, in_wfdDeviceInfo, in_wfdR2DeviceInfo);
  }
  ::ndk::ScopedAStatus onServiceDiscoveryResponse(const std::vector<uint8_t>& in_srcAddress, char16_t in_updateIndicator, const std::vector<uint8_t>& in_tlvs) override {
    return _impl->onServiceDiscoveryResponse(in_srcAddress, in_updateIndicator, in_tlvs);
  }
  ::ndk::ScopedAStatus onStaAuthorized(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress) override {
    return _impl->onStaAuthorized(in_srcAddress, in_p2pDeviceAddress);
  }
  ::ndk::ScopedAStatus onStaDeauthorized(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress) override {
    return _impl->onStaDeauthorized(in_srcAddress, in_p2pDeviceAddress);
  }
  ::ndk::ScopedAStatus onGroupFrequencyChanged(const std::string& in_groupIfname, int32_t in_frequency) override {
    return _impl->onGroupFrequencyChanged(in_groupIfname, in_frequency);
  }
  ::ndk::ScopedAStatus onDeviceFoundWithVendorElements(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo, const std::vector<uint8_t>& in_wfdR2DeviceInfo, const std::vector<uint8_t>& in_vendorElemBytes) override {
    return _impl->onDeviceFoundWithVendorElements(in_srcAddress, in_p2pDeviceAddress, in_primaryDeviceType, in_deviceName, in_configMethods, in_deviceCapabilities, in_groupCapabilities, in_wfdDeviceInfo, in_wfdR2DeviceInfo, in_vendorElemBytes);
  }
protected:
private:
  std::shared_ptr<ISupplicantP2pIfaceCallback> _impl;
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
