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
class SlotPortMapping {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t physicalSlotId = 0;
  int32_t portId = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SlotPortMapping& rhs) const {
    return std::tie(physicalSlotId, portId) != std::tie(rhs.physicalSlotId, rhs.portId);
  }
  inline bool operator<(const SlotPortMapping& rhs) const {
    return std::tie(physicalSlotId, portId) < std::tie(rhs.physicalSlotId, rhs.portId);
  }
  inline bool operator<=(const SlotPortMapping& rhs) const {
    return std::tie(physicalSlotId, portId) <= std::tie(rhs.physicalSlotId, rhs.portId);
  }
  inline bool operator==(const SlotPortMapping& rhs) const {
    return std::tie(physicalSlotId, portId) == std::tie(rhs.physicalSlotId, rhs.portId);
  }
  inline bool operator>(const SlotPortMapping& rhs) const {
    return std::tie(physicalSlotId, portId) > std::tie(rhs.physicalSlotId, rhs.portId);
  }
  inline bool operator>=(const SlotPortMapping& rhs) const {
    return std::tie(physicalSlotId, portId) >= std::tie(rhs.physicalSlotId, rhs.portId);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SlotPortMapping{";
    os << "physicalSlotId: " << ::android::internal::ToString(physicalSlotId);
    os << ", portId: " << ::android::internal::ToString(portId);
    os << "}";
    return os.str();
  }
};
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
