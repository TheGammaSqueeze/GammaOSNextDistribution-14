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
#include <aidl/android/hardware/gnss/GnssConstellationType.h>
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BlocklistedSource {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::gnss::GnssConstellationType constellation = ::aidl::android::hardware::gnss::GnssConstellationType::UNKNOWN;
  int32_t svid = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const BlocklistedSource& rhs) const {
    return std::tie(constellation, svid) != std::tie(rhs.constellation, rhs.svid);
  }
  inline bool operator<(const BlocklistedSource& rhs) const {
    return std::tie(constellation, svid) < std::tie(rhs.constellation, rhs.svid);
  }
  inline bool operator<=(const BlocklistedSource& rhs) const {
    return std::tie(constellation, svid) <= std::tie(rhs.constellation, rhs.svid);
  }
  inline bool operator==(const BlocklistedSource& rhs) const {
    return std::tie(constellation, svid) == std::tie(rhs.constellation, rhs.svid);
  }
  inline bool operator>(const BlocklistedSource& rhs) const {
    return std::tie(constellation, svid) > std::tie(rhs.constellation, rhs.svid);
  }
  inline bool operator>=(const BlocklistedSource& rhs) const {
    return std::tie(constellation, svid) >= std::tie(rhs.constellation, rhs.svid);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "BlocklistedSource{";
    os << "constellation: " << ::android::internal::ToString(constellation);
    os << ", svid: " << ::android::internal::ToString(svid);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
