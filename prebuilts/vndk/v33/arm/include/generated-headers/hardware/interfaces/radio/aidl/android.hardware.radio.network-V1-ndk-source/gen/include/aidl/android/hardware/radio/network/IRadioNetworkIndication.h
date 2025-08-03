#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/radio/RadioIndicationType.h>
#include <aidl/android/hardware/radio/RadioTechnology.h>
#include <aidl/android/hardware/radio/network/BarringInfo.h>
#include <aidl/android/hardware/radio/network/CellIdentity.h>
#include <aidl/android/hardware/radio/network/CellInfo.h>
#include <aidl/android/hardware/radio/network/LinkCapacityEstimate.h>
#include <aidl/android/hardware/radio/network/NetworkScanResult.h>
#include <aidl/android/hardware/radio/network/PhoneRestrictedState.h>
#include <aidl/android/hardware/radio/network/PhysicalChannelConfig.h>
#include <aidl/android/hardware/radio/network/SignalStrength.h>
#include <aidl/android/hardware/radio/network/SuppSvcNotification.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class IRadioNetworkIndication : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRadioNetworkIndication();
  virtual ~IRadioNetworkIndication();

  static const int32_t version = 1;
  static inline const std::string hash = "57e8e923513d80a26102e450d335e89b4346be66";
  static constexpr uint32_t TRANSACTION_barringInfoChanged = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_cdmaPrlChanged = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_cellInfoList = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_currentLinkCapacityEstimate = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_currentPhysicalChannelConfigs = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_currentSignalStrength = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_imsNetworkStateChanged = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_networkScanResult = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_networkStateChanged = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_nitzTimeReceived = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_registrationFailed = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_restrictedStateChanged = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_suppSvcNotify = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_voiceRadioTechChanged = FIRST_CALL_TRANSACTION + 13;

  static std::shared_ptr<IRadioNetworkIndication> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRadioNetworkIndication>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRadioNetworkIndication>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IRadioNetworkIndication>& impl);
  static const std::shared_ptr<IRadioNetworkIndication>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus barringInfoChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::vector<::aidl::android::hardware::radio::network::BarringInfo>& in_barringInfos) = 0;
  virtual ::ndk::ScopedAStatus cdmaPrlChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, int32_t in_version) = 0;
  virtual ::ndk::ScopedAStatus cellInfoList(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::network::CellInfo>& in_records) = 0;
  virtual ::ndk::ScopedAStatus currentLinkCapacityEstimate(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::LinkCapacityEstimate& in_lce) = 0;
  virtual ::ndk::ScopedAStatus currentPhysicalChannelConfigs(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::network::PhysicalChannelConfig>& in_configs) = 0;
  virtual ::ndk::ScopedAStatus currentSignalStrength(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::SignalStrength& in_signalStrength) = 0;
  virtual ::ndk::ScopedAStatus imsNetworkStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus networkScanResult(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::NetworkScanResult& in_result) = 0;
  virtual ::ndk::ScopedAStatus networkStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) = 0;
  virtual ::ndk::ScopedAStatus nitzTimeReceived(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_nitzTime, int64_t in_receivedTimeMs, int64_t in_ageMs) = 0;
  virtual ::ndk::ScopedAStatus registrationFailed(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::string& in_chosenPlmn, int32_t in_domain, int32_t in_causeCode, int32_t in_additionalCauseCode) = 0;
  virtual ::ndk::ScopedAStatus restrictedStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::network::PhoneRestrictedState in_state) = 0;
  virtual ::ndk::ScopedAStatus suppSvcNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::SuppSvcNotification& in_suppSvc) = 0;
  virtual ::ndk::ScopedAStatus voiceRadioTechChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::RadioTechnology in_rat) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRadioNetworkIndication> default_impl;
};
class IRadioNetworkIndicationDefault : public IRadioNetworkIndication {
public:
  ::ndk::ScopedAStatus barringInfoChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::vector<::aidl::android::hardware::radio::network::BarringInfo>& in_barringInfos) override;
  ::ndk::ScopedAStatus cdmaPrlChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, int32_t in_version) override;
  ::ndk::ScopedAStatus cellInfoList(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::network::CellInfo>& in_records) override;
  ::ndk::ScopedAStatus currentLinkCapacityEstimate(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::LinkCapacityEstimate& in_lce) override;
  ::ndk::ScopedAStatus currentPhysicalChannelConfigs(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::network::PhysicalChannelConfig>& in_configs) override;
  ::ndk::ScopedAStatus currentSignalStrength(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::SignalStrength& in_signalStrength) override;
  ::ndk::ScopedAStatus imsNetworkStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus networkScanResult(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::NetworkScanResult& in_result) override;
  ::ndk::ScopedAStatus networkStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type) override;
  ::ndk::ScopedAStatus nitzTimeReceived(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_nitzTime, int64_t in_receivedTimeMs, int64_t in_ageMs) override;
  ::ndk::ScopedAStatus registrationFailed(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::CellIdentity& in_cellIdentity, const std::string& in_chosenPlmn, int32_t in_domain, int32_t in_causeCode, int32_t in_additionalCauseCode) override;
  ::ndk::ScopedAStatus restrictedStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::network::PhoneRestrictedState in_state) override;
  ::ndk::ScopedAStatus suppSvcNotify(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::network::SuppSvcNotification& in_suppSvc) override;
  ::ndk::ScopedAStatus voiceRadioTechChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::RadioTechnology in_rat) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
