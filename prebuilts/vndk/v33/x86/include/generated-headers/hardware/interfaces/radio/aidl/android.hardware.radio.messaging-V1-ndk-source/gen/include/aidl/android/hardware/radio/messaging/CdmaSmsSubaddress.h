#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class CdmaSmsSubaddress {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t subaddressType = 0;
  bool odd = false;
  std::vector<uint8_t> digits;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaSmsSubaddress& rhs) const {
    return std::tie(subaddressType, odd, digits) != std::tie(rhs.subaddressType, rhs.odd, rhs.digits);
  }
  inline bool operator<(const CdmaSmsSubaddress& rhs) const {
    return std::tie(subaddressType, odd, digits) < std::tie(rhs.subaddressType, rhs.odd, rhs.digits);
  }
  inline bool operator<=(const CdmaSmsSubaddress& rhs) const {
    return std::tie(subaddressType, odd, digits) <= std::tie(rhs.subaddressType, rhs.odd, rhs.digits);
  }
  inline bool operator==(const CdmaSmsSubaddress& rhs) const {
    return std::tie(subaddressType, odd, digits) == std::tie(rhs.subaddressType, rhs.odd, rhs.digits);
  }
  inline bool operator>(const CdmaSmsSubaddress& rhs) const {
    return std::tie(subaddressType, odd, digits) > std::tie(rhs.subaddressType, rhs.odd, rhs.digits);
  }
  inline bool operator>=(const CdmaSmsSubaddress& rhs) const {
    return std::tie(subaddressType, odd, digits) >= std::tie(rhs.subaddressType, rhs.odd, rhs.digits);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { SUBADDRESS_TYPE_NSAP = 0 };
  enum : int32_t { SUBADDRESS_TYPE_USER_SPECIFIED = 1 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaSmsSubaddress{";
    os << "subaddressType: " << ::android::internal::ToString(subaddressType);
    os << ", odd: " << ::android::internal::ToString(odd);
    os << ", digits: " << ::android::internal::ToString(digits);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
