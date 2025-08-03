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
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class AttestationKey {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> keyBlob;
  std::vector<::aidl::android::hardware::security::keymint::KeyParameter> attestKeyParams;
  std::vector<uint8_t> issuerSubjectName;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AttestationKey& rhs) const {
    return std::tie(keyBlob, attestKeyParams, issuerSubjectName) != std::tie(rhs.keyBlob, rhs.attestKeyParams, rhs.issuerSubjectName);
  }
  inline bool operator<(const AttestationKey& rhs) const {
    return std::tie(keyBlob, attestKeyParams, issuerSubjectName) < std::tie(rhs.keyBlob, rhs.attestKeyParams, rhs.issuerSubjectName);
  }
  inline bool operator<=(const AttestationKey& rhs) const {
    return std::tie(keyBlob, attestKeyParams, issuerSubjectName) <= std::tie(rhs.keyBlob, rhs.attestKeyParams, rhs.issuerSubjectName);
  }
  inline bool operator==(const AttestationKey& rhs) const {
    return std::tie(keyBlob, attestKeyParams, issuerSubjectName) == std::tie(rhs.keyBlob, rhs.attestKeyParams, rhs.issuerSubjectName);
  }
  inline bool operator>(const AttestationKey& rhs) const {
    return std::tie(keyBlob, attestKeyParams, issuerSubjectName) > std::tie(rhs.keyBlob, rhs.attestKeyParams, rhs.issuerSubjectName);
  }
  inline bool operator>=(const AttestationKey& rhs) const {
    return std::tie(keyBlob, attestKeyParams, issuerSubjectName) >= std::tie(rhs.keyBlob, rhs.attestKeyParams, rhs.issuerSubjectName);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AttestationKey{";
    os << "keyBlob: " << ::android::internal::ToString(keyBlob);
    os << ", attestKeyParams: " << ::android::internal::ToString(attestKeyParams);
    os << ", issuerSubjectName: " << ::android::internal::ToString(issuerSubjectName);
    os << "}";
    return os.str();
  }
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
