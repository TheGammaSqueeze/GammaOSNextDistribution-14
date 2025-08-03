#include "aidl/android/hardware/wifi/supplicant/ISupplicantStaIfaceCallback.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/hardware/wifi/supplicant/BnSupplicantStaIfaceCallback.h>
#include <aidl/android/hardware/wifi/supplicant/BpSupplicantStaIfaceCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
static binder_status_t _aidl_android_hardware_wifi_supplicant_ISupplicantStaIfaceCallback_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnSupplicantStaIfaceCallback> _aidl_impl = std::static_pointer_cast<BnSupplicantStaIfaceCallback>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*onAnqpQueryDone*/): {
      std::vector<uint8_t> in_bssid;
      ::aidl::android::hardware::wifi::supplicant::AnqpData in_data;
      ::aidl::android::hardware::wifi::supplicant::Hs20AnqpData in_hs20Data;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_data);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_hs20Data);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onAnqpQueryDone(in_bssid, in_data, in_hs20Data);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 1 /*onAssociationRejected*/): {
      ::aidl::android::hardware::wifi::supplicant::AssociationRejectionData in_assocRejectData;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_assocRejectData);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onAssociationRejected(in_assocRejectData);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 2 /*onAuthenticationTimeout*/): {
      std::vector<uint8_t> in_bssid;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onAuthenticationTimeout(in_bssid);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 3 /*onAuxiliarySupplicantEvent*/): {
      ::aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode in_eventCode;
      std::vector<uint8_t> in_bssid;
      std::string in_reasonString;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_eventCode);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_reasonString);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onAuxiliarySupplicantEvent(in_eventCode, in_bssid, in_reasonString);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 4 /*onBssTmHandlingDone*/): {
      ::aidl::android::hardware::wifi::supplicant::BssTmData in_tmData;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_tmData);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onBssTmHandlingDone(in_tmData);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 5 /*onBssidChanged*/): {
      ::aidl::android::hardware::wifi::supplicant::BssidChangeReason in_reason;
      std::vector<uint8_t> in_bssid;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_reason);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onBssidChanged(in_reason, in_bssid);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 6 /*onDisconnected*/): {
      std::vector<uint8_t> in_bssid;
      bool in_locallyGenerated;
      ::aidl::android::hardware::wifi::supplicant::StaIfaceReasonCode in_reasonCode;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_locallyGenerated);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_reasonCode);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDisconnected(in_bssid, in_locallyGenerated, in_reasonCode);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 7 /*onDppFailure*/): {
      ::aidl::android::hardware::wifi::supplicant::DppFailureCode in_code;
      std::string in_ssid;
      std::string in_channelList;
      std::vector<char16_t> in_bandList;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_code);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_ssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_channelList);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bandList);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDppFailure(in_code, in_ssid, in_channelList, in_bandList);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 8 /*onDppProgress*/): {
      ::aidl::android::hardware::wifi::supplicant::DppProgressCode in_code;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_code);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDppProgress(in_code);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 9 /*onDppSuccess*/): {
      ::aidl::android::hardware::wifi::supplicant::DppEventType in_event;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_event);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDppSuccess(in_event);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 10 /*onDppSuccessConfigReceived*/): {
      std::vector<uint8_t> in_ssid;
      std::string in_password;
      std::vector<uint8_t> in_psk;
      ::aidl::android::hardware::wifi::supplicant::DppAkm in_securityAkm;
      ::aidl::android::hardware::wifi::supplicant::DppConnectionKeys in_dppConnectionKeys;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_ssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_password);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_psk);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_securityAkm);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_dppConnectionKeys);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDppSuccessConfigReceived(in_ssid, in_password, in_psk, in_securityAkm, in_dppConnectionKeys);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 11 /*onDppSuccessConfigSent*/): {

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onDppSuccessConfigSent();
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 12 /*onEapFailure*/): {
      std::vector<uint8_t> in_bssid;
      int32_t in_errorCode;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_errorCode);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onEapFailure(in_bssid, in_errorCode);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 13 /*onExtRadioWorkStart*/): {
      int32_t in_id;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_id);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onExtRadioWorkStart(in_id);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 14 /*onExtRadioWorkTimeout*/): {
      int32_t in_id;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_id);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onExtRadioWorkTimeout(in_id);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 15 /*onHs20DeauthImminentNotice*/): {
      std::vector<uint8_t> in_bssid;
      int32_t in_reasonCode;
      int32_t in_reAuthDelayInSec;
      std::string in_url;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_reasonCode);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_reAuthDelayInSec);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_url);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onHs20DeauthImminentNotice(in_bssid, in_reasonCode, in_reAuthDelayInSec, in_url);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 16 /*onHs20IconQueryDone*/): {
      std::vector<uint8_t> in_bssid;
      std::string in_fileName;
      std::vector<uint8_t> in_data;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_fileName);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_data);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onHs20IconQueryDone(in_bssid, in_fileName, in_data);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 17 /*onHs20SubscriptionRemediation*/): {
      std::vector<uint8_t> in_bssid;
      ::aidl::android::hardware::wifi::supplicant::OsuMethod in_osuMethod;
      std::string in_url;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_osuMethod);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_url);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onHs20SubscriptionRemediation(in_bssid, in_osuMethod, in_url);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 18 /*onHs20TermsAndConditionsAcceptanceRequestedNotification*/): {
      std::vector<uint8_t> in_bssid;
      std::string in_url;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_url);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onHs20TermsAndConditionsAcceptanceRequestedNotification(in_bssid, in_url);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 19 /*onNetworkAdded*/): {
      int32_t in_id;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_id);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onNetworkAdded(in_id);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 20 /*onNetworkNotFound*/): {
      std::vector<uint8_t> in_ssid;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_ssid);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onNetworkNotFound(in_ssid);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 21 /*onNetworkRemoved*/): {
      int32_t in_id;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_id);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onNetworkRemoved(in_id);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 22 /*onPmkCacheAdded*/): {
      int64_t in_expirationTimeInSec;
      std::vector<uint8_t> in_serializedEntry;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_expirationTimeInSec);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serializedEntry);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onPmkCacheAdded(in_expirationTimeInSec, in_serializedEntry);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 23 /*onStateChanged*/): {
      ::aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState in_newState;
      std::vector<uint8_t> in_bssid;
      int32_t in_id;
      std::vector<uint8_t> in_ssid;
      bool in_filsHlpSent;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_newState);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_id);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_ssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_filsHlpSent);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onStateChanged(in_newState, in_bssid, in_id, in_ssid, in_filsHlpSent);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 24 /*onWpsEventFail*/): {
      std::vector<uint8_t> in_bssid;
      ::aidl::android::hardware::wifi::supplicant::WpsConfigError in_configError;
      ::aidl::android::hardware::wifi::supplicant::WpsErrorIndication in_errorInd;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_bssid);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_configError);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_errorInd);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onWpsEventFail(in_bssid, in_configError, in_errorInd);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 25 /*onWpsEventPbcOverlap*/): {

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onWpsEventPbcOverlap();
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 26 /*onWpsEventSuccess*/): {

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onWpsEventSuccess();
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 27 /*onQosPolicyReset*/): {

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onQosPolicyReset();
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 28 /*onQosPolicyRequest*/): {
      int32_t in_qosPolicyRequestId;
      std::vector<::aidl::android::hardware::wifi::supplicant::QosPolicyData> in_qosPolicyData;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_qosPolicyRequestId);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_qosPolicyData);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->onQosPolicyRequest(in_qosPolicyRequestId, in_qosPolicyData);
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

