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
#include <aidl/android/hardware/security/keymint/SecurityLevel.h>
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class KeyCharacteristics {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::security::keymint::SecurityLevel securityLevel = ::aidl::android::hardware::security::keymint::SecurityLevel::SOFTWARE;
  std::vector<::aidl::android::hardware::security::keymint::KeyParameter> authorizations;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeyCharacteristics& rhs) const {
    return std::tie(securityLevel, authorizations) != std::tie(rhs.securityLevel, rhs.authorizations);
  }
  inline bool operator<(const KeyCharacteristics& rhs) const {
    return std::tie(securityLevel, authorizations) < std::tie(rhs.securityLevel, rhs.authorizations);
  }
  inline bool operator<=(const KeyCharacteristics& rhs) const {
    return std::tie(securityLevel, authorizations) <= std::tie(rhs.securityLevel, rhs.authorizations);
  }
  inline bool operator==(const KeyCharacteristics& rhs) const {
    return std::tie(securityLevel, authorizations) == std::tie(rhs.securityLevel, rhs.authorizations);
  }
  inline bool operator>(const KeyCharacteristics& rhs) const {
    return std::tie(securityLevel, authorizations) > std::tie(rhs.securityLevel, rhs.authorizations);
  }
  inline bool operator>=(const KeyCharacteristics& rhs) const {
    return std::tie(securityLevel, authorizations) >= std::tie(rhs.securityLevel, rhs.authorizations);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeyCharacteristics{";
    os << "securityLevel: " << ::android::internal::ToString(securityLevel);
    os << ", authorizations: " << ::android::internal::ToString(authorizations);
    os << "}";
    return os.str();
  }
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
