#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/AccessNetwork.h>
#include <aidl/android/hardware/radio/data/DataProfileInfo.h>
#include <aidl/android/hardware/radio/data/DataRequestReason.h>
#include <aidl/android/hardware/radio/data/DataThrottlingAction.h>
#include <aidl/android/hardware/radio/data/IRadioDataIndication.h>
#include <aidl/android/hardware/radio/data/IRadioDataResponse.h>
#include <aidl/android/hardware/radio/data/KeepaliveRequest.h>
#include <aidl/android/hardware/radio/data/LinkAddress.h>
#include <aidl/android/hardware/radio/data/SliceInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class IRadioData : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioData();
  virtual ~IRadioData();

  static const int32_t version = 1;
  static inline const std::string hash = "6d7a86008ea4fe79ced2a86b526a92618eb4c84a";
  static constexpr uint32_t TRANSACTION_allocatePduSessionId = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_cancelHandover = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_deactivateDataCall = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getDataCallList = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getSlicingConfig = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_releasePduSessionId = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_responseAcknowledgement = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_setDataAllowed = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_setDataProfile = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_setDataThrottling = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_setInitialAttachApn = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_setResponseFunctions = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_setupDataCall = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_startHandover = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_startKeepalive = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_stopKeepalive = FIRST_CALL_TRANSACTION + 15;

  static std::shared_ptr<IRadioData> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioData>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioData>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioData>& impl);
  static const std::shared_ptr<IRadioData>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus allocatePduSessionId(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus cancelHandover(int32_t in_serial, int32_t in_callId) = 0;
  virtual ::ndk::ScopedAStatus deactivateDataCall(int32_t in_serial, int32_t in_cid, ::aidl::android::hardware::radio::data::DataRequestReason in_reason) = 0;
  virtual ::ndk::ScopedAStatus getDataCallList(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus getSlicingConfig(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus releasePduSessionId(int32_t in_serial, int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus responseAcknowledgement() = 0;
  virtual ::ndk::ScopedAStatus setDataAllowed(int32_t in_serial, bool in_allow) = 0;
  virtual ::ndk::ScopedAStatus setDataProfile(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::data::DataProfileInfo>& in_profiles) = 0;
  virtual ::ndk::ScopedAStatus setDataThrottling(int32_t in_serial, ::aidl::android::hardware::radio::data::DataThrottlingAction in_dataThrottlingAction, int64_t in_completionDurationMillis) = 0;
  virtual ::ndk::ScopedAStatus setInitialAttachApn(int32_t in_serial, const std::optional<::aidl::android::hardware::radio::data::DataProfileInfo>& in_dataProfileInfo) = 0;
  virtual ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::data::IRadioDataResponse>& in_radioDataResponse, const std::shared_ptr<::aidl::android::hardware::radio::data::IRadioDataIndication>& in_radioDataIndication) = 0;
  virtual ::ndk::ScopedAStatus setupDataCall(int32_t in_serial, ::aidl::android::hardware::radio::AccessNetwork in_accessNetwork, const ::aidl::android::hardware::radio::data::DataProfileInfo& in_dataProfileInfo, bool in_roamingAllowed, ::aidl::android::hardware::radio::data::DataRequestReason in_reason, const std::vector<::aidl::android::hardware::radio::data::LinkAddress>& in_addresses, const std::vector<std::string>& in_dnses, int32_t in_pduSessionId, const std::optional<::aidl::android::hardware::radio::data::SliceInfo>& in_sliceInfo, bool in_matchAllRuleAllowed) = 0;
  virtual ::ndk::ScopedAStatus startHandover(int32_t in_serial, int32_t in_callId) = 0;
  virtual ::ndk::ScopedAStatus startKeepalive(int32_t in_serial, const ::aidl::android::hardware::radio::data::KeepaliveRequest& in_keepalive) = 0;
  virtual ::ndk::ScopedAStatus stopKeepalive(int32_t in_serial, int32_t in_sessionHandle) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioData> default_impl;
};
class IRadioDataDefault : public IRadioData {
public:
  ::ndk::ScopedAStatus allocatePduSessionId(int32_t in_serial) override;
  ::ndk::ScopedAStatus cancelHandover(int32_t in_serial, int32_t in_callId) override;
  ::ndk::ScopedAStatus deactivateDataCall(int32_t in_serial, int32_t in_cid, ::aidl::android::hardware::radio::data::DataRequestReason in_reason) override;
  ::ndk::ScopedAStatus getDataCallList(int32_t in_serial) override;
  ::ndk::ScopedAStatus getSlicingConfig(int32_t in_serial) override;
  ::ndk::ScopedAStatus releasePduSessionId(int32_t in_serial, int32_t in_id) override;
  ::ndk::ScopedAStatus responseAcknowledgement() override;
  ::ndk::ScopedAStatus setDataAllowed(int32_t in_serial, bool in_allow) override;
  ::ndk::ScopedAStatus setDataProfile(int32_t in_serial, const std::vector<::aidl::android::hardware::radio::data::DataProfileInfo>& in_profiles) override;
  ::ndk::ScopedAStatus setDataThrottling(int32_t in_serial, ::aidl::android::hardware::radio::data::DataThrottlingAction in_dataThrottlingAction, int64_t in_completionDurationMillis) override;
  ::ndk::ScopedAStatus setInitialAttachApn(int32_t in_serial, const std::optional<::aidl::android::hardware::radio::data::DataProfileInfo>& in_dataProfileInfo) override;
  ::ndk::ScopedAStatus setResponseFunctions(const std::shared_ptr<::aidl::android::hardware::radio::data::IRadioDataResponse>& in_radioDataResponse, const std::shared_ptr<::aidl::android::hardware::radio::data::IRadioDataIndication>& in_radioDataIndication) override;
  ::ndk::ScopedAStatus setupDataCall(int32_t in_serial, ::aidl::android::hardware::radio::AccessNetwork in_accessNetwork, const ::aidl::android::hardware::radio::data::DataProfileInfo& in_dataProfileInfo, bool in_roamingAllowed, ::aidl::android::hardware::radio::data::DataRequestReason in_reason, const std::vector<::aidl::android::hardware::radio::data::LinkAddress>& in_addresses, const std::vector<std::string>& in_dnses, int32_t in_pduSessionId, const std::optional<::aidl::android::hardware::radio::data::SliceInfo>& in_sliceInfo, bool in_matchAllRuleAllowed) override;
  ::ndk::ScopedAStatus startHandover(int32_t in_serial, int32_t in_callId) override;
  ::ndk::ScopedAStatus startKeepalive(int32_t in_serial, const ::aidl::android::hardware::radio::data::KeepaliveRequest& in_keepalive) override;
  ::ndk::ScopedAStatus stopKeepalive(int32_t in_serial, int32_t in_sessionHandle) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
