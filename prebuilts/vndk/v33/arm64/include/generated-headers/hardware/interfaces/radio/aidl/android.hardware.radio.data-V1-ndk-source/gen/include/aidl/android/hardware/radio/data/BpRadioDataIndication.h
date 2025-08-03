#pragma once

#include "aidl/android/hardware/radio/data/IRadioDataIndication.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class BpRadioDataIndication : public ::ndk::BpCInterface<IRadioDataIndication> {
public:
  explicit BpRadioDataIndication(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioDataIndication();

  ::ndk::ScopedAStatus dataCallListChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::data::SetupDataCallResult>& in_dcList) override;
  ::ndk::ScopedAStatus keepaliveStatus(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::KeepaliveStatus& in_status) override;
  ::ndk::ScopedAStatus pcoData(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::PcoDataInfo& in_pco) override;
  ::ndk::ScopedAStatus unthrottleApn(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::DataProfileInfo& in_dataProfileInfo) override;
  ::ndk::ScopedAStatus slicingConfigChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::SlicingConfig& in_slicingConfig) override;
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
