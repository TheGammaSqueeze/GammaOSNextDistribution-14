#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/RadioError.h>
#include <aidl/android/hardware/radio/network/CellInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class NetworkScanResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t status = 0;
  ::aidl::android::hardware::radio::RadioError error = ::aidl::android::hardware::radio::RadioError(0);
  std::vector<::aidl::android::hardware::radio::network::CellInfo> networkInfos;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NetworkScanResult& rhs) const {
    return std::tie(status, error, networkInfos) != std::tie(rhs.status, rhs.error, rhs.networkInfos);
  }
  inline bool operator<(const NetworkScanResult& rhs) const {
    return std::tie(status, error, networkInfos) < std::tie(rhs.status, rhs.error, rhs.networkInfos);
  }
  inline bool operator<=(const NetworkScanResult& rhs) const {
    return std::tie(status, error, networkInfos) <= std::tie(rhs.status, rhs.error, rhs.networkInfos);
  }
  inline bool operator==(const NetworkScanResult& rhs) const {
    return std::tie(status, error, networkInfos) == std::tie(rhs.status, rhs.error, rhs.networkInfos);
  }
  inline bool operator>(const NetworkScanResult& rhs) const {
    return std::tie(status, error, networkInfos) > std::tie(rhs.status, rhs.error, rhs.networkInfos);
  }
  inline bool operator>=(const NetworkScanResult& rhs) const {
    return std::tie(status, error, networkInfos) >= std::tie(rhs.status, rhs.error, rhs.networkInfos);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { SCAN_STATUS_PARTIAL = 1 };
  enum : int32_t { SCAN_STATUS_COMPLETE = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "NetworkScanResult{";
    os << "status: " << ::android::internal::ToString(status);
    os << ", error: " << ::android::internal::ToString(error);
    os << ", networkInfos: " << ::android::internal::ToString(networkInfos);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
