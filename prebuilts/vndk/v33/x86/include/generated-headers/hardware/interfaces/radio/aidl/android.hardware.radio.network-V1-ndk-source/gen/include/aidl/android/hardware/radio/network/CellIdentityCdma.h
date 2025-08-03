#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/OperatorInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class CellIdentityCdma {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t networkId = 0;
  int32_t systemId = 0;
  int32_t baseStationId = 0;
  int32_t longitude = 0;
  int32_t latitude = 0;
  ::aidl::android::hardware::radio::network::OperatorInfo operatorNames;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CellIdentityCdma& rhs) const {
    return std::tie(networkId, systemId, baseStationId, longitude, latitude, operatorNames) != std::tie(rhs.networkId, rhs.systemId, rhs.baseStationId, rhs.longitude, rhs.latitude, rhs.operatorNames);
  }
  inline bool operator<(const CellIdentityCdma& rhs) const {
    return std::tie(networkId, systemId, baseStationId, longitude, latitude, operatorNames) < std::tie(rhs.networkId, rhs.systemId, rhs.baseStationId, rhs.longitude, rhs.latitude, rhs.operatorNames);
  }
  inline bool operator<=(const CellIdentityCdma& rhs) const {
    return std::tie(networkId, systemId, baseStationId, longitude, latitude, operatorNames) <= std::tie(rhs.networkId, rhs.systemId, rhs.baseStationId, rhs.longitude, rhs.latitude, rhs.operatorNames);
  }
  inline bool operator==(const CellIdentityCdma& rhs) const {
    return std::tie(networkId, systemId, baseStationId, longitude, latitude, operatorNames) == std::tie(rhs.networkId, rhs.systemId, rhs.baseStationId, rhs.longitude, rhs.latitude, rhs.operatorNames);
  }
  inline bool operator>(const CellIdentityCdma& rhs) const {
    return std::tie(networkId, systemId, baseStationId, longitude, latitude, operatorNames) > std::tie(rhs.networkId, rhs.systemId, rhs.baseStationId, rhs.longitude, rhs.latitude, rhs.operatorNames);
  }
  inline bool operator>=(const CellIdentityCdma& rhs) const {
    return std::tie(networkId, systemId, baseStationId, longitude, latitude, operatorNames) >= std::tie(rhs.networkId, rhs.systemId, rhs.baseStationId, rhs.longitude, rhs.latitude, rhs.operatorNames);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CellIdentityCdma{";
    os << "networkId: " << ::android::internal::ToString(networkId);
    os << ", systemId: " << ::android::internal::ToString(systemId);
    os << ", baseStationId: " << ::android::internal::ToString(baseStationId);
    os << ", longitude: " << ::android::internal::ToString(longitude);
    os << ", latitude: " << ::android::internal::ToString(latitude);
    os << ", operatorNames: " << ::android::internal::ToString(operatorNames);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
