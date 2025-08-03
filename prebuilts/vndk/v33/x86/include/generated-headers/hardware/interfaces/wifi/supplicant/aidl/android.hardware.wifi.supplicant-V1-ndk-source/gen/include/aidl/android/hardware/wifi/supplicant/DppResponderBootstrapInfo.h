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
class DppResponderBootstrapInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t bootstrapId = 0;
  int32_t listenChannel = 0;
  std::string uri;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DppResponderBootstrapInfo& rhs) const {
    return std::tie(bootstrapId, listenChannel, uri) != std::tie(rhs.bootstrapId, rhs.listenChannel, rhs.uri);
  }
  inline bool operator<(const DppResponderBootstrapInfo& rhs) const {
    return std::tie(bootstrapId, listenChannel, uri) < std::tie(rhs.bootstrapId, rhs.listenChannel, rhs.uri);
  }
  inline bool operator<=(const DppResponderBootstrapInfo& rhs) const {
    return std::tie(bootstrapId, listenChannel, uri) <= std::tie(rhs.bootstrapId, rhs.listenChannel, rhs.uri);
  }
  inline bool operator==(const DppResponderBootstrapInfo& rhs) const {
    return std::tie(bootstrapId, listenChannel, uri) == std::tie(rhs.bootstrapId, rhs.listenChannel, rhs.uri);
  }
  inline bool operator>(const DppResponderBootstrapInfo& rhs) const {
    return std::tie(bootstrapId, listenChannel, uri) > std::tie(rhs.bootstrapId, rhs.listenChannel, rhs.uri);
  }
  inline bool operator>=(const DppResponderBootstrapInfo& rhs) const {
    return std::tie(bootstrapId, listenChannel, uri) >= std::tie(rhs.bootstrapId, rhs.listenChannel, rhs.uri);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DppResponderBootstrapInfo{";
    os << "bootstrapId: " << ::android::internal::ToString(bootstrapId);
    os << ", listenChannel: " << ::android::internal::ToString(listenChannel);
    os << ", uri: " << ::android::internal::ToString(uri);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
