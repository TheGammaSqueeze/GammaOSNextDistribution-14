#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioIndicationType.h>
#include <aidl/android/hardware/radio/voice/CdmaCallWaiting.h>
#include <aidl/android/hardware/radio/voice/CdmaInformationRecord.h>
#include <aidl/android/hardware/radio/voice/CdmaOtaProvisionStatus.h>
#include <aidl/android/hardware/radio/voice/CdmaSignalInfoRecord.h>
#include <aidl/android/hardware/radio/voice/EmergencyNumber.h>
#include <aidl/android/hardware/radio/voice/SrvccState.h>
#include <aidl/android/hardware/radio/voice/StkCcUnsolSsResult.h>
#include <aidl/android/hardware/radio/voice/UssdModeType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class IRadioVoiceIndication : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioVoiceIndication();
  virtual ~IRadioVoiceIndication();

  static const int32_t version = 1;
  static inline const std::string hash = "e9ffc70247a89e6c1e526c6334c37da46f33ebea";
  static constexpr uint32_t TRANSACTION_callRing = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_callStateChanged = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_cdmaCallWaiting = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_cdmaInfoRec = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_cdmaOtaProvisionStatus = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_currentEmergencyNumberList = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_enterEmergencyCallbackMode = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_exitEmergencyCallbackMode = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_indicateRingbackTone = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_onSupplementaryServiceIndication = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_onUssd = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_resendIncallMute = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_srvccStateNotify = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_stkCallControlAlphaNotify = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_stkCallSetup = FIRST_CALL_TRANSACTION + 14;

  static std::shared_ptr<IRadioVoiceIndication> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioVoiceIndication>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioVoiceIndication>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioVoiceIndication>& impl);
  static const std::shared_ptr<IRadioVoiceIndication>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus callRing(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_isGsm, const ::aidl::android::hardware::radio::voice::CdmaSignalInfoRecord& in_record) = 0;
  virtual ::ndk::ScopedAStatus callStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus cdmaCallWaiting(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::voice::CdmaCallWaiting& in_callWaitingRecord) = 0;
  virtual ::ndk::ScopedAStatus cdmaInfoRec(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::voice::CdmaInformationRecord>& in_records) = 0;
  virtual ::ndk::ScopedAStatus cdmaOtaProvisionStatus(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus in_status) = 0;
  virtual ::ndk::ScopedAStatus currentEmergencyNumberList(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::voice::EmergencyNumber>& in_emergencyNumberList) = 0;
  virtual ::ndk::ScopedAStatus enterEmergencyCallbackMode(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus exitEmergencyCallbackMode(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus indicateRingbackTone(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_start) = 0;
  virtual ::ndk::ScopedAStatus onSupplementaryServiceIndication(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::voice::StkCcUnsolSsResult& in_ss) = 0;
  virtual ::ndk::ScopedAStatus onUssd(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::voice::UssdModeType in_modeType, const std::string& in_msg) = 0;
  virtual ::ndk::ScopedAStatus resendIncallMute(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus srvccStateNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::voice::SrvccState in_state) = 0;
  virtual ::ndk::ScopedAStatus stkCallControlAlphaNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_alpha) = 0;
  virtual ::ndk::ScopedAStatus stkCallSetup(::aidl::android::hardware::radio::RadioIndicationType in_type, int64_t in_timeout) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioVoiceIndication> default_impl;
};
class IRadioVoiceIndicationDefault : public IRadioVoiceIndication {
public:
  ::ndk::ScopedAStatus callRing(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_isGsm, const ::aidl::android::hardware::radio::voice::CdmaSignalInfoRecord& in_record) override;
  ::ndk::ScopedAStatus callStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus cdmaCallWaiting(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::voice::CdmaCallWaiting& in_callWaitingRecord) override;
  ::ndk::ScopedAStatus cdmaInfoRec(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::voice::CdmaInformationRecord>& in_records) override;
  ::ndk::ScopedAStatus cdmaOtaProvisionStatus(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus in_status) override;
  ::ndk::ScopedAStatus currentEmergencyNumberList(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::voice::EmergencyNumber>& in_emergencyNumberList) override;
  ::ndk::ScopedAStatus enterEmergencyCallbackMode(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus exitEmergencyCallbackMode(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus indicateRingbackTone(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_start) override;
  ::ndk::ScopedAStatus onSupplementaryServiceIndication(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::voice::StkCcUnsolSsResult& in_ss) override;
  ::ndk::ScopedAStatus onUssd(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::voice::UssdModeType in_modeType, const std::string& in_msg) override;
  ::ndk::ScopedAStatus resendIncallMute(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus srvccStateNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::voice::SrvccState in_state) override;
  ::ndk::ScopedAStatus stkCallControlAlphaNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_alpha) override;
  ::ndk::ScopedAStatus stkCallSetup(::aidl::android::hardware::radio::RadioIndicationType in_type, int64_t in_timeout) override;
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
