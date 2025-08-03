#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/data/QosBandwidth.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class NrQos {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t fiveQi = 0;
  ::aidl::android::hardware::radio::data::QosBandwidth downlink;
  ::aidl::android::hardware::radio::data::QosBandwidth uplink;
  int8_t qfi = 0;
  char16_t averagingWindowMs = '\0';

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NrQos& rhs) const {
    return std::tie(fiveQi, downlink, uplink, qfi, averagingWindowMs) != std::tie(rhs.fiveQi, rhs.downlink, rhs.uplink, rhs.qfi, rhs.averagingWindowMs);
  }
  inline bool operator<(const NrQos& rhs) const {
    return std::tie(fiveQi, downlink, uplink, qfi, averagingWindowMs) < std::tie(rhs.fiveQi, rhs.downlink, rhs.uplink, rhs.qfi, rhs.averagingWindowMs);
  }
  inline bool operator<=(const NrQos& rhs) const {
    return std::tie(fiveQi, downlink, uplink, qfi, averagingWindowMs) <= std::tie(rhs.fiveQi, rhs.downlink, rhs.uplink, rhs.qfi, rhs.averagingWindowMs);
  }
  inline bool operator==(const NrQos& rhs) const {
    return std::tie(fiveQi, downlink, uplink, qfi, averagingWindowMs) == std::tie(rhs.fiveQi, rhs.downlink, rhs.uplink, rhs.qfi, rhs.averagingWindowMs);
  }
  inline bool operator>(const NrQos& rhs) const {
    return std::tie(fiveQi, downlink, uplink, qfi, averagingWindowMs) > std::tie(rhs.fiveQi, rhs.downlink, rhs.uplink, rhs.qfi, rhs.averagingWindowMs);
  }
  inline bool operator>=(const NrQos& rhs) const {
    return std::tie(fiveQi, downlink, uplink, qfi, averagingWindowMs) >= std::tie(rhs.fiveQi, rhs.downlink, rhs.uplink, rhs.qfi, rhs.averagingWindowMs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int8_t { FLOW_ID_RANGE_MIN = 1 };
  enum : int8_t { FLOW_ID_RANGE_MAX = 63 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "NrQos{";
    os << "fiveQi: " << ::android::internal::ToString(fiveQi);
    os << ", downlink: " << ::android::internal::ToString(downlink);
    os << ", uplink: " << ::android::internal::ToString(uplink);
    os << ", qfi: " << ::android::internal::ToString(qfi);
    os << ", averagingWindowMs: " << ::android::internal::ToString(averagingWindowMs);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
