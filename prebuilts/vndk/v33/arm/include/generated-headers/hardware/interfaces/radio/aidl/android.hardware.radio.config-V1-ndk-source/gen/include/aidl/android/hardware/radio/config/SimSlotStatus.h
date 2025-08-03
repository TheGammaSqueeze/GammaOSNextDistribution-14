#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/config/SimPortInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace config {
class SimSlotStatus {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t cardState = 0;
  std::string atr;
  std::string eid;
  std::vector<::aidl::android::hardware::radio::config::SimPortInfo> portInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SimSlotStatus& rhs) const {
    return std::tie(cardState, atr, eid, portInfo) != std::tie(rhs.cardState, rhs.atr, rhs.eid, rhs.portInfo);
  }
  inline bool operator<(const SimSlotStatus& rhs) const {
    return std::tie(cardState, atr, eid, portInfo) < std::tie(rhs.cardState, rhs.atr, rhs.eid, rhs.portInfo);
  }
  inline bool operator<=(const SimSlotStatus& rhs) const {
    return std::tie(cardState, atr, eid, portInfo) <= std::tie(rhs.cardState, rhs.atr, rhs.eid, rhs.portInfo);
  }
  inline bool operator==(const SimSlotStatus& rhs) const {
    return std::tie(cardState, atr, eid, portInfo) == std::tie(rhs.cardState, rhs.atr, rhs.eid, rhs.portInfo);
  }
  inline bool operator>(const SimSlotStatus& rhs) const {
    return std::tie(cardState, atr, eid, portInfo) > std::tie(rhs.cardState, rhs.atr, rhs.eid, rhs.portInfo);
  }
  inline bool operator>=(const SimSlotStatus& rhs) const {
    return std::tie(cardState, atr, eid, portInfo) >= std::tie(rhs.cardState, rhs.atr, rhs.eid, rhs.portInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SimSlotStatus{";
    os << "cardState: " << ::android::internal::ToString(cardState);
    os << ", atr: " << ::android::internal::ToString(atr);
    os << ", eid: " << ::android::internal::ToString(eid);
    os << ", portInfo: " << ::android::internal::ToString(portInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
