#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioIndicationType.h>
#include <aidl/android/hardware/radio/data/DataProfileInfo.h>
#include <aidl/android/hardware/radio/data/KeepaliveStatus.h>
#include <aidl/android/hardware/radio/data/PcoDataInfo.h>
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
class IRadioDataIndication : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioDataIndication();
  virtual ~IRadioDataIndication();

  static const int32_t version = 1;
  static inline const std::string hash = "6d7a86008ea4fe79ced2a86b526a92618eb4c84a";
  static constexpr uint32_t TRANSACTION_dataCallListChanged = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_keepaliveStatus = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_pcoData = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_unthrottleApn = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_slicingConfigChanged = FIRST_CALL_TRANSACTION + 4;

  static std::shared_ptr<IRadioDataIndication> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioDataIndication>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioDataIndication>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioDataIndication>& impl);
  static const std::shared_ptr<IRadioDataIndication>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus dataCallListChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::data::SetupDataCallResult>& in_dcList) = 0;
  virtual ::ndk::ScopedAStatus keepaliveStatus(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::KeepaliveStatus& in_status) = 0;
  virtual ::ndk::ScopedAStatus pcoData(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::PcoDataInfo& in_pco) = 0;
  virtual ::ndk::ScopedAStatus unthrottleApn(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::DataProfileInfo& in_dataProfileInfo) = 0;
  virtual ::ndk::ScopedAStatus slicingConfigChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::SlicingConfig& in_slicingConfig) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioDataIndication> default_impl;
};
class IRadioDataIndicationDefault : public IRadioDataIndication {
public:
  ::ndk::ScopedAStatus dataCallListChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::data::SetupDataCallResult>& in_dcList) override;
  ::ndk::ScopedAStatus keepaliveStatus(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::KeepaliveStatus& in_status) override;
  ::ndk::ScopedAStatus pcoData(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::PcoDataInfo& in_pco) override;
  ::ndk::ScopedAStatus unthrottleApn(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::DataProfileInfo& in_dataProfileInfo) override;
  ::ndk::ScopedAStatus slicingConfigChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::data::SlicingConfig& in_slicingConfig) override;
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
