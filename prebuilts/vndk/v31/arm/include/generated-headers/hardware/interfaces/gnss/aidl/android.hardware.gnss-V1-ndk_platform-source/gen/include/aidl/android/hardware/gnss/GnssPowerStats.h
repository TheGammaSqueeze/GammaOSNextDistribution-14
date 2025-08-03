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
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class GnssPowerStats {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::gnss::ElapsedRealtime elapsedRealtime;
  double totalEnergyMilliJoule = 0.000000;
  double singlebandTrackingModeEnergyMilliJoule = 0.000000;
  double multibandTrackingModeEnergyMilliJoule = 0.000000;
  double singlebandAcquisitionModeEnergyMilliJoule = 0.000000;
  double multibandAcquisitionModeEnergyMilliJoule = 0.000000;
  std::vector<double> otherModesEnergyMilliJoule;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GnssPowerStats& rhs) const {
    return std::tie(elapsedRealtime, totalEnergyMilliJoule, singlebandTrackingModeEnergyMilliJoule, multibandTrackingModeEnergyMilliJoule, singlebandAcquisitionModeEnergyMilliJoule, multibandAcquisitionModeEnergyMilliJoule, otherModesEnergyMilliJoule) != std::tie(rhs.elapsedRealtime, rhs.totalEnergyMilliJoule, rhs.singlebandTrackingModeEnergyMilliJoule, rhs.multibandTrackingModeEnergyMilliJoule, rhs.singlebandAcquisitionModeEnergyMilliJoule, rhs.multibandAcquisitionModeEnergyMilliJoule, rhs.otherModesEnergyMilliJoule);
  }
  inline bool operator<(const GnssPowerStats& rhs) const {
    return std::tie(elapsedRealtime, totalEnergyMilliJoule, singlebandTrackingModeEnergyMilliJoule, multibandTrackingModeEnergyMilliJoule, singlebandAcquisitionModeEnergyMilliJoule, multibandAcquisitionModeEnergyMilliJoule, otherModesEnergyMilliJoule) < std::tie(rhs.elapsedRealtime, rhs.totalEnergyMilliJoule, rhs.singlebandTrackingModeEnergyMilliJoule, rhs.multibandTrackingModeEnergyMilliJoule, rhs.singlebandAcquisitionModeEnergyMilliJoule, rhs.multibandAcquisitionModeEnergyMilliJoule, rhs.otherModesEnergyMilliJoule);
  }
  inline bool operator<=(const GnssPowerStats& rhs) const {
    return std::tie(elapsedRealtime, totalEnergyMilliJoule, singlebandTrackingModeEnergyMilliJoule, multibandTrackingModeEnergyMilliJoule, singlebandAcquisitionModeEnergyMilliJoule, multibandAcquisitionModeEnergyMilliJoule, otherModesEnergyMilliJoule) <= std::tie(rhs.elapsedRealtime, rhs.totalEnergyMilliJoule, rhs.singlebandTrackingModeEnergyMilliJoule, rhs.multibandTrackingModeEnergyMilliJoule, rhs.singlebandAcquisitionModeEnergyMilliJoule, rhs.multibandAcquisitionModeEnergyMilliJoule, rhs.otherModesEnergyMilliJoule);
  }
  inline bool operator==(const GnssPowerStats& rhs) const {
    return std::tie(elapsedRealtime, totalEnergyMilliJoule, singlebandTrackingModeEnergyMilliJoule, multibandTrackingModeEnergyMilliJoule, singlebandAcquisitionModeEnergyMilliJoule, multibandAcquisitionModeEnergyMilliJoule, otherModesEnergyMilliJoule) == std::tie(rhs.elapsedRealtime, rhs.totalEnergyMilliJoule, rhs.singlebandTrackingModeEnergyMilliJoule, rhs.multibandTrackingModeEnergyMilliJoule, rhs.singlebandAcquisitionModeEnergyMilliJoule, rhs.multibandAcquisitionModeEnergyMilliJoule, rhs.otherModesEnergyMilliJoule);
  }
  inline bool operator>(const GnssPowerStats& rhs) const {
    return std::tie(elapsedRealtime, totalEnergyMilliJoule, singlebandTrackingModeEnergyMilliJoule, multibandTrackingModeEnergyMilliJoule, singlebandAcquisitionModeEnergyMilliJoule, multibandAcquisitionModeEnergyMilliJoule, otherModesEnergyMilliJoule) > std::tie(rhs.elapsedRealtime, rhs.totalEnergyMilliJoule, rhs.singlebandTrackingModeEnergyMilliJoule, rhs.multibandTrackingModeEnergyMilliJoule, rhs.singlebandAcquisitionModeEnergyMilliJoule, rhs.multibandAcquisitionModeEnergyMilliJoule, rhs.otherModesEnergyMilliJoule);
  }
  inline bool operator>=(const GnssPowerStats& rhs) const {
    return std::tie(elapsedRealtime, totalEnergyMilliJoule, singlebandTrackingModeEnergyMilliJoule, multibandTrackingModeEnergyMilliJoule, singlebandAcquisitionModeEnergyMilliJoule, multibandAcquisitionModeEnergyMilliJoule, otherModesEnergyMilliJoule) >= std::tie(rhs.elapsedRealtime, rhs.totalEnergyMilliJoule, rhs.singlebandTrackingModeEnergyMilliJoule, rhs.multibandTrackingModeEnergyMilliJoule, rhs.singlebandAcquisitionModeEnergyMilliJoule, rhs.multibandAcquisitionModeEnergyMilliJoule, rhs.otherModesEnergyMilliJoule);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "GnssPowerStats{";
    os << "elapsedRealtime: " << ::android::internal::ToString(elapsedRealtime);
    os << ", totalEnergyMilliJoule: " << ::android::internal::ToString(totalEnergyMilliJoule);
    os << ", singlebandTrackingModeEnergyMilliJoule: " << ::android::internal::ToString(singlebandTrackingModeEnergyMilliJoule);
    os << ", multibandTrackingModeEnergyMilliJoule: " << ::android::internal::ToString(multibandTrackingModeEnergyMilliJoule);
    os << ", singlebandAcquisitionModeEnergyMilliJoule: " << ::android::internal::ToString(singlebandAcquisitionModeEnergyMilliJoule);
    os << ", multibandAcquisitionModeEnergyMilliJoule: " << ::android::internal::ToString(multibandAcquisitionModeEnergyMilliJoule);
    os << ", otherModesEnergyMilliJoule: " << ::android::internal::ToString(otherModesEnergyMilliJoule);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
