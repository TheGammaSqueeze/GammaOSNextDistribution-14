#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/data/PortRange.h>
#include <aidl/android/hardware/radio/data/QosFilterIpsecSpi.h>
#include <aidl/android/hardware/radio/data/QosFilterIpv6FlowLabel.h>
#include <aidl/android/hardware/radio/data/QosFilterTypeOfService.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class QosFilter {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<std::string> localAddresses;
  std::vector<std::string> remoteAddresses;
  std::optional<::aidl::android::hardware::radio::data::PortRange> localPort;
  std::optional<::aidl::android::hardware::radio::data::PortRange> remotePort;
  int8_t protocol = 0;
  ::aidl::android::hardware::radio::data::QosFilterTypeOfService tos;
  ::aidl::android::hardware::radio::data::QosFilterIpv6FlowLabel flowLabel;
  ::aidl::android::hardware::radio::data::QosFilterIpsecSpi spi;
  int8_t direction = 0;
  int32_t precedence = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const QosFilter& rhs) const {
    return std::tie(localAddresses, remoteAddresses, localPort, remotePort, protocol, tos, flowLabel, spi, direction, precedence) != std::tie(rhs.localAddresses, rhs.remoteAddresses, rhs.localPort, rhs.remotePort, rhs.protocol, rhs.tos, rhs.flowLabel, rhs.spi, rhs.direction, rhs.precedence);
  }
  inline bool operator<(const QosFilter& rhs) const {
    return std::tie(localAddresses, remoteAddresses, localPort, remotePort, protocol, tos, flowLabel, spi, direction, precedence) < std::tie(rhs.localAddresses, rhs.remoteAddresses, rhs.localPort, rhs.remotePort, rhs.protocol, rhs.tos, rhs.flowLabel, rhs.spi, rhs.direction, rhs.precedence);
  }
  inline bool operator<=(const QosFilter& rhs) const {
    return std::tie(localAddresses, remoteAddresses, localPort, remotePort, protocol, tos, flowLabel, spi, direction, precedence) <= std::tie(rhs.localAddresses, rhs.remoteAddresses, rhs.localPort, rhs.remotePort, rhs.protocol, rhs.tos, rhs.flowLabel, rhs.spi, rhs.direction, rhs.precedence);
  }
  inline bool operator==(const QosFilter& rhs) const {
    return std::tie(localAddresses, remoteAddresses, localPort, remotePort, protocol, tos, flowLabel, spi, direction, precedence) == std::tie(rhs.localAddresses, rhs.remoteAddresses, rhs.localPort, rhs.remotePort, rhs.protocol, rhs.tos, rhs.flowLabel, rhs.spi, rhs.direction, rhs.precedence);
  }
  inline bool operator>(const QosFilter& rhs) const {
    return std::tie(localAddresses, remoteAddresses, localPort, remotePort, protocol, tos, flowLabel, spi, direction, precedence) > std::tie(rhs.localAddresses, rhs.remoteAddresses, rhs.localPort, rhs.remotePort, rhs.protocol, rhs.tos, rhs.flowLabel, rhs.spi, rhs.direction, rhs.precedence);
  }
  inline bool operator>=(const QosFilter& rhs) const {
    return std::tie(localAddresses, remoteAddresses, localPort, remotePort, protocol, tos, flowLabel, spi, direction, precedence) >= std::tie(rhs.localAddresses, rhs.remoteAddresses, rhs.localPort, rhs.remotePort, rhs.protocol, rhs.tos, rhs.flowLabel, rhs.spi, rhs.direction, rhs.precedence);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int8_t { DIRECTION_DOWNLINK = 0 };
  enum : int8_t { DIRECTION_UPLINK = 1 };
  enum : int8_t { DIRECTION_BIDIRECTIONAL = 2 };
  enum : int8_t { PROTOCOL_UNSPECIFIED = -1 };
  enum : int8_t { PROTOCOL_TCP = 6 };
  enum : int8_t { PROTOCOL_UDP = 17 };
  enum : int8_t { PROTOCOL_ESP = 50 };
  enum : int8_t { PROTOCOL_AH = 51 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "QosFilter{";
    os << "localAddresses: " << ::android::internal::ToString(localAddresses);
    os << ", remoteAddresses: " << ::android::internal::ToString(remoteAddresses);
    os << ", localPort: " << ::android::internal::ToString(localPort);
    os << ", remotePort: " << ::android::internal::ToString(remotePort);
    os << ", protocol: " << ::android::internal::ToString(protocol);
    os << ", tos: " << ::android::internal::ToString(tos);
    os << ", flowLabel: " << ::android::internal::ToString(flowLabel);
    os << ", spi: " << ::android::internal::ToString(spi);
    os << ", direction: " << ::android::internal::ToString(direction);
    os << ", precedence: " << ::android::internal::ToString(precedence);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
