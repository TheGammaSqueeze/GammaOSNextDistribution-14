#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/sim/Carrier.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
class CarrierRestrictions {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::radio::sim::Carrier> allowedCarriers;
  std::vector<::aidl::android::hardware::radio::sim::Carrier> excludedCarriers;
  bool allowedCarriersPrioritized = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CarrierRestrictions& rhs) const {
    return std::tie(allowedCarriers, excludedCarriers, allowedCarriersPrioritized) != std::tie(rhs.allowedCarriers, rhs.excludedCarriers, rhs.allowedCarriersPrioritized);
  }
  inline bool operator<(const CarrierRestrictions& rhs) const {
    return std::tie(allowedCarriers, excludedCarriers, allowedCarriersPrioritized) < std::tie(rhs.allowedCarriers, rhs.excludedCarriers, rhs.allowedCarriersPrioritized);
  }
  inline bool operator<=(const CarrierRestrictions& rhs) const {
    return std::tie(allowedCarriers, excludedCarriers, allowedCarriersPrioritized) <= std::tie(rhs.allowedCarriers, rhs.excludedCarriers, rhs.allowedCarriersPrioritized);
  }
  inline bool operator==(const CarrierRestrictions& rhs) const {
    return std::tie(allowedCarriers, excludedCarriers, allowedCarriersPrioritized) == std::tie(rhs.allowedCarriers, rhs.excludedCarriers, rhs.allowedCarriersPrioritized);
  }
  inline bool operator>(const CarrierRestrictions& rhs) const {
    return std::tie(allowedCarriers, excludedCarriers, allowedCarriersPrioritized) > std::tie(rhs.allowedCarriers, rhs.excludedCarriers, rhs.allowedCarriersPrioritized);
  }
  inline bool operator>=(const CarrierRestrictions& rhs) const {
    return std::tie(allowedCarriers, excludedCarriers, allowedCarriersPrioritized) >= std::tie(rhs.allowedCarriers, rhs.excludedCarriers, rhs.allowedCarriersPrioritized);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CarrierRestrictions{";
    os << "allowedCarriers: " << ::android::internal::ToString(allowedCarriers);
    os << ", excludedCarriers: " << ::android::internal::ToString(excludedCarriers);
    os << ", allowedCarriersPrioritized: " << ::android::internal::ToString(allowedCarriersPrioritized);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
