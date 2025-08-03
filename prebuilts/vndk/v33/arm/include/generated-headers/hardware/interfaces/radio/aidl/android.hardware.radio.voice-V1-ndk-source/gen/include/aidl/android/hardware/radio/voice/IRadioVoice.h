#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/voice/CallForwardInfo.h>
#include <aidl/android/hardware/radio/voice/Dial.h>
#include <aidl/android/hardware/radio/voice/EmergencyCallRouting.h>
#include <aidl/android/hardware/radio/voice/IRadioVoiceIndication.h>
#include <aidl/android/hardware/radio/voice/IRadioVoiceResponse.h>
#include <aidl/android/hardware/radio/voice/TtyMode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class IRadioVoice : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioVoice();
  virtual ~IRadioVoice();

  static const int32_t version = 1;
  static inline const std::string hash = "e9ffc70247a89e6c1e526c6334c37da46f33ebea";
  static constexpr uint32_t TRANSACTION_acceptCall = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_cancelPendingUssd = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_conference = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_dial = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_emergencyDial = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_exitEmergencyCallbackMode = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_explicitCallTransfer = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getCallForwardStatus = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getCallWaiting = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getClip = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getClir = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getCurrentCalls = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getLastCallFailCause = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_getMute = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_getPreferredVoicePrivacy = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_getTtyMode = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_handleStkCallSetupRequestFromSim = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_hangup = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_hangupForegroundResumeBackground = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_hangupWaitingOrBackground = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_isVoNrEnabled = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_rejectCall = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_responseAcknowledgement = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_sendBurstDtmf = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_sendCdmaFeatureCode = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_sendDtmf = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_sendUssd = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_separateConnection = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_setCallForward = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_setCallWaiting = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_setClir = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_setMute = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_setPreferredVoicePrivacy = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_setResponseFunctions = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_setTtyMode = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_setVoNrEnabled = FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_startDtmf = FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_stopDtmf = FIRST_CALL_TRANSACTION + 37;
  static constexpr uint32_t TRANSACTION_switchWaitingOrHoldingAndActive = FIRST_CALL_TRANSACTION + 38;

  static std::shared_ptr<IRadioVoice> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioVoice>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioVoice>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioVoice>& impl);
  static const std::shared_ptr<IRadioVoice>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus acceptCall(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus cancelPendingUssd(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus conference(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus dial(int32_t in_serial, const ::aidl::android::hardware::radio::voice::Dial& in_dialInfo) = 0;
  virtual ::ndk::ScopedAStatus emergencyDial(int32_t in_serial, const ::aidl::android::hardware::radio::voice::Dial& in_dialInfo, int32_t in_categories, const std::vector<std::string>& in_urns, ::aidl::android::hardware::radio::voice::EmergencyCallRouting in_routing, bool in_hasKnownUserIntentEmergency, bool in_isTesting) = 0;
  virtual ::ndk::ScopedAStatus exitEmergencyCallbackMode(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus explicitCallTransfer(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getCallForwardStatus(int32_t in_serial, const ::aidl::android::hardware::radio::voice::CallForwardInfo& in_callInfo) = 0;
  virtual ::ndk::ScopedAStatus getCallWaiting(int32_t in_serial, int32_t in_serviceClass) = 0;
  virtual ::ndk::ScopedAStatus getClip(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getClir(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getCurrentCalls(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getLastCallFailCause(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getMute(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getPreferredVoicePrivacy(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getTtyMode(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus handleStkCallSetupRequestFromSim(int32_t in_serial, bool in_accept) = 0;
  virtual ::ndk::ScopedAStatus hangup(int32_t in_serial, int32_t in_gsmIndex) = 0;
  virtual ::ndk::ScopedAStatus hangupForegroundResumeBackground(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus hangupWaitingOrBackground(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus isVoNrEnabled(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus rejectCall(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus responseAcknowledgement() = 0;
  virtual ::ndk::ScopedAStatus sendBurstDtmf(int32_t in_serial, const std::string& in_dtmf, int32_t in_on, int32_t in_off) = 0;
  virtual ::ndk::ScopedAStatus sendCdmaFeatureCode(int32_t in_serial, const std::string& in_featureCode) = 0;
  virtual ::ndk::ScopedAStatus sendDtmf(int32_t in_serial, const std::string& in_s) = 0;
  virtual ::ndk::ScopedAStatus sendUssd(int32_t in_serial, const std::string& in_ussd) = 0;
  virtual ::ndk::ScopedAStatus separateConnection(int32_t in_serial, int32_t in_gsmIndex) = 0;
  virtual ::ndk::ScopedAStatus setCallForward(int32_t in_serial, const ::aidl::android::hardware::radio::voice::CallForwardInfo& in_callInfo) = 0;
  virtual ::ndk::ScopedAStatus setCallWaiting(int32_t in_serial, bool in_enable, int32_t in_serviceClass) = 0;
  virtual ::ndk::ScopedAStatus setClir(int32_t in_serial, int32_t in_status) = 0;
  virtual ::ndk::ScopedAStatus setMute(int32_t in_serial, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setPreferredVoicePrivacy(int32_t in_serial, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceResponse>& in_radioVoiceResponse, const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceIndication>& in_radioVoiceIndication) = 0;
  virtual ::ndk::ScopedAStatus setTtyMode(int32_t in_serial, ::aidl::android::hardware::radio::voice::TtyMode in_mode) = 0;
  virtual ::ndk::ScopedAStatus setVoNrEnabled(int32_t in_serial, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus startDtmf(int32_t in_serial, const std::string& in_s) = 0;
  virtual ::ndk::ScopedAStatus stopDtmf(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus switchWaitingOrHoldingAndActive(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioVoice> default_impl;
};
class IRadioVoiceDefault : public IRadioVoice {
public:
  ::ndk::ScopedAStatus acceptCall(int32_t in_serial) override;
  ::ndk::ScopedAStatus cancelPendingUssd(int32_t in_serial) override;
  ::ndk::ScopedAStatus conference(int32_t in_serial) override;
  ::ndk::ScopedAStatus dial(int32_t in_serial, const ::aidl::android::hardware::radio::voice::Dial& in_dialInfo) override;
  ::ndk::ScopedAStatus emergencyDial(int32_t in_serial, const ::aidl::android::hardware::radio::voice::Dial& in_dialInfo, int32_t in_categories, const std::vector<std::string>& in_urns, ::aidl::android::hardware::radio::voice::EmergencyCallRouting in_routing, bool in_hasKnownUserIntentEmergency, bool in_isTesting) override;
  ::ndk::ScopedAStatus exitEmergencyCallbackMode(int32_t in_serial) override;
  ::ndk::ScopedAStatus explicitCallTransfer(int32_t in_serial) override;
  ::ndk::ScopedAStatus getCallForwardStatus(int32_t in_serial, const ::aidl::android::hardware::radio::voice::CallForwardInfo& in_callInfo) override;
  ::ndk::ScopedAStatus getCallWaiting(int32_t in_serial, int32_t in_serviceClass) override;
  ::ndk::ScopedAStatus getClip(int32_t in_serial) override;
  ::ndk::ScopedAStatus getClir(int32_t in_serial) override;
  ::ndk::ScopedAStatus getCurrentCalls(int32_t in_serial) override;
  ::ndk::ScopedAStatus getLastCallFailCause(int32_t in_serial) override;
  ::ndk::ScopedAStatus getMute(int32_t in_serial) override;
  ::ndk::ScopedAStatus getPreferredVoicePrivacy(int32_t in_serial) override;
  ::ndk::ScopedAStatus getTtyMode(int32_t in_serial) override;
  ::ndk::ScopedAStatus handleStkCallSetupRequestFromSim(int32_t in_serial, bool in_accept) override;
  ::ndk::ScopedAStatus hangup(int32_t in_serial, int32_t in_gsmIndex) override;
  ::ndk::ScopedAStatus hangupForegroundResumeBackground(int32_t in_serial) override;
  ::ndk::ScopedAStatus hangupWaitingOrBackground(int32_t in_serial) override;
  ::ndk::ScopedAStatus isVoNrEnabled(int32_t in_serial) override;
  ::ndk::ScopedAStatus rejectCall(int32_t in_serial) override;
  ::ndk::ScopedAStatus responseAcknowledgement() override;
  ::ndk::ScopedAStatus sendBurstDtmf(int32_t in_serial, const std::string& in_dtmf, int32_t in_on, int32_t in_off) override;
  ::ndk::ScopedAStatus sendCdmaFeatureCode(int32_t in_serial, const std::string& in_featureCode) override;
  ::ndk::ScopedAStatus sendDtmf(int32_t in_serial, const std::string& in_s) override;
  ::ndk::ScopedAStatus sendUssd(int32_t in_serial, const std::string& in_ussd) override;
  ::ndk::ScopedAStatus separateConnection(int32_t in_serial, int32_t in_gsmIndex) override;
  ::ndk::ScopedAStatus setCallForward(int32_t in_serial, const ::aidl::android::hardware::radio::voice::CallForwardInfo& in_callInfo) override;
  ::ndk::ScopedAStatus setCallWaiting(int32_t in_serial, bool in_enable, int32_t in_serviceClass) override;
  ::ndk::ScopedAStatus setClir(int32_t in_serial, int32_t in_status) override;
  ::ndk::ScopedAStatus setMute(int32_t in_serial, bool in_enable) override;
  ::ndk::ScopedAStatus setPreferredVoicePrivacy(int32_t in_serial, bool in_enable) override;
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceResponse>& in_radioVoiceResponse, const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceIndication>& in_radioVoiceIndication) override;
  ::ndk::ScopedAStatus setTtyMode(int32_t in_serial, ::aidl::android::hardware::radio::voice::TtyMode in_mode) override;
  ::ndk::ScopedAStatus setVoNrEnabled(int32_t in_serial, bool in_enable) override;
  ::ndk::ScopedAStatus startDtmf(int32_t in_serial, const std::string& in_s) override;
  ::ndk::ScopedAStatus stopDtmf(int32_t in_serial) override;
  ::ndk::ScopedAStatus switchWaitingOrHoldingAndActive(int32_t in_serial) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
