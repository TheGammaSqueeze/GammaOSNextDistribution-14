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
#include <aidl/android/hardware/security/keymint/IKeyMintOperation.h>
#include <aidl/android/hardware/security/keymint/KeyParameter.h>
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class BeginResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t challenge = 0L;
  std::vector<::aidl::android::hardware::security::keymint::KeyParameter> params;
  std::shared_ptr<::aidl::android::hardware::security::keymint::IKeyMintOperation> operation;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const BeginResult& rhs) const {
    return std::tie(challenge, params, operation) != std::tie(rhs.challenge, rhs.params, rhs.operation);
  }
  inline bool operator<(const BeginResult& rhs) const {
    return std::tie(challenge, params, operation) < std::tie(rhs.challenge, rhs.params, rhs.operation);
  }
  inline bool operator<=(const BeginResult& rhs) const {
    return std::tie(challenge, params, operation) <= std::tie(rhs.challenge, rhs.params, rhs.operation);
  }
  inline bool operator==(const BeginResult& rhs) const {
    return std::tie(challenge, params, operation) == std::tie(rhs.challenge, rhs.params, rhs.operation);
  }
  inline bool operator>(const BeginResult& rhs) const {
    return std::tie(challenge, params, operation) > std::tie(rhs.challenge, rhs.params, rhs.operation);
  }
  inline bool operator>=(const BeginResult& rhs) const {
    return std::tie(challenge, params, operation) >= std::tie(rhs.challenge, rhs.params, rhs.operation);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "BeginResult{";
    os << "challenge: " << ::android::internal::ToString(challenge);
    os << ", params: " << ::android::internal::ToString(params);
    os << ", operation: " << ::android::internal::ToString(operation);
    os << "}";
    return os.str();
  }
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
