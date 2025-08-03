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
class SatelliteClockInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  double satHardwareCodeBiasMeters = 0.000000;
  double satTimeCorrectionMeters = 0.000000;
  double satClkDriftMps = 0.000000;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SatelliteClockInfo& rhs) const {
    return std::tie(satHardwareCodeBiasMeters, satTimeCorrectionMeters, satClkDriftMps) != std::tie(rhs.satHardwareCodeBiasMeters, rhs.satTimeCorrectionMeters, rhs.satClkDriftMps);
  }
  inline bool operator<(const SatelliteClockInfo& rhs) const {
    return std::tie(satHardwareCodeBiasMeters, satTimeCorrectionMeters, satClkDriftMps) < std::tie(rhs.satHardwareCodeBiasMeters, rhs.satTimeCorrectionMeters, rhs.satClkDriftMps);
  }
  inline bool operator<=(const SatelliteClockInfo& rhs) const {
    return std::tie(satHardwareCodeBiasMeters, satTimeCorrectionMeters, satClkDriftMps) <= std::tie(rhs.satHardwareCodeBiasMeters, rhs.satTimeCorrectionMeters, rhs.satClkDriftMps);
  }
  inline bool operator==(const SatelliteClockInfo& rhs) const {
    return std::tie(satHardwareCodeBiasMeters, satTimeCorrectionMeters, satClkDriftMps) == std::tie(rhs.satHardwareCodeBiasMeters, rhs.satTimeCorrectionMeters, rhs.satClkDriftMps);
  }
  inline bool operator>(const SatelliteClockInfo& rhs) const {
    return std::tie(satHardwareCodeBiasMeters, satTimeCorrectionMeters, satClkDriftMps) > std::tie(rhs.satHardwareCodeBiasMeters, rhs.satTimeCorrectionMeters, rhs.satClkDriftMps);
  }
  inline bool operator>=(const SatelliteClockInfo& rhs) const {
    return std::tie(satHardwareCodeBiasMeters, satTimeCorrectionMeters, satClkDriftMps) >= std::tie(rhs.satHardwareCodeBiasMeters, rhs.satTimeCorrectionMeters, rhs.satClkDriftMps);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SatelliteClockInfo{";
    os << "satHardwareCodeBiasMeters: " << ::android::internal::ToString(satHardwareCodeBiasMeters);
    os << ", satTimeCorrectionMeters: " << ::android::internal::ToString(satTimeCorrectionMeters);
    os << ", satClkDriftMps: " << ::android::internal::ToString(satClkDriftMps);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
