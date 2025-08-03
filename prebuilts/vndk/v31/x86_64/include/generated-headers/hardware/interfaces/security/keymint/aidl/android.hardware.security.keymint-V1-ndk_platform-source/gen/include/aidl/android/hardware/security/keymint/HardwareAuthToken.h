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
#include <aidl/android/hardware/security/secureclock/Timestamp.h>
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class HardwareAuthToken {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t challenge = 0L;
  int64_t userId = 0L;
  int64_t authenticatorId = 0L;
  ::aidl::android::hardware::security::keymint::HardwareAuthenticatorType authenticatorType = ::aidl::android::hardware::security::keymint::HardwareAuthenticatorType::NONE;
  ::aidl::android::hardware::security::secureclock::Timestamp timestamp;
  std::vector<uint8_t> mac;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HardwareAuthToken& rhs) const {
    return std::tie(challenge, userId, authenticatorId, authenticatorType, timestamp, mac) != std::tie(rhs.challenge, rhs.userId, rhs.authenticatorId, rhs.authenticatorType, rhs.timestamp, rhs.mac);
  }
  inline bool operator<(const HardwareAuthToken& rhs) const {
    return std::tie(challenge, userId, authenticatorId, authenticatorType, timestamp, mac) < std::tie(rhs.challenge, rhs.userId, rhs.authenticatorId, rhs.authenticatorType, rhs.timestamp, rhs.mac);
  }
  inline bool operator<=(const HardwareAuthToken& rhs) const {
    return std::tie(challenge, userId, authenticatorId, authenticatorType, timestamp, mac) <= std::tie(rhs.challenge, rhs.userId, rhs.authenticatorId, rhs.authenticatorType, rhs.timestamp, rhs.mac);
  }
  inline bool operator==(const HardwareAuthToken& rhs) const {
    return std::tie(challenge, userId, authenticatorId, authenticatorType, timestamp, mac) == std::tie(rhs.challenge, rhs.userId, rhs.authenticatorId, rhs.authenticatorType, rhs.timestamp, rhs.mac);
  }
  inline bool operator>(const HardwareAuthToken& rhs) const {
    return std::tie(challenge, userId, authenticatorId, authenticatorType, timestamp, mac) > std::tie(rhs.challenge, rhs.userId, rhs.authenticatorId, rhs.authenticatorType, rhs.timestamp, rhs.mac);
  }
  inline bool operator>=(const HardwareAuthToken& rhs) const {
    return std::tie(challenge, userId, authenticatorId, authenticatorType, timestamp, mac) >= std::tie(rhs.challenge, rhs.userId, rhs.authenticatorId, rhs.authenticatorType, rhs.timestamp, rhs.mac);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HardwareAuthToken{";
    os << "challenge: " << ::android::internal::ToString(challenge);
    os << ", userId: " << ::android::internal::ToString(userId);
    os << ", authenticatorId: " << ::android::internal::ToString(authenticatorId);
    os << ", authenticatorType: " << ::android::internal::ToString(authenticatorType);
    os << ", timestamp: " << ::android::internal::ToString(timestamp);
    os << ", mac: " << ::android::internal::ToString(mac);
    os << "}";
    return os.str();
  }
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
