#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class DeviceInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<uint8_t> deviceInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DeviceInfo& rhs) const {
    return std::tie(deviceInfo) != std::tie(rhs.deviceInfo);
  }
  inline bool operator<(const DeviceInfo& rhs) const {
    return std::tie(deviceInfo) < std::tie(rhs.deviceInfo);
  }
  inline bool operator<=(const DeviceInfo& rhs) const {
    return std::tie(deviceInfo) <= std::tie(rhs.deviceInfo);
  }
  inline bool operator==(const DeviceInfo& rhs) const {
    return std::tie(deviceInfo) == std::tie(rhs.deviceInfo);
  }
  inline bool operator>(const DeviceInfo& rhs) const {
    return std::tie(deviceInfo) > std::tie(rhs.deviceInfo);
  }
  inline bool operator>=(const DeviceInfo& rhs) const {
    return std::tie(deviceInfo) >= std::tie(rhs.deviceInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DeviceInfo{";
    os << "deviceInfo: " << ::android::internal::ToString(deviceInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
