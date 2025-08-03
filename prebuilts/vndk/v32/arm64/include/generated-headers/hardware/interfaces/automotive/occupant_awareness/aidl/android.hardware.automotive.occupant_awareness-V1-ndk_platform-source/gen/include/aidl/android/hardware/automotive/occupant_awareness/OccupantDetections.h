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
#include <aidl/android/hardware/automotive/occupant_awareness/OccupantDetection.h>
namespace aidl {
namespace android {
namespace hardware {
namespace automotive {
namespace occupant_awareness {
class OccupantDetections {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t timeStampMillis = 0L;
  std::vector<::aidl::android::hardware::automotive::occupant_awareness::OccupantDetection> detections;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const OccupantDetections& rhs) const {
    return std::tie(timeStampMillis, detections) != std::tie(rhs.timeStampMillis, rhs.detections);
  }
  inline bool operator<(const OccupantDetections& rhs) const {
    return std::tie(timeStampMillis, detections) < std::tie(rhs.timeStampMillis, rhs.detections);
  }
  inline bool operator<=(const OccupantDetections& rhs) const {
    return std::tie(timeStampMillis, detections) <= std::tie(rhs.timeStampMillis, rhs.detections);
  }
  inline bool operator==(const OccupantDetections& rhs) const {
    return std::tie(timeStampMillis, detections) == std::tie(rhs.timeStampMillis, rhs.detections);
  }
  inline bool operator>(const OccupantDetections& rhs) const {
    return std::tie(timeStampMillis, detections) > std::tie(rhs.timeStampMillis, rhs.detections);
  }
  inline bool operator>=(const OccupantDetections& rhs) const {
    return std::tie(timeStampMillis, detections) >= std::tie(rhs.timeStampMillis, rhs.detections);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "OccupantDetections{";
    os << "timeStampMillis: " << ::android::internal::ToString(timeStampMillis);
    os << ", detections: " << ::android::internal::ToString(detections);
    os << "}";
    return os.str();
  }
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
