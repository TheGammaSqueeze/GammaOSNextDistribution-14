#include "aidl/android/hardware/wifi/supplicant/ISupplicantP2pIfaceCallback.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/hardware/wifi/supplicant/BnSupplicantP2pIfaceCallback.h>
#include <aidl/android/hardware/wifi/supplicant/BpSupplicantP2pIfaceCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
static binder_status_t _aidl_android_hardware_wifi_supplicant_ISupplicantP2pIfaceCallback_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnSupplicantP2pIfaceCallback> _aidl_impl = std::static_pointer_cast<BnSupplicantP2pIfaceCallback>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*onDeviceFound*/): {
      std::vector<uint8_t> in_srcAddress;
      std::vector<uint8_t> in_p2pDeviceAddress;
      std::vector<uint8_t> in_primaryDeviceType;
      std::string in_deviceName;
      ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods;
      int8_t in_deviceCapabilities;
      ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities;
      std::vector<uint8_t> in_wfdDeviceInfo;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_srcAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_p2pDeviceAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_primaryDeviceType);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_deviceName);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_configMethods);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_deviceCapabilities);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_groupCapabilities);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_wfdDeviceInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDeviceFound(in_srcAddress, in_p2pDeviceAddress, in_primaryDeviceType, in_deviceName, in_configMethods, in_deviceCapabilities, in_groupCapabilities, in_wfdDeviceInfo);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 1 /*onDeviceLost*/): {
      std::vector<uint8_t> in_p2pDeviceAddress;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_p2pDeviceAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDeviceLost(in_p2pDeviceAddress);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 2 /*onFindStopped*/): {

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onFindStopped();
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 3 /*onGoNegotiationCompleted*/): {
      ::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_status);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onGoNegotiationCompleted(in_status);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 4 /*onGoNegotiationRequest*/): {
      std::vector<uint8_t> in_srcAddress;
      ::aidl::android::hardware::wifi::supplicant::WpsDevPasswordId in_passwordId;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_srcAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_passwordId);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onGoNegotiationRequest(in_srcAddress, in_passwordId);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 5 /*onGroupFormationFailure*/): {
      std::string in_failureReason;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_failureReason);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onGroupFormationFailure(in_failureReason);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 6 /*onGroupFormationSuccess*/): {

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onGroupFormationSuccess();
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 7 /*onGroupRemoved*/): {
      std::string in_groupIfname;
      bool in_isGroupOwner;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_groupIfname);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_isGroupOwner);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onGroupRemoved(in_groupIfname, in_isGroupOwner);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 8 /*onGroupStarted*/): {
      std::string in_groupIfname;
      bool in_isGroupOwner;
      std::vector<uint8_t> in_ssid;
      int32_t in_frequency;
      std::vector<uint8_t> in_psk;
      std::string in_passphrase;
      std::vector<uint8_t> in_goDeviceAddress;
      bool in_isPersistent;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_groupIfname);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_isGroupOwner);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_ssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_frequency);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_psk);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_passphrase);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_goDeviceAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_isPersistent);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onGroupStarted(in_groupIfname, in_isGroupOwner, in_ssid, in_frequency, in_psk, in_passphrase, in_goDeviceAddress, in_isPersistent);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 9 /*onInvitationReceived*/): {
      std::vector<uint8_t> in_srcAddress;
      std::vector<uint8_t> in_goDeviceAddress;
      std::vector<uint8_t> in_bssid;
      int32_t in_persistentNetworkId;
      int32_t in_operatingFrequency;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_srcAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_goDeviceAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_persistentNetworkId);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_operatingFrequency);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onInvitationReceived(in_srcAddress, in_goDeviceAddress, in_bssid, in_persistentNetworkId, in_operatingFrequency);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 10 /*onInvitationResult*/): {
      std::vector<uint8_t> in_bssid;
      ::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_status);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onInvitationResult(in_bssid, in_status);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 11 /*onProvisionDiscoveryCompleted*/): {
      std::vector<uint8_t> in_p2pDeviceAddress;
      bool in_isRequest;
      ::aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode in_status;
      ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods;
      std::string in_generatedPin;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_p2pDeviceAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_isRequest);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_status);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_configMethods);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_generatedPin);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onProvisionDiscoveryCompleted(in_p2pDeviceAddress, in_isRequest, in_status, in_configMethods, in_generatedPin);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 12 /*onR2DeviceFound*/): {
      std::vector<uint8_t> in_srcAddress;
      std::vector<uint8_t> in_p2pDeviceAddress;
      std::vector<uint8_t> in_primaryDeviceType;
      std::string in_deviceName;
      ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods;
      int8_t in_deviceCapabilities;
      ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities;
      std::vector<uint8_t> in_wfdDeviceInfo;
      std::vector<uint8_t> in_wfdR2DeviceInfo;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_srcAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_p2pDeviceAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_primaryDeviceType);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_deviceName);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_configMethods);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_deviceCapabilities);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_groupCapabilities);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_wfdDeviceInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_wfdR2DeviceInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onR2DeviceFound(in_srcAddress, in_p2pDeviceAddress, in_primaryDeviceType, in_deviceName, in_configMethods, in_deviceCapabilities, in_groupCapabilities, in_wfdDeviceInfo, in_wfdR2DeviceInfo);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 13 /*onServiceDiscoveryResponse*/): {
      std::vector<uint8_t> in_srcAddress;
      char16_t in_updateIndicator;
      std::vector<uint8_t> in_tlvs;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_srcAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_updateIndicator);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_tlvs);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onServiceDiscoveryResponse(in_srcAddress, in_updateIndicator, in_tlvs);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 14 /*onStaAuthorized*/): {
      std::vector<uint8_t> in_srcAddress;
      std::vector<uint8_t> in_p2pDeviceAddress;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_srcAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_p2pDeviceAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onStaAuthorized(in_srcAddress, in_p2pDeviceAddress);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 15 /*onStaDeauthorized*/): {
      std::vector<uint8_t> in_srcAddress;
      std::vector<uint8_t> in_p2pDeviceAddress;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_srcAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_p2pDeviceAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onStaDeauthorized(in_srcAddress, in_p2pDeviceAddress);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 16 /*onGroupFrequencyChanged*/): {
      std::string in_groupIfname;
      int32_t in_frequency;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_groupIfname);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_frequency);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onGroupFrequencyChanged(in_groupIfname, in_frequency);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 17 /*onDeviceFoundWithVendorElements*/): {
      std::vector<uint8_t> in_srcAddress;
      std::vector<uint8_t> in_p2pDeviceAddress;
      std::vector<uint8_t> in_primaryDeviceType;
      std::string in_deviceName;
      ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods;
      int8_t in_deviceCapabilities;
      ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities;
      std::vector<uint8_t> in_wfdDeviceInfo;
      std::vector<uint8_t> in_wfdR2DeviceInfo;
      std::vector<uint8_t> in_vendorElemBytes;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_srcAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_p2pDeviceAddress);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_primaryDeviceType);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_deviceName);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_configMethods);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_deviceCapabilities);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_groupCapabilities);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_wfdDeviceInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_wfdR2DeviceInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_vendorElemBytes);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDeviceFoundWithVendorElements(in_srcAddress, in_p2pDeviceAddress, in_primaryDeviceType, in_deviceName, in_configMethods, in_deviceCapabilities, in_groupCapabilities, in_wfdDeviceInfo, in_wfdR2DeviceInfo, in_vendorElemBytes);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777214 /*getInterfaceVersion*/): {
      int32_t _aidl_return;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceVersion(&_aidl_return);
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
    case (FIRST_CALL_TRANSACTION + 16777213 /*getInterfaceHash*/): {
      std::string _aidl_return;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getInterfaceHash(&_aidl_return);
      _aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());
      if (_aidl_ret_status != STATUS_OK) break;

      if (!AStatus_isOk(_aidl_status.get())) break;

      _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_out, _aidl_return);
      if (_aidl_ret_status != STATUS_OK) break;

      break;
    }
  }
  return _aidl_ret_status;
}

