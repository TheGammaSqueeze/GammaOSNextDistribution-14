#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/supplicant/AnqpData.h>
#include <aidl/android/hardware/wifi/supplicant/AssociationRejectionData.h>
#include <aidl/android/hardware/wifi/supplicant/AuxiliarySupplicantEventCode.h>
#include <aidl/android/hardware/wifi/supplicant/BssTmData.h>
#include <aidl/android/hardware/wifi/supplicant/BssidChangeReason.h>
#include <aidl/android/hardware/wifi/supplicant/DppAkm.h>
#include <aidl/android/hardware/wifi/supplicant/DppConnectionKeys.h>
#include <aidl/android/hardware/wifi/supplicant/DppEventType.h>
#include <aidl/android/hardware/wifi/supplicant/DppFailureCode.h>
#include <aidl/android/hardware/wifi/supplicant/DppProgressCode.h>
#include <aidl/android/hardware/wifi/supplicant/Hs20AnqpData.h>
#include <aidl/android/hardware/wifi/supplicant/OsuMethod.h>
#include <aidl/android/hardware/wifi/supplicant/QosPolicyData.h>
#include <aidl/android/hardware/wifi/supplicant/StaIfaceCallbackState.h>
#include <aidl/android/hardware/wifi/supplicant/StaIfaceReasonCode.h>
#include <aidl/android/hardware/wifi/supplicant/WpsConfigError.h>
#include <aidl/android/hardware/wifi/supplicant/WpsErrorIndication.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class ISupplicantStaIfaceCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISupplicantStaIfaceCallback();
  virtual ~ISupplicantStaIfaceCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "5b8bcab6b43177dffdec5873e84205b04757cc9d";
  static constexpr uint32_t TRANSACTION_onAnqpQueryDone = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onAssociationRejected = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_onAuthenticationTimeout = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_onAuxiliarySupplicantEvent = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_onBssTmHandlingDone = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_onBssidChanged = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_onDisconnected = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_onDppFailure = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_onDppProgress = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_onDppSuccess = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_onDppSuccessConfigReceived = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_onDppSuccessConfigSent = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_onEapFailure = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_onExtRadioWorkStart = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_onExtRadioWorkTimeout = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_onHs20DeauthImminentNotice = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_onHs20IconQueryDone = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_onHs20SubscriptionRemediation = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_onHs20TermsAndConditionsAcceptanceRequestedNotification = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_onNetworkAdded = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_onNetworkNotFound = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_onNetworkRemoved = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_onPmkCacheAdded = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_onStateChanged = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_onWpsEventFail = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_onWpsEventPbcOverlap = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_onWpsEventSuccess = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_onQosPolicyReset = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_onQosPolicyRequest = FIRST_CALL_TRANSACTION + 28;

  static std::shared_ptr<ISupplicantStaIfaceCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantStaIfaceCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantStaIfaceCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISupplicantStaIfaceCallback>& impl);
  static const std::shared_ptr<ISupplicantStaIfaceCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onAnqpQueryDone(const std::vector<uint8_t>& in_bssid, const ::aidl::android::hardware::wifi::supplicant::AnqpData& in_data, const ::aidl::android::hardware::wifi::supplicant::Hs20AnqpData& in_hs20Data) = 0;
  virtual ::ndk::ScopedAStatus onAssociationRejected(const ::aidl::android::hardware::wifi::supplicant::AssociationRejectionData& in_assocRejectData) = 0;
  virtual ::ndk::ScopedAStatus onAuthenticationTimeout(const std::vector<uint8_t>& in_bssid) = 0;
  virtual ::ndk::ScopedAStatus onAuxiliarySupplicantEvent(::aidl::android::hardware::wifi::supplicant::AuxiliarySupplicantEventCode in_eventCode, const std::vector<uint8_t>& in_bssid, const std::string& in_reasonString) = 0;
  virtual ::ndk::ScopedAStatus onBssTmHandlingDone(const ::aidl::android::hardware::wifi::supplicant::BssTmData& in_tmData) = 0;
  virtual ::ndk::ScopedAStatus onBssidChanged(::aidl::android::hardware::wifi::supplicant::BssidChangeReason in_reason, const std::vector<uint8_t>& in_bssid) = 0;
  virtual ::ndk::ScopedAStatus onDisconnected(const std::vector<uint8_t>& in_bssid, bool in_locallyGenerated, ::aidl::android::hardware::wifi::supplicant::StaIfaceReasonCode in_reasonCode) = 0;
  virtual ::ndk::ScopedAStatus onDppFailure(::aidl::android::hardware::wifi::supplicant::DppFailureCode in_code, const std::string& in_ssid, const std::string& in_channelList, const std::vector<char16_t>& in_bandList) = 0;
  virtual ::ndk::ScopedAStatus onDppProgress(::aidl::android::hardware::wifi::supplicant::DppProgressCode in_code) = 0;
  virtual ::ndk::ScopedAStatus onDppSuccess(::aidl::android::hardware::wifi::supplicant::DppEventType in_event) = 0;
  virtual ::ndk::ScopedAStatus onDppSuccessConfigReceived(const std::vector<uint8_t>& in_ssid, const std::string& in_password, const std::vector<uint8_t>& in_psk, ::aidl::android::hardware::wifi::supplicant::DppAkm in_securityAkm, const ::aidl::android::hardware::wifi::supplicant::DppConnectionKeys& in_dppConnectionKeys) = 0;
  virtual ::ndk::ScopedAStatus onDppSuccessConfigSent() = 0;
  virtual ::ndk::ScopedAStatus onEapFailure(const std::vector<uint8_t>& in_bssid, int32_t in_errorCode) = 0;
  virtual ::ndk::ScopedAStatus onExtRadioWorkStart(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus onExtRadioWorkTimeout(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus onHs20DeauthImminentNotice(const std::vector<uint8_t>& in_bssid, int32_t in_reasonCode, int32_t in_reAuthDelayInSec, const std::string& in_url) = 0;
  virtual ::ndk::ScopedAStatus onHs20IconQueryDone(const std::vector<uint8_t>& in_bssid, const std::string& in_fileName, const std::vector<uint8_t>& in_data) = 0;
  virtual ::ndk::ScopedAStatus onHs20SubscriptionRemediation(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::OsuMethod in_osuMethod, const std::string& in_url) = 0;
  virtual ::ndk::ScopedAStatus onHs20TermsAndConditionsAcceptanceRequestedNotification(const std::vector<uint8_t>& in_bssid, const std::string& in_url) = 0;
  virtual ::ndk::ScopedAStatus onNetworkAdded(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus onNetworkNotFound(const std::vector<uint8_t>& in_ssid) = 0;
  virtual ::ndk::ScopedAStatus onNetworkRemoved(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus onPmkCacheAdded(int64_t in_expirationTimeInSec, const std::vector<uint8_t>& in_serializedEntry) = 0;
  virtual ::ndk::ScopedAStatus onStateChanged(::aidl::android::hardware::wifi::supplicant::StaIfaceCallbackState in_newState, const std::vector<uint8_t>& in_bssid, int32_t in_id, const std::vector<uint8_t>& in_ssid, bool in_filsHlpSent) = 0;
  virtual ::ndk::ScopedAStatus onWpsEventFail(const std::vector<uint8_t>& in_bssid, ::aidl::android::hardware::wifi::supplicant::WpsConfigError in_configError, ::aidl::android::hardware::wifi::supplicant::WpsErrorIndication in_errorInd) = 0;
  virtual ::ndk::ScopedAStatus onWpsEventPbcOverlap() = 0;
  virtual ::ndk::ScopedAStatus onWpsEventSuccess() = 0;
  virtual ::ndk::ScopedAStatus onQosPolicyReset() = 0;
  virtual ::ndk::ScopedAStatus onQosPolicyRequest(int32_t in_qosPolicyRequestId, const std::vector<::aidl::android::hardware::wifi::supplicant::QosPolicyData>& in_qosPolicyData) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISupplicantStaIfaceCallback> default_impl;
};
class ISupplicantStaIfaceCallbackDefault : public ISupplicantStaIfaceCallback {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
