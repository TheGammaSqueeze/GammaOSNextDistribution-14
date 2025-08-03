#pragma once

#include "aidl/android/hardware/radio/voice/IRadioVoiceResponse.h"

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
class BnRadioVoiceResponse : public ::ndk::BnCInterface<IRadioVoiceResponse> {
public:
  BnRadioVoiceResponse();
  virtual ~BnRadioVoiceResponse();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioVoiceResponseDelegator : public BnRadioVoiceResponse {
public:
  explicit IRadioVoiceResponseDelegator(const std::shared_ptr<IRadioVoiceResponse> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioVoiceResponse::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus acceptCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->acceptCallResponse(in_info);
  }
  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override {
    return _impl->acknowledgeRequest(in_serial);
  }
  ::ndk::ScopedAStatus cancelPendingUssdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->cancelPendingUssdResponse(in_info);
  }
  ::ndk::ScopedAStatus conferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->conferenceResponse(in_info);
  }
  ::ndk::ScopedAStatus dialResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->dialResponse(in_info);
  }
  ::ndk::ScopedAStatus emergencyDialResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->emergencyDialResponse(in_info);
  }
  ::ndk::ScopedAStatus exitEmergencyCallbackModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->exitEmergencyCallbackModeResponse(in_info);
  }
  ::ndk::ScopedAStatus explicitCallTransferResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->explicitCallTransferResponse(in_info);
  }
  ::ndk::ScopedAStatus getCallForwardStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::voice::CallForwardInfo>& in_callForwardInfos) override {
    return _impl->getCallForwardStatusResponse(in_info, in_callForwardInfos);
  }
  ::ndk::ScopedAStatus getCallWaitingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable, int32_t in_serviceClass) override {
    return _impl->getCallWaitingResponse(in_info, in_enable, in_serviceClass);
  }
  ::ndk::ScopedAStatus getClipResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::voice::ClipStatus in_status) override {
    return _impl->getClipResponse(in_info, in_status);
  }
  ::ndk::ScopedAStatus getClirResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_n, int32_t in_m) override {
    return _impl->getClirResponse(in_info, in_n, in_m);
  }
  ::ndk::ScopedAStatus getCurrentCallsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::voice::Call>& in_calls) override {
    return _impl->getCurrentCallsResponse(in_info, in_calls);
  }
  ::ndk::ScopedAStatus getLastCallFailCauseResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::voice::LastCallFailCauseInfo& in_failCauseinfo) override {
    return _impl->getLastCallFailCauseResponse(in_info, in_failCauseinfo);
  }
  ::ndk::ScopedAStatus getMuteResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable) override {
    return _impl->getMuteResponse(in_info, in_enable);
  }
  ::ndk::ScopedAStatus getPreferredVoicePrivacyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable) override {
    return _impl->getPreferredVoicePrivacyResponse(in_info, in_enable);
  }
  ::ndk::ScopedAStatus getTtyModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::voice::TtyMode in_mode) override {
    return _impl->getTtyModeResponse(in_info, in_mode);
  }
  ::ndk::ScopedAStatus handleStkCallSetupRequestFromSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->handleStkCallSetupRequestFromSimResponse(in_info);
  }
  ::ndk::ScopedAStatus hangupConnectionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->hangupConnectionResponse(in_info);
  }
  ::ndk::ScopedAStatus hangupForegroundResumeBackgroundResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->hangupForegroundResumeBackgroundResponse(in_info);
  }
  ::ndk::ScopedAStatus hangupWaitingOrBackgroundResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->hangupWaitingOrBackgroundResponse(in_info);
  }
  ::ndk::ScopedAStatus isVoNrEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable) override {
    return _impl->isVoNrEnabledResponse(in_info, in_enable);
  }
  ::ndk::ScopedAStatus rejectCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->rejectCallResponse(in_info);
  }
  ::ndk::ScopedAStatus sendBurstDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->sendBurstDtmfResponse(in_info);
  }
  ::ndk::ScopedAStatus sendCdmaFeatureCodeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->sendCdmaFeatureCodeResponse(in_info);
  }
  ::ndk::ScopedAStatus sendDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->sendDtmfResponse(in_info);
  }
  ::ndk::ScopedAStatus sendUssdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->sendUssdResponse(in_info);
  }
  ::ndk::ScopedAStatus separateConnectionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->separateConnectionResponse(in_info);
  }
  ::ndk::ScopedAStatus setCallForwardResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setCallForwardResponse(in_info);
  }
  ::ndk::ScopedAStatus setCallWaitingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setCallWaitingResponse(in_info);
  }
  ::ndk::ScopedAStatus setClirResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setClirResponse(in_info);
  }
  ::ndk::ScopedAStatus setMuteResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setMuteResponse(in_info);
  }
  ::ndk::ScopedAStatus setPreferredVoicePrivacyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setPreferredVoicePrivacyResponse(in_info);
  }
  ::ndk::ScopedAStatus setTtyModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setTtyModeResponse(in_info);
  }
  ::ndk::ScopedAStatus setVoNrEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->setVoNrEnabledResponse(in_info);
  }
  ::ndk::ScopedAStatus startDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->startDtmfResponse(in_info);
  }
  ::ndk::ScopedAStatus stopDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->stopDtmfResponse(in_info);
  }
  ::ndk::ScopedAStatus switchWaitingOrHoldingAndActiveResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override {
    return _impl->switchWaitingOrHoldingAndActiveResponse(in_info);
  }
protected:
private:
  std::shared_ptr<IRadioVoiceResponse> _impl;
};

}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
