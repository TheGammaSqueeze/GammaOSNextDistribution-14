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
class KeepaliveRequest {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t type = 0;
  std::vector<uint8_t> sourceAddress;
  int32_t sourcePort = 0;
  std::vector<uint8_t> destinationAddress;
  int32_t destinationPort = 0;
  int32_t maxKeepaliveIntervalMillis = 0;
  int32_t cid = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeepaliveRequest& rhs) const {
    return std::tie(type, sourceAddress, sourcePort, destinationAddress, destinationPort, maxKeepaliveIntervalMillis, cid) != std::tie(rhs.type, rhs.sourceAddress, rhs.sourcePort, rhs.destinationAddress, rhs.destinationPort, rhs.maxKeepaliveIntervalMillis, rhs.cid);
  }
  inline bool operator<(const KeepaliveRequest& rhs) const {
    return std::tie(type, sourceAddress, sourcePort, destinationAddress, destinationPort, maxKeepaliveIntervalMillis, cid) < std::tie(rhs.type, rhs.sourceAddress, rhs.sourcePort, rhs.destinationAddress, rhs.destinationPort, rhs.maxKeepaliveIntervalMillis, rhs.cid);
  }
  inline bool operator<=(const KeepaliveRequest& rhs) const {
    return std::tie(type, sourceAddress, sourcePort, destinationAddress, destinationPort, maxKeepaliveIntervalMillis, cid) <= std::tie(rhs.type, rhs.sourceAddress, rhs.sourcePort, rhs.destinationAddress, rhs.destinationPort, rhs.maxKeepaliveIntervalMillis, rhs.cid);
  }
  inline bool operator==(const KeepaliveRequest& rhs) const {
    return std::tie(type, sourceAddress, sourcePort, destinationAddress, destinationPort, maxKeepaliveIntervalMillis, cid) == std::tie(rhs.type, rhs.sourceAddress, rhs.sourcePort, rhs.destinationAddress, rhs.destinationPort, rhs.maxKeepaliveIntervalMillis, rhs.cid);
  }
  inline bool operator>(const KeepaliveRequest& rhs) const {
    return std::tie(type, sourceAddress, sourcePort, destinationAddress, destinationPort, maxKeepaliveIntervalMillis, cid) > std::tie(rhs.type, rhs.sourceAddress, rhs.sourcePort, rhs.destinationAddress, rhs.destinationPort, rhs.maxKeepaliveIntervalMillis, rhs.cid);
  }
  inline bool operator>=(const KeepaliveRequest& rhs) const {
    return std::tie(type, sourceAddress, sourcePort, destinationAddress, destinationPort, maxKeepaliveIntervalMillis, cid) >= std::tie(rhs.type, rhs.sourceAddress, rhs.sourcePort, rhs.destinationAddress, rhs.destinationPort, rhs.maxKeepaliveIntervalMillis, rhs.cid);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { TYPE_NATT_IPV4 = 0 };
  enum : int32_t { TYPE_NATT_IPV6 = 1 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeepaliveRequest{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", sourceAddress: " << ::android::internal::ToString(sourceAddress);
    os << ", sourcePort: " << ::android::internal::ToString(sourcePort);
    os << ", destinationAddress: " << ::android::internal::ToString(destinationAddress);
    os << ", destinationPort: " << ::android::internal::ToString(destinationPort);
    os << ", maxKeepaliveIntervalMillis: " << ::android::internal::ToString(maxKeepaliveIntervalMillis);
    os << ", cid: " << ::android::internal::ToString(cid);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
