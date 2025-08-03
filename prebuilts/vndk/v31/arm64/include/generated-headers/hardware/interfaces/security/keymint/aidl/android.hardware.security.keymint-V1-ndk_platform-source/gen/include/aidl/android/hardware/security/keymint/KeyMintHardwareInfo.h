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
#include <aidl/android/hardware/security/keymint/SecurityLevel.h>
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class KeyMintHardwareInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t versionNumber = 0;
  ::aidl::android::hardware::security::keymint::SecurityLevel securityLevel = ::aidl::android::hardware::security::keymint::SecurityLevel::SOFTWARE;
  std::string keyMintName;
  std::string keyMintAuthorName;
  bool timestampTokenRequired = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeyMintHardwareInfo& rhs) const {
    return std::tie(versionNumber, securityLevel, keyMintName, keyMintAuthorName, timestampTokenRequired) != std::tie(rhs.versionNumber, rhs.securityLevel, rhs.keyMintName, rhs.keyMintAuthorName, rhs.timestampTokenRequired);
  }
  inline bool operator<(const KeyMintHardwareInfo& rhs) const {
    return std::tie(versionNumber, securityLevel, keyMintName, keyMintAuthorName, timestampTokenRequired) < std::tie(rhs.versionNumber, rhs.securityLevel, rhs.keyMintName, rhs.keyMintAuthorName, rhs.timestampTokenRequired);
  }
  inline bool operator<=(const KeyMintHardwareInfo& rhs) const {
    return std::tie(versionNumber, securityLevel, keyMintName, keyMintAuthorName, timestampTokenRequired) <= std::tie(rhs.versionNumber, rhs.securityLevel, rhs.keyMintName, rhs.keyMintAuthorName, rhs.timestampTokenRequired);
  }
  inline bool operator==(const KeyMintHardwareInfo& rhs) const {
    return std::tie(versionNumber, securityLevel, keyMintName, keyMintAuthorName, timestampTokenRequired) == std::tie(rhs.versionNumber, rhs.securityLevel, rhs.keyMintName, rhs.keyMintAuthorName, rhs.timestampTokenRequired);
  }
  inline bool operator>(const KeyMintHardwareInfo& rhs) const {
    return std::tie(versionNumber, securityLevel, keyMintName, keyMintAuthorName, timestampTokenRequired) > std::tie(rhs.versionNumber, rhs.securityLevel, rhs.keyMintName, rhs.keyMintAuthorName, rhs.timestampTokenRequired);
  }
  inline bool operator>=(const KeyMintHardwareInfo& rhs) const {
    return std::tie(versionNumber, securityLevel, keyMintName, keyMintAuthorName, timestampTokenRequired) >= std::tie(rhs.versionNumber, rhs.securityLevel, rhs.keyMintName, rhs.keyMintAuthorName, rhs.timestampTokenRequired);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeyMintHardwareInfo{";
    os << "versionNumber: " << ::android::internal::ToString(versionNumber);
    os << ", securityLevel: " << ::android::internal::ToString(securityLevel);
    os << ", keyMintName: " << ::android::internal::ToString(keyMintName);
    os << ", keyMintAuthorName: " << ::android::internal::ToString(keyMintAuthorName);
    os << ", timestampTokenRequired: " << ::android::internal::ToString(timestampTokenRequired);
    os << "}";
    return os.str();
  }
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
