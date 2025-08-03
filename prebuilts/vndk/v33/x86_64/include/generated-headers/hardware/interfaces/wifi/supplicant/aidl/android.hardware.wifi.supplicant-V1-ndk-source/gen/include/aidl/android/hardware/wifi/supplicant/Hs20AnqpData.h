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
class Hs20AnqpData {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> operatorFriendlyName;
  std::vector<uint8_t> wanMetrics;
  std::vector<uint8_t> connectionCapability;
  std::vector<uint8_t> osuProvidersList;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Hs20AnqpData& rhs) const {
    return std::tie(operatorFriendlyName, wanMetrics, connectionCapability, osuProvidersList) != std::tie(rhs.operatorFriendlyName, rhs.wanMetrics, rhs.connectionCapability, rhs.osuProvidersList);
  }
  inline bool operator<(const Hs20AnqpData& rhs) const {
    return std::tie(operatorFriendlyName, wanMetrics, connectionCapability, osuProvidersList) < std::tie(rhs.operatorFriendlyName, rhs.wanMetrics, rhs.connectionCapability, rhs.osuProvidersList);
  }
  inline bool operator<=(const Hs20AnqpData& rhs) const {
    return std::tie(operatorFriendlyName, wanMetrics, connectionCapability, osuProvidersList) <= std::tie(rhs.operatorFriendlyName, rhs.wanMetrics, rhs.connectionCapability, rhs.osuProvidersList);
  }
  inline bool operator==(const Hs20AnqpData& rhs) const {
    return std::tie(operatorFriendlyName, wanMetrics, connectionCapability, osuProvidersList) == std::tie(rhs.operatorFriendlyName, rhs.wanMetrics, rhs.connectionCapability, rhs.osuProvidersList);
  }
  inline bool operator>(const Hs20AnqpData& rhs) const {
    return std::tie(operatorFriendlyName, wanMetrics, connectionCapability, osuProvidersList) > std::tie(rhs.operatorFriendlyName, rhs.wanMetrics, rhs.connectionCapability, rhs.osuProvidersList);
  }
  inline bool operator>=(const Hs20AnqpData& rhs) const {
    return std::tie(operatorFriendlyName, wanMetrics, connectionCapability, osuProvidersList) >= std::tie(rhs.operatorFriendlyName, rhs.wanMetrics, rhs.connectionCapability, rhs.osuProvidersList);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Hs20AnqpData{";
    os << "operatorFriendlyName: " << ::android::internal::ToString(operatorFriendlyName);
    os << ", wanMetrics: " << ::android::internal::ToString(wanMetrics);
    os << ", connectionCapability: " << ::android::internal::ToString(connectionCapability);
    os << ", osuProvidersList: " << ::android::internal::ToString(osuProvidersList);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
