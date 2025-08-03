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
#include <aidl/android/hardware/gnss/GnssSignalType.h>
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class GnssClock {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t gnssClockFlags = 0;
  int32_t leapSecond = 0;
  int64_t timeNs = 0L;
  double timeUncertaintyNs = 0.000000;
  int64_t fullBiasNs = 0L;
  double biasNs = 0.000000;
  double biasUncertaintyNs = 0.000000;
  double driftNsps = 0.000000;
  double driftUncertaintyNsps = 0.000000;
  int32_t hwClockDiscontinuityCount = 0;
  ::aidl::android::hardware::gnss::GnssSignalType referenceSignalTypeForIsb;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GnssClock& rhs) const {
    return std::tie(gnssClockFlags, leapSecond, timeNs, timeUncertaintyNs, fullBiasNs, biasNs, biasUncertaintyNs, driftNsps, driftUncertaintyNsps, hwClockDiscontinuityCount, referenceSignalTypeForIsb) != std::tie(rhs.gnssClockFlags, rhs.leapSecond, rhs.timeNs, rhs.timeUncertaintyNs, rhs.fullBiasNs, rhs.biasNs, rhs.biasUncertaintyNs, rhs.driftNsps, rhs.driftUncertaintyNsps, rhs.hwClockDiscontinuityCount, rhs.referenceSignalTypeForIsb);
  }
  inline bool operator<(const GnssClock& rhs) const {
    return std::tie(gnssClockFlags, leapSecond, timeNs, timeUncertaintyNs, fullBiasNs, biasNs, biasUncertaintyNs, driftNsps, driftUncertaintyNsps, hwClockDiscontinuityCount, referenceSignalTypeForIsb) < std::tie(rhs.gnssClockFlags, rhs.leapSecond, rhs.timeNs, rhs.timeUncertaintyNs, rhs.fullBiasNs, rhs.biasNs, rhs.biasUncertaintyNs, rhs.driftNsps, rhs.driftUncertaintyNsps, rhs.hwClockDiscontinuityCount, rhs.referenceSignalTypeForIsb);
  }
  inline bool operator<=(const GnssClock& rhs) const {
    return std::tie(gnssClockFlags, leapSecond, timeNs, timeUncertaintyNs, fullBiasNs, biasNs, biasUncertaintyNs, driftNsps, driftUncertaintyNsps, hwClockDiscontinuityCount, referenceSignalTypeForIsb) <= std::tie(rhs.gnssClockFlags, rhs.leapSecond, rhs.timeNs, rhs.timeUncertaintyNs, rhs.fullBiasNs, rhs.biasNs, rhs.biasUncertaintyNs, rhs.driftNsps, rhs.driftUncertaintyNsps, rhs.hwClockDiscontinuityCount, rhs.referenceSignalTypeForIsb);
  }
  inline bool operator==(const GnssClock& rhs) const {
    return std::tie(gnssClockFlags, leapSecond, timeNs, timeUncertaintyNs, fullBiasNs, biasNs, biasUncertaintyNs, driftNsps, driftUncertaintyNsps, hwClockDiscontinuityCount, referenceSignalTypeForIsb) == std::tie(rhs.gnssClockFlags, rhs.leapSecond, rhs.timeNs, rhs.timeUncertaintyNs, rhs.fullBiasNs, rhs.biasNs, rhs.biasUncertaintyNs, rhs.driftNsps, rhs.driftUncertaintyNsps, rhs.hwClockDiscontinuityCount, rhs.referenceSignalTypeForIsb);
  }
  inline bool operator>(const GnssClock& rhs) const {
    return std::tie(gnssClockFlags, leapSecond, timeNs, timeUncertaintyNs, fullBiasNs, biasNs, biasUncertaintyNs, driftNsps, driftUncertaintyNsps, hwClockDiscontinuityCount, referenceSignalTypeForIsb) > std::tie(rhs.gnssClockFlags, rhs.leapSecond, rhs.timeNs, rhs.timeUncertaintyNs, rhs.fullBiasNs, rhs.biasNs, rhs.biasUncertaintyNs, rhs.driftNsps, rhs.driftUncertaintyNsps, rhs.hwClockDiscontinuityCount, rhs.referenceSignalTypeForIsb);
  }
  inline bool operator>=(const GnssClock& rhs) const {
    return std::tie(gnssClockFlags, leapSecond, timeNs, timeUncertaintyNs, fullBiasNs, biasNs, biasUncertaintyNs, driftNsps, driftUncertaintyNsps, hwClockDiscontinuityCount, referenceSignalTypeForIsb) >= std::tie(rhs.gnssClockFlags, rhs.leapSecond, rhs.timeNs, rhs.timeUncertaintyNs, rhs.fullBiasNs, rhs.biasNs, rhs.biasUncertaintyNs, rhs.driftNsps, rhs.driftUncertaintyNsps, rhs.hwClockDiscontinuityCount, rhs.referenceSignalTypeForIsb);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { HAS_LEAP_SECOND = 1 };
  enum : int32_t { HAS_TIME_UNCERTAINTY = 2 };
  enum : int32_t { HAS_FULL_BIAS = 4 };
  enum : int32_t { HAS_BIAS = 8 };
  enum : int32_t { HAS_BIAS_UNCERTAINTY = 16 };
  enum : int32_t { HAS_DRIFT = 32 };
  enum : int32_t { HAS_DRIFT_UNCERTAINTY = 64 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "GnssClock{";
    os << "gnssClockFlags: " << ::android::internal::ToString(gnssClockFlags);
    os << ", leapSecond: " << ::android::internal::ToString(leapSecond);
    os << ", timeNs: " << ::android::internal::ToString(timeNs);
    os << ", timeUncertaintyNs: " << ::android::internal::ToString(timeUncertaintyNs);
    os << ", fullBiasNs: " << ::android::internal::ToString(fullBiasNs);
    os << ", biasNs: " << ::android::internal::ToString(biasNs);
    os << ", biasUncertaintyNs: " << ::android::internal::ToString(biasUncertaintyNs);
    os << ", driftNsps: " << ::android::internal::ToString(driftNsps);
    os << ", driftUncertaintyNsps: " << ::android::internal::ToString(driftUncertaintyNsps);
    os << ", hwClockDiscontinuityCount: " << ::android::internal::ToString(hwClockDiscontinuityCount);
    os << ", referenceSignalTypeForIsb: " << ::android::internal::ToString(referenceSignalTypeForIsb);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
