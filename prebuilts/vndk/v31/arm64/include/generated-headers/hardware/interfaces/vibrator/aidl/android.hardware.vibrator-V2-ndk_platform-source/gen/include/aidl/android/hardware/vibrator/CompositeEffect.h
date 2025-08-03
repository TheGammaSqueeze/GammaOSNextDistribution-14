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
#include <aidl/android/hardware/vibrator/CompositePrimitive.h>
namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class CompositeEffect {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t delayMs = 0;
  ::aidl::android::hardware::vibrator::CompositePrimitive primitive = ::aidl::android::hardware::vibrator::CompositePrimitive::NOOP;
  float scale = 0.000000f;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CompositeEffect& rhs) const {
    return std::tie(delayMs, primitive, scale) != std::tie(rhs.delayMs, rhs.primitive, rhs.scale);
  }
  inline bool operator<(const CompositeEffect& rhs) const {
    return std::tie(delayMs, primitive, scale) < std::tie(rhs.delayMs, rhs.primitive, rhs.scale);
  }
  inline bool operator<=(const CompositeEffect& rhs) const {
    return std::tie(delayMs, primitive, scale) <= std::tie(rhs.delayMs, rhs.primitive, rhs.scale);
  }
  inline bool operator==(const CompositeEffect& rhs) const {
    return std::tie(delayMs, primitive, scale) == std::tie(rhs.delayMs, rhs.primitive, rhs.scale);
  }
  inline bool operator>(const CompositeEffect& rhs) const {
    return std::tie(delayMs, primitive, scale) > std::tie(rhs.delayMs, rhs.primitive, rhs.scale);
  }
  inline bool operator>=(const CompositeEffect& rhs) const {
    return std::tie(delayMs, primitive, scale) >= std::tie(rhs.delayMs, rhs.primitive, rhs.scale);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CompositeEffect{";
    os << "delayMs: " << ::android::internal::ToString(delayMs);
    os << ", primitive: " << ::android::internal::ToString(primitive);
    os << ", scale: " << ::android::internal::ToString(scale);
    os << "}";
    return os.str();
  }
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
