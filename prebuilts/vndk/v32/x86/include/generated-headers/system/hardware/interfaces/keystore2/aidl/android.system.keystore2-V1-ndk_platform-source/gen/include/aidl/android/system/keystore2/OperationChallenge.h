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
namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class OperationChallenge {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t challenge = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const OperationChallenge& rhs) const {
    return std::tie(challenge) != std::tie(rhs.challenge);
  }
  inline bool operator<(const OperationChallenge& rhs) const {
    return std::tie(challenge) < std::tie(rhs.challenge);
  }
  inline bool operator<=(const OperationChallenge& rhs) const {
    return std::tie(challenge) <= std::tie(rhs.challenge);
  }
  inline bool operator==(const OperationChallenge& rhs) const {
    return std::tie(challenge) == std::tie(rhs.challenge);
  }
  inline bool operator>(const OperationChallenge& rhs) const {
    return std::tie(challenge) > std::tie(rhs.challenge);
  }
  inline bool operator>=(const OperationChallenge& rhs) const {
    return std::tie(challenge) >= std::tie(rhs.challenge);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "OperationChallenge{";
    os << "challenge: " << ::android::internal::ToString(challenge);
    os << "}";
    return os.str();
  }
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
