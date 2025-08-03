#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/common/Hdr.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class HdrCapabilities {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::graphics::common::Hdr> types;
  float maxLuminance = 0.000000f;
  float maxAverageLuminance = 0.000000f;
  float minLuminance = 0.000000f;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HdrCapabilities& rhs) const {
    return std::tie(types, maxLuminance, maxAverageLuminance, minLuminance) != std::tie(rhs.types, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator<(const HdrCapabilities& rhs) const {
    return std::tie(types, maxLuminance, maxAverageLuminance, minLuminance) < std::tie(rhs.types, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator<=(const HdrCapabilities& rhs) const {
    return std::tie(types, maxLuminance, maxAverageLuminance, minLuminance) <= std::tie(rhs.types, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator==(const HdrCapabilities& rhs) const {
    return std::tie(types, maxLuminance, maxAverageLuminance, minLuminance) == std::tie(rhs.types, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator>(const HdrCapabilities& rhs) const {
    return std::tie(types, maxLuminance, maxAverageLuminance, minLuminance) > std::tie(rhs.types, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator>=(const HdrCapabilities& rhs) const {
    return std::tie(types, maxLuminance, maxAverageLuminance, minLuminance) >= std::tie(rhs.types, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HdrCapabilities{";
    os << "types: " << ::android::internal::ToString(types);
    os << ", maxLuminance: " << ::android::internal::ToString(maxLuminance);
    os << ", maxAverageLuminance: " << ::android::internal::ToString(maxAverageLuminance);
    os << ", minLuminance: " << ::android::internal::ToString(minLuminance);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
