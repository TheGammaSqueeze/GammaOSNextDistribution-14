#pragma once

#include "aidl/android/hardware/radio/data/IRadioDataResponse.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class BpRadioDataResponse : public ::ndk::BpCInterface<IRadioDataResponse> {
public:
  explicit BpRadioDataResponse(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioDataResponse();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
