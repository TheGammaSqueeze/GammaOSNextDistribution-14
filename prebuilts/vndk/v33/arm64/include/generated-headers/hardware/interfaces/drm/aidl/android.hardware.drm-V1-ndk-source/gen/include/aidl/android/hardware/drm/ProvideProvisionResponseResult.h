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
class ProvideProvisionResponseResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> certificate;
  std::vector<uint8_t> wrappedKey;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ProvideProvisionResponseResult& rhs) const {
    return std::tie(certificate, wrappedKey) != std::tie(rhs.certificate, rhs.wrappedKey);
  }
  inline bool operator<(const ProvideProvisionResponseResult& rhs) const {
    return std::tie(certificate, wrappedKey) < std::tie(rhs.certificate, rhs.wrappedKey);
  }
  inline bool operator<=(const ProvideProvisionResponseResult& rhs) const {
    return std::tie(certificate, wrappedKey) <= std::tie(rhs.certificate, rhs.wrappedKey);
  }
  inline bool operator==(const ProvideProvisionResponseResult& rhs) const {
    return std::tie(certificate, wrappedKey) == std::tie(rhs.certificate, rhs.wrappedKey);
  }
  inline bool operator>(const ProvideProvisionResponseResult& rhs) const {
    return std::tie(certificate, wrappedKey) > std::tie(rhs.certificate, rhs.wrappedKey);
  }
  inline bool operator>=(const ProvideProvisionResponseResult& rhs) const {
    return std::tie(certificate, wrappedKey) >= std::tie(rhs.certificate, rhs.wrappedKey);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ProvideProvisionResponseResult{";
    os << "certificate: " << ::android::internal::ToString(certificate);
    os << ", wrappedKey: " << ::android::internal::ToString(wrappedKey);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
