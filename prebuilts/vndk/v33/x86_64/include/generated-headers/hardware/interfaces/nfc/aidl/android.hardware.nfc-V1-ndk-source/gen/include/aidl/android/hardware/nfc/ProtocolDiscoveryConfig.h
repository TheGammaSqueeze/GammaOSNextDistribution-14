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
namespace nfc {
class ProtocolDiscoveryConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t protocol18092Active = 0;
  int8_t protocolBPrime = 0;
  int8_t protocolDual = 0;
  int8_t protocol15693 = 0;
  int8_t protocolKovio = 0;
  int8_t protocolMifare = 0;
  int8_t discoveryPollKovio = 0;
  int8_t discoveryPollBPrime = 0;
  int8_t discoveryListenBPrime = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ProtocolDiscoveryConfig& rhs) const {
    return std::tie(protocol18092Active, protocolBPrime, protocolDual, protocol15693, protocolKovio, protocolMifare, discoveryPollKovio, discoveryPollBPrime, discoveryListenBPrime) != std::tie(rhs.protocol18092Active, rhs.protocolBPrime, rhs.protocolDual, rhs.protocol15693, rhs.protocolKovio, rhs.protocolMifare, rhs.discoveryPollKovio, rhs.discoveryPollBPrime, rhs.discoveryListenBPrime);
  }
  inline bool operator<(const ProtocolDiscoveryConfig& rhs) const {
    return std::tie(protocol18092Active, protocolBPrime, protocolDual, protocol15693, protocolKovio, protocolMifare, discoveryPollKovio, discoveryPollBPrime, discoveryListenBPrime) < std::tie(rhs.protocol18092Active, rhs.protocolBPrime, rhs.protocolDual, rhs.protocol15693, rhs.protocolKovio, rhs.protocolMifare, rhs.discoveryPollKovio, rhs.discoveryPollBPrime, rhs.discoveryListenBPrime);
  }
  inline bool operator<=(const ProtocolDiscoveryConfig& rhs) const {
    return std::tie(protocol18092Active, protocolBPrime, protocolDual, protocol15693, protocolKovio, protocolMifare, discoveryPollKovio, discoveryPollBPrime, discoveryListenBPrime) <= std::tie(rhs.protocol18092Active, rhs.protocolBPrime, rhs.protocolDual, rhs.protocol15693, rhs.protocolKovio, rhs.protocolMifare, rhs.discoveryPollKovio, rhs.discoveryPollBPrime, rhs.discoveryListenBPrime);
  }
  inline bool operator==(const ProtocolDiscoveryConfig& rhs) const {
    return std::tie(protocol18092Active, protocolBPrime, protocolDual, protocol15693, protocolKovio, protocolMifare, discoveryPollKovio, discoveryPollBPrime, discoveryListenBPrime) == std::tie(rhs.protocol18092Active, rhs.protocolBPrime, rhs.protocolDual, rhs.protocol15693, rhs.protocolKovio, rhs.protocolMifare, rhs.discoveryPollKovio, rhs.discoveryPollBPrime, rhs.discoveryListenBPrime);
  }
  inline bool operator>(const ProtocolDiscoveryConfig& rhs) const {
    return std::tie(protocol18092Active, protocolBPrime, protocolDual, protocol15693, protocolKovio, protocolMifare, discoveryPollKovio, discoveryPollBPrime, discoveryListenBPrime) > std::tie(rhs.protocol18092Active, rhs.protocolBPrime, rhs.protocolDual, rhs.protocol15693, rhs.protocolKovio, rhs.protocolMifare, rhs.discoveryPollKovio, rhs.discoveryPollBPrime, rhs.discoveryListenBPrime);
  }
  inline bool operator>=(const ProtocolDiscoveryConfig& rhs) const {
    return std::tie(protocol18092Active, protocolBPrime, protocolDual, protocol15693, protocolKovio, protocolMifare, discoveryPollKovio, discoveryPollBPrime, discoveryListenBPrime) >= std::tie(rhs.protocol18092Active, rhs.protocolBPrime, rhs.protocolDual, rhs.protocol15693, rhs.protocolKovio, rhs.protocolMifare, rhs.discoveryPollKovio, rhs.discoveryPollBPrime, rhs.discoveryListenBPrime);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ProtocolDiscoveryConfig{";
    os << "protocol18092Active: " << ::android::internal::ToString(protocol18092Active);
    os << ", protocolBPrime: " << ::android::internal::ToString(protocolBPrime);
    os << ", protocolDual: " << ::android::internal::ToString(protocolDual);
    os << ", protocol15693: " << ::android::internal::ToString(protocol15693);
    os << ", protocolKovio: " << ::android::internal::ToString(protocolKovio);
    os << ", protocolMifare: " << ::android::internal::ToString(protocolMifare);
    os << ", discoveryPollKovio: " << ::android::internal::ToString(discoveryPollKovio);
    os << ", discoveryPollBPrime: " << ::android::internal::ToString(discoveryPollBPrime);
    os << ", discoveryListenBPrime: " << ::android::internal::ToString(discoveryListenBPrime);
    os << "}";
    return os.str();
  }
};
}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
