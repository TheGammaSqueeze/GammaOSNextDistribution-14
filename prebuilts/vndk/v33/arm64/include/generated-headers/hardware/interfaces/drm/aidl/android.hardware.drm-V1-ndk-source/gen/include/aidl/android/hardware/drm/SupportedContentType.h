#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/drm/SecurityLevel.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class SupportedContentType {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string mime;
  ::aidl::android::hardware::drm::SecurityLevel minLevel = ::aidl::android::hardware::drm::SecurityLevel(0);
  ::aidl::android::hardware::drm::SecurityLevel maxLevel = ::aidl::android::hardware::drm::SecurityLevel(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SupportedContentType& rhs) const {
    return std::tie(mime, minLevel, maxLevel) != std::tie(rhs.mime, rhs.minLevel, rhs.maxLevel);
  }
  inline bool operator<(const SupportedContentType& rhs) const {
    return std::tie(mime, minLevel, maxLevel) < std::tie(rhs.mime, rhs.minLevel, rhs.maxLevel);
  }
  inline bool operator<=(const SupportedContentType& rhs) const {
    return std::tie(mime, minLevel, maxLevel) <= std::tie(rhs.mime, rhs.minLevel, rhs.maxLevel);
  }
  inline bool operator==(const SupportedContentType& rhs) const {
    return std::tie(mime, minLevel, maxLevel) == std::tie(rhs.mime, rhs.minLevel, rhs.maxLevel);
  }
  inline bool operator>(const SupportedContentType& rhs) const {
    return std::tie(mime, minLevel, maxLevel) > std::tie(rhs.mime, rhs.minLevel, rhs.maxLevel);
  }
  inline bool operator>=(const SupportedContentType& rhs) const {
    return std::tie(mime, minLevel, maxLevel) >= std::tie(rhs.mime, rhs.minLevel, rhs.maxLevel);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SupportedContentType{";
    os << "mime: " << ::android::internal::ToString(mime);
    os << ", minLevel: " << ::android::internal::ToString(minLevel);
    os << ", maxLevel: " << ::android::internal::ToString(maxLevel);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
