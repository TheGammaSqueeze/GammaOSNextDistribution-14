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
#include <aidl/android/system/keystore2/Authorization.h>
#include <aidl/android/system/keystore2/KeyDescriptor.h>
namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class KeyMetadata {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::system::keystore2::KeyDescriptor key;
  ::aidl::android::hardware::security::keymint::SecurityLevel keySecurityLevel = ::aidl::android::hardware::security::keymint::SecurityLevel(0);
  std::vector<::aidl::android::system::keystore2::Authorization> authorizations;
  std::optional<std::vector<uint8_t>> certificate;
  std::optional<std::vector<uint8_t>> certificateChain;
  int64_t modificationTimeMs = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeyMetadata& rhs) const {
    return std::tie(key, keySecurityLevel, authorizations, certificate, certificateChain, modificationTimeMs) != std::tie(rhs.key, rhs.keySecurityLevel, rhs.authorizations, rhs.certificate, rhs.certificateChain, rhs.modificationTimeMs);
  }
  inline bool operator<(const KeyMetadata& rhs) const {
    return std::tie(key, keySecurityLevel, authorizations, certificate, certificateChain, modificationTimeMs) < std::tie(rhs.key, rhs.keySecurityLevel, rhs.authorizations, rhs.certificate, rhs.certificateChain, rhs.modificationTimeMs);
  }
  inline bool operator<=(const KeyMetadata& rhs) const {
    return std::tie(key, keySecurityLevel, authorizations, certificate, certificateChain, modificationTimeMs) <= std::tie(rhs.key, rhs.keySecurityLevel, rhs.authorizations, rhs.certificate, rhs.certificateChain, rhs.modificationTimeMs);
  }
  inline bool operator==(const KeyMetadata& rhs) const {
    return std::tie(key, keySecurityLevel, authorizations, certificate, certificateChain, modificationTimeMs) == std::tie(rhs.key, rhs.keySecurityLevel, rhs.authorizations, rhs.certificate, rhs.certificateChain, rhs.modificationTimeMs);
  }
  inline bool operator>(const KeyMetadata& rhs) const {
    return std::tie(key, keySecurityLevel, authorizations, certificate, certificateChain, modificationTimeMs) > std::tie(rhs.key, rhs.keySecurityLevel, rhs.authorizations, rhs.certificate, rhs.certificateChain, rhs.modificationTimeMs);
  }
  inline bool operator>=(const KeyMetadata& rhs) const {
    return std::tie(key, keySecurityLevel, authorizations, certificate, certificateChain, modificationTimeMs) >= std::tie(rhs.key, rhs.keySecurityLevel, rhs.authorizations, rhs.certificate, rhs.certificateChain, rhs.modificationTimeMs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeyMetadata{";
    os << "key: " << ::android::internal::ToString(key);
    os << ", keySecurityLevel: " << ::android::internal::ToString(keySecurityLevel);
    os << ", authorizations: " << ::android::internal::ToString(authorizations);
    os << ", certificate: " << ::android::internal::ToString(certificate);
    os << ", certificateChain: " << ::android::internal::ToString(certificateChain);
    os << ", modificationTimeMs: " << ::android::internal::ToString(modificationTimeMs);
    os << "}";
    return os.str();
  }
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
