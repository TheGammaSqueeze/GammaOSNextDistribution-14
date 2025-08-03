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
namespace network {
class LteVopsInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool isVopsSupported = false;
  bool isEmcBearerSupported = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const LteVopsInfo& rhs) const {
    return std::tie(isVopsSupported, isEmcBearerSupported) != std::tie(rhs.isVopsSupported, rhs.isEmcBearerSupported);
  }
  inline bool operator<(const LteVopsInfo& rhs) const {
    return std::tie(isVopsSupported, isEmcBearerSupported) < std::tie(rhs.isVopsSupported, rhs.isEmcBearerSupported);
  }
  inline bool operator<=(const LteVopsInfo& rhs) const {
    return std::tie(isVopsSupported, isEmcBearerSupported) <= std::tie(rhs.isVopsSupported, rhs.isEmcBearerSupported);
  }
  inline bool operator==(const LteVopsInfo& rhs) const {
    return std::tie(isVopsSupported, isEmcBearerSupported) == std::tie(rhs.isVopsSupported, rhs.isEmcBearerSupported);
  }
  inline bool operator>(const LteVopsInfo& rhs) const {
    return std::tie(isVopsSupported, isEmcBearerSupported) > std::tie(rhs.isVopsSupported, rhs.isEmcBearerSupported);
  }
  inline bool operator>=(const LteVopsInfo& rhs) const {
    return std::tie(isVopsSupported, isEmcBearerSupported) >= std::tie(rhs.isVopsSupported, rhs.isEmcBearerSupported);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "LteVopsInfo{";
    os << "isVopsSupported: " << ::android::internal::ToString(isVopsSupported);
    os << ", isEmcBearerSupported: " << ::android::internal::ToString(isEmcBearerSupported);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
