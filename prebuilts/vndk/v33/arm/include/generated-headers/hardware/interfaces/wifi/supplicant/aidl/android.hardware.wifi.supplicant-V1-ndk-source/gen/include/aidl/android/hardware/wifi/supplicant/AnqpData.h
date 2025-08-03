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
class AnqpData {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> venueName;
  std::vector<uint8_t> roamingConsortium;
  std::vector<uint8_t> ipAddrTypeAvailability;
  std::vector<uint8_t> naiRealm;
  std::vector<uint8_t> anqp3gppCellularNetwork;
  std::vector<uint8_t> domainName;
  std::vector<uint8_t> venueUrl;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AnqpData& rhs) const {
    return std::tie(venueName, roamingConsortium, ipAddrTypeAvailability, naiRealm, anqp3gppCellularNetwork, domainName, venueUrl) != std::tie(rhs.venueName, rhs.roamingConsortium, rhs.ipAddrTypeAvailability, rhs.naiRealm, rhs.anqp3gppCellularNetwork, rhs.domainName, rhs.venueUrl);
  }
  inline bool operator<(const AnqpData& rhs) const {
    return std::tie(venueName, roamingConsortium, ipAddrTypeAvailability, naiRealm, anqp3gppCellularNetwork, domainName, venueUrl) < std::tie(rhs.venueName, rhs.roamingConsortium, rhs.ipAddrTypeAvailability, rhs.naiRealm, rhs.anqp3gppCellularNetwork, rhs.domainName, rhs.venueUrl);
  }
  inline bool operator<=(const AnqpData& rhs) const {
    return std::tie(venueName, roamingConsortium, ipAddrTypeAvailability, naiRealm, anqp3gppCellularNetwork, domainName, venueUrl) <= std::tie(rhs.venueName, rhs.roamingConsortium, rhs.ipAddrTypeAvailability, rhs.naiRealm, rhs.anqp3gppCellularNetwork, rhs.domainName, rhs.venueUrl);
  }
  inline bool operator==(const AnqpData& rhs) const {
    return std::tie(venueName, roamingConsortium, ipAddrTypeAvailability, naiRealm, anqp3gppCellularNetwork, domainName, venueUrl) == std::tie(rhs.venueName, rhs.roamingConsortium, rhs.ipAddrTypeAvailability, rhs.naiRealm, rhs.anqp3gppCellularNetwork, rhs.domainName, rhs.venueUrl);
  }
  inline bool operator>(const AnqpData& rhs) const {
    return std::tie(venueName, roamingConsortium, ipAddrTypeAvailability, naiRealm, anqp3gppCellularNetwork, domainName, venueUrl) > std::tie(rhs.venueName, rhs.roamingConsortium, rhs.ipAddrTypeAvailability, rhs.naiRealm, rhs.anqp3gppCellularNetwork, rhs.domainName, rhs.venueUrl);
  }
  inline bool operator>=(const AnqpData& rhs) const {
    return std::tie(venueName, roamingConsortium, ipAddrTypeAvailability, naiRealm, anqp3gppCellularNetwork, domainName, venueUrl) >= std::tie(rhs.venueName, rhs.roamingConsortium, rhs.ipAddrTypeAvailability, rhs.naiRealm, rhs.anqp3gppCellularNetwork, rhs.domainName, rhs.venueUrl);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AnqpData{";
    os << "venueName: " << ::android::internal::ToString(venueName);
    os << ", roamingConsortium: " << ::android::internal::ToString(roamingConsortium);
    os << ", ipAddrTypeAvailability: " << ::android::internal::ToString(ipAddrTypeAvailability);
    os << ", naiRealm: " << ::android::internal::ToString(naiRealm);
    os << ", anqp3gppCellularNetwork: " << ::android::internal::ToString(anqp3gppCellularNetwork);
    os << ", domainName: " << ::android::internal::ToString(domainName);
    os << ", venueUrl: " << ::android::internal::ToString(venueUrl);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
