#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioResponseInfo.h>
#include <aidl/android/hardware/radio/data/KeepaliveStatus.h>
#include <aidl/android/hardware/radio/data/SetupDataCallResult.h>
#include <aidl/android/hardware/radio/data/SlicingConfig.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class IRadioDataResponse : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioDataResponse();
  virtual ~IRadioDataResponse();

  static const int32_t version = 1;
  static inline const std::string hash = "6d7a86008ea4fe79ced2a86b526a92618eb4c84a";
  static constexpr uint32_t TRANSACTION_acknowledgeRequest = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_allocatePduSessionIdResponse = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_cancelHandoverResponse = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_deactivateDataCallResponse = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getDataCallListResponse = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getSlicingConfigResponse = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_releasePduSessionIdResponse = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_setDataAllowedResponse = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_setDataProfileResponse = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_setDataThrottlingResponse = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_setInitialAttachApnResponse = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_setupDataCallResponse = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_startHandoverResponse = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_startKeepaliveResponse = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_stopKeepaliveResponse = FIRST_CALL_TRANSACTION + 14;

  static std::shared_ptr<IRadioDataResponse> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioDataResponse>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioDataResponse>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioDataResponse>& impl);
  static const std::shared_ptr<IRadioDataResponse>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) = 0;
  virtual ::ndk::ScopedAStatus allocatePduSessionIdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus cancelHandoverResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus deactivateDataCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getDataCallListResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::data::SetupDataCallResult>& in_dcResponse) = 0;
  virtual ::ndk::ScopedAStatus getSlicingConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::data::SlicingConfig& in_slicingConfig) = 0;
  virtual ::ndk::ScopedAStatus releasePduSessionIdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setDataAllowedResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setDataProfileResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setDataThrottlingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setInitialAttachApnResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus setupDataCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::data::SetupDataCallResult& in_dcResponse) = 0;
  virtual ::ndk::ScopedAStatus startHandoverResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus startKeepaliveResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::data::KeepaliveStatus& in_status) = 0;
  virtual ::ndk::ScopedAStatus stopKeepaliveResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioDataResponse> default_impl;
};
class IRadioDataResponseDefault : public IRadioDataResponse {
public:
  ::ndk::ScopedAStatus acknowledgeRequest(int32_t in_serial) override;
  ::ndk::ScopedAStatus allocatePduSessionIdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, int32_t in_id) override;
  ::ndk::ScopedAStatus cancelHandoverResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus deactivateDataCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus getDataCallListResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const std::vector<::aidl::android::hardware::radio::data::SetupDataCallResult>& in_dcResponse) override;
  ::ndk::ScopedAStatus getSlicingConfigResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::data::SlicingConfig& in_slicingConfig) override;
  ::ndk::ScopedAStatus releasePduSessionIdResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setDataAllowedResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setDataProfileResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setDataThrottlingResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setInitialAttachApnResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus setupDataCallResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::data::SetupDataCallResult& in_dcResponse) override;
  ::ndk::ScopedAStatus startHandoverResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
  ::ndk::ScopedAStatus startKeepaliveResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info, const ::aidl::android::hardware::radio::data::KeepaliveStatus& in_status) override;
  ::ndk::ScopedAStatus stopKeepaliveResponse(const ::aidl::android::hardware::radio::RadioResponseInfo& in_info) override;
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
