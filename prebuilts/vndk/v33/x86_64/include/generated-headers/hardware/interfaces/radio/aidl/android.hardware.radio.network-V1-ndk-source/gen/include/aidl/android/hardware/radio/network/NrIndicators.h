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
class NrIndicators {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool isEndcAvailable = false;
  bool isDcNrRestricted = false;
  bool isNrAvailable = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NrIndicators& rhs) const {
    return std::tie(isEndcAvailable, isDcNrRestricted, isNrAvailable) != std::tie(rhs.isEndcAvailable, rhs.isDcNrRestricted, rhs.isNrAvailable);
  }
  inline bool operator<(const NrIndicators& rhs) const {
    return std::tie(isEndcAvailable, isDcNrRestricted, isNrAvailable) < std::tie(rhs.isEndcAvailable, rhs.isDcNrRestricted, rhs.isNrAvailable);
  }
  inline bool operator<=(const NrIndicators& rhs) const {
    return std::tie(isEndcAvailable, isDcNrRestricted, isNrAvailable) <= std::tie(rhs.isEndcAvailable, rhs.isDcNrRestricted, rhs.isNrAvailable);
  }
  inline bool operator==(const NrIndicators& rhs) const {
    return std::tie(isEndcAvailable, isDcNrRestricted, isNrAvailable) == std::tie(rhs.isEndcAvailable, rhs.isDcNrRestricted, rhs.isNrAvailable);
  }
  inline bool operator>(const NrIndicators& rhs) const {
    return std::tie(isEndcAvailable, isDcNrRestricted, isNrAvailable) > std::tie(rhs.isEndcAvailable, rhs.isDcNrRestricted, rhs.isNrAvailable);
  }
  inline bool operator>=(const NrIndicators& rhs) const {
    return std::tie(isEndcAvailable, isDcNrRestricted, isNrAvailable) >= std::tie(rhs.isEndcAvailable, rhs.isDcNrRestricted, rhs.isNrAvailable);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NrIndicators{";
    os << "isEndcAvailable: " << ::android::internal::ToString(isEndcAvailable);
    os << ", isDcNrRestricted: " << ::android::internal::ToString(isDcNrRestricted);
    os << ", isNrAvailable: " << ::android::internal::ToString(isNrAvailable);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
