#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioIndicationType.h>
#include <aidl/android/hardware/radio/sim/CdmaSubscriptionSource.h>
#include <aidl/android/hardware/radio/sim/PbReceivedStatus.h>
#include <aidl/android/hardware/radio/sim/PhonebookRecordInfo.h>
#include <aidl/android/hardware/radio/sim/SimRefreshResult.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class IRadioSimIndication : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioSimIndication();
  virtual ~IRadioSimIndication();

  static const int32_t version = 1;
  static inline const std::string hash = "01cea196fdf8f5e41fda8dc41125f1cc2b96f757";
  static constexpr uint32_t TRANSACTION_carrierInfoForImsiEncryption = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_cdmaSubscriptionSourceChanged = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_simPhonebookChanged = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_simPhonebookRecordsReceived = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_simRefresh = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_simStatusChanged = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_stkEventNotify = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_stkProactiveCommand = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_stkSessionEnd = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_subscriptionStatusChanged = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_uiccApplicationsEnablementChanged = FIRST_CALL_TRANSACTION + 10;

  static std::shared_ptr<IRadioSimIndication> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioSimIndication>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioSimIndication>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioSimIndication>& impl);
  static const std::shared_ptr<IRadioSimIndication>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus carrierInfoForImsiEncryption(::aidl::android::hardware::radio::RadioIndicationType in_info) = 0;
  virtual ::ndk::ScopedAStatus cdmaSubscriptionSourceChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::sim::CdmaSubscriptionSource in_cdmaSource) = 0;
  virtual ::ndk::ScopedAStatus simPhonebookChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus simPhonebookRecordsReceived(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::sim::PbReceivedStatus in_status, const std::vector<::aidl::android::hardware::radio::sim::PhonebookRecordInfo>& in_records) = 0;
  virtual ::ndk::ScopedAStatus simRefresh(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::sim::SimRefreshResult& in_refreshResult) = 0;
  virtual ::ndk::ScopedAStatus simStatusChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus stkEventNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_cmd) = 0;
  virtual ::ndk::ScopedAStatus stkProactiveCommand(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_cmd) = 0;
  virtual ::ndk::ScopedAStatus stkSessionEnd(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus subscriptionStatusChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_activate) = 0;
  virtual ::ndk::ScopedAStatus uiccApplicationsEnablementChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, bool in_enabled) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioSimIndication> default_impl;
};
class IRadioSimIndicationDefault : public IRadioSimIndication {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
