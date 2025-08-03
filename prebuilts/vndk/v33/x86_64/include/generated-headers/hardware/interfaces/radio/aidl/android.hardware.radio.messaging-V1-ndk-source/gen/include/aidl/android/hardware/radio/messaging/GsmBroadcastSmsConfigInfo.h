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
namespace messaging {
class GsmBroadcastSmsConfigInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t fromServiceId = 0;
  int32_t toServiceId = 0;
  int32_t fromCodeScheme = 0;
  int32_t toCodeScheme = 0;
  bool selected = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GsmBroadcastSmsConfigInfo& rhs) const {
    return std::tie(fromServiceId, toServiceId, fromCodeScheme, toCodeScheme, selected) != std::tie(rhs.fromServiceId, rhs.toServiceId, rhs.fromCodeScheme, rhs.toCodeScheme, rhs.selected);
  }
  inline bool operator<(const GsmBroadcastSmsConfigInfo& rhs) const {
    return std::tie(fromServiceId, toServiceId, fromCodeScheme, toCodeScheme, selected) < std::tie(rhs.fromServiceId, rhs.toServiceId, rhs.fromCodeScheme, rhs.toCodeScheme, rhs.selected);
  }
  inline bool operator<=(const GsmBroadcastSmsConfigInfo& rhs) const {
    return std::tie(fromServiceId, toServiceId, fromCodeScheme, toCodeScheme, selected) <= std::tie(rhs.fromServiceId, rhs.toServiceId, rhs.fromCodeScheme, rhs.toCodeScheme, rhs.selected);
  }
  inline bool operator==(const GsmBroadcastSmsConfigInfo& rhs) const {
    return std::tie(fromServiceId, toServiceId, fromCodeScheme, toCodeScheme, selected) == std::tie(rhs.fromServiceId, rhs.toServiceId, rhs.fromCodeScheme, rhs.toCodeScheme, rhs.selected);
  }
  inline bool operator>(const GsmBroadcastSmsConfigInfo& rhs) const {
    return std::tie(fromServiceId, toServiceId, fromCodeScheme, toCodeScheme, selected) > std::tie(rhs.fromServiceId, rhs.toServiceId, rhs.fromCodeScheme, rhs.toCodeScheme, rhs.selected);
  }
  inline bool operator>=(const GsmBroadcastSmsConfigInfo& rhs) const {
    return std::tie(fromServiceId, toServiceId, fromCodeScheme, toCodeScheme, selected) >= std::tie(rhs.fromServiceId, rhs.toServiceId, rhs.fromCodeScheme, rhs.toCodeScheme, rhs.selected);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "GsmBroadcastSmsConfigInfo{";
    os << "fromServiceId: " << ::android::internal::ToString(fromServiceId);
    os << ", toServiceId: " << ::android::internal::ToString(toServiceId);
    os << ", fromCodeScheme: " << ::android::internal::ToString(fromCodeScheme);
    os << ", toCodeScheme: " << ::android::internal::ToString(toCodeScheme);
    os << ", selected: " << ::android::internal::ToString(selected);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
