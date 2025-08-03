#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantStaIfaceCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BpSupplicantStaIfaceCallback : public ::ndk::BpCInterface<ISupplicantStaIfaceCallback> {
public:
  explicit BpSupplicantStaIfaceCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpSupplicantStaIfaceCallback();

  ::ndk::ScopedAStatus onAnqpQueryDone(const std::vector<uint8_t>& in_bssid, const ::aidl::android::hardware::wifi::supplicant::AnqpData& in_data, const ::aidl::android::hardware::wifi::supplicant::Hs20AnqpData& in_hs20Data) override;
  ::ndk::ScopedAStatus onAssociationRejected(const ::aidl::android::hardware::wifi::supplicant::AssociationRejectionData& in_assocRejectData) override;
  ::ndk::ScopedAStatus onAuthenticationTimeout(const std::vector<uint8_t>& in_bssid) override;
  ::ndk::ScopedAStatus onAuxiliarySupplicantEvent(::aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode in_eventCode, const std::vector<uint8_t>& in_bssid, const std::string& in_reasonString) override;
  ::ndk::ScopedAStatus onBssTmHandlingDone(const ::aidl::android::hardware::wifi::supplicant::BssTmData& in_tmData) override;
  ::ndk::ScopedAStatus onBssidChanged(::aidl::android::hardware::wifi::supplicant::BssidChangeReason in_reason, const std::vector<uint8_t>& in_bssid) override;
  ::ndk::ScopedAStatus onDisconnected(const std::vector<uint8_t>& in_bssid, bool in_locallyGenerated, ::aidl::android::hardware::wifi::supplicant::StaIfaceReasonCode in_reasonCode) override;
  ::ndk::ScopedAStatus onDppFailure(::aidl::android::hardware::wifi::supplicant::DppFailureCode in_code, const std::string& in_ssid, const std::string& in_channelList, const std::vector<char16_t>& in_bandList) override;
  ::ndk::ScopedAStatus onDppProgress(::aidl::android::hardware::wifi::supplicant::DppProgressCode in_code) override;
  ::ndk::ScopedAStatus onDppSuccess(::aidl::android::hardware::wifi::supplicant::DppEventType in_event) override;
  ::ndk::ScopedAStatus onDppSuccessConfigReceived(const std::vector<uint8_t>& in_ssid, const std::string& in_password, const std::vector<uint8_t>& in_psk, ::aidl::android::hardware::wifi::supplicant::DppAkm in_securityAkm, const ::aidl::android::hardware::wifi::supplicant::DppConnectionKeys& in_dppConnectionKeys) override;
  ::ndk::ScopedAStatus onDppSuccessConfigSent() override;
  ::ndk::ScopedAStatus onEapFailure(const std::vector<uint8_t>& in_bssid, int32_t in_errorCode) override;
  ::ndk::ScopedAStatus onExtRadioWorkStart(int32_t in_id) override;
  ::ndk::ScopedAStatus onExtRadioWorkTimeout(int32_t in_id) override;
  ::ndk::ScopedAStatus onHs20DeauthImminentNotice(const std::vector<uint8_t>& in_bssid, int32_t in_reasonCode, int32_t in_reAuthDelayInSec, const std::string& in_url) override;
  ::ndk::ScopedAStatus onHs20IconQueryDone(const std::vector<uint8_t>& in_bssid, const std::string& in_fileName, const std::vector<uint8_t>& in_data) override;
  ::ndk::ScopedAStatus onHs20SubscriptionRemediation(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::OsuMethod in_osuMethod, const std::string& in_url) override;
  ::ndk::ScopedAStatus onHs20TermsAndConditionsAcceptanceRequestedNotification(const std::vector<uint8_t>& in_bssid, const std::string& in_url) override;
  ::ndk::ScopedAStatus onNetworkAdded(int32_t in_id) override;
  ::ndk::ScopedAStatus onNetworkNotFound(const std::vector<uint8_t>& in_ssid) override;
  ::ndk::ScopedAStatus onNetworkRemoved(int32_t in_id) override;
  ::ndk::ScopedAStatus onPmkCacheAdded(int64_t in_expirationTimeInSec, const std::vector<uint8_t>& in_serializedEntry) override;
  ::ndk::ScopedAStatus onStateChanged(::aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState in_newState, const std::vector<uint8_t>& in_bssid, int32_t in_id, const std::vector<uint8_t>& in_ssid, bool in_filsHlpSent) override;
  ::ndk::ScopedAStatus onWpsEventFail(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::WpsConfigError in_configError, ::aidl::android::hardware::wifi::supplicant::WpsErrorIndication in_errorInd) override;
  ::ndk::ScopedAStatus onWpsEventPbcOverlap() override;
  ::ndk::ScopedAStatus onWpsEventSuccess() override;
  ::ndk::ScopedAStatus onQosPolicyReset() override;
  ::ndk::ScopedAStatus onQosPolicyRequest(int32_t in_qosPolicyRequestId, const std::vector<::aidl::android::hardware::wifi::supplicant::QosPolicyData>& in_qosPolicyData) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
