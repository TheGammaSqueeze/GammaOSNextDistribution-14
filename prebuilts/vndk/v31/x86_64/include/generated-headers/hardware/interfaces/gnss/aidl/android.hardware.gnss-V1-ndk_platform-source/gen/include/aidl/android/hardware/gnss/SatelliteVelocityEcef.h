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
class SatelliteVelocityEcef {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  double velXMps = 0.000000;
  double velYMps = 0.000000;
  double velZMps = 0.000000;
  double ureRateMps = 0.000000;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SatelliteVelocityEcef& rhs) const {
    return std::tie(velXMps, velYMps, velZMps, ureRateMps) != std::tie(rhs.velXMps, rhs.velYMps, rhs.velZMps, rhs.ureRateMps);
  }
  inline bool operator<(const SatelliteVelocityEcef& rhs) const {
    return std::tie(velXMps, velYMps, velZMps, ureRateMps) < std::tie(rhs.velXMps, rhs.velYMps, rhs.velZMps, rhs.ureRateMps);
  }
  inline bool operator<=(const SatelliteVelocityEcef& rhs) const {
    return std::tie(velXMps, velYMps, velZMps, ureRateMps) <= std::tie(rhs.velXMps, rhs.velYMps, rhs.velZMps, rhs.ureRateMps);
  }
  inline bool operator==(const SatelliteVelocityEcef& rhs) const {
    return std::tie(velXMps, velYMps, velZMps, ureRateMps) == std::tie(rhs.velXMps, rhs.velYMps, rhs.velZMps, rhs.ureRateMps);
  }
  inline bool operator>(const SatelliteVelocityEcef& rhs) const {
    return std::tie(velXMps, velYMps, velZMps, ureRateMps) > std::tie(rhs.velXMps, rhs.velYMps, rhs.velZMps, rhs.ureRateMps);
  }
  inline bool operator>=(const SatelliteVelocityEcef& rhs) const {
    return std::tie(velXMps, velYMps, velZMps, ureRateMps) >= std::tie(rhs.velXMps, rhs.velYMps, rhs.velZMps, rhs.ureRateMps);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SatelliteVelocityEcef{";
    os << "velXMps: " << ::android::internal::ToString(velXMps);
    os << ", velYMps: " << ::android::internal::ToString(velYMps);
    os << ", velZMps: " << ::android::internal::ToString(velZMps);
    os << ", ureRateMps: " << ::android::internal::ToString(ureRateMps);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
