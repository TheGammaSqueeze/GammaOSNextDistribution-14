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
#include <aidl/android/system/keystore2/Domain.h>
namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class KeyDescriptor {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::system::keystore2::Domain domain = ::aidl::android::system::keystore2::Domain(0);
  int64_t nspace = 0L;
  std::optional<std::string> alias;
  std::optional<std::vector<uint8_t>> blob;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeyDescriptor& rhs) const {
    return std::tie(domain, nspace, alias, blob) != std::tie(rhs.domain, rhs.nspace, rhs.alias, rhs.blob);
  }
  inline bool operator<(const KeyDescriptor& rhs) const {
    return std::tie(domain, nspace, alias, blob) < std::tie(rhs.domain, rhs.nspace, rhs.alias, rhs.blob);
  }
  inline bool operator<=(const KeyDescriptor& rhs) const {
    return std::tie(domain, nspace, alias, blob) <= std::tie(rhs.domain, rhs.nspace, rhs.alias, rhs.blob);
  }
  inline bool operator==(const KeyDescriptor& rhs) const {
    return std::tie(domain, nspace, alias, blob) == std::tie(rhs.domain, rhs.nspace, rhs.alias, rhs.blob);
  }
  inline bool operator>(const KeyDescriptor& rhs) const {
    return std::tie(domain, nspace, alias, blob) > std::tie(rhs.domain, rhs.nspace, rhs.alias, rhs.blob);
  }
  inline bool operator>=(const KeyDescriptor& rhs) const {
    return std::tie(domain, nspace, alias, blob) >= std::tie(rhs.domain, rhs.nspace, rhs.alias, rhs.blob);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeyDescriptor{";
    os << "domain: " << ::android::internal::ToString(domain);
    os << ", nspace: " << ::android::internal::ToString(nspace);
    os << ", alias: " << ::android::internal::ToString(alias);
    os << ", blob: " << ::android::internal::ToString(blob);
    os << "}";
    return os.str();
  }
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
