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
namespace gnss {
namespace measurement_corrections {
class ReflectingPlane {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  double latitudeDegrees = 0.000000;
  double longitudeDegrees = 0.000000;
  double altitudeMeters = 0.000000;
  double reflectingPlaneAzimuthDegrees = 0.000000;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ReflectingPlane& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, reflectingPlaneAzimuthDegrees) != std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.reflectingPlaneAzimuthDegrees);
  }
  inline bool operator<(const ReflectingPlane& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, reflectingPlaneAzimuthDegrees) < std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.reflectingPlaneAzimuthDegrees);
  }
  inline bool operator<=(const ReflectingPlane& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, reflectingPlaneAzimuthDegrees) <= std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.reflectingPlaneAzimuthDegrees);
  }
  inline bool operator==(const ReflectingPlane& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, reflectingPlaneAzimuthDegrees) == std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.reflectingPlaneAzimuthDegrees);
  }
  inline bool operator>(const ReflectingPlane& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, reflectingPlaneAzimuthDegrees) > std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.reflectingPlaneAzimuthDegrees);
  }
  inline bool operator>=(const ReflectingPlane& rhs) const {
    return std::tie(latitudeDegrees, longitudeDegrees, altitudeMeters, reflectingPlaneAzimuthDegrees) >= std::tie(rhs.latitudeDegrees, rhs.longitudeDegrees, rhs.altitudeMeters, rhs.reflectingPlaneAzimuthDegrees);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ReflectingPlane{";
    os << "latitudeDegrees: " << ::android::internal::ToString(latitudeDegrees);
    os << ", longitudeDegrees: " << ::android::internal::ToString(longitudeDegrees);
    os << ", altitudeMeters: " << ::android::internal::ToString(altitudeMeters);
    os << ", reflectingPlaneAzimuthDegrees: " << ::android::internal::ToString(reflectingPlaneAzimuthDegrees);
    os << "}";
    return os.str();
  }
};
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
