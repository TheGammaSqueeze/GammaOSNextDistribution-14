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
#include <aidl/android/hardware/gnss/ElapsedRealtime.h>
#include <aidl/android/hardware/gnss/GnssClock.h>
#include <aidl/android/hardware/gnss/GnssMeasurement.h>
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class GnssData {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::gnss::GnssMeasurement> measurements;
  ::aidl::android::hardware::gnss::GnssClock clock;
  ::aidl::android::hardware::gnss::ElapsedRealtime elapsedRealtime;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime) != std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime);
  }
  inline bool operator<(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime) < std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime);
  }
  inline bool operator<=(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime) <= std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime);
  }
  inline bool operator==(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime) == std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime);
  }
  inline bool operator>(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime) > std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime);
  }
  inline bool operator>=(const GnssData& rhs) const {
    return std::tie(measurements, clock, elapsedRealtime) >= std::tie(rhs.measurements, rhs.clock, rhs.elapsedRealtime);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "GnssData{";
    os << "measurements: " << ::android::internal::ToString(measurements);
    os << ", clock: " << ::android::internal::ToString(clock);
    os << ", elapsedRealtime: " << ::android::internal::ToString(elapsedRealtime);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
