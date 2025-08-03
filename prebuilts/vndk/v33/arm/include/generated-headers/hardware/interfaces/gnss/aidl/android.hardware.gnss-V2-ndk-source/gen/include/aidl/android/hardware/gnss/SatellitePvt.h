#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/SatelliteClockInfo.h>
#include <aidl/android/hardware/gnss/SatellitePositionEcef.h>
#include <aidl/android/hardware/gnss/SatellitePvt.h>
#include <aidl/android/hardware/gnss/SatelliteVelocityEcef.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class SatellitePvt {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class SatelliteEphemerisSource : int32_t {
    DEMODULATED = 0,
    SERVER_NORMAL = 1,
    SERVER_LONG_TERM = 2,
    OTHER = 3,
  };

  int32_t flags = 0;
  ::aidl::android::hardware::gnss::SatellitePositionEcef satPosEcef;
  ::aidl::android::hardware::gnss::SatelliteVelocityEcef satVelEcef;
  ::aidl::android::hardware::gnss::SatelliteClockInfo satClockInfo;
  double ionoDelayMeters = 0.000000;
  double tropoDelayMeters = 0.000000;
  int64_t timeOfClockSeconds = 0L;
  int32_t issueOfDataClock = 0;
  int64_t timeOfEphemerisSeconds = 0L;
  int32_t issueOfDataEphemeris = 0;
  ::aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource ephemerisSource = ::aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource::OTHER;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters, timeOfClockSeconds, issueOfDataClock, timeOfEphemerisSeconds, issueOfDataEphemeris, ephemerisSource) != std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters, rhs.timeOfClockSeconds, rhs.issueOfDataClock, rhs.timeOfEphemerisSeconds, rhs.issueOfDataEphemeris, rhs.ephemerisSource);
  }
  inline bool operator<(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters, timeOfClockSeconds, issueOfDataClock, timeOfEphemerisSeconds, issueOfDataEphemeris, ephemerisSource) < std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters, rhs.timeOfClockSeconds, rhs.issueOfDataClock, rhs.timeOfEphemerisSeconds, rhs.issueOfDataEphemeris, rhs.ephemerisSource);
  }
  inline bool operator<=(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters, timeOfClockSeconds, issueOfDataClock, timeOfEphemerisSeconds, issueOfDataEphemeris, ephemerisSource) <= std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters, rhs.timeOfClockSeconds, rhs.issueOfDataClock, rhs.timeOfEphemerisSeconds, rhs.issueOfDataEphemeris, rhs.ephemerisSource);
  }
  inline bool operator==(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters, timeOfClockSeconds, issueOfDataClock, timeOfEphemerisSeconds, issueOfDataEphemeris, ephemerisSource) == std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters, rhs.timeOfClockSeconds, rhs.issueOfDataClock, rhs.timeOfEphemerisSeconds, rhs.issueOfDataEphemeris, rhs.ephemerisSource);
  }
  inline bool operator>(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters, timeOfClockSeconds, issueOfDataClock, timeOfEphemerisSeconds, issueOfDataEphemeris, ephemerisSource) > std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters, rhs.timeOfClockSeconds, rhs.issueOfDataClock, rhs.timeOfEphemerisSeconds, rhs.issueOfDataEphemeris, rhs.ephemerisSource);
  }
  inline bool operator>=(const SatellitePvt& rhs) const {
    return std::tie(flags, satPosEcef, satVelEcef, satClockInfo, ionoDelayMeters, tropoDelayMeters, timeOfClockSeconds, issueOfDataClock, timeOfEphemerisSeconds, issueOfDataEphemeris, ephemerisSource) >= std::tie(rhs.flags, rhs.satPosEcef, rhs.satVelEcef, rhs.satClockInfo, rhs.ionoDelayMeters, rhs.tropoDelayMeters, rhs.timeOfClockSeconds, rhs.issueOfDataClock, rhs.timeOfEphemerisSeconds, rhs.issueOfDataEphemeris, rhs.ephemerisSource);
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
    os << ", timeOfClockSeconds: " << ::android::internal::ToString(timeOfClockSeconds);
    os << ", issueOfDataClock: " << ::android::internal::ToString(issueOfDataClock);
    os << ", timeOfEphemerisSeconds: " << ::android::internal::ToString(timeOfEphemerisSeconds);
    os << ", issueOfDataEphemeris: " << ::android::internal::ToString(issueOfDataEphemeris);
    os << ", ephemerisSource: " << ::android::internal::ToString(ephemerisSource);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(SatellitePvt::SatelliteEphemerisSource val) {
  switch(val) {
  case SatellitePvt::SatelliteEphemerisSource::DEMODULATED:
    return "DEMODULATED";
  case SatellitePvt::SatelliteEphemerisSource::SERVER_NORMAL:
    return "SERVER_NORMAL";
  case SatellitePvt::SatelliteEphemerisSource::SERVER_LONG_TERM:
    return "SERVER_LONG_TERM";
  case SatellitePvt::SatelliteEphemerisSource::OTHER:
    return "OTHER";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource, 4> enum_values<aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource> = {
  aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource::DEMODULATED,
  aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource::SERVER_NORMAL,
  aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource::SERVER_LONG_TERM,
  aidl::android::hardware::gnss::SatellitePvt::SatelliteEphemerisSource::OTHER,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
