#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioResponseInfo.h>
#include <aidl/android/hardware/radio/voice/Call.h>
#include <aidl/android/hardware/radio/voice/CallForwardInfo.h>
#include <aidl/android/hardware/radio/voice/ClipStatus.h>
#include <aidl/android/hardware/radio/voice/LastCallFailCauseInfo.h>
#include <aidl/android/hardware/radio/voice/TtyMode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class IRadioVoiceResponse : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioVoiceResponse();
  virtual ~IRadioVoiceResponse();

  static const int32_t version = 1;
  static inline const std::string hash = "e9ffc70247a89e6c1e526c6334c37da46f33ebea";
  static constexpr uint32_t TRANSACTION_acceptCallResponse = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_acknowledgeRequest = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_cancelPendingUssdResponse = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_conferenceResponse = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_dialResponse = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_emergencyDialResponse = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_exitEmergencyCallbackModeResponse = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_explicitCallTransferResponse = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getCallForwardStatusResponse = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getCallWaitingResponse = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getClipResponse = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getClirResponse = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getCurrentCallsResponse = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_getLastCallFailCauseResponse = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_getMuteResponse = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_getPreferredVoicePrivacyResponse = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_getTtyModeResponse = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_handleStkCallSetupRequestFromSimResponse = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_hangupConnectionResponse = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_hangupForegroundResumeBackgroundResponse = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_hangupWaitingOrBackgroundResponse = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_isVoNrEnabledResponse = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_rejectCallResponse = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_sendBurstDtmfResponse = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_sendCdmaFeatureCodeResponse = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_sendDtmfResponse = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_sendUssdResponse = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_separateConnectionResponse = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_setCallForwardResponse = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_setCallWaitingResponse = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_setClirResponse = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_setMuteResponse = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_setPreferredVoicePrivacyResponse = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_setTtyModeResponse = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_setVoNrEnabledResponse = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_startDtmfResponse = FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_stopDtmfResponse = FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_switchWaitingOrHoldingAndActiveResponse = FIRST_CALL_TRANSACTION + 37;

  static std::shared_ptr<IRadioVoiceResponse> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioVoiceResponse>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioVoiceResponse>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioVoiceResponse>& impl);
  static const std::shared_ptr<IRadioVoiceResponse>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus acceptCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus cancelPendingUssdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus conferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus dialResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus emergencyDialResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus exitEmergencyCallbackModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus explicitCallTransferResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getCallForwardStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::voice::CallForwardInfo>& in_callForwardInfos) = 0;
  virtual ::ndk::ScopedAStatus getCallWaitingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable, int32_t in_serviceClass) = 0;
  virtual ::ndk::ScopedAStatus getClipResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::voice::ClipStatus in_status) = 0;
  virtual ::ndk::ScopedAStatus getClirResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_n, int32_t in_m) = 0;
  virtual ::ndk::ScopedAStatus getCurrentCallsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::voice::Call>& in_calls) = 0;
  virtual ::ndk::ScopedAStatus getLastCallFailCauseResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::voice::LastCallFailCauseInfo& in_failCauseinfo) = 0;
  virtual ::ndk::ScopedAStatus getMuteResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus getPreferredVoicePrivacyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus getTtyModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::voice::TtyMode in_mode) = 0;
  virtual ::ndk::ScopedAStatus handleStkCallSetupRequestFromSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus hangupConnectionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus hangupForegroundResumeBackgroundResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus hangupWaitingOrBackgroundResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus isVoNrEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus rejectCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus sendBurstDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus sendCdmaFeatureCodeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus sendDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus sendUssdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus separateConnectionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setCallForwardResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setCallWaitingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setClirResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setMuteResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setPreferredVoicePrivacyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setTtyModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setVoNrEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus startDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus stopDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus switchWaitingOrHoldingAndActiveResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioVoiceResponse> default_impl;
};
class IRadioVoiceResponseDefault : public IRadioVoiceResponse {
public:
  ::ndk::ScopedAStatus acceptCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override;
  ::ndk::ScopedAStatus cancelPendingUssdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus conferenceResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus dialResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus emergencyDialResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus exitEmergencyCallbackModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus explicitCallTransferResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus getCallForwardStatusResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::voice::CallForwardInfo>& in_callForwardInfos) override;
  ::ndk::ScopedAStatus getCallWaitingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable, int32_t in_serviceClass) override;
  ::ndk::ScopedAStatus getClipResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::voice::ClipStatus in_status) override;
  ::ndk::ScopedAStatus getClirResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_n, int32_t in_m) override;
  ::ndk::ScopedAStatus getCurrentCallsResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::voice::Call>& in_calls) override;
  ::ndk::ScopedAStatus getLastCallFailCauseResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::voice::LastCallFailCauseInfo& in_failCauseinfo) override;
  ::ndk::ScopedAStatus getMuteResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable) override;
  ::ndk::ScopedAStatus getPreferredVoicePrivacyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable) override;
  ::ndk::ScopedAStatus getTtyModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, ::aidl::android::hardware::radio::voice::TtyMode in_mode) override;
  ::ndk::ScopedAStatus handleStkCallSetupRequestFromSimResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus hangupConnectionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus hangupForegroundResumeBackgroundResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus hangupWaitingOrBackgroundResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus isVoNrEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, bool in_enable) override;
  ::ndk::ScopedAStatus rejectCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus sendBurstDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus sendCdmaFeatureCodeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus sendDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus sendUssdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus separateConnectionResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setCallForwardResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setCallWaitingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setClirResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setMuteResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setPreferredVoicePrivacyResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setTtyModeResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setVoNrEnabledResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus startDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus stopDtmfResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus switchWaitingOrHoldingAndActiveResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
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
