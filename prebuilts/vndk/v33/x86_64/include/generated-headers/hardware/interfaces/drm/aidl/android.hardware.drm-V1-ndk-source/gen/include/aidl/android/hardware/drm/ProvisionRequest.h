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
namespace drm {
class ProvisionRequest {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> request;
  std::string defaultUrl;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ProvisionRequest& rhs) const {
    return std::tie(request, defaultUrl) != std::tie(rhs.request, rhs.defaultUrl);
  }
  inline bool operator<(const ProvisionRequest& rhs) const {
    return std::tie(request, defaultUrl) < std::tie(rhs.request, rhs.defaultUrl);
  }
  inline bool operator<=(const ProvisionRequest& rhs) const {
    return std::tie(request, defaultUrl) <= std::tie(rhs.request, rhs.defaultUrl);
  }
  inline bool operator==(const ProvisionRequest& rhs) const {
    return std::tie(request, defaultUrl) == std::tie(rhs.request, rhs.defaultUrl);
  }
  inline bool operator>(const ProvisionRequest& rhs) const {
    return std::tie(request, defaultUrl) > std::tie(rhs.request, rhs.defaultUrl);
  }
  inline bool operator>=(const ProvisionRequest& rhs) const {
    return std::tie(request, defaultUrl) >= std::tie(rhs.request, rhs.defaultUrl);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ProvisionRequest{";
    os << "request: " << ::android::internal::ToString(request);
    os << ", defaultUrl: " << ::android::internal::ToString(defaultUrl);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
