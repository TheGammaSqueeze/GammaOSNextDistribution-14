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
namespace graphics {
namespace common {
class XyColor {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  float x = 0.000000f;
  float y = 0.000000f;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const XyColor& rhs) const {
    return std::tie(x, y) != std::tie(rhs.x, rhs.y);
  }
  inline bool operator<(const XyColor& rhs) const {
    return std::tie(x, y) < std::tie(rhs.x, rhs.y);
  }
  inline bool operator<=(const XyColor& rhs) const {
    return std::tie(x, y) <= std::tie(rhs.x, rhs.y);
  }
  inline bool operator==(const XyColor& rhs) const {
    return std::tie(x, y) == std::tie(rhs.x, rhs.y);
  }
  inline bool operator>(const XyColor& rhs) const {
    return std::tie(x, y) > std::tie(rhs.x, rhs.y);
  }
  inline bool operator>=(const XyColor& rhs) const {
    return std::tie(x, y) >= std::tie(rhs.x, rhs.y);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "XyColor{";
    os << "x: " << ::android::internal::ToString(x);
    os << ", y: " << ::android::internal::ToString(y);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
