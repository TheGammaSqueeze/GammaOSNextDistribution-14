#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantP2pIfaceCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BpSupplicantP2pIfaceCallback : public ::ndk::BpCInterface<ISupplicantP2pIfaceCallback> {
public:
  explicit BpSupplicantP2pIfaceCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpSupplicantP2pIfaceCallback();

  ::ndk::ScopedAStatus onDeviceFound(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo) override;
  ::ndk::ScopedAStatus onDeviceLost(const std::vector<uint8_t>& in_p2pDeviceAddress) override;
  ::ndk::ScopedAStatus onFindStopped() override;
  ::ndk::ScopedAStatus onGoNegotiationCompleted(::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status) override;
  ::ndk::ScopedAStatus onGoNegotiationRequest(const std::vector<uint8_t>& in_srcAddress, ::aidl::android::hardware::wifi::supplicant::WpsDevPasswordId in_passwordId) override;
  ::ndk::ScopedAStatus onGroupFormationFailure(const std::string& in_failureReason) override;
  ::ndk::ScopedAStatus onGroupFormationSuccess() override;
  ::ndk::ScopedAStatus onGroupRemoved(const std::string& in_groupIfname, bool in_isGroupOwner) override;
  ::ndk::ScopedAStatus onGroupStarted(const std::string& in_groupIfname, bool in_isGroupOwner, const std::vector<uint8_t>& in_ssid, int32_t in_frequency, const std::vector<uint8_t>& in_psk, const std::string& in_passphrase, const std::vector<uint8_t>& in_goDeviceAddress, bool in_isPersistent) override;
  ::ndk::ScopedAStatus onInvitationReceived(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_goDeviceAddress, const std::vector<uint8_t>& in_bssid, int32_t in_persistentNetworkId, int32_t in_operatingFrequency) override;
  ::ndk::ScopedAStatus onInvitationResult(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status) override;
  ::ndk::ScopedAStatus onProvisionDiscoveryCompleted(const std::vector<uint8_t>& in_p2pDeviceAddress, bool in_isRequest, ::aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode in_status, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, const std::string& in_generatedPin) override;
  ::ndk::ScopedAStatus onR2DeviceFound(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo, const std::vector<uint8_t>& in_wfdR2DeviceInfo) override;
  ::ndk::ScopedAStatus onServiceDiscoveryResponse(const std::vector<uint8_t>& in_srcAddress, char16_t in_updateIndicator, const std::vector<uint8_t>& in_tlvs) override;
  ::ndk::ScopedAStatus onStaAuthorized(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress) override;
  ::ndk::ScopedAStatus onStaDeauthorized(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress) override;
  ::ndk::ScopedAStatus onGroupFrequencyChanged(const std::string& in_groupIfname, int32_t in_frequency) override;
  ::ndk::ScopedAStatus onDeviceFoundWithVendorElements(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo, const std::vector<uint8_t>& in_wfdR2DeviceInfo, const std::vector<uint8_t>& in_vendorElemBytes) override;
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
