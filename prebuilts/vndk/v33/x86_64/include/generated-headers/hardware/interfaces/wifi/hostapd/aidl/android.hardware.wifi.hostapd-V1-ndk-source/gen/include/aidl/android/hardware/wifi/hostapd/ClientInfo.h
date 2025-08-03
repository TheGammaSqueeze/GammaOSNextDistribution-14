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
namespace hostapd {
class ClientInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string ifaceName;
  std::string apIfaceInstance;
  std::vector<uint8_t> clientAddress;
  bool isConnected = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ClientInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, clientAddress, isConnected) != std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.clientAddress, rhs.isConnected);
  }
  inline bool operator<(const ClientInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, clientAddress, isConnected) < std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.clientAddress, rhs.isConnected);
  }
  inline bool operator<=(const ClientInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, clientAddress, isConnected) <= std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.clientAddress, rhs.isConnected);
  }
  inline bool operator==(const ClientInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, clientAddress, isConnected) == std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.clientAddress, rhs.isConnected);
  }
  inline bool operator>(const ClientInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, clientAddress, isConnected) > std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.clientAddress, rhs.isConnected);
  }
  inline bool operator>=(const ClientInfo& rhs) const {
    return std::tie(ifaceName, apIfaceInstance, clientAddress, isConnected) >= std::tie(rhs.ifaceName, rhs.apIfaceInstance, rhs.clientAddress, rhs.isConnected);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ClientInfo{";
    os << "ifaceName: " << ::android::internal::ToString(ifaceName);
    os << ", apIfaceInstance: " << ::android::internal::ToString(apIfaceInstance);
    os << ", clientAddress: " << ::android::internal::ToString(clientAddress);
    os << ", isConnected: " << ::android::internal::ToString(isConnected);
    os << "}";
    return os.str();
  }
};
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
