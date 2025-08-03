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
#include <aidl/android/hardware/radio/RadioResponseType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
class RadioResponseInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::RadioResponseType type = ::aidl::android::hardware::radio::RadioResponseType(0);
  int32_t serial = 0;
  ::aidl::android::hardware::radio::RadioError error = ::aidl::android::hardware::radio::RadioError(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RadioResponseInfo& rhs) const {
    return std::tie(type, serial, error) != std::tie(rhs.type, rhs.serial, rhs.error);
  }
  inline bool operator<(const RadioResponseInfo& rhs) const {
    return std::tie(type, serial, error) < std::tie(rhs.type, rhs.serial, rhs.error);
  }
  inline bool operator<=(const RadioResponseInfo& rhs) const {
    return std::tie(type, serial, error) <= std::tie(rhs.type, rhs.serial, rhs.error);
  }
  inline bool operator==(const RadioResponseInfo& rhs) const {
    return std::tie(type, serial, error) == std::tie(rhs.type, rhs.serial, rhs.error);
  }
  inline bool operator>(const RadioResponseInfo& rhs) const {
    return std::tie(type, serial, error) > std::tie(rhs.type, rhs.serial, rhs.error);
  }
  inline bool operator>=(const RadioResponseInfo& rhs) const {
    return std::tie(type, serial, error) >= std::tie(rhs.type, rhs.serial, rhs.error);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "RadioResponseInfo{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", serial: " << ::android::internal::ToString(serial);
    os << ", error: " << ::android::internal::ToString(error);
    os << "}";
    return os.str();
  }
};
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
