#pragma once
#include <android/binder_interface_utils.h>

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
  static const char* descriptor;

  ::aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel gazeConfidence = ::aidl::android::hardware::automotive::occupant_awareness::ConfidenceLevel(0);
  std::vector<double> headPosition;
  std::vector<double> headAngleUnitVector;
  std::vector<double> gazeAngleUnitVector;
  ::aidl::android::hardware::automotive::occupant_awareness::VehicleRegion gazeTarget = ::aidl::android::hardware::automotive::occupant_awareness::VehicleRegion(0);
  std::string customGazeTarget;
  int64_t timeOnTargetMillis = {};

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
