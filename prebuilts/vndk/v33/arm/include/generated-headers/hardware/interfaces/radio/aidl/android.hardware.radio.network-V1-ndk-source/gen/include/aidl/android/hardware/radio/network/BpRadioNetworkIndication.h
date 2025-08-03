#pragma once

#include "aidl/android/hardware/radio/network/IRadioNetworkIndication.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class BpRadioNetworkIndication : public ::ndk::BpCInterface<IRadioNetworkIndication> {
public:
  explicit BpRadioNetworkIndication(const ::ndk::SpAIBinder& binder);
  virtual ~BpRadioNetworkIndication();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
