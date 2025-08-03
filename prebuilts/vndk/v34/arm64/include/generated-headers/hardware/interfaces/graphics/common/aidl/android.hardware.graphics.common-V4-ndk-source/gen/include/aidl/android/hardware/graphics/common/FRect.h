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
namespace graphics {
namespace common {
class FRect {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  float left = 0.000000f;
  float top = 0.000000f;
  float right = 0.000000f;
  float bottom = 0.000000f;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const FRect& rhs) const {
    return std::tie(left, top, right, bottom) != std::tie(rhs.left, rhs.top, rhs.right, rhs.bottom);
  }
  inline bool operator<(const FRect& rhs) const {
    return std::tie(left, top, right, bottom) < std::tie(rhs.left, rhs.top, rhs.right, rhs.bottom);
  }
  inline bool operator<=(const FRect& rhs) const {
    return std::tie(left, top, right, bottom) <= std::tie(rhs.left, rhs.top, rhs.right, rhs.bottom);
  }
  inline bool operator==(const FRect& rhs) const {
    return std::tie(left, top, right, bottom) == std::tie(rhs.left, rhs.top, rhs.right, rhs.bottom);
  }
  inline bool operator>(const FRect& rhs) const {
    return std::tie(left, top, right, bottom) > std::tie(rhs.left, rhs.top, rhs.right, rhs.bottom);
  }
  inline bool operator>=(const FRect& rhs) const {
    return std::tie(left, top, right, bottom) >= std::tie(rhs.left, rhs.top, rhs.right, rhs.bottom);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "FRect{";
    os << "left: " << ::android::internal::ToString(left);
    os << ", top: " << ::android::internal::ToString(top);
    os << ", right: " << ::android::internal::ToString(right);
    os << ", bottom: " << ::android::internal::ToString(bottom);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
