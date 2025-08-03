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
class EphemeralStorageKeyResponse {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> ephemeralKey;
  std::optional<std::vector<uint8_t>> upgradedBlob;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const EphemeralStorageKeyResponse& rhs) const {
    return std::tie(ephemeralKey, upgradedBlob) != std::tie(rhs.ephemeralKey, rhs.upgradedBlob);
  }
  inline bool operator<(const EphemeralStorageKeyResponse& rhs) const {
    return std::tie(ephemeralKey, upgradedBlob) < std::tie(rhs.ephemeralKey, rhs.upgradedBlob);
  }
  inline bool operator<=(const EphemeralStorageKeyResponse& rhs) const {
    return std::tie(ephemeralKey, upgradedBlob) <= std::tie(rhs.ephemeralKey, rhs.upgradedBlob);
  }
  inline bool operator==(const EphemeralStorageKeyResponse& rhs) const {
    return std::tie(ephemeralKey, upgradedBlob) == std::tie(rhs.ephemeralKey, rhs.upgradedBlob);
  }
  inline bool operator>(const EphemeralStorageKeyResponse& rhs) const {
    return std::tie(ephemeralKey, upgradedBlob) > std::tie(rhs.ephemeralKey, rhs.upgradedBlob);
  }
  inline bool operator>=(const EphemeralStorageKeyResponse& rhs) const {
    return std::tie(ephemeralKey, upgradedBlob) >= std::tie(rhs.ephemeralKey, rhs.upgradedBlob);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "EphemeralStorageKeyResponse{";
    os << "ephemeralKey: " << ::android::internal::ToString(ephemeralKey);
    os << ", upgradedBlob: " << ::android::internal::ToString(upgradedBlob);
    os << "}";
    return os.str();
  }
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
