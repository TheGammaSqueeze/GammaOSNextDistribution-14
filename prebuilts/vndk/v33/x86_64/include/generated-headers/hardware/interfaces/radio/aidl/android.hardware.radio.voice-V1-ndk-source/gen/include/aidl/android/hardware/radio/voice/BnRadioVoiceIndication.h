#pragma once

#include "aidl/android/hardware/radio/voice/IRadioVoiceIndication.h"

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
class BnRadioVoiceIndication : public ::ndk::BnCInterface<IRadioVoiceIndication> {
public:
  BnRadioVoiceIndication();
  virtual ~BnRadioVoiceIndication();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioVoiceIndicationDelegator : public BnRadioVoiceIndication {
public:
  explicit IRadioVoiceIndicationDelegator(const std::shared_ptr<IRadioVoiceIndication> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioVoiceIndication::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus callRing(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_isGsm, const ::aidl::android::hardware::radio::voice::CdmaSignalInfoRecord& in_record) override {
    return _impl->callRing(in_type, in_isGsm, in_record);
  }
  ::ndk::ScopedAStatus callStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->callStateChanged(in_type);
  }
  ::ndk::ScopedAStatus cdmaCallWaiting(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::voice::CdmaCallWaiting& in_callWaitingRecord) override {
    return _impl->cdmaCallWaiting(in_type, in_callWaitingRecord);
  }
  ::ndk::ScopedAStatus cdmaInfoRec(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::voice::CdmaInformationRecord>& in_records) override {
    return _impl->cdmaInfoRec(in_type, in_records);
  }
  ::ndk::ScopedAStatus cdmaOtaProvisionStatus(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus in_status) override {
    return _impl->cdmaOtaProvisionStatus(in_type, in_status);
  }
  ::ndk::ScopedAStatus currentEmergencyNumberList(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::voice::EmergencyNumber>& in_emergencyNumberList) override {
    return _impl->currentEmergencyNumberList(in_type, in_emergencyNumberList);
  }
  ::ndk::ScopedAStatus enterEmergencyCallbackMode(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->enterEmergencyCallbackMode(in_type);
  }
  ::ndk::ScopedAStatus exitEmergencyCallbackMode(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->exitEmergencyCallbackMode(in_type);
  }
  ::ndk::ScopedAStatus indicateRingbackTone(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_start) override {
    return _impl->indicateRingbackTone(in_type, in_start);
  }
  ::ndk::ScopedAStatus onSupplementaryServiceIndication(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::voice::StkCcUnsolSsResult& in_ss) override {
    return _impl->onSupplementaryServiceIndication(in_type, in_ss);
  }
  ::ndk::ScopedAStatus onUssd(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::voice::UssdModeType in_modeType, const std::string& in_msg) override {
    return _impl->onUssd(in_type, in_modeType, in_msg);
  }
  ::ndk::ScopedAStatus resendIncallMute(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->resendIncallMute(in_type);
  }
  ::ndk::ScopedAStatus srvccStateNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::voice::SrvccState in_state) override {
    return _impl->srvccStateNotify(in_type, in_state);
  }
  ::ndk::ScopedAStatus stkCallControlAlphaNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_alpha) override {
    return _impl->stkCallControlAlphaNotify(in_type, in_alpha);
  }
  ::ndk::ScopedAStatus stkCallSetup(::aidl::android::hardware::radio::RadioIndicationType in_type, int64_t in_timeout) override {
    return _impl->stkCallSetup(in_type, in_timeout);
  }
protected:
private:
  std::shared_ptr<IRadioVoiceIndication> _impl;
};

}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
