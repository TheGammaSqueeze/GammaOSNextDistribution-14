#include "aidl/android/hardware/radio/network/IRadioNetworkResponse.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/hardware/radio/network/BnRadioNetworkResponse.h>
#include <aidl/android/hardware/radio/network/BpRadioNetworkResponse.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
static binder_status_t _aidl_android_hardware_radio_network_IRadioNetworkResponse_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnRadioNetworkResponse> _aidl_impl = std::static_pointer_cast<BnRadioNetworkResponse>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*acknowledgeRequest*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->acknowledgeRequest(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 1 /*getAllowedNetworkTypesBitmapResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      int32_t in_networkTypeBitmap;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_networkTypeBitmap);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getAllowedNetworkTypesBitmapResponse(in_info, in_networkTypeBitmap);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 2 /*getAvailableBandModesResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      std::vector<::aidl::android::hardware::radio::network::RadioBandMode> in_bandModes;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bandModes);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getAvailableBandModesResponse(in_info, in_bandModes);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 3 /*getAvailableNetworksResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      std::vector<::aidl::android::hardware::radio::network::OperatorInfo> in_networkInfos;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_networkInfos);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getAvailableNetworksResponse(in_info, in_networkInfos);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 4 /*getBarringInfoResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      ::aidl::android::hardware::radio::network::CellIdentity in_cellIdentity;
      std::vector<::aidl::android::hardware::radio::network::BarringInfo> in_barringInfos;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_cellIdentity);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_barringInfos);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getBarringInfoResponse(in_info, in_cellIdentity, in_barringInfos);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 5 /*getCdmaRoamingPreferenceResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      ::aidl::android::hardware::radio::network::CdmaRoamingType in_type;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_type);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getCdmaRoamingPreferenceResponse(in_info, in_type);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 6 /*getCellInfoListResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      std::vector<::aidl::android::hardware::radio::network::CellInfo> in_cellInfo;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_cellInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getCellInfoListResponse(in_info, in_cellInfo);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 7 /*getDataRegistrationStateResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      ::aidl::android::hardware::radio::network::RegStateResult in_dataRegResponse;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_dataRegResponse);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getDataRegistrationStateResponse(in_info, in_dataRegResponse);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 8 /*getImsRegistrationStateResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      bool in_isRegistered;
      ::aidl::android::hardware::radio::RadioTechnologyFamily in_ratFamily;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_isRegistered);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_ratFamily);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getImsRegistrationStateResponse(in_info, in_isRegistered, in_ratFamily);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 9 /*getNetworkSelectionModeResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      bool in_manual;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_manual);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getNetworkSelectionModeResponse(in_info, in_manual);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 10 /*getOperatorResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      std::string in_longName;
      std::string in_shortName;
      std::string in_numeric;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_longName);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_shortName);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_numeric);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getOperatorResponse(in_info, in_longName, in_shortName, in_numeric);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 11 /*getSignalStrengthResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      ::aidl::android::hardware::radio::network::SignalStrength in_signalStrength;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_signalStrength);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getSignalStrengthResponse(in_info, in_signalStrength);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 12 /*getSystemSelectionChannelsResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier> in_specifiers;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_specifiers);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getSystemSelectionChannelsResponse(in_info, in_specifiers);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 13 /*getVoiceRadioTechnologyResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      ::aidl::android::hardware::radio::RadioTechnology in_rat;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_rat);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getVoiceRadioTechnologyResponse(in_info, in_rat);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 14 /*getVoiceRegistrationStateResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      ::aidl::android::hardware::radio::network::RegStateResult in_voiceRegResponse;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_voiceRegResponse);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getVoiceRegistrationStateResponse(in_info, in_voiceRegResponse);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 15 /*isNrDualConnectivityEnabledResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      bool in_isEnabled;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_isEnabled);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->isNrDualConnectivityEnabledResponse(in_info, in_isEnabled);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 16 /*setAllowedNetworkTypesBitmapResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setAllowedNetworkTypesBitmapResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 17 /*setBandModeResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setBandModeResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 18 /*setBarringPasswordResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setBarringPasswordResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 19 /*setCdmaRoamingPreferenceResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setCdmaRoamingPreferenceResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 20 /*setCellInfoListRateResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setCellInfoListRateResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 21 /*setIndicationFilterResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setIndicationFilterResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 22 /*setLinkCapacityReportingCriteriaResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setLinkCapacityReportingCriteriaResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 23 /*setLocationUpdatesResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setLocationUpdatesResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 24 /*setNetworkSelectionModeAutomaticResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setNetworkSelectionModeAutomaticResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 25 /*setNetworkSelectionModeManualResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setNetworkSelectionModeManualResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 26 /*setNrDualConnectivityStateResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setNrDualConnectivityStateResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 27 /*setSignalStrengthReportingCriteriaResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setSignalStrengthReportingCriteriaResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 28 /*setSuppServiceNotificationsResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setSuppServiceNotificationsResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 29 /*setSystemSelectionChannelsResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setSystemSelectionChannelsResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 30 /*startNetworkScanResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->startNetworkScanResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 31 /*stopNetworkScanResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->stopNetworkScanResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 32 /*supplyNetworkDepersonalizationResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      int32_t in_remainingRetries;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_remainingRetries);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->supplyNetworkDepersonalizationResponse(in_info, in_remainingRetries);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 33 /*setUsageSettingResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setUsageSettingResponse(in_info);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 34 /*getUsageSettingResponse*/): {
      ::aidl::android::hardware::radio::RadioResponseInfo in_info;
      ::aidl::android::hardware::radio::network::UsageSetting in_usageSetting;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_info);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_usageSetting);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getUsageSettingResponse(in_info, in_usageSetting);
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

