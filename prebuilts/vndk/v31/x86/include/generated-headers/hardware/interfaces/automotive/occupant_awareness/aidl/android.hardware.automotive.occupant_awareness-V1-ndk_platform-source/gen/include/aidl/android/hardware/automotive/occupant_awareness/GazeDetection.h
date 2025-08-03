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
#include <aidl/android/hardware/automotive/occupant_awareness/VehicleRegion.h>
namespace aidl {
namespace android {
namespace hardware {
namespace automotive {
namespace occupant_awareness {
class GazeDetection {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel gazeConfidence = ::aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel(0);
  std::vector<double> headPosition;
  std::vector<double> headAngleUnitVector;
  std::vector<double> gazeAngleUnitVector;
  ::aidl::android::hardware::automotive::occupant_awareness::VehicleRegion gazeTarget = ::aidl::android::hardware::automotive::occupant_awareness::VehicleRegion(0);
  std::string customGazeTarget;
  int64_t timeOnTargetMillis = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GazeDetection& rhs) const {
    return std::tie(gazeConfidence, headPosition, headAngleUnitVector, gazeAngleUnitVector, gazeTarget, customGazeTarget, timeOnTargetMillis) != std::tie(rhs.gazeConfidence, rhs.headPosition, rhs.headAngleUnitVector, rhs.gazeAngleUnitVector, rhs.gazeTarget, rhs.customGazeTarget, rhs.timeOnTargetMillis);
  }
  inline bool operator<(const GazeDetection& rhs) const {
    return std::tie(gazeConfidence, headPosition, headAngleUnitVector, gazeAngleUnitVector, gazeTarget, customGazeTarget, timeOnTargetMillis) < std::tie(rhs.gazeConfidence, rhs.headPosition, rhs.headAngleUnitVector, rhs.gazeAngleUnitVector, rhs.gazeTarget, rhs.customGazeTarget, rhs.timeOnTargetMillis);
  }
  inline bool operator<=(const GazeDetection& rhs) const {
    return std::tie(gazeConfidence, headPosition, headAngleUnitVector, gazeAngleUnitVector, gazeTarget, customGazeTarget, timeOnTargetMillis) <= std::tie(rhs.gazeConfidence, rhs.headPosition, rhs.headAngleUnitVector, rhs.gazeAngleUnitVector, rhs.gazeTarget, rhs.customGazeTarget, rhs.timeOnTargetMillis);
  }
  inline bool operator==(const GazeDetection& rhs) const {
    return std::tie(gazeConfidence, headPosition, headAngleUnitVector, gazeAngleUnitVector, gazeTarget, customGazeTarget, timeOnTargetMillis) == std::tie(rhs.gazeConfidence, rhs.headPosition, rhs.headAngleUnitVector, rhs.gazeAngleUnitVector, rhs.gazeTarget, rhs.customGazeTarget, rhs.timeOnTargetMillis);
  }
  inline bool operator>(const GazeDetection& rhs) const {
    return std::tie(gazeConfidence, headPosition, headAngleUnitVector, gazeAngleUnitVector, gazeTarget, customGazeTarget, timeOnTargetMillis) > std::tie(rhs.gazeConfidence, rhs.headPosition, rhs.headAngleUnitVector, rhs.gazeAngleUnitVector, rhs.gazeTarget, rhs.customGazeTarget, rhs.timeOnTargetMillis);
  }
  inline bool operator>=(const GazeDetection& rhs) const {
    return std::tie(gazeConfidence, headPosition, headAngleUnitVector, gazeAngleUnitVector, gazeTarget, customGazeTarget, timeOnTargetMillis) >= std::tie(rhs.gazeConfidence, rhs.headPosition, rhs.headAngleUnitVector, rhs.gazeAngleUnitVector, rhs.gazeTarget, rhs.customGazeTarget, rhs.timeOnTargetMillis);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "GazeDetection{";
    os << "gazeConfidence: " << ::android::internal::ToString(gazeConfidence);
    os << ", headPosition: " << ::android::internal::ToString(headPosition);
    os << ", headAngleUnitVector: " << ::android::internal::ToString(headAngleUnitVector);
    os << ", gazeAngleUnitVector: " << ::android::internal::ToString(gazeAngleUnitVector);
    os << ", gazeTarget: " << ::android::internal::ToString(gazeTarget);
    os << ", customGazeTarget: " << ::android::internal::ToString(customGazeTarget);
    os << ", timeOnTargetMillis: " << ::android::internal::ToString(timeOnTargetMillis);
    os << "}";
    return os.str();
  }
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
