#pragma once

#include "aidl/android/hardware/radio/voice/IRadioVoice.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class BpRadioVoice : public ::ndk::BpCInterface<IRadioVoice> {
public:
  explicit BpRadioVoice(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioVoice();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
