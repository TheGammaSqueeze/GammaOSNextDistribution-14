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
class Cta861_3 {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  float maxContentLightLevel = 0.000000f;
  float maxFrameAverageLightLevel = 0.000000f;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Cta861_3& rhs) const {
    return std::tie(maxContentLightLevel, maxFrameAverageLightLevel) != std::tie(rhs.maxContentLightLevel, rhs.maxFrameAverageLightLevel);
  }
  inline bool operator<(const Cta861_3& rhs) const {
    return std::tie(maxContentLightLevel, maxFrameAverageLightLevel) < std::tie(rhs.maxContentLightLevel, rhs.maxFrameAverageLightLevel);
  }
  inline bool operator<=(const Cta861_3& rhs) const {
    return std::tie(maxContentLightLevel, maxFrameAverageLightLevel) <= std::tie(rhs.maxContentLightLevel, rhs.maxFrameAverageLightLevel);
  }
  inline bool operator==(const Cta861_3& rhs) const {
    return std::tie(maxContentLightLevel, maxFrameAverageLightLevel) == std::tie(rhs.maxContentLightLevel, rhs.maxFrameAverageLightLevel);
  }
  inline bool operator>(const Cta861_3& rhs) const {
    return std::tie(maxContentLightLevel, maxFrameAverageLightLevel) > std::tie(rhs.maxContentLightLevel, rhs.maxFrameAverageLightLevel);
  }
  inline bool operator>=(const Cta861_3& rhs) const {
    return std::tie(maxContentLightLevel, maxFrameAverageLightLevel) >= std::tie(rhs.maxContentLightLevel, rhs.maxFrameAverageLightLevel);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Cta861_3{";
    os << "maxContentLightLevel: " << ::android::internal::ToString(maxContentLightLevel);
    os << ", maxFrameAverageLightLevel: " << ::android::internal::ToString(maxFrameAverageLightLevel);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
