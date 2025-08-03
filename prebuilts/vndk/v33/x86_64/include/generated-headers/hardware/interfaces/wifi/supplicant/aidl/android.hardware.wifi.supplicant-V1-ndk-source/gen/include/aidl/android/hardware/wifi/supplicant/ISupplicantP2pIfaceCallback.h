#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/supplicant/P2pGroupCapabilityMask.h>
#include <aidl/android/hardware/wifi/supplicant/P2pProvDiscStatusCode.h>
#include <aidl/android/hardware/wifi/supplicant/P2pStatusCode.h>
#include <aidl/android/hardware/wifi/supplicant/WpsConfigMethods.h>
#include <aidl/android/hardware/wifi/supplicant/WpsDevPasswordId.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class ISupplicantP2pIfaceCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISupplicantP2pIfaceCallback();
  virtual ~ISupplicantP2pIfaceCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "5b8bcab6b43177dffdec5873e84205b04757cc9d";
  static constexpr uint32_t TRANSACTION_onDeviceFound = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onDeviceLost = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_onFindStopped = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_onGoNegotiationCompleted = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_onGoNegotiationRequest = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_onGroupFormationFailure = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_onGroupFormationSuccess = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_onGroupRemoved = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_onGroupStarted = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_onInvitationReceived = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_onInvitationResult = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_onProvisionDiscoveryCompleted = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_onR2DeviceFound = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_onServiceDiscoveryResponse = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_onStaAuthorized = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_onStaDeauthorized = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_onGroupFrequencyChanged = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_onDeviceFoundWithVendorElements = FIRST_CALL_TRANSACTION + 17;

  static std::shared_ptr<ISupplicantP2pIfaceCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantP2pIfaceCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantP2pIfaceCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISupplicantP2pIfaceCallback>& impl);
  static const std::shared_ptr<ISupplicantP2pIfaceCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onDeviceFound(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo) = 0;
  virtual ::ndk::ScopedAStatus onDeviceLost(const std::vector<uint8_t>& in_p2pDeviceAddress) = 0;
  virtual ::ndk::ScopedAStatus onFindStopped() = 0;
  virtual ::ndk::ScopedAStatus onGoNegotiationCompleted(::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status) = 0;
  virtual ::ndk::ScopedAStatus onGoNegotiationRequest(const std::vector<uint8_t>& in_srcAddress, ::aidl::android::hardware::wifi::supplicant::WpsDevPasswordId in_passwordId) = 0;
  virtual ::ndk::ScopedAStatus onGroupFormationFailure(const std::string& in_failureReason) = 0;
  virtual ::ndk::ScopedAStatus onGroupFormationSuccess() = 0;
  virtual ::ndk::ScopedAStatus onGroupRemoved(const std::string& in_groupIfname, bool in_isGroupOwner) = 0;
  virtual ::ndk::ScopedAStatus onGroupStarted(const std::string& in_groupIfname, bool in_isGroupOwner, const std::vector<uint8_t>& in_ssid, int32_t in_frequency, const std::vector<uint8_t>& in_psk, const std::string& in_passphrase, const std::vector<uint8_t>& in_goDeviceAddress, bool in_isPersistent) = 0;
  virtual ::ndk::ScopedAStatus onInvitationReceived(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_goDeviceAddress, const std::vector<uint8_t>& in_bssid, int32_t in_persistentNetworkId, int32_t in_operatingFrequency) = 0;
  virtual ::ndk::ScopedAStatus onInvitationResult(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status) = 0;
  virtual ::ndk::ScopedAStatus onProvisionDiscoveryCompleted(const std::vector<uint8_t>& in_p2pDeviceAddress, bool in_isRequest, ::aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode in_status, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, const std::string& in_generatedPin) = 0;
  virtual ::ndk::ScopedAStatus onR2DeviceFound(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo, const std::vector<uint8_t>& in_wfdR2DeviceInfo) = 0;
  virtual ::ndk::ScopedAStatus onServiceDiscoveryResponse(const std::vector<uint8_t>& in_srcAddress, char16_t in_updateIndicator, const std::vector<uint8_t>& in_tlvs) = 0;
  virtual ::ndk::ScopedAStatus onStaAuthorized(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress) = 0;
  virtual ::ndk::ScopedAStatus onStaDeauthorized(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress) = 0;
  virtual ::ndk::ScopedAStatus onGroupFrequencyChanged(const std::string& in_groupIfname, int32_t in_frequency) = 0;
  virtual ::ndk::ScopedAStatus onDeviceFoundWithVendorElements(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo, const std::vector<uint8_t>& in_wfdR2DeviceInfo, const std::vector<uint8_t>& in_vendorElemBytes) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISupplicantP2pIfaceCallback> default_impl;
};
class ISupplicantP2pIfaceCallbackDefault : public ISupplicantP2pIfaceCallback {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
