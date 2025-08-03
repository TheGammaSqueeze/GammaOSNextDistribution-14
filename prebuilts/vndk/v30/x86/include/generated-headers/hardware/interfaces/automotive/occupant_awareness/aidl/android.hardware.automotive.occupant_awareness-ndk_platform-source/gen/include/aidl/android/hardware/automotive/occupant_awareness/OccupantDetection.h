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
#include <aidl/android/hardware/automotive/occupant_awareness/DriverMonitoringDetection.h>
#include <aidl/android/hardware/automotive/occupant_awareness/GazeDetection.h>
#include <aidl/android/hardware/automotive/occupant_awareness/PresenceDetection.h>
#include <aidl/android/hardware/automotive/occupant_awareness/Role.h>
namespace aidl {
namespace android {
namespace hardware {
namespace automotive {
namespace occupant_awareness {
class OccupantDetection {
public:
  static const char* descriptor;

  ::aidl::android::hardware::automotive::occupant_awareness::Role role = ::aidl::android::hardware::automotive::occupant_awareness::Role(0);
  std::vector<::aidl::android::hardware::automotive::occupant_awareness::PresenceDetection> presenceData;
  std::vector<::aidl::android::hardware::automotive::occupant_awareness::GazeDetection> gazeData;
  std::vector<::aidl::android::hardware::automotive::occupant_awareness::DriverMonitoringDetection> attentionData;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
