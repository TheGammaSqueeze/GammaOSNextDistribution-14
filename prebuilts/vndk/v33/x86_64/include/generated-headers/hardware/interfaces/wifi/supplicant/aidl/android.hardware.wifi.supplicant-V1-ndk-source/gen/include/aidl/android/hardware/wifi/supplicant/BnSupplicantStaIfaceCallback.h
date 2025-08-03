#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantStaIfaceCallback.h"

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
namespace wifi {
namespace supplicant {
class BnSupplicantStaIfaceCallback : public ::ndk::BnCInterface<ISupplicantStaIfaceCallback> {
public:
  BnSupplicantStaIfaceCallback();
  virtual ~BnSupplicantStaIfaceCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISupplicantStaIfaceCallbackDelegator : public BnSupplicantStaIfaceCallback {
public:
  explicit ISupplicantStaIfaceCallbackDelegator(const std::shared_ptr<ISupplicantStaIfaceCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISupplicantStaIfaceCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onAnqpQueryDone(const std::vector<uint8_t>& in_bssid, const ::aidl::android::hardware::wifi::supplicant::AnqpData& in_data, const ::aidl::android::hardware::wifi::supplicant::Hs20AnqpData& in_hs20Data) override {
    return _impl->onAnqpQueryDone(in_bssid, in_data, in_hs20Data);
  }
  ::ndk::ScopedAStatus onAssociationRejected(const ::aidl::android::hardware::wifi::supplicant::AssociationRejectionData& in_assocRejectData) override {
    return _impl->onAssociationRejected(in_assocRejectData);
  }
  ::ndk::ScopedAStatus onAuthenticationTimeout(const std::vector<uint8_t>& in_bssid) override {
    return _impl->onAuthenticationTimeout(in_bssid);
  }
  ::ndk::ScopedAStatus onAuxiliarySupplicantEvent(::aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode in_eventCode, const std::vector<uint8_t>& in_bssid, const std::string& in_reasonString) override {
    return _impl->onAuxiliarySupplicantEvent(in_eventCode, in_bssid, in_reasonString);
  }
  ::ndk::ScopedAStatus onBssTmHandlingDone(const ::aidl::android::hardware::wifi::supplicant::BssTmData& in_tmData) override {
    return _impl->onBssTmHandlingDone(in_tmData);
  }
  ::ndk::ScopedAStatus onBssidChanged(::aidl::android::hardware::wifi::supplicant::BssidChangeReason in_reason, const std::vector<uint8_t>& in_bssid) override {
    return _impl->onBssidChanged(in_reason, in_bssid);
  }
  ::ndk::ScopedAStatus onDisconnected(const std::vector<uint8_t>& in_bssid, bool in_locallyGenerated, ::aidl::android::hardware::wifi::supplicant::StaIfaceReasonCode in_reasonCode) override {
    return _impl->onDisconnected(in_bssid, in_locallyGenerated, in_reasonCode);
  }
  ::ndk::ScopedAStatus onDppFailure(::aidl::android::hardware::wifi::supplicant::DppFailureCode in_code, const std::string& in_ssid, const std::string& in_channelList, const std::vector<char16_t>& in_bandList) override {
    return _impl->onDppFailure(in_code, in_ssid, in_channelList, in_bandList);
  }
  ::ndk::ScopedAStatus onDppProgress(::aidl::android::hardware::wifi::supplicant::DppProgressCode in_code) override {
    return _impl->onDppProgress(in_code);
  }
  ::ndk::ScopedAStatus onDppSuccess(::aidl::android::hardware::wifi::supplicant::DppEventType in_event) override {
    return _impl->onDppSuccess(in_event);
  }
  ::ndk::ScopedAStatus onDppSuccessConfigReceived(const std::vector<uint8_t>& in_ssid, const std::string& in_password, const std::vector<uint8_t>& in_psk, ::aidl::android::hardware::wifi::supplicant::DppAkm in_securityAkm, const ::aidl::android::hardware::wifi::supplicant::DppConnectionKeys& in_dppConnectionKeys) override {
    return _impl->onDppSuccessConfigReceived(in_ssid, in_password, in_psk, in_securityAkm, in_dppConnectionKeys);
  }
  ::ndk::ScopedAStatus onDppSuccessConfigSent() override {
    return _impl->onDppSuccessConfigSent();
  }
  ::ndk::ScopedAStatus onEapFailure(const std::vector<uint8_t>& in_bssid, int32_t in_errorCode) override {
    return _impl->onEapFailure(in_bssid, in_errorCode);
  }
  ::ndk::ScopedAStatus onExtRadioWorkStart(int32_t in_id) override {
    return _impl->onExtRadioWorkStart(in_id);
  }
  ::ndk::ScopedAStatus onExtRadioWorkTimeout(int32_t in_id) override {
    return _impl->onExtRadioWorkTimeout(in_id);
  }
  ::ndk::ScopedAStatus onHs20DeauthImminentNotice(const std::vector<uint8_t>& in_bssid, int32_t in_reasonCode, int32_t in_reAuthDelayInSec, const std::string& in_url) override {
    return _impl->onHs20DeauthImminentNotice(in_bssid, in_reasonCode, in_reAuthDelayInSec, in_url);
  }
  ::ndk::ScopedAStatus onHs20IconQueryDone(const std::vector<uint8_t>& in_bssid, const std::string& in_fileName, const std::vector<uint8_t>& in_data) override {
    return _impl->onHs20IconQueryDone(in_bssid, in_fileName, in_data);
  }
  ::ndk::ScopedAStatus onHs20SubscriptionRemediation(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::OsuMethod in_osuMethod, const std::string& in_url) override {
    return _impl->onHs20SubscriptionRemediation(in_bssid, in_osuMethod, in_url);
  }
  ::ndk::ScopedAStatus onHs20TermsAndConditionsAcceptanceRequestedNotification(const std::vector<uint8_t>& in_bssid, const std::string& in_url) override {
    return _impl->onHs20TermsAndConditionsAcceptanceRequestedNotification(in_bssid, in_url);
  }
  ::ndk::ScopedAStatus onNetworkAdded(int32_t in_id) override {
    return _impl->onNetworkAdded(in_id);
  }
  ::ndk::ScopedAStatus onNetworkNotFound(const std::vector<uint8_t>& in_ssid) override {
    return _impl->onNetworkNotFound(in_ssid);
  }
  ::ndk::ScopedAStatus onNetworkRemoved(int32_t in_id) override {
    return _impl->onNetworkRemoved(in_id);
  }
  ::ndk::ScopedAStatus onPmkCacheAdded(int64_t in_expirationTimeInSec, const std::vector<uint8_t>& in_serializedEntry) override {
    return _impl->onPmkCacheAdded(in_expirationTimeInSec, in_serializedEntry);
  }
  ::ndk::ScopedAStatus onStateChanged(::aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState in_newState, const std::vector<uint8_t>& in_bssid, int32_t in_id, const std::vector<uint8_t>& in_ssid, bool in_filsHlpSent) override {
    return _impl->onStateChanged(in_newState, in_bssid, in_id, in_ssid, in_filsHlpSent);
  }
  ::ndk::ScopedAStatus onWpsEventFail(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::WpsConfigError in_configError, ::aidl::android::hardware::wifi::supplicant::WpsErrorIndication in_errorInd) override {
    return _impl->onWpsEventFail(in_bssid, in_configError, in_errorInd);
  }
  ::ndk::ScopedAStatus onWpsEventPbcOverlap() override {
    return _impl->onWpsEventPbcOverlap();
  }
  ::ndk::ScopedAStatus onWpsEventSuccess() override {
    return _impl->onWpsEventSuccess();
  }
  ::ndk::ScopedAStatus onQosPolicyReset() override {
    return _impl->onQosPolicyReset();
  }
  ::ndk::ScopedAStatus onQosPolicyRequest(int32_t in_qosPolicyRequestId, const std::vector<::aidl::android::hardware::wifi::supplicant::QosPolicyData>& in_qosPolicyData) override {
    return _impl->onQosPolicyRequest(in_qosPolicyRequestId, in_qosPolicyData);
  }
protected:
private:
  std::shared_ptr<ISupplicantStaIfaceCallback> _impl;
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
