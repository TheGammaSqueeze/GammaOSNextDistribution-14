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
#include <aidl/android/hardware/automotive/occupant_awareness/ConfidenceLevel.h>
namespace aidl {
namespace android {
namespace hardware {
namespace automotive {
namespace occupant_awareness {
class DriverMonitoringDetection {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel confidenceScore = ::aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel(0);
  bool isLookingOnRoad = false;
  int64_t gazeDurationMillis = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DriverMonitoringDetection& rhs) const {
    return std::tie(confidenceScore, isLookingOnRoad, gazeDurationMillis) != std::tie(rhs.confidenceScore, rhs.isLookingOnRoad, rhs.gazeDurationMillis);
  }
  inline bool operator<(const DriverMonitoringDetection& rhs) const {
    return std::tie(confidenceScore, isLookingOnRoad, gazeDurationMillis) < std::tie(rhs.confidenceScore, rhs.isLookingOnRoad, rhs.gazeDurationMillis);
  }
  inline bool operator<=(const DriverMonitoringDetection& rhs) const {
    return std::tie(confidenceScore, isLookingOnRoad, gazeDurationMillis) <= std::tie(rhs.confidenceScore, rhs.isLookingOnRoad, rhs.gazeDurationMillis);
  }
  inline bool operator==(const DriverMonitoringDetection& rhs) const {
    return std::tie(confidenceScore, isLookingOnRoad, gazeDurationMillis) == std::tie(rhs.confidenceScore, rhs.isLookingOnRoad, rhs.gazeDurationMillis);
  }
  inline bool operator>(const DriverMonitoringDetection& rhs) const {
    return std::tie(confidenceScore, isLookingOnRoad, gazeDurationMillis) > std::tie(rhs.confidenceScore, rhs.isLookingOnRoad, rhs.gazeDurationMillis);
  }
  inline bool operator>=(const DriverMonitoringDetection& rhs) const {
    return std::tie(confidenceScore, isLookingOnRoad, gazeDurationMillis) >= std::tie(rhs.confidenceScore, rhs.isLookingOnRoad, rhs.gazeDurationMillis);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DriverMonitoringDetection{";
    os << "confidenceScore: " << ::android::internal::ToString(confidenceScore);
    os << ", isLookingOnRoad: " << ::android::internal::ToString(isLookingOnRoad);
    os << ", gazeDurationMillis: " << ::android::internal::ToString(gazeDurationMillis);
    os << "}";
    return os.str();
  }
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
