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
namespace wifi {
namespace supplicant {
class OceRssiBasedAssocRejectAttr {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t deltaRssi = 0;
  int32_t retryDelayS = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const OceRssiBasedAssocRejectAttr& rhs) const {
    return std::tie(deltaRssi, retryDelayS) != std::tie(rhs.deltaRssi, rhs.retryDelayS);
  }
  inline bool operator<(const OceRssiBasedAssocRejectAttr& rhs) const {
    return std::tie(deltaRssi, retryDelayS) < std::tie(rhs.deltaRssi, rhs.retryDelayS);
  }
  inline bool operator<=(const OceRssiBasedAssocRejectAttr& rhs) const {
    return std::tie(deltaRssi, retryDelayS) <= std::tie(rhs.deltaRssi, rhs.retryDelayS);
  }
  inline bool operator==(const OceRssiBasedAssocRejectAttr& rhs) const {
    return std::tie(deltaRssi, retryDelayS) == std::tie(rhs.deltaRssi, rhs.retryDelayS);
  }
  inline bool operator>(const OceRssiBasedAssocRejectAttr& rhs) const {
    return std::tie(deltaRssi, retryDelayS) > std::tie(rhs.deltaRssi, rhs.retryDelayS);
  }
  inline bool operator>=(const OceRssiBasedAssocRejectAttr& rhs) const {
    return std::tie(deltaRssi, retryDelayS) >= std::tie(rhs.deltaRssi, rhs.retryDelayS);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "OceRssiBasedAssocRejectAttr{";
    os << "deltaRssi: " << ::android::internal::ToString(deltaRssi);
    os << ", retryDelayS: " << ::android::internal::ToString(retryDelayS);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