static AIBinder_Class* _g_aidl_android_hardware_wifi_supplicant_ISupplicantStaIfaceCallback_clazz = ::ndk::ICInterface::defineClass(ISupplicantStaIfaceCallback::descriptor, _aidl_android_hardware_wifi_supplicant_ISupplicantStaIfaceCallback_onTransact);

BpSupplicantStaIfaceCallback::BpSupplicantStaIfaceCallback(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpSupplicantStaIfaceCallback::~BpSupplicantStaIfaceCallback() {}

::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onAnqpQueryDone(const std::vector<uint8_t>& in_bssid, const ::aidl::android::hardware::wifi::supplicant::AnqpData& in_data, const ::aidl::android::hardware::wifi::supplicant::Hs20AnqpData& in_hs20Data) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_data);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_hs20Data);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 0 /*onAnqpQueryDone*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onAnqpQueryDone(in_bssid, in_data, in_hs20Data);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onAssociationRejected(const ::aidl::android::hardware::wifi::supplicant::AssociationRejectionData& in_assocRejectData) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_assocRejectData);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 1 /*onAssociationRejected*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onAssociationRejected(in_assocRejectData);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onAuthenticationTimeout(const std::vector<uint8_t>& in_bssid) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 2 /*onAuthenticationTimeout*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onAuthenticationTimeout(in_bssid);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onAuxiliarySupplicantEvent(::aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode in_eventCode, const std::vector<uint8_t>& in_bssid, const std::string& in_reasonString) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_eventCode);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_reasonString);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 3 /*onAuxiliarySupplicantEvent*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onAuxiliarySupplicantEvent(in_eventCode, in_bssid, in_reasonString);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onBssTmHandlingDone(const ::aidl::android::hardware::wifi::supplicant::BssTmData& in_tmData) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_tmData);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 4 /*onBssTmHandlingDone*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onBssTmHandlingDone(in_tmData);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onBssidChanged(::aidl::android::hardware::wifi::supplicant::BssidChangeReason in_reason, const std::vector<uint8_t>& in_bssid) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_reason);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 5 /*onBssidChanged*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onBssidChanged(in_reason, in_bssid);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onDisconnected(const std::vector<uint8_t>& in_bssid, bool in_locallyGenerated, ::aidl::android::hardware::wifi::supplicant::StaIfaceReasonCode in_reasonCode) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_locallyGenerated);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_reasonCode);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 6 /*onDisconnected*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onDisconnected(in_bssid, in_locallyGenerated, in_reasonCode);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onDppFailure(::aidl::android::hardware::wifi::supplicant::DppFailureCode in_code, const std::string& in_ssid, const std::string& in_channelList, const std::vector<char16_t>& in_bandList) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_code);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_ssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_channelList);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bandList);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 7 /*onDppFailure*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onDppFailure(in_code, in_ssid, in_channelList, in_bandList);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onDppProgress(::aidl::android::hardware::wifi::supplicant::DppProgressCode in_code) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_code);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 8 /*onDppProgress*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onDppProgress(in_code);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onDppSuccess(::aidl::android::hardware::wifi::supplicant::DppEventType in_event) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_event);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 9 /*onDppSuccess*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onDppSuccess(in_event);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onDppSuccessConfigReceived(const std::vector<uint8_t>& in_ssid, const std::string& in_password, const std::vector<uint8_t>& in_psk, ::aidl::android::hardware::wifi::supplicant::DppAkm in_securityAkm, const ::aidl::android::hardware::wifi::supplicant::DppConnectionKeys& in_dppConnectionKeys) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_ssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_password);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_psk);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_securityAkm);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_dppConnectionKeys);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 10 /*onDppSuccessConfigReceived*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onDppSuccessConfigReceived(in_ssid, in_password, in_psk, in_securityAkm, in_dppConnectionKeys);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onDppSuccessConfigSent() {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 11 /*onDppSuccessConfigSent*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onDppSuccessConfigSent();
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onEapFailure(const std::vector<uint8_t>& in_bssid, int32_t in_errorCode) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_errorCode);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 12 /*onEapFailure*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onEapFailure(in_bssid, in_errorCode);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onExtRadioWorkStart(int32_t in_id) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_id);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 13 /*onExtRadioWorkStart*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onExtRadioWorkStart(in_id);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onExtRadioWorkTimeout(int32_t in_id) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_id);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 14 /*onExtRadioWorkTimeout*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onExtRadioWorkTimeout(in_id);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onHs20DeauthImminentNotice(const std::vector<uint8_t>& in_bssid, int32_t in_reasonCode, int32_t in_reAuthDelayInSec, const std::string& in_url) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_reasonCode);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_reAuthDelayInSec);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_url);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 15 /*onHs20DeauthImminentNotice*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onHs20DeauthImminentNotice(in_bssid, in_reasonCode, in_reAuthDelayInSec, in_url);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onHs20IconQueryDone(const std::vector<uint8_t>& in_bssid, const std::string& in_fileName, const std::vector<uint8_t>& in_data) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_fileName);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_data);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16 /*onHs20IconQueryDone*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onHs20IconQueryDone(in_bssid, in_fileName, in_data);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onHs20SubscriptionRemediation(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::OsuMethod in_osuMethod, const std::string& in_url) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_osuMethod);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_url);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 17 /*onHs20SubscriptionRemediation*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onHs20SubscriptionRemediation(in_bssid, in_osuMethod, in_url);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onHs20TermsAndConditionsAcceptanceRequestedNotification(const std::vector<uint8_t>& in_bssid, const std::string& in_url) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_url);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 18 /*onHs20TermsAndConditionsAcceptanceRequestedNotification*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onHs20TermsAndConditionsAcceptanceRequestedNotification(in_bssid, in_url);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onNetworkAdded(int32_t in_id) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_id);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 19 /*onNetworkAdded*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onNetworkAdded(in_id);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onNetworkNotFound(const std::vector<uint8_t>& in_ssid) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_ssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 20 /*onNetworkNotFound*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onNetworkNotFound(in_ssid);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onNetworkRemoved(int32_t in_id) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_id);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 21 /*onNetworkRemoved*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onNetworkRemoved(in_id);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onPmkCacheAdded(int64_t in_expirationTimeInSec, const std::vector<uint8_t>& in_serializedEntry) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_expirationTimeInSec);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serializedEntry);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 22 /*onPmkCacheAdded*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onPmkCacheAdded(in_expirationTimeInSec, in_serializedEntry);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onStateChanged(::aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState in_newState, const std::vector<uint8_t>& in_bssid, int32_t in_id, const std::vector<uint8_t>& in_ssid, bool in_filsHlpSent) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_newState);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_id);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_ssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_filsHlpSent);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 23 /*onStateChanged*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onStateChanged(in_newState, in_bssid, in_id, in_ssid, in_filsHlpSent);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onWpsEventFail(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::WpsConfigError in_configError, ::aidl::android::hardware::wifi::supplicant::WpsErrorIndication in_errorInd) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_bssid);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_configError);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_errorInd);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 24 /*onWpsEventFail*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onWpsEventFail(in_bssid, in_configError, in_errorInd);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onWpsEventPbcOverlap() {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 25 /*onWpsEventPbcOverlap*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onWpsEventPbcOverlap();
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onWpsEventSuccess() {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 26 /*onWpsEventSuccess*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onWpsEventSuccess();
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onQosPolicyReset() {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 27 /*onQosPolicyReset*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onQosPolicyReset();
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::onQosPolicyRequest(int32_t in_qosPolicyRequestId, const std::vector<::aidl::android::hardware::wifi::supplicant::QosPolicyData>& in_qosPolicyData) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_qosPolicyRequestId);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_qosPolicyData);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 28 /*onQosPolicyRequest*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->onQosPolicyRequest(in_qosPolicyRequestId, in_qosPolicyData);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::getInterfaceVersion(int32_t* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->getInterfaceVersion(_aidl_return);
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
::ndk::ScopedAStatus BpSupplicantStaIfaceCallback::getInterfaceHash(std::string* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ISupplicantStaIfaceCallback::getDefaultImpl()) {
    _aidl_status = ISupplicantStaIfaceCallback::getDefaultImpl()->getInterfaceHash(_aidl_return);
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
// Source for BnSupplicantStaIfaceCallback
BnSupplicantStaIfaceCallback::BnSupplicantStaIfaceCallback() {}
BnSupplicantStaIfaceCallback::~BnSupplicantStaIfaceCallback() {}
::ndk::SpAIBinder BnSupplicantStaIfaceCallback::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_hardware_wifi_supplicant_ISupplicantStaIfaceCallback_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markVintfStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnSupplicantStaIfaceCallback::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = ISupplicantStaIfaceCallback::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnSupplicantStaIfaceCallback::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = ISupplicantStaIfaceCallback::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for ISupplicantStaIfaceCallback
const char* ISupplicantStaIfaceCallback::descriptor = "android.hardware.wifi.supplicant.ISupplicantStaIfaceCallback";
ISupplicantStaIfaceCallback::ISupplicantStaIfaceCallback() {}
ISupplicantStaIfaceCallback::~ISupplicantStaIfaceCallback() {}


std::shared_ptr<ISupplicantStaIfaceCallback> ISupplicantStaIfaceCallback::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_hardware_wifi_supplicant_ISupplicantStaIfaceCallback_clazz)) { return nullptr; }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<ISupplicantStaIfaceCallback>(interface);
  }
  return ::ndk::SharedRefBase::make<BpSupplicantStaIfaceCallback>(binder);
}

binder_status_t ISupplicantStaIfaceCallback::writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantStaIfaceCallback>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t ISupplicantStaIfaceCallback::readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantStaIfaceCallback>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = ISupplicantStaIfaceCallback::fromBinder(binder);
  return STATUS_OK;
}
bool ISupplicantStaIfaceCallback::setDefaultImpl(const std::shared_ptr<ISupplicantStaIfaceCallback>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!ISupplicantStaIfaceCallback::default_impl);
  if (impl) {
    ISupplicantStaIfaceCallback::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<ISupplicantStaIfaceCallback>& ISupplicantStaIfaceCallback::getDefaultImpl() {
  return ISupplicantStaIfaceCallback::default_impl;
}
std::shared_ptr<ISupplicantStaIfaceCallback> ISupplicantStaIfaceCallback::default_impl = nullptr;
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onAnqpQueryDone(const std::vector<uint8_t>& /*in_bssid*/, const ::aidl::android::hardware::wifi::supplicant::AnqpData& /*in_data*/, const ::aidl::android::hardware::wifi::supplicant::Hs20AnqpData& /*in_hs20Data*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onAssociationRejected(const ::aidl::android::hardware::wifi::supplicant::AssociationRejectionData& /*in_assocRejectData*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onAuthenticationTimeout(const std::vector<uint8_t>& /*in_bssid*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onAuxiliarySupplicantEvent(::aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode /*in_eventCode*/, const std::vector<uint8_t>& /*in_bssid*/, const std::string& /*in_reasonString*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onBssTmHandlingDone(const ::aidl::android::hardware::wifi::supplicant::BssTmData& /*in_tmData*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onBssidChanged(::aidl::android::hardware::wifi::supplicant::BssidChangeReason /*in_reason*/, const std::vector<uint8_t>& /*in_bssid*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onDisconnected(const std::vector<uint8_t>& /*in_bssid*/, bool /*in_locallyGenerated*/, ::aidl::android::hardware::wifi::supplicant::StaIfaceReasonCode /*in_reasonCode*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onDppFailure(::aidl::android::hardware::wifi::supplicant::DppFailureCode /*in_code*/, const std::string& /*in_ssid*/, const std::string& /*in_channelList*/, const std::vector<char16_t>& /*in_bandList*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onDppProgress(::aidl::android::hardware::wifi::supplicant::DppProgressCode /*in_code*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onDppSuccess(::aidl::android::hardware::wifi::supplicant::DppEventType /*in_event*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onDppSuccessConfigReceived(const std::vector<uint8_t>& /*in_ssid*/, const std::string& /*in_password*/, const std::vector<uint8_t>& /*in_psk*/, ::aidl::android::hardware::wifi::supplicant::DppAkm /*in_securityAkm*/, const ::aidl::android::hardware::wifi::supplicant::DppConnectionKeys& /*in_dppConnectionKeys*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onDppSuccessConfigSent() {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onEapFailure(const std::vector<uint8_t>& /*in_bssid*/, int32_t /*in_errorCode*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onExtRadioWorkStart(int32_t /*in_id*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onExtRadioWorkTimeout(int32_t /*in_id*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onHs20DeauthImminentNotice(const std::vector<uint8_t>& /*in_bssid*/, int32_t /*in_reasonCode*/, int32_t /*in_reAuthDelayInSec*/, const std::string& /*in_url*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onHs20IconQueryDone(const std::vector<uint8_t>& /*in_bssid*/, const std::string& /*in_fileName*/, const std::vector<uint8_t>& /*in_data*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onHs20SubscriptionRemediation(const std::vector<uint8_t>& /*in_bssid*/, ::aidl::android::hardware::wifi::supplicant::OsuMethod /*in_osuMethod*/, const std::string& /*in_url*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onHs20TermsAndConditionsAcceptanceRequestedNotification(const std::vector<uint8_t>& /*in_bssid*/, const std::string& /*in_url*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onNetworkAdded(int32_t /*in_id*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onNetworkNotFound(const std::vector<uint8_t>& /*in_ssid*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onNetworkRemoved(int32_t /*in_id*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onPmkCacheAdded(int64_t /*in_expirationTimeInSec*/, const std::vector<uint8_t>& /*in_serializedEntry*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onStateChanged(::aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState /*in_newState*/, const std::vector<uint8_t>& /*in_bssid*/, int32_t /*in_id*/, const std::vector<uint8_t>& /*in_ssid*/, bool /*in_filsHlpSent*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onWpsEventFail(const std::vector<uint8_t>& /*in_bssid*/, ::aidl::android::hardware::wifi::supplicant::WpsConfigError /*in_configError*/, ::aidl::android::hardware::wifi::supplicant::WpsErrorIndication /*in_errorInd*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onWpsEventPbcOverlap() {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onWpsEventSuccess() {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onQosPolicyReset() {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::onQosPolicyRequest(int32_t /*in_qosPolicyRequestId*/, const std::vector<::aidl::android::hardware::wifi::supplicant::QosPolicyData>& /*in_qosPolicyData*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus ISupplicantStaIfaceCallbackDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder ISupplicantStaIfaceCallbackDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool ISupplicantStaIfaceCallbackDefault::isRemote() {
  return false;
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