static AIBinder_Class* _g_aidl_android_hardware_wifi_supplicant_ISupplicantP2pIfaceCallback_clazz = ::ndk::ICInterface::defineClass(ISupplicantP2pIfaceCallback::descriptor, _aidl_android_hardware_wifi_supplicant_ISupplicantP2pIfaceCallback_onTransact);

BpSupplicantP2pIfaceCallback::BpSupplicantP2pIfaceCallback(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpSupplicantP2pIfaceCallback::~BpSupplicantP2pIfaceCallback() {}

::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onDeviceFound(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_srcAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_p2pDeviceAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_primaryDeviceType);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_deviceName);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_configMethods);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_deviceCapabilities);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_groupCapabilities);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_wfdDeviceInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*onDeviceFound*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onDeviceFound(in_srcAddress, in_p2pDeviceAddress, in_primaryDeviceType, in_deviceName, in_configMethods, in_deviceCapabilities, in_groupCapabilities, in_wfdDeviceInfo);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onDeviceLost(const std::vector<uint8_t>& in_p2pDeviceAddress) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_p2pDeviceAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 1 /*onDeviceLost*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onDeviceLost(in_p2pDeviceAddress);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onFindStopped() {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 2 /*onFindStopped*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onFindStopped();
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onGoNegotiationCompleted(::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_status);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 3 /*onGoNegotiationCompleted*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onGoNegotiationCompleted(in_status);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onGoNegotiationRequest(const std::vector<uint8_t>& in_srcAddress, ::aidl::android::hardware::wifi::supplicant::WpsDevPasswordId in_passwordId) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_srcAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_passwordId);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 4 /*onGoNegotiationRequest*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onGoNegotiationRequest(in_srcAddress, in_passwordId);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onGroupFormationFailure(const std::string& in_failureReason) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_failureReason);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 5 /*onGroupFormationFailure*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onGroupFormationFailure(in_failureReason);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onGroupFormationSuccess() {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 6 /*onGroupFormationSuccess*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onGroupFormationSuccess();
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onGroupRemoved(const std::string& in_groupIfname, bool in_isGroupOwner) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_groupIfname);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_isGroupOwner);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 7 /*onGroupRemoved*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onGroupRemoved(in_groupIfname, in_isGroupOwner);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onGroupStarted(const std::string& in_groupIfname, bool in_isGroupOwner, const std::vector<uint8_t>& in_ssid, int32_t in_frequency, const std::vector<uint8_t>& in_psk, const std::string& in_passphrase, const std::vector<uint8_t>& in_goDeviceAddress, bool in_isPersistent) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_groupIfname);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_isGroupOwner);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_ssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_frequency);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_psk);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_passphrase);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_goDeviceAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_isPersistent);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 8 /*onGroupStarted*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onGroupStarted(in_groupIfname, in_isGroupOwner, in_ssid, in_frequency, in_psk, in_passphrase, in_goDeviceAddress, in_isPersistent);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onInvitationReceived(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_goDeviceAddress, const std::vector<uint8_t>& in_bssid, int32_t in_persistentNetworkId, int32_t in_operatingFrequency) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_srcAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_goDeviceAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_persistentNetworkId);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_operatingFrequency);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 9 /*onInvitationReceived*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onInvitationReceived(in_srcAddress, in_goDeviceAddress, in_bssid, in_persistentNetworkId, in_operatingFrequency);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onInvitationResult(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::P2pStatusCode in_status) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_status);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 10 /*onInvitationResult*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onInvitationResult(in_bssid, in_status);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onProvisionDiscoveryCompleted(const std::vector<uint8_t>& in_p2pDeviceAddress, bool in_isRequest, ::aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode in_status, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, const std::string& in_generatedPin) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_p2pDeviceAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_isRequest);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_status);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_configMethods);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_generatedPin);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 11 /*onProvisionDiscoveryCompleted*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onProvisionDiscoveryCompleted(in_p2pDeviceAddress, in_isRequest, in_status, in_configMethods, in_generatedPin);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onR2DeviceFound(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo, const std::vector<uint8_t>& in_wfdR2DeviceInfo) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_srcAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_p2pDeviceAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_primaryDeviceType);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_deviceName);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_configMethods);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_deviceCapabilities);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_groupCapabilities);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_wfdDeviceInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_wfdR2DeviceInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 12 /*onR2DeviceFound*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onR2DeviceFound(in_srcAddress, in_p2pDeviceAddress, in_primaryDeviceType, in_deviceName, in_configMethods, in_deviceCapabilities, in_groupCapabilities, in_wfdDeviceInfo, in_wfdR2DeviceInfo);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onServiceDiscoveryResponse(const std::vector<uint8_t>& in_srcAddress, char16_t in_updateIndicator, const std::vector<uint8_t>& in_tlvs) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_srcAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_updateIndicator);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_tlvs);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 13 /*onServiceDiscoveryResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onServiceDiscoveryResponse(in_srcAddress, in_updateIndicator, in_tlvs);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onStaAuthorized(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_srcAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_p2pDeviceAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 14 /*onStaAuthorized*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onStaAuthorized(in_srcAddress, in_p2pDeviceAddress);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onStaDeauthorized(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_srcAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_p2pDeviceAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 15 /*onStaDeauthorized*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onStaDeauthorized(in_srcAddress, in_p2pDeviceAddress);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onGroupFrequencyChanged(const std::string& in_groupIfname, int32_t in_frequency) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_groupIfname);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_frequency);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16 /*onGroupFrequencyChanged*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onGroupFrequencyChanged(in_groupIfname, in_frequency);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::onDeviceFoundWithVendorElements(const std::vector<uint8_t>& in_srcAddress, const std::vector<uint8_t>& in_p2pDeviceAddress, const std::vector<uint8_t>& in_primaryDeviceType, const std::string& in_deviceName, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods in_configMethods, int8_t in_deviceCapabilities, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask in_groupCapabilities, const std::vector<uint8_t>& in_wfdDeviceInfo, const std::vector<uint8_t>& in_wfdR2DeviceInfo, const std::vector<uint8_t>& in_vendorElemBytes) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_srcAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_p2pDeviceAddress);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_primaryDeviceType);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_deviceName);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_configMethods);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_deviceCapabilities);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_groupCapabilities);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_wfdDeviceInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_wfdR2DeviceInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_vendorElemBytes);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 17 /*onDeviceFoundWithVendorElements*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->onDeviceFoundWithVendorElements(in_srcAddress, in_p2pDeviceAddress, in_primaryDeviceType, in_deviceName, in_configMethods, in_deviceCapabilities, in_groupCapabilities, in_wfdDeviceInfo, in_wfdR2DeviceInfo, in_vendorElemBytes);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::getInterfaceVersion(int32_t* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  if (_aidl_cached_version != -1) {
    *_aidl_return = _aidl_cached_version;
    _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
    return _aidl_status;
  }
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16777214 /*getInterfaceVersion*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->getInterfaceVersion(_aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_cached_version = *_aidl_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantP2pIfaceCallback::getInterfaceHash(std::string* _aidl_return) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  const std::lock_guard<std::mutex> lock(_aidl_cached_hash_mutex);
  if (_aidl_cached_hash != "-1") {
    *_aidl_return = _aidl_cached_hash;
    _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
    return _aidl_status;
  }
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16777213 /*getInterfaceHash*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    0
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantP2pIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantP2pIfaceCallback::getDefaultImpl()->getInterfaceHash(_aidl_return);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;
  _aidl_ret_status = ::ndk::AParcel_readData(_aidl_out.get(), _aidl_return);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_cached_hash = *_aidl_return;
  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
// Source for BnSupplicantP2pIfaceCallback
BnSupplicantP2pIfaceCallback::BnSupplicantP2pIfaceCallback() {}
BnSupplicantP2pIfaceCallback::~BnSupplicantP2pIfaceCallback() {}
::ndk::SpAIBinder BnSupplicantP2pIfaceCallback::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_hardware_wifi_supplicant_ISupplicantP2pIfaceCallback_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markVintfStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnSupplicantP2pIfaceCallback::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = ISupplicantP2pIfaceCallback::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnSupplicantP2pIfaceCallback::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = ISupplicantP2pIfaceCallback::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for ISupplicantP2pIfaceCallback
const char* ISupplicantP2pIfaceCallback::descriptor = "android.hardware.wifi.supplicant.ISupplicantP2pIfaceCallback";
ISupplicantP2pIfaceCallback::ISupplicantP2pIfaceCallback() {}
ISupplicantP2pIfaceCallback::~ISupplicantP2pIfaceCallback() {}


std::shared_ptr<ISupplicantP2pIfaceCallback> ISupplicantP2pIfaceCallback::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_hardware_wifi_supplicant_ISupplicantP2pIfaceCallback_clazz)) { return nullptr; }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<ISupplicantP2pIfaceCallback>(interface);
  }
  return ::ndk::SharedRefBase::make<BpSupplicantP2pIfaceCallback>(binder);
}

binder_status_t ISupplicantP2pIfaceCallback::writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantP2pIfaceCallback>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ISupplicantP2pIfaceCallback::readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantP2pIfaceCallback>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = ISupplicantP2pIfaceCallback::fromBinder(binder);
  return STATUS_OK;
}
bool ISupplicantP2pIfaceCallback::setDefaultImpl(const std::shared_ptr<ISupplicantP2pIfaceCallback>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!ISupplicantP2pIfaceCallback::default_impl);
  if (impl) {
    ISupplicantP2pIfaceCallback::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ISupplicantP2pIfaceCallback>& ISupplicantP2pIfaceCallback::getDefaultImpl() {
  return ISupplicantP2pIfaceCallback::default_impl;
}
std::shared_ptr<ISupplicantP2pIfaceCallback> ISupplicantP2pIfaceCallback::default_impl = nullptr;
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onDeviceFound(const std::vector<uint8_t>& /*in_srcAddress*/, const std::vector<uint8_t>& /*in_p2pDeviceAddress*/, const std::vector<uint8_t>& /*in_primaryDeviceType*/, const std::string& /*in_deviceName*/, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods /*in_configMethods*/, int8_t /*in_deviceCapabilities*/, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask /*in_groupCapabilities*/, const std::vector<uint8_t>& /*in_wfdDeviceInfo*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onDeviceLost(const std::vector<uint8_t>& /*in_p2pDeviceAddress*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onFindStopped() {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onGoNegotiationCompleted(::aidl::android::hardware::wifi::supplicant::P2pStatusCode /*in_status*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onGoNegotiationRequest(const std::vector<uint8_t>& /*in_srcAddress*/, ::aidl::android::hardware::wifi::supplicant::WpsDevPasswordId /*in_passwordId*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onGroupFormationFailure(const std::string& /*in_failureReason*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onGroupFormationSuccess() {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onGroupRemoved(const std::string& /*in_groupIfname*/, bool /*in_isGroupOwner*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onGroupStarted(const std::string& /*in_groupIfname*/, bool /*in_isGroupOwner*/, const std::vector<uint8_t>& /*in_ssid*/, int32_t /*in_frequency*/, const std::vector<uint8_t>& /*in_psk*/, const std::string& /*in_passphrase*/, const std::vector<uint8_t>& /*in_goDeviceAddress*/, bool /*in_isPersistent*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onInvitationReceived(const std::vector<uint8_t>& /*in_srcAddress*/, const std::vector<uint8_t>& /*in_goDeviceAddress*/, const std::vector<uint8_t>& /*in_bssid*/, int32_t /*in_persistentNetworkId*/, int32_t /*in_operatingFrequency*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onInvitationResult(const std::vector<uint8_t>& /*in_bssid*/, ::aidl::android::hardware::wifi::supplicant::P2pStatusCode /*in_status*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onProvisionDiscoveryCompleted(const std::vector<uint8_t>& /*in_p2pDeviceAddress*/, bool /*in_isRequest*/, ::aidl::android::hardware::wifi::supplicant::P2pProvDiscStatusCode /*in_status*/, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods /*in_configMethods*/, const std::string& /*in_generatedPin*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onR2DeviceFound(const std::vector<uint8_t>& /*in_srcAddress*/, const std::vector<uint8_t>& /*in_p2pDeviceAddress*/, const std::vector<uint8_t>& /*in_primaryDeviceType*/, const std::string& /*in_deviceName*/, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods /*in_configMethods*/, int8_t /*in_deviceCapabilities*/, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask /*in_groupCapabilities*/, const std::vector<uint8_t>& /*in_wfdDeviceInfo*/, const std::vector<uint8_t>& /*in_wfdR2DeviceInfo*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onServiceDiscoveryResponse(const std::vector<uint8_t>& /*in_srcAddress*/, char16_t /*in_updateIndicator*/, const std::vector<uint8_t>& /*in_tlvs*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onStaAuthorized(const std::vector<uint8_t>& /*in_srcAddress*/, const std::vector<uint8_t>& /*in_p2pDeviceAddress*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onStaDeauthorized(const std::vector<uint8_t>& /*in_srcAddress*/, const std::vector<uint8_t>& /*in_p2pDeviceAddress*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onGroupFrequencyChanged(const std::string& /*in_groupIfname*/, int32_t /*in_frequency*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::onDeviceFoundWithVendorElements(const std::vector<uint8_t>& /*in_srcAddress*/, const std::vector<uint8_t>& /*in_p2pDeviceAddress*/, const std::vector<uint8_t>& /*in_primaryDeviceType*/, const std::string& /*in_deviceName*/, ::aidl::android::hardware::wifi::supplicant::WpsConfigMethods /*in_configMethods*/, int8_t /*in_deviceCapabilities*/, ::aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask /*in_groupCapabilities*/, const std::vector<uint8_t>& /*in_wfdDeviceInfo*/, const std::vector<uint8_t>& /*in_wfdR2DeviceInfo*/, const std::vector<uint8_t>& /*in_vendorElemBytes*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus ISupplicantP2pIfaceCallbackDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder ISupplicantP2pIfaceCallbackDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ISupplicantP2pIfaceCallbackDefault::isRemote() {
  return false;
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
