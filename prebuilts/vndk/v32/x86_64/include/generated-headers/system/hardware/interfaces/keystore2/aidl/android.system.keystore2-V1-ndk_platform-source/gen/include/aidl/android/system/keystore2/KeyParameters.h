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
#include <aidl/android/hardware/security/keymint/KeyParameter.h>
namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class KeyParameters {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::security::keymint::KeyParameter> keyParameter;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeyParameters& rhs) const {
    return std::tie(keyParameter) != std::tie(rhs.keyParameter);
  }
  inline bool operator<(const KeyParameters& rhs) const {
    return std::tie(keyParameter) < std::tie(rhs.keyParameter);
  }
  inline bool operator<=(const KeyParameters& rhs) const {
    return std::tie(keyParameter) <= std::tie(rhs.keyParameter);
  }
  inline bool operator==(const KeyParameters& rhs) const {
    return std::tie(keyParameter) == std::tie(rhs.keyParameter);
  }
  inline bool operator>(const KeyParameters& rhs) const {
    return std::tie(keyParameter) > std::tie(rhs.keyParameter);
  }
  inline bool operator>=(const KeyParameters& rhs) const {
    return std::tie(keyParameter) >= std::tie(rhs.keyParameter);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeyParameters{";
    os << "keyParameter: " << ::android::internal::ToString(keyParameter);
    os << "}";
    return os.str();
  }
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
