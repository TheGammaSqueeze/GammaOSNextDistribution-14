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
class EpsQos {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t qci = 0;
  ::aidl::android::hardware::radio::data::QosBandwidth downlink;
  ::aidl::android::hardware::radio::data::QosBandwidth uplink;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const EpsQos& rhs) const {
    return std::tie(qci, downlink, uplink) != std::tie(rhs.qci, rhs.downlink, rhs.uplink);
  }
  inline bool operator<(const EpsQos& rhs) const {
    return std::tie(qci, downlink, uplink) < std::tie(rhs.qci, rhs.downlink, rhs.uplink);
  }
  inline bool operator<=(const EpsQos& rhs) const {
    return std::tie(qci, downlink, uplink) <= std::tie(rhs.qci, rhs.downlink, rhs.uplink);
  }
  inline bool operator==(const EpsQos& rhs) const {
    return std::tie(qci, downlink, uplink) == std::tie(rhs.qci, rhs.downlink, rhs.uplink);
  }
  inline bool operator>(const EpsQos& rhs) const {
    return std::tie(qci, downlink, uplink) > std::tie(rhs.qci, rhs.downlink, rhs.uplink);
  }
  inline bool operator>=(const EpsQos& rhs) const {
    return std::tie(qci, downlink, uplink) >= std::tie(rhs.qci, rhs.downlink, rhs.uplink);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "EpsQos{";
    os << "qci: " << ::android::internal::ToString(qci);
    os << ", downlink: " << ::android::internal::ToString(downlink);
    os << ", uplink: " << ::android::internal::ToString(uplink);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
