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
#include <aidl/android/hardware/keymaster/SecurityLevel.h>
#include <aidl/android/hardware/keymaster/Timestamp.h>
namespace aidl {
namespace android {
namespace hardware {
namespace keymaster {
class VerificationToken {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t challenge = 0L;
  ::aidl::android::hardware::keymaster::Timestamp timestamp;
  ::aidl::android::hardware::keymaster::SecurityLevel securityLevel = ::aidl::android::hardware::keymaster::SecurityLevel::SOFTWARE;
  std::vector<uint8_t> mac;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const VerificationToken& rhs) const {
    return std::tie(challenge, timestamp, securityLevel, mac) != std::tie(rhs.challenge, rhs.timestamp, rhs.securityLevel, rhs.mac);
  }
  inline bool operator<(const VerificationToken& rhs) const {
    return std::tie(challenge, timestamp, securityLevel, mac) < std::tie(rhs.challenge, rhs.timestamp, rhs.securityLevel, rhs.mac);
  }
  inline bool operator<=(const VerificationToken& rhs) const {
    return std::tie(challenge, timestamp, securityLevel, mac) <= std::tie(rhs.challenge, rhs.timestamp, rhs.securityLevel, rhs.mac);
  }
  inline bool operator==(const VerificationToken& rhs) const {
    return std::tie(challenge, timestamp, securityLevel, mac) == std::tie(rhs.challenge, rhs.timestamp, rhs.securityLevel, rhs.mac);
  }
  inline bool operator>(const VerificationToken& rhs) const {
    return std::tie(challenge, timestamp, securityLevel, mac) > std::tie(rhs.challenge, rhs.timestamp, rhs.securityLevel, rhs.mac);
  }
  inline bool operator>=(const VerificationToken& rhs) const {
    return std::tie(challenge, timestamp, securityLevel, mac) >= std::tie(rhs.challenge, rhs.timestamp, rhs.securityLevel, rhs.mac);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "VerificationToken{";
    os << "challenge: " << ::android::internal::ToString(challenge);
    os << ", timestamp: " << ::android::internal::ToString(timestamp);
    os << ", securityLevel: " << ::android::internal::ToString(securityLevel);
    os << ", mac: " << ::android::internal::ToString(mac);
    os << "}";
    return os.str();
  }
};
}  // namespace keymaster
}  // namespace hardware
}  // namespace android
}  // namespace aidl
