#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/data/DataCallFailCause.h>
#include <aidl/android/hardware/radio/data/LinkAddress.h>
#include <aidl/android/hardware/radio/data/PdpProtocolType.h>
#include <aidl/android/hardware/radio/data/Qos.h>
#include <aidl/android/hardware/radio/data/QosSession.h>
#include <aidl/android/hardware/radio/data/SliceInfo.h>
#include <aidl/android/hardware/radio/data/TrafficDescriptor.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class SetupDataCallResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::data::DataCallFailCause cause = ::aidl::android::hardware::radio::data::DataCallFailCause(0);
  int64_t suggestedRetryTime = 0L;
  int32_t cid = 0;
  int32_t active = 0;
  ::aidl::android::hardware::radio::data::PdpProtocolType type = ::aidl::android::hardware::radio::data::PdpProtocolType(0);
  std::string ifname;
  std::vector<::aidl::android::hardware::radio::data::LinkAddress> addresses;
  std::vector<std::string> dnses;
  std::vector<std::string> gateways;
  std::vector<std::string> pcscf;
  int32_t mtuV4 = 0;
  int32_t mtuV6 = 0;
  ::aidl::android::hardware::radio::data::Qos defaultQos;
  std::vector<::aidl::android::hardware::radio::data::QosSession> qosSessions;
  int8_t handoverFailureMode = 0;
  int32_t pduSessionId = 0;
  std::optional<::aidl::android::hardware::radio::data::SliceInfo> sliceInfo;
  std::vector<::aidl::android::hardware::radio::data::TrafficDescriptor> trafficDescriptors;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SetupDataCallResult& rhs) const {
    return std::tie(cause, suggestedRetryTime, cid, active, type, ifname, addresses, dnses, gateways, pcscf, mtuV4, mtuV6, defaultQos, qosSessions, handoverFailureMode, pduSessionId, sliceInfo, trafficDescriptors) != std::tie(rhs.cause, rhs.suggestedRetryTime, rhs.cid, rhs.active, rhs.type, rhs.ifname, rhs.addresses, rhs.dnses, rhs.gateways, rhs.pcscf, rhs.mtuV4, rhs.mtuV6, rhs.defaultQos, rhs.qosSessions, rhs.handoverFailureMode, rhs.pduSessionId, rhs.sliceInfo, rhs.trafficDescriptors);
  }
  inline bool operator<(const SetupDataCallResult& rhs) const {
    return std::tie(cause, suggestedRetryTime, cid, active, type, ifname, addresses, dnses, gateways, pcscf, mtuV4, mtuV6, defaultQos, qosSessions, handoverFailureMode, pduSessionId, sliceInfo, trafficDescriptors) < std::tie(rhs.cause, rhs.suggestedRetryTime, rhs.cid, rhs.active, rhs.type, rhs.ifname, rhs.addresses, rhs.dnses, rhs.gateways, rhs.pcscf, rhs.mtuV4, rhs.mtuV6, rhs.defaultQos, rhs.qosSessions, rhs.handoverFailureMode, rhs.pduSessionId, rhs.sliceInfo, rhs.trafficDescriptors);
  }
  inline bool operator<=(const SetupDataCallResult& rhs) const {
    return std::tie(cause, suggestedRetryTime, cid, active, type, ifname, addresses, dnses, gateways, pcscf, mtuV4, mtuV6, defaultQos, qosSessions, handoverFailureMode, pduSessionId, sliceInfo, trafficDescriptors) <= std::tie(rhs.cause, rhs.suggestedRetryTime, rhs.cid, rhs.active, rhs.type, rhs.ifname, rhs.addresses, rhs.dnses, rhs.gateways, rhs.pcscf, rhs.mtuV4, rhs.mtuV6, rhs.defaultQos, rhs.qosSessions, rhs.handoverFailureMode, rhs.pduSessionId, rhs.sliceInfo, rhs.trafficDescriptors);
  }
  inline bool operator==(const SetupDataCallResult& rhs) const {
    return std::tie(cause, suggestedRetryTime, cid, active, type, ifname, addresses, dnses, gateways, pcscf, mtuV4, mtuV6, defaultQos, qosSessions, handoverFailureMode, pduSessionId, sliceInfo, trafficDescriptors) == std::tie(rhs.cause, rhs.suggestedRetryTime, rhs.cid, rhs.active, rhs.type, rhs.ifname, rhs.addresses, rhs.dnses, rhs.gateways, rhs.pcscf, rhs.mtuV4, rhs.mtuV6, rhs.defaultQos, rhs.qosSessions, rhs.handoverFailureMode, rhs.pduSessionId, rhs.sliceInfo, rhs.trafficDescriptors);
  }
  inline bool operator>(const SetupDataCallResult& rhs) const {
    return std::tie(cause, suggestedRetryTime, cid, active, type, ifname, addresses, dnses, gateways, pcscf, mtuV4, mtuV6, defaultQos, qosSessions, handoverFailureMode, pduSessionId, sliceInfo, trafficDescriptors) > std::tie(rhs.cause, rhs.suggestedRetryTime, rhs.cid, rhs.active, rhs.type, rhs.ifname, rhs.addresses, rhs.dnses, rhs.gateways, rhs.pcscf, rhs.mtuV4, rhs.mtuV6, rhs.defaultQos, rhs.qosSessions, rhs.handoverFailureMode, rhs.pduSessionId, rhs.sliceInfo, rhs.trafficDescriptors);
  }
  inline bool operator>=(const SetupDataCallResult& rhs) const {
    return std::tie(cause, suggestedRetryTime, cid, active, type, ifname, addresses, dnses, gateways, pcscf, mtuV4, mtuV6, defaultQos, qosSessions, handoverFailureMode, pduSessionId, sliceInfo, trafficDescriptors) >= std::tie(rhs.cause, rhs.suggestedRetryTime, rhs.cid, rhs.active, rhs.type, rhs.ifname, rhs.addresses, rhs.dnses, rhs.gateways, rhs.pcscf, rhs.mtuV4, rhs.mtuV6, rhs.defaultQos, rhs.qosSessions, rhs.handoverFailureMode, rhs.pduSessionId, rhs.sliceInfo, rhs.trafficDescriptors);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { DATA_CONNECTION_STATUS_INACTIVE = 0 };
  enum : int32_t { DATA_CONNECTION_STATUS_DORMANT = 1 };
  enum : int32_t { DATA_CONNECTION_STATUS_ACTIVE = 2 };
  enum : int8_t { HANDOVER_FAILURE_MODE_LEGACY = 0 };
  enum : int8_t { HANDOVER_FAILURE_MODE_DO_FALLBACK = 1 };
  enum : int8_t { HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER = 2 };
  enum : int8_t { HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_SETUP_NORMAL = 3 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SetupDataCallResult{";
    os << "cause: " << ::android::internal::ToString(cause);
    os << ", suggestedRetryTime: " << ::android::internal::ToString(suggestedRetryTime);
    os << ", cid: " << ::android::internal::ToString(cid);
    os << ", active: " << ::android::internal::ToString(active);
    os << ", type: " << ::android::internal::ToString(type);
    os << ", ifname: " << ::android::internal::ToString(ifname);
    os << ", addresses: " << ::android::internal::ToString(addresses);
    os << ", dnses: " << ::android::internal::ToString(dnses);
    os << ", gateways: " << ::android::internal::ToString(gateways);
    os << ", pcscf: " << ::android::internal::ToString(pcscf);
    os << ", mtuV4: " << ::android::internal::ToString(mtuV4);
    os << ", mtuV6: " << ::android::internal::ToString(mtuV6);
    os << ", defaultQos: " << ::android::internal::ToString(defaultQos);
    os << ", qosSessions: " << ::android::internal::ToString(qosSessions);
    os << ", handoverFailureMode: " << ::android::internal::ToString(handoverFailureMode);
    os << ", pduSessionId: " << ::android::internal::ToString(pduSessionId);
    os << ", sliceInfo: " << ::android::internal::ToString(sliceInfo);
    os << ", trafficDescriptors: " << ::android::internal::ToString(trafficDescriptors);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
