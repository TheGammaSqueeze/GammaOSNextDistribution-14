#pragma once

#include "aidl/android/hardware/radio/voice/IRadioVoice.h"

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
namespace voice {
class BnRadioVoice : public ::ndk::BnCInterface<IRadioVoice> {
public:
  BnRadioVoice();
  virtual ~BnRadioVoice();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioVoiceDelegator : public BnRadioVoice {
public:
  explicit IRadioVoiceDelegator(const std::shared_ptr<IRadioVoice> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioVoice::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus acceptCall(int32_t in_serial) override {
    return _impl->acceptCall(in_serial);
  }
  ::ndk::ScopedAStatus cancelPendingUssd(int32_t in_serial) override {
    return _impl->cancelPendingUssd(in_serial);
  }
  ::ndk::ScopedAStatus conference(int32_t in_serial) override {
    return _impl->conference(in_serial);
  }
  ::ndk::ScopedAStatus dial(int32_t in_serial, const ::aidl::android::hardware::radio::voice::Dial& in_dialInfo) override {
    return _impl->dial(in_serial, in_dialInfo);
  }
  ::ndk::ScopedAStatus emergencyDial(int32_t in_serial, const ::aidl::android::hardware::radio::voice::Dial& in_dialInfo, int32_t in_categories, const std::vector<std::string>& in_urns, ::aidl::android::hardware::radio::voice::EmergencyCallRouting in_routing, bool in_hasKnownUserIntentEmergency, bool in_isTesting) override {
    return _impl->emergencyDial(in_serial, in_dialInfo, in_categories, in_urns, in_routing, in_hasKnownUserIntentEmergency, in_isTesting);
  }
  ::ndk::ScopedAStatus exitEmergencyCallbackMode(int32_t in_serial) override {
    return _impl->exitEmergencyCallbackMode(in_serial);
  }
  ::ndk::ScopedAStatus explicitCallTransfer(int32_t in_serial) override {
    return _impl->explicitCallTransfer(in_serial);
  }
  ::ndk::ScopedAStatus getCallForwardStatus(int32_t in_serial, const ::aidl::android::hardware::radio::voice::CallForwardInfo& in_callInfo) override {
    return _impl->getCallForwardStatus(in_serial, in_callInfo);
  }
  ::ndk::ScopedAStatus getCallWaiting(int32_t in_serial, int32_t in_serviceClass) override {
    return _impl->getCallWaiting(in_serial, in_serviceClass);
  }
  ::ndk::ScopedAStatus getClip(int32_t in_serial) override {
    return _impl->getClip(in_serial);
  }
  ::ndk::ScopedAStatus getClir(int32_t in_serial) override {
    return _impl->getClir(in_serial);
  }
  ::ndk::ScopedAStatus getCurrentCalls(int32_t in_serial) override {
    return _impl->getCurrentCalls(in_serial);
  }
  ::ndk::ScopedAStatus getLastCallFailCause(int32_t in_serial) override {
    return _impl->getLastCallFailCause(in_serial);
  }
  ::ndk::ScopedAStatus getMute(int32_t in_serial) override {
    return _impl->getMute(in_serial);
  }
  ::ndk::ScopedAStatus getPreferredVoicePrivacy(int32_t in_serial) override {
    return _impl->getPreferredVoicePrivacy(in_serial);
  }
  ::ndk::ScopedAStatus getTtyMode(int32_t in_serial) override {
    return _impl->getTtyMode(in_serial);
  }
  ::ndk::ScopedAStatus handleStkCallSetupRequestFromSim(int32_t in_serial, bool in_accept) override {
    return _impl->handleStkCallSetupRequestFromSim(in_serial, in_accept);
  }
  ::ndk::ScopedAStatus hangup(int32_t in_serial, int32_t in_gsmIndex) override {
    return _impl->hangup(in_serial, in_gsmIndex);
  }
  ::ndk::ScopedAStatus hangupForegroundResumeBackground(int32_t in_serial) override {
    return _impl->hangupForegroundResumeBackground(in_serial);
  }
  ::ndk::ScopedAStatus hangupWaitingOrBackground(int32_t in_serial) override {
    return _impl->hangupWaitingOrBackground(in_serial);
  }
  ::ndk::ScopedAStatus isVoNrEnabled(int32_t in_serial) override {
    return _impl->isVoNrEnabled(in_serial);
  }
  ::ndk::ScopedAStatus rejectCall(int32_t in_serial) override {
    return _impl->rejectCall(in_serial);
  }
  ::ndk::ScopedAStatus responseAcknowledgement() override {
    return _impl->responseAcknowledgement();
  }
  ::ndk::ScopedAStatus sendBurstDtmf(int32_t in_serial, const std::string& in_dtmf, int32_t in_on, int32_t in_off) override {
    return _impl->sendBurstDtmf(in_serial, in_dtmf, in_on, in_off);
  }
  ::ndk::ScopedAStatus sendCdmaFeatureCode(int32_t in_serial, const std::string& in_featureCode) override {
    return _impl->sendCdmaFeatureCode(in_serial, in_featureCode);
  }
  ::ndk::ScopedAStatus sendDtmf(int32_t in_serial, const std::string& in_s) override {
    return _impl->sendDtmf(in_serial, in_s);
  }
  ::ndk::ScopedAStatus sendUssd(int32_t in_serial, const std::string& in_ussd) override {
    return _impl->sendUssd(in_serial, in_ussd);
  }
  ::ndk::ScopedAStatus separateConnection(int32_t in_serial, int32_t in_gsmIndex) override {
    return _impl->separateConnection(in_serial, in_gsmIndex);
  }
  ::ndk::ScopedAStatus setCallForward(int32_t in_serial, const ::aidl::android::hardware::radio::voice::CallForwardInfo& in_callInfo) override {
    return _impl->setCallForward(in_serial, in_callInfo);
  }
  ::ndk::ScopedAStatus setCallWaiting(int32_t in_serial, bool in_enable, int32_t in_serviceClass) override {
    return _impl->setCallWaiting(in_serial, in_enable, in_serviceClass);
  }
  ::ndk::ScopedAStatus setClir(int32_t in_serial, int32_t in_status) override {
    return _impl->setClir(in_serial, in_status);
  }
  ::ndk::ScopedAStatus setMute(int32_t in_serial, bool in_enable) override {
    return _impl->setMute(in_serial, in_enable);
  }
  ::ndk::ScopedAStatus setPreferredVoicePrivacy(int32_t in_serial, bool in_enable) override {
    return _impl->setPreferredVoicePrivacy(in_serial, in_enable);
  }
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceResponse>& in_radioVoiceResponse, const std::shared_ptr<::aidl::android::hardware::radio::voice::IRadioVoiceIndication>& in_radioVoiceIndication) override {
    return _impl->setResponseFunctions(in_radioVoiceResponse, in_radioVoiceIndication);
  }
  ::ndk::ScopedAStatus setTtyMode(int32_t in_serial, ::aidl::android::hardware::radio::voice::TtyMode in_mode) override {
    return _impl->setTtyMode(in_serial, in_mode);
  }
  ::ndk::ScopedAStatus setVoNrEnabled(int32_t in_serial, bool in_enable) override {
    return _impl->setVoNrEnabled(in_serial, in_enable);
  }
  ::ndk::ScopedAStatus startDtmf(int32_t in_serial, const std::string& in_s) override {
    return _impl->startDtmf(in_serial, in_s);
  }
  ::ndk::ScopedAStatus stopDtmf(int32_t in_serial) override {
    return _impl->stopDtmf(in_serial);
  }
  ::ndk::ScopedAStatus switchWaitingOrHoldingAndActive(int32_t in_serial) override {
    return _impl->switchWaitingOrHoldingAndActive(in_serial);
  }
protected:
private:
  std::shared_ptr<IRadioVoice> _impl;
};

}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
