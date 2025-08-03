#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/ElapsedRealtime.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class GnssLocation {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t gnssLocationFlags = 0;
  double latitudeDegrees = 0.000000;
  double longitudeDegrees = 0.000000;
  double altitudeMeters = 0.000000;
  double speedMetersPerSec = 0.000000;
  double bearingDegrees = 0.000000;
  double horizontalAccuracyMeters = 0.000000;
  double verticalAccuracyMeters = 0.000000;
  double speedAccuracyMetersPerSecond = 0.000000;
  double bearingAccuracyDegrees = 0.000000;
  int64_t timestampMillis = 0L;
  ::aidl::android::hardware::gnss::ElapsedRealtime elapsedRealtime;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GnssLocation& rhs) const {
    return std::tie(gnssLocationFlags, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, timestampMillis, elapsedRealtime) != std::tie(rhs.gnssLocationFlags, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.timestampMillis, rhs.elapsedRealtime);
  }
  inline bool operator<(const GnssLocation& rhs) const {
    return std::tie(gnssLocationFlags, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, timestampMillis, elapsedRealtime) < std::tie(rhs.gnssLocationFlags, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.timestampMillis, rhs.elapsedRealtime);
  }
  inline bool operator<=(const GnssLocation& rhs) const {
    return std::tie(gnssLocationFlags, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, timestampMillis, elapsedRealtime) <= std::tie(rhs.gnssLocationFlags, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.timestampMillis, rhs.elapsedRealtime);
  }
  inline bool operator==(const GnssLocation& rhs) const {
    return std::tie(gnssLocationFlags, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, timestampMillis, elapsedRealtime) == std::tie(rhs.gnssLocationFlags, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.timestampMillis, rhs.elapsedRealtime);
  }
  inline bool operator>(const GnssLocation& rhs) const {
    return std::tie(gnssLocationFlags, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, timestampMillis, elapsedRealtime) > std::tie(rhs.gnssLocationFlags, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.timestampMillis, rhs.elapsedRealtime);
  }
  inline bool operator>=(const GnssLocation& rhs) const {
    return std::tie(gnssLocationFlags, latitudeDegrees, longitudeDegrees, altitudeMeters, speedMetersPerSec, bearingDegrees, horizontalAccuracyMeters, verticalAccuracyMeters, speedAccuracyMetersPerSecond, bearingAccuracyDegrees, timestampMillis, elapsedRealtime) >= std::tie(rhs.gnssLocationFlags, rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.speedMetersPerSec, rhs.bearingDegrees, rhs.horizontalAccuracyMeters, rhs.verticalAccuracyMeters, rhs.speedAccuracyMetersPerSecond, rhs.bearingAccuracyDegrees, rhs.timestampMillis, rhs.elapsedRealtime);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { HAS_LAT_LONG = 1 };
  enum : int32_t { HAS_ALTITUDE = 2 };
  enum : int32_t { HAS_SPEED = 4 };
  enum : int32_t { HAS_BEARING = 8 };
  enum : int32_t { HAS_HORIZONTAL_ACCURACY = 16 };
  enum : int32_t { HAS_VERTICAL_ACCURACY = 32 };
  enum : int32_t { HAS_SPEED_ACCURACY = 64 };
  enum : int32_t { HAS_BEARING_ACCURACY = 128 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "GnssLocation{";
    os << "gnssLocationFlags: " << ::android::internal::ToString(gnssLocationFlags);
    os << ", latitudeDegrees: " << ::android::internal::ToString(latitudeDegrees);
    os << ", longitudeDegrees: " << ::android::internal::ToString(longitudeDegrees);
    os << ", altitudeMeters: " << ::android::internal::ToString(altitudeMeters);
    os << ", speedMetersPerSec: " << ::android::internal::ToString(speedMetersPerSec);
    os << ", bearingDegrees: " << ::android::internal::ToString(bearingDegrees);
    os << ", horizontalAccuracyMeters: " << ::android::internal::ToString(horizontalAccuracyMeters);
    os << ", verticalAccuracyMeters: " << ::android::internal::ToString(verticalAccuracyMeters);
    os << ", speedAccuracyMetersPerSecond: " << ::android::internal::ToString(speedAccuracyMetersPerSecond);
    os << ", bearingAccuracyDegrees: " << ::android::internal::ToString(bearingAccuracyDegrees);
    os << ", timestampMillis: " << ::android::internal::ToString(timestampMillis);
    os << ", elapsedRealtime: " << ::android::internal::ToString(elapsedRealtime);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
