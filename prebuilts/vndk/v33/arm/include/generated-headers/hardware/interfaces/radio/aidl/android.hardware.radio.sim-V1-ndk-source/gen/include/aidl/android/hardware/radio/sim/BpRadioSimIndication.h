#pragma once

#include "aidl/android/hardware/radio/sim/IRadioSimIndication.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class BpRadioSimIndication : public ::ndk::BpCInterface<IRadioSimIndication> {
public:
  explicit BpRadioSimIndication(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioSimIndication();

  ::ndk::ScopedAStatus carrierInfoForImsiEncryption(::aidl::android::hardware::radio::RadioIndicationType in_info) override;
  ::ndk::ScopedAStatus cdmaSubscriptionSourceChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::sim::CdmaSubscriptionSource in_cdmaSource) override;
  ::ndk::ScopedAStatus simPhonebookChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus simPhonebookRecordsReceived(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::sim::PbReceivedStatus in_status, const std::vector<::aidl::android::hardware::radio::sim::PhonebookRecordInfo>& in_records) override;
  ::ndk::ScopedAStatus simRefresh(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::sim::SimRefreshResult& in_refreshResult) override;
  ::ndk::ScopedAStatus simStatusChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus stkEventNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_cmd) override;
  ::ndk::ScopedAStatus stkProactiveCommand(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_cmd) override;
  ::ndk::ScopedAStatus stkSessionEnd(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus subscriptionStatusChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_activate) override;
  ::ndk::ScopedAStatus uiccApplicationsEnablementChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_enabled) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
