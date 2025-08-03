#include "aidl/android/hardware/radio/voice/IRadioVoice.h"

#include <android/binder_parcel_utils.h>
#include <aidl/android/hardware/radio/voice/BnRadioVoice.h>
#include <aidl/android/hardware/radio/voice/BnRadioVoiceIndication.h>
#include <aidl/android/hardware/radio/voice/BnRadioVoiceResponse.h>
#include <aidl/android/hardware/radio/voice/BpRadioVoice.h>
#include <aidl/android/hardware/radio/voice/BpRadioVoiceIndication.h>
#include <aidl/android/hardware/radio/voice/BpRadioVoiceResponse.h>
#include <aidl/android/hardware/radio/voice/IRadioVoiceIndication.h>
#include <aidl/android/hardware/radio/voice/IRadioVoiceResponse.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
static binder_status_t _aidl_android_hardware_radio_voice_IRadioVoice_onTransact(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, AParcel* _aidl_out) {
  (void)_aidl_in;
  (void)_aidl_out;
  binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;
  std::shared_ptr<BnRadioVoice> _aidl_impl = std::static_pointer_cast<BnRadioVoice>(::ndk::ICInterface::asInterface(_aidl_binder));
  switch (_aidl_code) {
    case (FIRST_CALL_TRANSACTION + 0 /*acceptCall*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->acceptCall(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 1 /*cancelPendingUssd*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->cancelPendingUssd(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 2 /*conference*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->conference(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 3 /*dial*/): {
      int32_t in_serial;
      ::aidl::android::hardware::radio::voice::Dial in_dialInfo;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_dialInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->dial(in_serial, in_dialInfo);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 4 /*emergencyDial*/): {
      int32_t in_serial;
      ::aidl::android::hardware::radio::voice::Dial in_dialInfo;
      int32_t in_categories;
      std::vector<std::string> in_urns;
      ::aidl::android::hardware::radio::voice::EmergencyCallRouting in_routing;
      bool in_hasKnownUserIntentEmergency;
      bool in_isTesting;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_dialInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_categories);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_urns);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_routing);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_hasKnownUserIntentEmergency);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_isTesting);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->emergencyDial(in_serial, in_dialInfo, in_categories, in_urns, in_routing, in_hasKnownUserIntentEmergency, in_isTesting);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 5 /*exitEmergencyCallbackMode*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->exitEmergencyCallbackMode(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 6 /*explicitCallTransfer*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->explicitCallTransfer(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 7 /*getCallForwardStatus*/): {
      int32_t in_serial;
      ::aidl::android::hardware::radio::voice::CallForwardInfo in_callInfo;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_callInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getCallForwardStatus(in_serial, in_callInfo);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 8 /*getCallWaiting*/): {
      int32_t in_serial;
      int32_t in_serviceClass;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serviceClass);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getCallWaiting(in_serial, in_serviceClass);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 9 /*getClip*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getClip(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 10 /*getClir*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getClir(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 11 /*getCurrentCalls*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getCurrentCalls(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 12 /*getLastCallFailCause*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getLastCallFailCause(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 13 /*getMute*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getMute(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 14 /*getPreferredVoicePrivacy*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getPreferredVoicePrivacy(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 15 /*getTtyMode*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->getTtyMode(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 16 /*handleStkCallSetupRequestFromSim*/): {
      int32_t in_serial;
      bool in_accept;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_accept);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->handleStkCallSetupRequestFromSim(in_serial, in_accept);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 17 /*hangup*/): {
      int32_t in_serial;
      int32_t in_gsmIndex;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_gsmIndex);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->hangup(in_serial, in_gsmIndex);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 18 /*hangupForegroundResumeBackground*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->hangupForegroundResumeBackground(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 19 /*hangupWaitingOrBackground*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->hangupWaitingOrBackground(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 20 /*isVoNrEnabled*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->isVoNrEnabled(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 21 /*rejectCall*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->rejectCall(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 22 /*responseAcknowledgement*/): {

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->responseAcknowledgement();
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 23 /*sendBurstDtmf*/): {
      int32_t in_serial;
      std::string in_dtmf;
      int32_t in_on;
      int32_t in_off;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_dtmf);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_on);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_off);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->sendBurstDtmf(in_serial, in_dtmf, in_on, in_off);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 24 /*sendCdmaFeatureCode*/): {
      int32_t in_serial;
      std::string in_featureCode;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_featureCode);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->sendCdmaFeatureCode(in_serial, in_featureCode);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 25 /*sendDtmf*/): {
      int32_t in_serial;
      std::string in_s;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_s);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->sendDtmf(in_serial, in_s);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 26 /*sendUssd*/): {
      int32_t in_serial;
      std::string in_ussd;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_ussd);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->sendUssd(in_serial, in_ussd);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 27 /*separateConnection*/): {
      int32_t in_serial;
      int32_t in_gsmIndex;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_gsmIndex);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->separateConnection(in_serial, in_gsmIndex);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 28 /*setCallForward*/): {
      int32_t in_serial;
      ::aidl::android::hardware::radio::voice::CallForwardInfo in_callInfo;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_callInfo);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setCallForward(in_serial, in_callInfo);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 29 /*setCallWaiting*/): {
      int32_t in_serial;
      bool in_enable;
      int32_t in_serviceClass;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_enable);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serviceClass);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setCallWaiting(in_serial, in_enable, in_serviceClass);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 30 /*setClir*/): {
      int32_t in_serial;
      int32_t in_status;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_status);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setClir(in_serial, in_status);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 31 /*setMute*/): {
      int32_t in_serial;
      bool in_enable;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_enable);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setMute(in_serial, in_enable);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 32 /*setPreferredVoicePrivacy*/): {
      int32_t in_serial;
      bool in_enable;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_enable);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setPreferredVoicePrivacy(in_serial, in_enable);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 33 /*setResponseFunctions*/): {
      std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceResponse> in_radioVoiceResponse;
      std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceIndication> in_radioVoiceIndication;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_radioVoiceResponse);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_radioVoiceIndication);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setResponseFunctions(in_radioVoiceResponse, in_radioVoiceIndication);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 34 /*setTtyMode*/): {
      int32_t in_serial;
      ::aidl::android::hardware::radio::voice::TtyMode in_mode;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_mode);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setTtyMode(in_serial, in_mode);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 35 /*setVoNrEnabled*/): {
      int32_t in_serial;
      bool in_enable;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_enable);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->setVoNrEnabled(in_serial, in_enable);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 36 /*startDtmf*/): {
      int32_t in_serial;
      std::string in_s;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_s);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->startDtmf(in_serial, in_s);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 37 /*stopDtmf*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->stopDtmf(in_serial);
      _aidl_ret_status = STATUS_OK;
      break;
    }
    case (FIRST_CALL_TRANSACTION + 38 /*switchWaitingOrHoldingAndActive*/): {
      int32_t in_serial;

      _aidl_ret_status = ::ndk::AParcel_readData(_aidl_in, &in_serial);
      if (_aidl_ret_status != STATUS_OK) break;

      ::ndk::ScopedAStatus _aidl_status = _aidl_impl->switchWaitingOrHoldingAndActive(in_serial);
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

static AIBinder_Class* _g_aidl_android_hardware_radio_voice_IRadioVoice_clazz = ::ndk::ICInterface::defineClass(IRadioVoice::descriptor, _aidl_android_hardware_radio_voice_IRadioVoice_onTransact);

BpRadioVoice::BpRadioVoice(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}
BpRadioVoice::~BpRadioVoice() {}

::ndk::ScopedAStatus BpRadioVoice::acceptCall(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 0 /*acceptCall*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->acceptCall(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::cancelPendingUssd(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 1 /*cancelPendingUssd*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->cancelPendingUssd(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::conference(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 2 /*conference*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->conference(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::dial(int32_t in_serial, const ::aidl::android::hardware::radio::voice::Dial& in_dialInfo) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_dialInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 3 /*dial*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->dial(in_serial, in_dialInfo);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::emergencyDial(int32_t in_serial, const ::aidl::android::hardware::radio::voice::Dial& in_dialInfo, int32_t in_categories, const std::vector<std::string>& in_urns, ::aidl::android::hardware::radio::voice::EmergencyCallRouting in_routing, bool in_hasKnownUserIntentEmergency, bool in_isTesting) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_dialInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_categories);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_urns);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_routing);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_hasKnownUserIntentEmergency);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_isTesting);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 4 /*emergencyDial*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->emergencyDial(in_serial, in_dialInfo, in_categories, in_urns, in_routing, in_hasKnownUserIntentEmergency, in_isTesting);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::exitEmergencyCallbackMode(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 5 /*exitEmergencyCallbackMode*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->exitEmergencyCallbackMode(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::explicitCallTransfer(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 6 /*explicitCallTransfer*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->explicitCallTransfer(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getCallForwardStatus(int32_t in_serial, const ::aidl::android::hardware::radio::voice::CallForwardInfo& in_callInfo) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_callInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 7 /*getCallForwardStatus*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getCallForwardStatus(in_serial, in_callInfo);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getCallWaiting(int32_t in_serial, int32_t in_serviceClass) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serviceClass);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 8 /*getCallWaiting*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getCallWaiting(in_serial, in_serviceClass);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getClip(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 9 /*getClip*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getClip(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getClir(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 10 /*getClir*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getClir(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getCurrentCalls(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 11 /*getCurrentCalls*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getCurrentCalls(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getLastCallFailCause(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 12 /*getLastCallFailCause*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getLastCallFailCause(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getMute(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 13 /*getMute*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getMute(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getPreferredVoicePrivacy(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 14 /*getPreferredVoicePrivacy*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getPreferredVoicePrivacy(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getTtyMode(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 15 /*getTtyMode*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getTtyMode(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::handleStkCallSetupRequestFromSim(int32_t in_serial, bool in_accept) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_accept);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 16 /*handleStkCallSetupRequestFromSim*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->handleStkCallSetupRequestFromSim(in_serial, in_accept);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::hangup(int32_t in_serial, int32_t in_gsmIndex) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_gsmIndex);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 17 /*hangup*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->hangup(in_serial, in_gsmIndex);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::hangupForegroundResumeBackground(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 18 /*hangupForegroundResumeBackground*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->hangupForegroundResumeBackground(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::hangupWaitingOrBackground(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 19 /*hangupWaitingOrBackground*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->hangupWaitingOrBackground(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::isVoNrEnabled(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 20 /*isVoNrEnabled*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->isVoNrEnabled(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::rejectCall(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 21 /*rejectCall*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->rejectCall(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::responseAcknowledgement() {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 22 /*responseAcknowledgement*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->responseAcknowledgement();
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::sendBurstDtmf(int32_t in_serial, const std::string& in_dtmf, int32_t in_on, int32_t in_off) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_dtmf);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_on);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_off);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 23 /*sendBurstDtmf*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->sendBurstDtmf(in_serial, in_dtmf, in_on, in_off);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::sendCdmaFeatureCode(int32_t in_serial, const std::string& in_featureCode) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_featureCode);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 24 /*sendCdmaFeatureCode*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->sendCdmaFeatureCode(in_serial, in_featureCode);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::sendDtmf(int32_t in_serial, const std::string& in_s) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_s);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 25 /*sendDtmf*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->sendDtmf(in_serial, in_s);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::sendUssd(int32_t in_serial, const std::string& in_ussd) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_ussd);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 26 /*sendUssd*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->sendUssd(in_serial, in_ussd);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::separateConnection(int32_t in_serial, int32_t in_gsmIndex) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_gsmIndex);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 27 /*separateConnection*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->separateConnection(in_serial, in_gsmIndex);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::setCallForward(int32_t in_serial, const ::aidl::android::hardware::radio::voice::CallForwardInfo& in_callInfo) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_callInfo);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 28 /*setCallForward*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->setCallForward(in_serial, in_callInfo);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::setCallWaiting(int32_t in_serial, bool in_enable, int32_t in_serviceClass) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_enable);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serviceClass);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 29 /*setCallWaiting*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->setCallWaiting(in_serial, in_enable, in_serviceClass);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::setClir(int32_t in_serial, int32_t in_status) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_status);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 30 /*setClir*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->setClir(in_serial, in_status);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::setMute(int32_t in_serial, bool in_enable) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_enable);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 31 /*setMute*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->setMute(in_serial, in_enable);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::setPreferredVoicePrivacy(int32_t in_serial, bool in_enable) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_enable);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 32 /*setPreferredVoicePrivacy*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->setPreferredVoicePrivacy(in_serial, in_enable);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceResponse>& in_radioVoiceResponse, const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceIndication>& in_radioVoiceIndication) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_radioVoiceResponse);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_radioVoiceIndication);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 33 /*setResponseFunctions*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->setResponseFunctions(in_radioVoiceResponse, in_radioVoiceIndication);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::setTtyMode(int32_t in_serial, ::aidl::android::hardware::radio::voice::TtyMode in_mode) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_mode);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 34 /*setTtyMode*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->setTtyMode(in_serial, in_mode);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::setVoNrEnabled(int32_t in_serial, bool in_enable) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_enable);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 35 /*setVoNrEnabled*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->setVoNrEnabled(in_serial, in_enable);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::startDtmf(int32_t in_serial, const std::string& in_s) {
  binder_status_t _aidl_ret_status = STATUS_OK;
  ::ndk::ScopedAStatus _aidl_status;
  ::ndk::ScopedAParcel _aidl_in;
  ::ndk::ScopedAParcel _aidl_out;

  _aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_serial);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = ::ndk::AParcel_writeData(_aidl_in.get(), in_s);
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_ret_status = AIBinder_transact(
    asBinder().get(),
    (FIRST_CALL_TRANSACTION + 36 /*startDtmf*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->startDtmf(in_serial, in_s);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::stopDtmf(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 37 /*stopDtmf*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->stopDtmf(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::switchWaitingOrHoldingAndActive(int32_t in_serial) {
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
    (FIRST_CALL_TRANSACTION + 38 /*switchWaitingOrHoldingAndActive*/),
    _aidl_in.getR(),
    _aidl_out.getR(),
    FLAG_ONEWAY
    #ifdef BINDER_STABILITY_SUPPORT
    | FLAG_PRIVATE_LOCAL
    #endif  // BINDER_STABILITY_SUPPORT
    );
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->switchWaitingOrHoldingAndActive(in_serial);
    goto _aidl_status_return;
  }
  if (_aidl_ret_status != STATUS_OK) goto _aidl_error;

  _aidl_error:
  _aidl_status.set(AStatus_fromStatus(_aidl_ret_status));
  _aidl_status_return:
  return _aidl_status;
}
::ndk::ScopedAStatus BpRadioVoice::getInterfaceVersion(int32_t* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getInterfaceVersion(_aidl_return);
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
::ndk::ScopedAStatus BpRadioVoice::getInterfaceHash(std::string* _aidl_return) {
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
  if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && IRadioVoice::getDefaultImpl()) {
    _aidl_status = IRadioVoice::getDefaultImpl()->getInterfaceHash(_aidl_return);
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
// Source for BnRadioVoice
BnRadioVoice::BnRadioVoice() {}
BnRadioVoice::~BnRadioVoice() {}
::ndk::SpAIBinder BnRadioVoice::createBinder() {
  AIBinder* binder = AIBinder_new(_g_aidl_android_hardware_radio_voice_IRadioVoice_clazz, static_cast<void*>(this));
  #ifdef BINDER_STABILITY_SUPPORT
  AIBinder_markVintfStability(binder);
  #endif  // BINDER_STABILITY_SUPPORT
  return ::ndk::SpAIBinder(binder);
}
::ndk::ScopedAStatus BnRadioVoice::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = IRadioVoice::version;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus BnRadioVoice::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = IRadioVoice::hash;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
// Source for IRadioVoice
const char* IRadioVoice::descriptor = "android.hardware.radio.voice.IRadioVoice";
IRadioVoice::IRadioVoice() {}
IRadioVoice::~IRadioVoice() {}


std::shared_ptr<IRadioVoice> IRadioVoice::fromBinder(const ::ndk::SpAIBinder& binder) {
  if (!AIBinder_associateClass(binder.get(), _g_aidl_android_hardware_radio_voice_IRadioVoice_clazz)) { return nullptr; }
  std::shared_ptr<::ndk::ICInterface> interface = ::ndk::ICInterface::asInterface(binder.get());
  if (interface) {
    return std::static_pointer_cast<IRadioVoice>(interface);
  }
  return ::ndk::SharedRefBase::make<BpRadioVoice>(binder);
}

binder_status_t IRadioVoice::writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioVoice>& instance) {
  return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : nullptr);
}
binder_status_t IRadioVoice::readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioVoice>* instance) {
  ::ndk::SpAIBinder binder;
  binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());
  if (status != STATUS_OK) return status;
  *instance = IRadioVoice::fromBinder(binder);
  return STATUS_OK;
}
bool IRadioVoice::setDefaultImpl(const std::shared_ptr<IRadioVoice>& impl) {
  // Only one user of this interface can use this function
  // at a time. This is a heuristic to detect if two different
  // users in the same process use this function.
  assert(!IRadioVoice::default_impl);
  if (impl) {
    IRadioVoice::default_impl = impl;
    return true;
  }
  return false;
}
const std::shared_ptr<IRadioVoice>& IRadioVoice::getDefaultImpl() {
  return IRadioVoice::default_impl;
}
std::shared_ptr<IRadioVoice> IRadioVoice::default_impl = nullptr;
::ndk::ScopedAStatus IRadioVoiceDefault::acceptCall(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::cancelPendingUssd(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::conference(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::dial(int32_t /*in_serial*/, const ::aidl::android::hardware::radio::voice::Dial& /*in_dialInfo*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::emergencyDial(int32_t /*in_serial*/, const ::aidl::android::hardware::radio::voice::Dial& /*in_dialInfo*/, int32_t /*in_categories*/, const std::vector<std::string>& /*in_urns*/, ::aidl::android::hardware::radio::voice::EmergencyCallRouting /*in_routing*/, bool /*in_hasKnownUserIntentEmergency*/, bool /*in_isTesting*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::exitEmergencyCallbackMode(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::explicitCallTransfer(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getCallForwardStatus(int32_t /*in_serial*/, const ::aidl::android::hardware::radio::voice::CallForwardInfo& /*in_callInfo*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getCallWaiting(int32_t /*in_serial*/, int32_t /*in_serviceClass*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getClip(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getClir(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getCurrentCalls(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getLastCallFailCause(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getMute(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getPreferredVoicePrivacy(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getTtyMode(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::handleStkCallSetupRequestFromSim(int32_t /*in_serial*/, bool /*in_accept*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::hangup(int32_t /*in_serial*/, int32_t /*in_gsmIndex*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::hangupForegroundResumeBackground(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::hangupWaitingOrBackground(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::isVoNrEnabled(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::rejectCall(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::responseAcknowledgement() {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::sendBurstDtmf(int32_t /*in_serial*/, const std::string& /*in_dtmf*/, int32_t /*in_on*/, int32_t /*in_off*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::sendCdmaFeatureCode(int32_t /*in_serial*/, const std::string& /*in_featureCode*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::sendDtmf(int32_t /*in_serial*/, const std::string& /*in_s*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::sendUssd(int32_t /*in_serial*/, const std::string& /*in_ussd*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::separateConnection(int32_t /*in_serial*/, int32_t /*in_gsmIndex*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::setCallForward(int32_t /*in_serial*/, const ::aidl::android::hardware::radio::voice::CallForwardInfo& /*in_callInfo*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::setCallWaiting(int32_t /*in_serial*/, bool /*in_enable*/, int32_t /*in_serviceClass*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::setClir(int32_t /*in_serial*/, int32_t /*in_status*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::setMute(int32_t /*in_serial*/, bool /*in_enable*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::setPreferredVoicePrivacy(int32_t /*in_serial*/, bool /*in_enable*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceResponse>& /*in_radioVoiceResponse*/, const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceIndication>& /*in_radioVoiceIndication*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::setTtyMode(int32_t /*in_serial*/, ::aidl::android::hardware::radio::voice::TtyMode /*in_mode*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::setVoNrEnabled(int32_t /*in_serial*/, bool /*in_enable*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::startDtmf(int32_t /*in_serial*/, const std::string& /*in_s*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::stopDtmf(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::switchWaitingOrHoldingAndActive(int32_t /*in_serial*/) {
  ::ndk::ScopedAStatus _aidl_status;
  _aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));
  return _aidl_status;
}
::ndk::ScopedAStatus IRadioVoiceDefault::getInterfaceVersion(int32_t* _aidl_return) {
  *_aidl_return = 0;
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::ScopedAStatus IRadioVoiceDefault::getInterfaceHash(std::string* _aidl_return) {
  *_aidl_return = "";
  return ::ndk::ScopedAStatus(AStatus_newOk());
}
::ndk::SpAIBinder IRadioVoiceDefault::asBinder() {
  return ::ndk::SpAIBinder();
}
bool IRadioVoiceDefault::isRemote() {
  return false;
}
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
