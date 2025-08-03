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
class MloLink {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t linkId = 0;
  std::vector<uint8_t> staLinkMacAddress;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const MloLink& rhs) const {
    return std::tie(linkId, staLinkMacAddress) != std::tie(rhs.linkId, rhs.staLinkMacAddress);
  }
  inline bool operator<(const MloLink& rhs) const {
    return std::tie(linkId, staLinkMacAddress) < std::tie(rhs.linkId, rhs.staLinkMacAddress);
  }
  inline bool operator<=(const MloLink& rhs) const {
    return std::tie(linkId, staLinkMacAddress) <= std::tie(rhs.linkId, rhs.staLinkMacAddress);
  }
  inline bool operator==(const MloLink& rhs) const {
    return std::tie(linkId, staLinkMacAddress) == std::tie(rhs.linkId, rhs.staLinkMacAddress);
  }
  inline bool operator>(const MloLink& rhs) const {
    return std::tie(linkId, staLinkMacAddress) > std::tie(rhs.linkId, rhs.staLinkMacAddress);
  }
  inline bool operator>=(const MloLink& rhs) const {
    return std::tie(linkId, staLinkMacAddress) >= std::tie(rhs.linkId, rhs.staLinkMacAddress);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "MloLink{";
    os << "linkId: " << ::android::internal::ToString(linkId);
    os << ", staLinkMacAddress: " << ::android::internal::ToString(staLinkMacAddress);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
