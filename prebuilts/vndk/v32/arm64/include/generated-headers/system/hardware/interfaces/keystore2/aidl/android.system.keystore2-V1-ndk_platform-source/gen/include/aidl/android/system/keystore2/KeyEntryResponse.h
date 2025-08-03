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
#include <aidl/android/system/keystore2/IKeystoreSecurityLevel.h>
#include <aidl/android/system/keystore2/KeyMetadata.h>
namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class KeyEntryResponse {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::shared_ptr<::aidl::android::system::keystore2::IKeystoreSecurityLevel> iSecurityLevel;
  ::aidl::android::system::keystore2::KeyMetadata metadata;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeyEntryResponse& rhs) const {
    return std::tie(iSecurityLevel, metadata) != std::tie(rhs.iSecurityLevel, rhs.metadata);
  }
  inline bool operator<(const KeyEntryResponse& rhs) const {
    return std::tie(iSecurityLevel, metadata) < std::tie(rhs.iSecurityLevel, rhs.metadata);
  }
  inline bool operator<=(const KeyEntryResponse& rhs) const {
    return std::tie(iSecurityLevel, metadata) <= std::tie(rhs.iSecurityLevel, rhs.metadata);
  }
  inline bool operator==(const KeyEntryResponse& rhs) const {
    return std::tie(iSecurityLevel, metadata) == std::tie(rhs.iSecurityLevel, rhs.metadata);
  }
  inline bool operator>(const KeyEntryResponse& rhs) const {
    return std::tie(iSecurityLevel, metadata) > std::tie(rhs.iSecurityLevel, rhs.metadata);
  }
  inline bool operator>=(const KeyEntryResponse& rhs) const {
    return std::tie(iSecurityLevel, metadata) >= std::tie(rhs.iSecurityLevel, rhs.metadata);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeyEntryResponse{";
    os << "iSecurityLevel: " << ::android::internal::ToString(iSecurityLevel);
    os << ", metadata: " << ::android::internal::ToString(metadata);
    os << "}";
    return os.str();
  }
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
