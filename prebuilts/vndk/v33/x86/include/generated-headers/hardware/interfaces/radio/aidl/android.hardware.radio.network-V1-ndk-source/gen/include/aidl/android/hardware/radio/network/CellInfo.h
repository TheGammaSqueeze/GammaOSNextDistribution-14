#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/CellConnectionStatus.h>
#include <aidl/android/hardware/radio/network/CellInfoRatSpecificInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool registered = false;
  ::aidl::android::hardware::radio::network::CellConnectionStatus connectionStatus = ::aidl::android::hardware::radio::network::CellConnectionStatus(0);
  ::aidl::android::hardware::radio::network::CellInfoRatSpecificInfo ratSpecificInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellInfo& rhs) const {
    return std::tie(registered, connectionStatus, ratSpecificInfo) != std::tie(rhs.registered, rhs.connectionStatus, rhs.ratSpecificInfo);
  }
  inline bool operator<(const CellInfo& rhs) const {
    return std::tie(registered, connectionStatus, ratSpecificInfo) < std::tie(rhs.registered, rhs.connectionStatus, rhs.ratSpecificInfo);
  }
  inline bool operator<=(const CellInfo& rhs) const {
    return std::tie(registered, connectionStatus, ratSpecificInfo) <= std::tie(rhs.registered, rhs.connectionStatus, rhs.ratSpecificInfo);
  }
  inline bool operator==(const CellInfo& rhs) const {
    return std::tie(registered, connectionStatus, ratSpecificInfo) == std::tie(rhs.registered, rhs.connectionStatus, rhs.ratSpecificInfo);
  }
  inline bool operator>(const CellInfo& rhs) const {
    return std::tie(registered, connectionStatus, ratSpecificInfo) > std::tie(rhs.registered, rhs.connectionStatus, rhs.ratSpecificInfo);
  }
  inline bool operator>=(const CellInfo& rhs) const {
    return std::tie(registered, connectionStatus, ratSpecificInfo) >= std::tie(rhs.registered, rhs.connectionStatus, rhs.ratSpecificInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellInfo{";
    os << "registered: " << ::android::internal::ToString(registered);
    os << ", connectionStatus: " << ::android::internal::ToString(connectionStatus);
    os << ", ratSpecificInfo: " << ::android::internal::ToString(ratSpecificInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
