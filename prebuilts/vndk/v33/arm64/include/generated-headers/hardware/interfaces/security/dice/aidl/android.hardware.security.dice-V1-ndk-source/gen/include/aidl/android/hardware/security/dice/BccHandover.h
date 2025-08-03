#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/security/dice/Bcc.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace dice {
class BccHandover {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::array<uint8_t, 32> cdiAttest = {{}};
  std::array<uint8_t, 32> cdiSeal = {{}};
  ::aidl::android::hardware::security::dice::Bcc bcc;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const BccHandover& rhs) const {
    return std::tie(cdiAttest, cdiSeal, bcc) != std::tie(rhs.cdiAttest, rhs.cdiSeal, rhs.bcc);
  }
  inline bool operator<(const BccHandover& rhs) const {
    return std::tie(cdiAttest, cdiSeal, bcc) < std::tie(rhs.cdiAttest, rhs.cdiSeal, rhs.bcc);
  }
  inline bool operator<=(const BccHandover& rhs) const {
    return std::tie(cdiAttest, cdiSeal, bcc) <= std::tie(rhs.cdiAttest, rhs.cdiSeal, rhs.bcc);
  }
  inline bool operator==(const BccHandover& rhs) const {
    return std::tie(cdiAttest, cdiSeal, bcc) == std::tie(rhs.cdiAttest, rhs.cdiSeal, rhs.bcc);
  }
  inline bool operator>(const BccHandover& rhs) const {
    return std::tie(cdiAttest, cdiSeal, bcc) > std::tie(rhs.cdiAttest, rhs.cdiSeal, rhs.bcc);
  }
  inline bool operator>=(const BccHandover& rhs) const {
    return std::tie(cdiAttest, cdiSeal, bcc) >= std::tie(rhs.cdiAttest, rhs.cdiSeal, rhs.bcc);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "BccHandover{";
    os << "cdiAttest: " << ::android::internal::ToString(cdiAttest);
    os << ", cdiSeal: " << ::android::internal::ToString(cdiSeal);
    os << ", bcc: " << ::android::internal::ToString(bcc);
    os << "}";
    return os.str();
  }
};
}  // namespace dice
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
