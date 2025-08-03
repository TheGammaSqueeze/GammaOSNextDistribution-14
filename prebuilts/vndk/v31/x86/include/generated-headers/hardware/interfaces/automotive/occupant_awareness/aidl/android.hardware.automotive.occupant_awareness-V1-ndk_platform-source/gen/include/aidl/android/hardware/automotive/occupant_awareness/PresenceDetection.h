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
namespace automotive {
namespace occupant_awareness {
class PresenceDetection {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool isOccupantDetected = false;
  int64_t detectionDurationMillis = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PresenceDetection& rhs) const {
    return std::tie(isOccupantDetected, detectionDurationMillis) != std::tie(rhs.isOccupantDetected, rhs.detectionDurationMillis);
  }
  inline bool operator<(const PresenceDetection& rhs) const {
    return std::tie(isOccupantDetected, detectionDurationMillis) < std::tie(rhs.isOccupantDetected, rhs.detectionDurationMillis);
  }
  inline bool operator<=(const PresenceDetection& rhs) const {
    return std::tie(isOccupantDetected, detectionDurationMillis) <= std::tie(rhs.isOccupantDetected, rhs.detectionDurationMillis);
  }
  inline bool operator==(const PresenceDetection& rhs) const {
    return std::tie(isOccupantDetected, detectionDurationMillis) == std::tie(rhs.isOccupantDetected, rhs.detectionDurationMillis);
  }
  inline bool operator>(const PresenceDetection& rhs) const {
    return std::tie(isOccupantDetected, detectionDurationMillis) > std::tie(rhs.isOccupantDetected, rhs.detectionDurationMillis);
  }
  inline bool operator>=(const PresenceDetection& rhs) const {
    return std::tie(isOccupantDetected, detectionDurationMillis) >= std::tie(rhs.isOccupantDetected, rhs.detectionDurationMillis);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PresenceDetection{";
    os << "isOccupantDetected: " << ::android::internal::ToString(isOccupantDetected);
    os << ", detectionDurationMillis: " << ::android::internal::ToString(detectionDurationMillis);
    os << "}";
    return os.str();
  }
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
