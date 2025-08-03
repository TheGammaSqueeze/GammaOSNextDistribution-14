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
namespace network {
class SuppSvcNotification {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool isMT = false;
  int32_t code = 0;
  int32_t index = 0;
  int32_t type = 0;
  std::string number;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SuppSvcNotification& rhs) const {
    return std::tie(isMT, code, index, type, number) != std::tie(rhs.isMT, rhs.code, rhs.index, rhs.type, rhs.number);
  }
  inline bool operator<(const SuppSvcNotification& rhs) const {
    return std::tie(isMT, code, index, type, number) < std::tie(rhs.isMT, rhs.code, rhs.index, rhs.type, rhs.number);
  }
  inline bool operator<=(const SuppSvcNotification& rhs) const {
    return std::tie(isMT, code, index, type, number) <= std::tie(rhs.isMT, rhs.code, rhs.index, rhs.type, rhs.number);
  }
  inline bool operator==(const SuppSvcNotification& rhs) const {
    return std::tie(isMT, code, index, type, number) == std::tie(rhs.isMT, rhs.code, rhs.index, rhs.type, rhs.number);
  }
  inline bool operator>(const SuppSvcNotification& rhs) const {
    return std::tie(isMT, code, index, type, number) > std::tie(rhs.isMT, rhs.code, rhs.index, rhs.type, rhs.number);
  }
  inline bool operator>=(const SuppSvcNotification& rhs) const {
    return std::tie(isMT, code, index, type, number) >= std::tie(rhs.isMT, rhs.code, rhs.index, rhs.type, rhs.number);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SuppSvcNotification{";
    os << "isMT: " << ::android::internal::ToString(isMT);
    os << ", code: " << ::android::internal::ToString(code);
    os << ", index: " << ::android::internal::ToString(index);
    os << ", type: " << ::android::internal::ToString(type);
    os << ", number: " << ::android::internal::ToString(number);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
