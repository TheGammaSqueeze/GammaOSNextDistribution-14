#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace config {
class SimPortInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string iccId;
  int32_t logicalSlotId = 0;
  bool portActive = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SimPortInfo& rhs) const {
    return std::tie(iccId, logicalSlotId, portActive) != std::tie(rhs.iccId, rhs.logicalSlotId, rhs.portActive);
  }
  inline bool operator<(const SimPortInfo& rhs) const {
    return std::tie(iccId, logicalSlotId, portActive) < std::tie(rhs.iccId, rhs.logicalSlotId, rhs.portActive);
  }
  inline bool operator<=(const SimPortInfo& rhs) const {
    return std::tie(iccId, logicalSlotId, portActive) <= std::tie(rhs.iccId, rhs.logicalSlotId, rhs.portActive);
  }
  inline bool operator==(const SimPortInfo& rhs) const {
    return std::tie(iccId, logicalSlotId, portActive) == std::tie(rhs.iccId, rhs.logicalSlotId, rhs.portActive);
  }
  inline bool operator>(const SimPortInfo& rhs) const {
    return std::tie(iccId, logicalSlotId, portActive) > std::tie(rhs.iccId, rhs.logicalSlotId, rhs.portActive);
  }
  inline bool operator>=(const SimPortInfo& rhs) const {
    return std::tie(iccId, logicalSlotId, portActive) >= std::tie(rhs.iccId, rhs.logicalSlotId, rhs.portActive);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SimPortInfo{";
    os << "iccId: " << ::android::internal::ToString(iccId);
    os << ", logicalSlotId: " << ::android::internal::ToString(logicalSlotId);
    os << ", portActive: " << ::android::internal::ToString(portActive);
    os << "}";
    return os.str();
  }
};
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
