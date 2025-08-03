#pragma once

#include "aidl/android/hardware/radio/data/IRadioData.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class BpRadioData : public ::ndk::BpCInterface<IRadioData> {
public:
  explicit BpRadioData(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioData();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
