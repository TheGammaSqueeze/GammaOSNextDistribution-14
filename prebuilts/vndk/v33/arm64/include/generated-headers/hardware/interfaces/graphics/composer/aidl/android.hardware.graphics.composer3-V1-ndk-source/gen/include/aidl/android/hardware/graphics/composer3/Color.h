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
namespace composer3 {
class Color {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  float r = 0.000000f;
  float g = 0.000000f;
  float b = 0.000000f;
  float a = 0.000000f;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Color& rhs) const {
    return std::tie(r, g, b, a) != std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator<(const Color& rhs) const {
    return std::tie(r, g, b, a) < std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator<=(const Color& rhs) const {
    return std::tie(r, g, b, a) <= std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator==(const Color& rhs) const {
    return std::tie(r, g, b, a) == std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator>(const Color& rhs) const {
    return std::tie(r, g, b, a) > std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator>=(const Color& rhs) const {
    return std::tie(r, g, b, a) >= std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Color{";
    os << "r: " << ::android::internal::ToString(r);
    os << ", g: " << ::android::internal::ToString(g);
    os << ", b: " << ::android::internal::ToString(b);
    os << ", a: " << ::android::internal::ToString(a);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