static AIBinder_Class* _g_aidl_android_hardware_radio_network_IRadioNetworkResponse_clazz = ::ndk::ICInterface::defineClass(IRadioNetworkResponse::descriptor, _aidl_android_hardware_radio_network_IRadioNetworkResponse_onTransact);

BpRadioNetworkResponse::BpRadioNetworkResponse(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpRadioNetworkResponse::~BpRadioNetworkResponse() {}

::ndk::ScopedAStatus BpRadioNetworkResponse::acknowledgeRequest(int32_t in_serial) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*acknowledgeRequest*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->acknowledgeRequest(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_networkTypeBitmap) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_networkTypeBitmap);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 1 /*getAllowedNetworkTypesBitmapResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getAllowedNetworkTypesBitmapResponse(in_info, in_networkTypeBitmap);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getAvailableBandModesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::RadioBandMode>& in_bandModes) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bandModes);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 2 /*getAvailableBandModesResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getAvailableBandModesResponse(in_info, in_bandModes);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getAvailableNetworksResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::OperatorInfo>& in_networkInfos) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_networkInfos);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 3 /*getAvailableNetworksResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getAvailableNetworksResponse(in_info, in_networkInfos);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getBarringInfoResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::vector<::aidl::android::hardware::radio::network::BarringInfo>& in_barringInfos) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_cellIdentity);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_barringInfos);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 4 /*getBarringInfoResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getBarringInfoResponse(in_info, in_cellIdentity, in_barringInfos);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::network::CdmaRoamingType in_type) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_type);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 5 /*getCdmaRoamingPreferenceResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getCdmaRoamingPreferenceResponse(in_info, in_type);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getCellInfoListResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::CellInfo>& in_cellInfo) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_cellInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 6 /*getCellInfoListResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getCellInfoListResponse(in_info, in_cellInfo);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getDataRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::RegStateResult& in_dataRegResponse) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_dataRegResponse);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 7 /*getDataRegistrationStateResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getDataRegistrationStateResponse(in_info, in_dataRegResponse);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getImsRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isRegistered, ::aidl::android::hardware::radio::RadioTechnologyFamily in_ratFamily) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_isRegistered);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_ratFamily);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 8 /*getImsRegistrationStateResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getImsRegistrationStateResponse(in_info, in_isRegistered, in_ratFamily);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getNetworkSelectionModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_manual) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_manual);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 9 /*getNetworkSelectionModeResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getNetworkSelectionModeResponse(in_info, in_manual);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getOperatorResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::string& in_longName, const std::string& in_shortName, const std::string& in_numeric) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_longName);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_shortName);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_numeric);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 10 /*getOperatorResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getOperatorResponse(in_info, in_longName, in_shortName, in_numeric);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getSignalStrengthResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::SignalStrength& in_signalStrength) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_signalStrength);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 11 /*getSignalStrengthResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getSignalStrengthResponse(in_info, in_signalStrength);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier>& in_specifiers) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_specifiers);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 12 /*getSystemSelectionChannelsResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getSystemSelectionChannelsResponse(in_info, in_specifiers);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getVoiceRadioTechnologyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::RadioTechnology in_rat) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_rat);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 13 /*getVoiceRadioTechnologyResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getVoiceRadioTechnologyResponse(in_info, in_rat);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getVoiceRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::network::RegStateResult& in_voiceRegResponse) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_voiceRegResponse);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 14 /*getVoiceRegistrationStateResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getVoiceRegistrationStateResponse(in_info, in_voiceRegResponse);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::isNrDualConnectivityEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_isEnabled) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_isEnabled);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 15 /*isNrDualConnectivityEnabledResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->isNrDualConnectivityEnabledResponse(in_info, in_isEnabled);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16 /*setAllowedNetworkTypesBitmapResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setAllowedNetworkTypesBitmapResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setBandModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 17 /*setBandModeResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setBandModeResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setBarringPasswordResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 18 /*setBarringPasswordResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setBarringPasswordResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 19 /*setCdmaRoamingPreferenceResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setCdmaRoamingPreferenceResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setCellInfoListRateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 20 /*setCellInfoListRateResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setCellInfoListRateResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setIndicationFilterResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 21 /*setIndicationFilterResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setIndicationFilterResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setLinkCapacityReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 22 /*setLinkCapacityReportingCriteriaResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setLinkCapacityReportingCriteriaResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setLocationUpdatesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 23 /*setLocationUpdatesResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setLocationUpdatesResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setNetworkSelectionModeAutomaticResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 24 /*setNetworkSelectionModeAutomaticResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setNetworkSelectionModeAutomaticResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setNetworkSelectionModeManualResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 25 /*setNetworkSelectionModeManualResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setNetworkSelectionModeManualResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setNrDualConnectivityStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 26 /*setNrDualConnectivityStateResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setNrDualConnectivityStateResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setSignalStrengthReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 27 /*setSignalStrengthReportingCriteriaResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setSignalStrengthReportingCriteriaResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setSuppServiceNotificationsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 28 /*setSuppServiceNotificationsResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setSuppServiceNotificationsResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 29 /*setSystemSelectionChannelsResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setSystemSelectionChannelsResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::startNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 30 /*startNetworkScanResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->startNetworkScanResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::stopNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 31 /*stopNetworkScanResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->stopNetworkScanResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::supplyNetworkDepersonalizationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_remainingRetries) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_remainingRetries);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 32 /*supplyNetworkDepersonalizationResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->supplyNetworkDepersonalizationResponse(in_info, in_remainingRetries);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::setUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 33 /*setUsageSettingResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->setUsageSettingResponse(in_info);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::network::UsageSetting in_usageSetting) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_info);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_usageSetting);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 34 /*getUsageSettingResponse*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getUsageSettingResponse(in_info, in_usageSetting);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioNetworkResponse::getInterfaceVersion(int32_t* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getInterfaceVersion(_aidl_return);
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
::ndk::ScopedAStatus BpRadioNetworkResponse::getInterfaceHash(std::string* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioNetworkResponse::getDefaultImpl()) {
    _aidl_status = IRadioNetworkResponse::getDefaultImpl()->getInterfaceHash(_aidl_return);
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
// Source for BnRadioNetworkResponse
BnRadioNetworkResponse::BnRadioNetworkResponse() {}
BnRadioNetworkResponse::~BnRadioNetworkResponse() {}
::ndk::SpAIBinder BnRadioNetworkResponse::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_hardware_radio_network_IRadioNetworkResponse_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markVintfStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnRadioNetworkResponse::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = IRadioNetworkResponse::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnRadioNetworkResponse::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = IRadioNetworkResponse::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for IRadioNetworkResponse
const char* IRadioNetworkResponse::descriptor = "android.hardware.radio.network.IRadioNetworkResponse";
IRadioNetworkResponse::IRadioNetworkResponse() {}
IRadioNetworkResponse::~IRadioNetworkResponse() {}


std::shared_ptr<IRadioNetworkResponse> IRadioNetworkResponse::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_hardware_radio_network_IRadioNetworkResponse_clazz)) { return nullptr; }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<IRadioNetworkResponse>(interface);
  }
  return ::ndk::SharedRefBase::make<BpRadioNetworkResponse>(binder);
}

binder_status_t IRadioNetworkResponse::writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioNetworkResponse>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t IRadioNetworkResponse::readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioNetworkResponse>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = IRadioNetworkResponse::fromBinder(binder);
  return STATUS_OK;
}
bool IRadioNetworkResponse::setDefaultImpl(const std::shared_ptr<IRadioNetworkResponse>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!IRadioNetworkResponse::default_impl);
  if (impl) {
    IRadioNetworkResponse::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<IRadioNetworkResponse>& IRadioNetworkResponse::getDefaultImpl() {
  return IRadioNetworkResponse::default_impl;
}
std::shared_ptr<IRadioNetworkResponse> IRadioNetworkResponse::default_impl = nullptr;
::ndk::ScopedAStatus IRadioNetworkResponseDefault::acknowledgeRequest(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, int32_t /*in_networkTypeBitmap*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getAvailableBandModesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, const std::vector<::aidl::android::hardware::radio::network::RadioBandMode>& /*in_bandModes*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getAvailableNetworksResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, const std::vector<::aidl::android::hardware::radio::network::OperatorInfo>& /*in_networkInfos*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getBarringInfoResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, const ::aidl::android::hardware::radio::network::CellIdentity& /*in_cellIdentity*/, const std::vector<::aidl::android::hardware::radio::network::BarringInfo>& /*in_barringInfos*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, ::aidl::android::hardware::radio::network::CdmaRoamingType /*in_type*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getCellInfoListResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, const std::vector<::aidl::android::hardware::radio::network::CellInfo>& /*in_cellInfo*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getDataRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, const ::aidl::android::hardware::radio::network::RegStateResult& /*in_dataRegResponse*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getImsRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, bool /*in_isRegistered*/, ::aidl::android::hardware::radio::RadioTechnologyFamily /*in_ratFamily*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getNetworkSelectionModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, bool /*in_manual*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getOperatorResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, const std::string& /*in_longName*/, const std::string& /*in_shortName*/, const std::string& /*in_numeric*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getSignalStrengthResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, const ::aidl::android::hardware::radio::network::SignalStrength& /*in_signalStrength*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, const std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier>& /*in_specifiers*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getVoiceRadioTechnologyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, ::aidl::android::hardware::radio::RadioTechnology /*in_rat*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getVoiceRegistrationStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, const ::aidl::android::hardware::radio::network::RegStateResult& /*in_voiceRegResponse*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::isNrDualConnectivityEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, bool /*in_isEnabled*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setAllowedNetworkTypesBitmapResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setBandModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setBarringPasswordResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setCdmaRoamingPreferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setCellInfoListRateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setIndicationFilterResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setLinkCapacityReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setLocationUpdatesResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setNetworkSelectionModeAutomaticResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setNetworkSelectionModeManualResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setNrDualConnectivityStateResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setSignalStrengthReportingCriteriaResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setSuppServiceNotificationsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setSystemSelectionChannelsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::startNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::stopNetworkScanResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::supplyNetworkDepersonalizationResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, int32_t /*in_remainingRetries*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::setUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getUsageSettingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& /*in_info*/, ::aidl::android::hardware::radio::network::UsageSetting /*in_usageSetting*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus IRadioNetworkResponseDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder IRadioNetworkResponseDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool IRadioNetworkResponseDefault::isRemote() {
  return false;
}
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
