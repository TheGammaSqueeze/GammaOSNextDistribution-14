#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/supplicant/IfaceType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class IfaceInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::wifi::supplicant::IfaceType type = ::aidl::android::hardware::wifi::supplicant::IfaceType(0);
  std::string name;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const IfaceInfo& rhs) const {
    return std::tie(type, name) != std::tie(rhs.type, rhs.name);
  }
  inline bool operator<(const IfaceInfo& rhs) const {
    return std::tie(type, name) < std::tie(rhs.type, rhs.name);
  }
  inline bool operator<=(const IfaceInfo& rhs) const {
    return std::tie(type, name) <= std::tie(rhs.type, rhs.name);
  }
  inline bool operator==(const IfaceInfo& rhs) const {
    return std::tie(type, name) == std::tie(rhs.type, rhs.name);
  }
  inline bool operator>(const IfaceInfo& rhs) const {
    return std::tie(type, name) > std::tie(rhs.type, rhs.name);
  }
  inline bool operator>=(const IfaceInfo& rhs) const {
    return std::tie(type, name) >= std::tie(rhs.type, rhs.name);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "IfaceInfo{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", name: " << ::android::internal::ToString(name);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
