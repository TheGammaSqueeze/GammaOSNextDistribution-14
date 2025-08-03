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
#include <aidl/android/hardware/security/keymint/HardwareAuthenticatorType.h>
namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class AuthenticatorSpec {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::security::keymint::HardwareAuthenticatorType authenticatorType = ::aidl::android::hardware::security::keymint::HardwareAuthenticatorType(0);
  int64_t authenticatorId = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AuthenticatorSpec& rhs) const {
    return std::tie(authenticatorType, authenticatorId) != std::tie(rhs.authenticatorType, rhs.authenticatorId);
  }
  inline bool operator<(const AuthenticatorSpec& rhs) const {
    return std::tie(authenticatorType, authenticatorId) < std::tie(rhs.authenticatorType, rhs.authenticatorId);
  }
  inline bool operator<=(const AuthenticatorSpec& rhs) const {
    return std::tie(authenticatorType, authenticatorId) <= std::tie(rhs.authenticatorType, rhs.authenticatorId);
  }
  inline bool operator==(const AuthenticatorSpec& rhs) const {
    return std::tie(authenticatorType, authenticatorId) == std::tie(rhs.authenticatorType, rhs.authenticatorId);
  }
  inline bool operator>(const AuthenticatorSpec& rhs) const {
    return std::tie(authenticatorType, authenticatorId) > std::tie(rhs.authenticatorType, rhs.authenticatorId);
  }
  inline bool operator>=(const AuthenticatorSpec& rhs) const {
    return std::tie(authenticatorType, authenticatorId) >= std::tie(rhs.authenticatorType, rhs.authenticatorId);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AuthenticatorSpec{";
    os << "authenticatorType: " << ::android::internal::ToString(authenticatorType);
    os << ", authenticatorId: " << ::android::internal::ToString(authenticatorId);
    os << "}";
    return os.str();
  }
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
