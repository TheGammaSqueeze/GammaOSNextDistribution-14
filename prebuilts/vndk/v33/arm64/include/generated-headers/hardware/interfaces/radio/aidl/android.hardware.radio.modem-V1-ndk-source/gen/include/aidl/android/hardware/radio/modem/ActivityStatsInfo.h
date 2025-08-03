#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/modem/ActivityStatsTechSpecificInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class ActivityStatsInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t sleepModeTimeMs = 0;
  int32_t idleModeTimeMs = 0;
  std::vector<::aidl::android::hardware::radio::modem::ActivityStatsTechSpecificInfo> techSpecificInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ActivityStatsInfo& rhs) const {
    return std::tie(sleepModeTimeMs, idleModeTimeMs, techSpecificInfo) != std::tie(rhs.sleepModeTimeMs, rhs.idleModeTimeMs, rhs.techSpecificInfo);
  }
  inline bool operator<(const ActivityStatsInfo& rhs) const {
    return std::tie(sleepModeTimeMs, idleModeTimeMs, techSpecificInfo) < std::tie(rhs.sleepModeTimeMs, rhs.idleModeTimeMs, rhs.techSpecificInfo);
  }
  inline bool operator<=(const ActivityStatsInfo& rhs) const {
    return std::tie(sleepModeTimeMs, idleModeTimeMs, techSpecificInfo) <= std::tie(rhs.sleepModeTimeMs, rhs.idleModeTimeMs, rhs.techSpecificInfo);
  }
  inline bool operator==(const ActivityStatsInfo& rhs) const {
    return std::tie(sleepModeTimeMs, idleModeTimeMs, techSpecificInfo) == std::tie(rhs.sleepModeTimeMs, rhs.idleModeTimeMs, rhs.techSpecificInfo);
  }
  inline bool operator>(const ActivityStatsInfo& rhs) const {
    return std::tie(sleepModeTimeMs, idleModeTimeMs, techSpecificInfo) > std::tie(rhs.sleepModeTimeMs, rhs.idleModeTimeMs, rhs.techSpecificInfo);
  }
  inline bool operator>=(const ActivityStatsInfo& rhs) const {
    return std::tie(sleepModeTimeMs, idleModeTimeMs, techSpecificInfo) >= std::tie(rhs.sleepModeTimeMs, rhs.idleModeTimeMs, rhs.techSpecificInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ActivityStatsInfo{";
    os << "sleepModeTimeMs: " << ::android::internal::ToString(sleepModeTimeMs);
    os << ", idleModeTimeMs: " << ::android::internal::ToString(idleModeTimeMs);
    os << ", techSpecificInfo: " << ::android::internal::ToString(techSpecificInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
