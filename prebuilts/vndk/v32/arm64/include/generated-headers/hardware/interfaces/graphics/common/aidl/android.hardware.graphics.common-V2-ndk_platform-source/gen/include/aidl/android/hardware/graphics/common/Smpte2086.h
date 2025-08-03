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
#include <aidl/android/hardware/graphics/common/XyColor.h>
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
class Smpte2086 {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::graphics::common::XyColor primaryRed;
  ::aidl::android::hardware::graphics::common::XyColor primaryGreen;
  ::aidl::android::hardware::graphics::common::XyColor primaryBlue;
  ::aidl::android::hardware::graphics::common::XyColor whitePoint;
  float maxLuminance = 0.000000f;
  float minLuminance = 0.000000f;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Smpte2086& rhs) const {
    return std::tie(primaryRed, primaryGreen, primaryBlue, whitePoint, maxLuminance, minLuminance) != std::tie(rhs.primaryRed, rhs.primaryGreen, rhs.primaryBlue, rhs.whitePoint, rhs.maxLuminance, rhs.minLuminance);
  }
  inline bool operator<(const Smpte2086& rhs) const {
    return std::tie(primaryRed, primaryGreen, primaryBlue, whitePoint, maxLuminance, minLuminance) < std::tie(rhs.primaryRed, rhs.primaryGreen, rhs.primaryBlue, rhs.whitePoint, rhs.maxLuminance, rhs.minLuminance);
  }
  inline bool operator<=(const Smpte2086& rhs) const {
    return std::tie(primaryRed, primaryGreen, primaryBlue, whitePoint, maxLuminance, minLuminance) <= std::tie(rhs.primaryRed, rhs.primaryGreen, rhs.primaryBlue, rhs.whitePoint, rhs.maxLuminance, rhs.minLuminance);
  }
  inline bool operator==(const Smpte2086& rhs) const {
    return std::tie(primaryRed, primaryGreen, primaryBlue, whitePoint, maxLuminance, minLuminance) == std::tie(rhs.primaryRed, rhs.primaryGreen, rhs.primaryBlue, rhs.whitePoint, rhs.maxLuminance, rhs.minLuminance);
  }
  inline bool operator>(const Smpte2086& rhs) const {
    return std::tie(primaryRed, primaryGreen, primaryBlue, whitePoint, maxLuminance, minLuminance) > std::tie(rhs.primaryRed, rhs.primaryGreen, rhs.primaryBlue, rhs.whitePoint, rhs.maxLuminance, rhs.minLuminance);
  }
  inline bool operator>=(const Smpte2086& rhs) const {
    return std::tie(primaryRed, primaryGreen, primaryBlue, whitePoint, maxLuminance, minLuminance) >= std::tie(rhs.primaryRed, rhs.primaryGreen, rhs.primaryBlue, rhs.whitePoint, rhs.maxLuminance, rhs.minLuminance);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Smpte2086{";
    os << "primaryRed: " << ::android::internal::ToString(primaryRed);
    os << ", primaryGreen: " << ::android::internal::ToString(primaryGreen);
    os << ", primaryBlue: " << ::android::internal::ToString(primaryBlue);
    os << ", whitePoint: " << ::android::internal::ToString(whitePoint);
    os << ", maxLuminance: " << ::android::internal::ToString(maxLuminance);
    os << ", minLuminance: " << ::android::internal::ToString(minLuminance);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
