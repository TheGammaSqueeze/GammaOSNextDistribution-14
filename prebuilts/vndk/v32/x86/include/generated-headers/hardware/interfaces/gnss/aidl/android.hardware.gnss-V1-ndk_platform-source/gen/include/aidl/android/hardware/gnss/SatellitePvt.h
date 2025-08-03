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
#include <aidl/android/hardware/gnss/SatelliteClockInfo.h>
#include <aidl/android/hardware/gnss/SatellitePositionEcef.h>
#include <aidl/android/hardware/gnss/SatelliteVelocityEcef.h>
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class SatellitePvt {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t flags = 0;
  ::aidl::android::hardware::gnss::SatellitePositionEcef satPosEcef;
  ::aidl::android::hardware::gnss::SatelliteVelocityEcef satVelEcef;
  ::aidl::android::hardware::gnss::SatelliteClockInfo satClockInfo;
  double ionoDelayMeters = 0.000000;
  double tropoDelayMeters = 0.000000;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters) != std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters);
  }
  inline bool operator<(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters) < std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters);
  }
  inline bool operator<=(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters) <= std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters);
  }
  inline bool operator==(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters) == std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters);
  }
  inline bool operator>(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters) > std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters);
  }
  inline bool operator>=(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters) >= std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { HAS_POSITION_VELOCITY_CLOCK_INFO = 1 };
  enum : int32_t { HAS_IONO = 2 };
  enum : int32_t { HAS_TROPO = 4 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SatellitePvt{";
    os << "flags: " << ::android::internal::ToString(flags);
    os << ", satPosEcef: " << ::android::internal::ToString(satPosEcef);
    os << ", satVelEcef: " << ::android::internal::ToString(satVelEcef);
    os << ", satClockInfo: " << ::android::internal::ToString(satClockInfo);
    os << ", ionoDelayMeters: " << ::android::internal::ToString(ionoDelayMeters);
    os << ", tropoDelayMeters: " << ::android::internal::ToString(tropoDelayMeters);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
