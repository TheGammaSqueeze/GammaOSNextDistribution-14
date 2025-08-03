#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/gnss/measurement_corrections/SingleSatCorrection.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
class MeasurementCorrections {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  double latitudeDegrees = 0.000000;
  double longitudeDegrees = 0.000000;
  double altitudeMeters = 0.000000;
  double horizontalPositionUncertaintyMeters = 0.000000;
  double verticalPositionUncertaintyMeters = 0.000000;
  int64_t toaGpsNanosecondsOfWeek = 0L;
  std::vector<::aidl::android::hardware::gnss::measurement_corrections::SingleSatCorrection> satCorrections;
  bool hasEnvironmentBearing = false;
  float environmentBearingDegrees = 0.000000f;
  float environmentBearingUncertaintyDegrees = 0.000000f;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const MeasurementCorrections& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, horizontalPositionUncertaintyMeters, verticalPositionUncertaintyMeters, toaGpsNanosecondsOfWeek, satCorrections, hasEnvironmentBearing, environmentBearingDegrees, environmentBearingUncertaintyDegrees) != std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.horizontalPositionUncertaintyMeters, rhs.verticalPositionUncertaintyMeters, rhs.toaGpsNanosecondsOfWeek, rhs.satCorrections, rhs.hasEnvironmentBearing, rhs.environmentBearingDegrees, rhs.environmentBearingUncertaintyDegrees);
  }
  inline bool operator<(const MeasurementCorrections& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, horizontalPositionUncertaintyMeters, verticalPositionUncertaintyMeters, toaGpsNanosecondsOfWeek, satCorrections, hasEnvironmentBearing, environmentBearingDegrees, environmentBearingUncertaintyDegrees) < std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.horizontalPositionUncertaintyMeters, rhs.verticalPositionUncertaintyMeters, rhs.toaGpsNanosecondsOfWeek, rhs.satCorrections, rhs.hasEnvironmentBearing, rhs.environmentBearingDegrees, rhs.environmentBearingUncertaintyDegrees);
  }
  inline bool operator<=(const MeasurementCorrections& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, horizontalPositionUncertaintyMeters, verticalPositionUncertaintyMeters, toaGpsNanosecondsOfWeek, satCorrections, hasEnvironmentBearing, environmentBearingDegrees, environmentBearingUncertaintyDegrees) <= std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.horizontalPositionUncertaintyMeters, rhs.verticalPositionUncertaintyMeters, rhs.toaGpsNanosecondsOfWeek, rhs.satCorrections, rhs.hasEnvironmentBearing, rhs.environmentBearingDegrees, rhs.environmentBearingUncertaintyDegrees);
  }
  inline bool operator==(const MeasurementCorrections& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, horizontalPositionUncertaintyMeters, verticalPositionUncertaintyMeters, toaGpsNanosecondsOfWeek, satCorrections, hasEnvironmentBearing, environmentBearingDegrees, environmentBearingUncertaintyDegrees) == std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.horizontalPositionUncertaintyMeters, rhs.verticalPositionUncertaintyMeters, rhs.toaGpsNanosecondsOfWeek, rhs.satCorrections, rhs.hasEnvironmentBearing, rhs.environmentBearingDegrees, rhs.environmentBearingUncertaintyDegrees);
  }
  inline bool operator>(const MeasurementCorrections& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, horizontalPositionUncertaintyMeters, verticalPositionUncertaintyMeters, toaGpsNanosecondsOfWeek, satCorrections, hasEnvironmentBearing, environmentBearingDegrees, environmentBearingUncertaintyDegrees) > std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.horizontalPositionUncertaintyMeters, rhs.verticalPositionUncertaintyMeters, rhs.toaGpsNanosecondsOfWeek, rhs.satCorrections, rhs.hasEnvironmentBearing, rhs.environmentBearingDegrees, rhs.environmentBearingUncertaintyDegrees);
  }
  inline bool operator>=(const MeasurementCorrections& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, horizontalPositionUncertaintyMeters, verticalPositionUncertaintyMeters, toaGpsNanosecondsOfWeek, satCorrections, hasEnvironmentBearing, environmentBearingDegrees, environmentBearingUncertaintyDegrees) >= std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.horizontalPositionUncertaintyMeters, rhs.verticalPositionUncertaintyMeters, rhs.toaGpsNanosecondsOfWeek, rhs.satCorrections, rhs.hasEnvironmentBearing, rhs.environmentBearingDegrees, rhs.environmentBearingUncertaintyDegrees);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "MeasurementCorrections{";
    os << "latitudeDegrees: " << ::android::internal::ToString(latitudeDegrees);
    os << ", longitudeDegrees: " << ::android::internal::ToString(longitudeDegrees);
    os << ", altitudeMeters: " << ::android::internal::ToString(altitudeMeters);
    os << ", horizontalPositionUncertaintyMeters: " << ::android::internal::ToString(horizontalPositionUncertaintyMeters);
    os << ", verticalPositionUncertaintyMeters: " << ::android::internal::ToString(verticalPositionUncertaintyMeters);
    os << ", toaGpsNanosecondsOfWeek: " << ::android::internal::ToString(toaGpsNanosecondsOfWeek);
    os << ", satCorrections: " << ::android::internal::ToString(satCorrections);
    os << ", hasEnvironmentBearing: " << ::android::internal::ToString(hasEnvironmentBearing);
    os << ", environmentBearingDegrees: " << ::android::internal::ToString(environmentBearingDegrees);
    os << ", environmentBearingUncertaintyDegrees: " << ::android::internal::ToString(environmentBearingUncertaintyDegrees);
    os << "}";
    return os.str();
  }
};
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
