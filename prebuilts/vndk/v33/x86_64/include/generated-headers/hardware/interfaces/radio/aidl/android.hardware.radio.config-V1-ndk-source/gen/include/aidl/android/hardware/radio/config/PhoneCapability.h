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
namespace config {
class PhoneCapability {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t maxActiveData = 0;
  int8_t maxActiveInternetData = 0;
  bool isInternetLingeringSupported = false;
  std::vector<uint8_t> logicalModemIds;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PhoneCapability& rhs) const {
    return std::tie(maxActiveData, maxActiveInternetData, isInternetLingeringSupported, logicalModemIds) != std::tie(rhs.maxActiveData, rhs.maxActiveInternetData, rhs.isInternetLingeringSupported, rhs.logicalModemIds);
  }
  inline bool operator<(const PhoneCapability& rhs) const {
    return std::tie(maxActiveData, maxActiveInternetData, isInternetLingeringSupported, logicalModemIds) < std::tie(rhs.maxActiveData, rhs.maxActiveInternetData, rhs.isInternetLingeringSupported, rhs.logicalModemIds);
  }
  inline bool operator<=(const PhoneCapability& rhs) const {
    return std::tie(maxActiveData, maxActiveInternetData, isInternetLingeringSupported, logicalModemIds) <= std::tie(rhs.maxActiveData, rhs.maxActiveInternetData, rhs.isInternetLingeringSupported, rhs.logicalModemIds);
  }
  inline bool operator==(const PhoneCapability& rhs) const {
    return std::tie(maxActiveData, maxActiveInternetData, isInternetLingeringSupported, logicalModemIds) == std::tie(rhs.maxActiveData, rhs.maxActiveInternetData, rhs.isInternetLingeringSupported, rhs.logicalModemIds);
  }
  inline bool operator>(const PhoneCapability& rhs) const {
    return std::tie(maxActiveData, maxActiveInternetData, isInternetLingeringSupported, logicalModemIds) > std::tie(rhs.maxActiveData, rhs.maxActiveInternetData, rhs.isInternetLingeringSupported, rhs.logicalModemIds);
  }
  inline bool operator>=(const PhoneCapability& rhs) const {
    return std::tie(maxActiveData, maxActiveInternetData, isInternetLingeringSupported, logicalModemIds) >= std::tie(rhs.maxActiveData, rhs.maxActiveInternetData, rhs.isInternetLingeringSupported, rhs.logicalModemIds);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PhoneCapability{";
    os << "maxActiveData: " << ::android::internal::ToString(maxActiveData);
    os << ", maxActiveInternetData: " << ::android::internal::ToString(maxActiveInternetData);
    os << ", isInternetLingeringSupported: " << ::android::internal::ToString(isInternetLingeringSupported);
    os << ", logicalModemIds: " << ::android::internal::ToString(logicalModemIds);
    os << "}";
    return os.str();
  }
};
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
