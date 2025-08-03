#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/data/Qos.h>
#include <aidl/android/hardware/radio/data/QosFilter.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class QosSession {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t qosSessionId = 0;
  ::aidl::android::hardware::radio::data::Qos qos;
  std::vector<::aidl::android::hardware::radio::data::QosFilter> qosFilters;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const QosSession& rhs) const {
    return std::tie(qosSessionId, qos, qosFilters) != std::tie(rhs.qosSessionId, rhs.qos, rhs.qosFilters);
  }
  inline bool operator<(const QosSession& rhs) const {
    return std::tie(qosSessionId, qos, qosFilters) < std::tie(rhs.qosSessionId, rhs.qos, rhs.qosFilters);
  }
  inline bool operator<=(const QosSession& rhs) const {
    return std::tie(qosSessionId, qos, qosFilters) <= std::tie(rhs.qosSessionId, rhs.qos, rhs.qosFilters);
  }
  inline bool operator==(const QosSession& rhs) const {
    return std::tie(qosSessionId, qos, qosFilters) == std::tie(rhs.qosSessionId, rhs.qos, rhs.qosFilters);
  }
  inline bool operator>(const QosSession& rhs) const {
    return std::tie(qosSessionId, qos, qosFilters) > std::tie(rhs.qosSessionId, rhs.qos, rhs.qosFilters);
  }
  inline bool operator>=(const QosSession& rhs) const {
    return std::tie(qosSessionId, qos, qosFilters) >= std::tie(rhs.qosSessionId, rhs.qos, rhs.qosFilters);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "QosSession{";
    os << "qosSessionId: " << ::android::internal::ToString(qosSessionId);
    os << ", qos: " << ::android::internal::ToString(qos);
    os << ", qosFilters: " << ::android::internal::ToString(qosFilters);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
