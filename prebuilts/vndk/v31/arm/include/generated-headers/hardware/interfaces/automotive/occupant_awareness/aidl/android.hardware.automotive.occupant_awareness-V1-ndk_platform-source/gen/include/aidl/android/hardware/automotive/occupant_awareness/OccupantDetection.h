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
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::automotive::occupant_awareness::Role role = ::aidl::android::hardware::automotive::occupant_awareness::Role(0);
  std::vector<::aidl::android::hardware::automotive::occupant_awareness::PresenceDetection> presenceData;
  std::vector<::aidl::android::hardware::automotive::occupant_awareness::GazeDetection> gazeData;
  std::vector<::aidl::android::hardware::automotive::occupant_awareness::DriverMonitoringDetection> attentionData;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const OccupantDetection& rhs) const {
    return std::tie(role, presenceData, gazeData, attentionData) != std::tie(rhs.role, rhs.presenceData, rhs.gazeData, rhs.attentionData);
  }
  inline bool operator<(const OccupantDetection& rhs) const {
    return std::tie(role, presenceData, gazeData, attentionData) < std::tie(rhs.role, rhs.presenceData, rhs.gazeData, rhs.attentionData);
  }
  inline bool operator<=(const OccupantDetection& rhs) const {
    return std::tie(role, presenceData, gazeData, attentionData) <= std::tie(rhs.role, rhs.presenceData, rhs.gazeData, rhs.attentionData);
  }
  inline bool operator==(const OccupantDetection& rhs) const {
    return std::tie(role, presenceData, gazeData, attentionData) == std::tie(rhs.role, rhs.presenceData, rhs.gazeData, rhs.attentionData);
  }
  inline bool operator>(const OccupantDetection& rhs) const {
    return std::tie(role, presenceData, gazeData, attentionData) > std::tie(rhs.role, rhs.presenceData, rhs.gazeData, rhs.attentionData);
  }
  inline bool operator>=(const OccupantDetection& rhs) const {
    return std::tie(role, presenceData, gazeData, attentionData) >= std::tie(rhs.role, rhs.presenceData, rhs.gazeData, rhs.attentionData);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "OccupantDetection{";
    os << "role: " << ::android::internal::ToString(role);
    os << ", presenceData: " << ::android::internal::ToString(presenceData);
    os << ", gazeData: " << ::android::internal::ToString(gazeData);
    os << ", attentionData: " << ::android::internal::ToString(attentionData);
    os << "}";
    return os.str();
  }
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
