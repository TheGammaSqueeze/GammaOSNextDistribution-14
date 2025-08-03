#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/drm/SupportedContentType.h>
#include <aidl/android/hardware/drm/Uuid.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class CryptoSchemes {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::drm::Uuid> uuids;
  std::vector<::aidl::android::hardware::drm::SupportedContentType> mimeTypes;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CryptoSchemes& rhs) const {
    return std::tie(uuids, mimeTypes) != std::tie(rhs.uuids, rhs.mimeTypes);
  }
  inline bool operator<(const CryptoSchemes& rhs) const {
    return std::tie(uuids, mimeTypes) < std::tie(rhs.uuids, rhs.mimeTypes);
  }
  inline bool operator<=(const CryptoSchemes& rhs) const {
    return std::tie(uuids, mimeTypes) <= std::tie(rhs.uuids, rhs.mimeTypes);
  }
  inline bool operator==(const CryptoSchemes& rhs) const {
    return std::tie(uuids, mimeTypes) == std::tie(rhs.uuids, rhs.mimeTypes);
  }
  inline bool operator>(const CryptoSchemes& rhs) const {
    return std::tie(uuids, mimeTypes) > std::tie(rhs.uuids, rhs.mimeTypes);
  }
  inline bool operator>=(const CryptoSchemes& rhs) const {
    return std::tie(uuids, mimeTypes) >= std::tie(rhs.uuids, rhs.mimeTypes);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CryptoSchemes{";
    os << "uuids: " << ::android::internal::ToString(uuids);
    os << ", mimeTypes: " << ::android::internal::ToString(mimeTypes);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
