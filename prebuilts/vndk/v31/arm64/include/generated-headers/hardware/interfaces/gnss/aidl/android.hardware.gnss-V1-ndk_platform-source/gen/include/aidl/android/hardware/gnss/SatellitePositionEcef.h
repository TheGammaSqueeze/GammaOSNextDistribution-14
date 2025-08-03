#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class SatellitePositionEcef {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  double posXMeters = 0.000000;
  double posYMeters = 0.000000;
  double posZMeters = 0.000000;
  double ureMeters = 0.000000;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SatellitePositionEcef& rhs) const {
    return std::tie(posXMeters, posYMeters, posZMeters, ureMeters) != std::tie(rhs.posXMeters, rhs.posYMeters, rhs.posZMeters, rhs.ureMeters);
  }
  inline bool operator<(const SatellitePositionEcef& rhs) const {
    return std::tie(posXMeters, posYMeters, posZMeters, ureMeters) < std::tie(rhs.posXMeters, rhs.posYMeters, rhs.posZMeters, rhs.ureMeters);
  }
  inline bool operator<=(const SatellitePositionEcef& rhs) const {
    return std::tie(posXMeters, posYMeters, posZMeters, ureMeters) <= std::tie(rhs.posXMeters, rhs.posYMeters, rhs.posZMeters, rhs.ureMeters);
  }
  inline bool operator==(const SatellitePositionEcef& rhs) const {
    return std::tie(posXMeters, posYMeters, posZMeters, ureMeters) == std::tie(rhs.posXMeters, rhs.posYMeters, rhs.posZMeters, rhs.ureMeters);
  }
  inline bool operator>(const SatellitePositionEcef& rhs) const {
    return std::tie(posXMeters, posYMeters, posZMeters, ureMeters) > std::tie(rhs.posXMeters, rhs.posYMeters, rhs.posZMeters, rhs.ureMeters);
  }
  inline bool operator>=(const SatellitePositionEcef& rhs) const {
    return std::tie(posXMeters, posYMeters, posZMeters, ureMeters) >= std::tie(rhs.posXMeters, rhs.posYMeters, rhs.posZMeters, rhs.ureMeters);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SatellitePositionEcef{";
    os << "posXMeters: " << ::android::internal::ToString(posXMeters);
    os << ", posYMeters: " << ::android::internal::ToString(posYMeters);
    os << ", posZMeters: " << ::android::internal::ToString(posZMeters);
    os << ", ureMeters: " << ::android::internal::ToString(ureMeters);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
