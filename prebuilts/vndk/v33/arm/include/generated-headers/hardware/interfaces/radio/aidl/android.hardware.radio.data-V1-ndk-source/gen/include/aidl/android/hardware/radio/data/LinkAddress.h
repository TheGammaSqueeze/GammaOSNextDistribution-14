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
namespace data {
class LinkAddress {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string address;
  int32_t addressProperties = 0;
  int64_t deprecationTime = 0L;
  int64_t expirationTime = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const LinkAddress& rhs) const {
    return std::tie(address, addressProperties, deprecationTime, expirationTime) != std::tie(rhs.address, rhs.addressProperties, rhs.deprecationTime, rhs.expirationTime);
  }
  inline bool operator<(const LinkAddress& rhs) const {
    return std::tie(address, addressProperties, deprecationTime, expirationTime) < std::tie(rhs.address, rhs.addressProperties, rhs.deprecationTime, rhs.expirationTime);
  }
  inline bool operator<=(const LinkAddress& rhs) const {
    return std::tie(address, addressProperties, deprecationTime, expirationTime) <= std::tie(rhs.address, rhs.addressProperties, rhs.deprecationTime, rhs.expirationTime);
  }
  inline bool operator==(const LinkAddress& rhs) const {
    return std::tie(address, addressProperties, deprecationTime, expirationTime) == std::tie(rhs.address, rhs.addressProperties, rhs.deprecationTime, rhs.expirationTime);
  }
  inline bool operator>(const LinkAddress& rhs) const {
    return std::tie(address, addressProperties, deprecationTime, expirationTime) > std::tie(rhs.address, rhs.addressProperties, rhs.deprecationTime, rhs.expirationTime);
  }
  inline bool operator>=(const LinkAddress& rhs) const {
    return std::tie(address, addressProperties, deprecationTime, expirationTime) >= std::tie(rhs.address, rhs.addressProperties, rhs.deprecationTime, rhs.expirationTime);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { ADDRESS_PROPERTY_NONE = 0 };
  enum : int32_t { ADDRESS_PROPERTY_DEPRECATED = 32 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "LinkAddress{";
    os << "address: " << ::android::internal::ToString(address);
    os << ", addressProperties: " << ::android::internal::ToString(addressProperties);
    os << ", deprecationTime: " << ::android::internal::ToString(deprecationTime);
    os << ", expirationTime: " << ::android::internal::ToString(expirationTime);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
