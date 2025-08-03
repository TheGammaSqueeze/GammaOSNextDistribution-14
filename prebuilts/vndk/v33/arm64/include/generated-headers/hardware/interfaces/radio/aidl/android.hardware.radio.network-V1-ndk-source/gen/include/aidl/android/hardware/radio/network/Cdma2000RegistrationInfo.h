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
namespace network {
class Cdma2000RegistrationInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool cssSupported = false;
  int32_t roamingIndicator = 0;
  int32_t systemIsInPrl = 0;
  int32_t defaultRoamingIndicator = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Cdma2000RegistrationInfo& rhs) const {
    return std::tie(cssSupported, roamingIndicator, systemIsInPrl, defaultRoamingIndicator) != std::tie(rhs.cssSupported, rhs.roamingIndicator, rhs.systemIsInPrl, rhs.defaultRoamingIndicator);
  }
  inline bool operator<(const Cdma2000RegistrationInfo& rhs) const {
    return std::tie(cssSupported, roamingIndicator, systemIsInPrl, defaultRoamingIndicator) < std::tie(rhs.cssSupported, rhs.roamingIndicator, rhs.systemIsInPrl, rhs.defaultRoamingIndicator);
  }
  inline bool operator<=(const Cdma2000RegistrationInfo& rhs) const {
    return std::tie(cssSupported, roamingIndicator, systemIsInPrl, defaultRoamingIndicator) <= std::tie(rhs.cssSupported, rhs.roamingIndicator, rhs.systemIsInPrl, rhs.defaultRoamingIndicator);
  }
  inline bool operator==(const Cdma2000RegistrationInfo& rhs) const {
    return std::tie(cssSupported, roamingIndicator, systemIsInPrl, defaultRoamingIndicator) == std::tie(rhs.cssSupported, rhs.roamingIndicator, rhs.systemIsInPrl, rhs.defaultRoamingIndicator);
  }
  inline bool operator>(const Cdma2000RegistrationInfo& rhs) const {
    return std::tie(cssSupported, roamingIndicator, systemIsInPrl, defaultRoamingIndicator) > std::tie(rhs.cssSupported, rhs.roamingIndicator, rhs.systemIsInPrl, rhs.defaultRoamingIndicator);
  }
  inline bool operator>=(const Cdma2000RegistrationInfo& rhs) const {
    return std::tie(cssSupported, roamingIndicator, systemIsInPrl, defaultRoamingIndicator) >= std::tie(rhs.cssSupported, rhs.roamingIndicator, rhs.systemIsInPrl, rhs.defaultRoamingIndicator);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { PRL_INDICATOR_NOT_REGISTERED = -1 };
  enum : int32_t { PRL_INDICATOR_NOT_IN_PRL = 0 };
  enum : int32_t { PRL_INDICATOR_IN_PRL = 1 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "Cdma2000RegistrationInfo{";
    os << "cssSupported: " << ::android::internal::ToString(cssSupported);
    os << ", roamingIndicator: " << ::android::internal::ToString(roamingIndicator);
    os << ", systemIsInPrl: " << ::android::internal::ToString(systemIsInPrl);
    os << ", defaultRoamingIndicator: " << ::android::internal::ToString(defaultRoamingIndicator);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
