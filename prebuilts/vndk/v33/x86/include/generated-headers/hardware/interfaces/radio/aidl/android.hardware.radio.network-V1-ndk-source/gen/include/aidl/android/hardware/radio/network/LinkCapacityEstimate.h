#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class LinkCapacityEstimate {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t downlinkCapacityKbps = 0;
  int32_t uplinkCapacityKbps = 0;
  int32_t secondaryDownlinkCapacityKbps = 0;
  int32_t secondaryUplinkCapacityKbps = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const LinkCapacityEstimate& rhs) const {
    return std::tie(downlinkCapacityKbps, uplinkCapacityKbps, secondaryDownlinkCapacityKbps, secondaryUplinkCapacityKbps) != std::tie(rhs.downlinkCapacityKbps, rhs.uplinkCapacityKbps, rhs.secondaryDownlinkCapacityKbps, rhs.secondaryUplinkCapacityKbps);
  }
  inline bool operator<(const LinkCapacityEstimate& rhs) const {
    return std::tie(downlinkCapacityKbps, uplinkCapacityKbps, secondaryDownlinkCapacityKbps, secondaryUplinkCapacityKbps) < std::tie(rhs.downlinkCapacityKbps, rhs.uplinkCapacityKbps, rhs.secondaryDownlinkCapacityKbps, rhs.secondaryUplinkCapacityKbps);
  }
  inline bool operator<=(const LinkCapacityEstimate& rhs) const {
    return std::tie(downlinkCapacityKbps, uplinkCapacityKbps, secondaryDownlinkCapacityKbps, secondaryUplinkCapacityKbps) <= std::tie(rhs.downlinkCapacityKbps, rhs.uplinkCapacityKbps, rhs.secondaryDownlinkCapacityKbps, rhs.secondaryUplinkCapacityKbps);
  }
  inline bool operator==(const LinkCapacityEstimate& rhs) const {
    return std::tie(downlinkCapacityKbps, uplinkCapacityKbps, secondaryDownlinkCapacityKbps, secondaryUplinkCapacityKbps) == std::tie(rhs.downlinkCapacityKbps, rhs.uplinkCapacityKbps, rhs.secondaryDownlinkCapacityKbps, rhs.secondaryUplinkCapacityKbps);
  }
  inline bool operator>(const LinkCapacityEstimate& rhs) const {
    return std::tie(downlinkCapacityKbps, uplinkCapacityKbps, secondaryDownlinkCapacityKbps, secondaryUplinkCapacityKbps) > std::tie(rhs.downlinkCapacityKbps, rhs.uplinkCapacityKbps, rhs.secondaryDownlinkCapacityKbps, rhs.secondaryUplinkCapacityKbps);
  }
  inline bool operator>=(const LinkCapacityEstimate& rhs) const {
    return std::tie(downlinkCapacityKbps, uplinkCapacityKbps, secondaryDownlinkCapacityKbps, secondaryUplinkCapacityKbps) >= std::tie(rhs.downlinkCapacityKbps, rhs.uplinkCapacityKbps, rhs.secondaryDownlinkCapacityKbps, rhs.secondaryUplinkCapacityKbps);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "LinkCapacityEstimate{";
    os << "downlinkCapacityKbps: " << ::android::internal::ToString(downlinkCapacityKbps);
    os << ", uplinkCapacityKbps: " << ::android::internal::ToString(uplinkCapacityKbps);
    os << ", secondaryDownlinkCapacityKbps: " << ::android::internal::ToString(secondaryDownlinkCapacityKbps);
    os << ", secondaryUplinkCapacityKbps: " << ::android::internal::ToString(secondaryUplinkCapacityKbps);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
