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
namespace voice {
class CallForwardInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t status = 0;
  int32_t reason = 0;
  int32_t serviceClass = 0;
  int32_t toa = 0;
  std::string number;
  int32_t timeSeconds = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CallForwardInfo& rhs) const {
    return std::tie(status, reason, serviceClass, toa, number, timeSeconds) != std::tie(rhs.status, rhs.reason, rhs.serviceClass, rhs.toa, rhs.number, rhs.timeSeconds);
  }
  inline bool operator<(const CallForwardInfo& rhs) const {
    return std::tie(status, reason, serviceClass, toa, number, timeSeconds) < std::tie(rhs.status, rhs.reason, rhs.serviceClass, rhs.toa, rhs.number, rhs.timeSeconds);
  }
  inline bool operator<=(const CallForwardInfo& rhs) const {
    return std::tie(status, reason, serviceClass, toa, number, timeSeconds) <= std::tie(rhs.status, rhs.reason, rhs.serviceClass, rhs.toa, rhs.number, rhs.timeSeconds);
  }
  inline bool operator==(const CallForwardInfo& rhs) const {
    return std::tie(status, reason, serviceClass, toa, number, timeSeconds) == std::tie(rhs.status, rhs.reason, rhs.serviceClass, rhs.toa, rhs.number, rhs.timeSeconds);
  }
  inline bool operator>(const CallForwardInfo& rhs) const {
    return std::tie(status, reason, serviceClass, toa, number, timeSeconds) > std::tie(rhs.status, rhs.reason, rhs.serviceClass, rhs.toa, rhs.number, rhs.timeSeconds);
  }
  inline bool operator>=(const CallForwardInfo& rhs) const {
    return std::tie(status, reason, serviceClass, toa, number, timeSeconds) >= std::tie(rhs.status, rhs.reason, rhs.serviceClass, rhs.toa, rhs.number, rhs.timeSeconds);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { STATUS_DISABLE = 0 };
  enum : int32_t { STATUS_ENABLE = 1 };
  enum : int32_t { STATUS_INTERROGATE = 2 };
  enum : int32_t { STATUS_REGISTRATION = 3 };
  enum : int32_t { STATUS_ERASURE = 4 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CallForwardInfo{";
    os << "status: " << ::android::internal::ToString(status);
    os << ", reason: " << ::android::internal::ToString(reason);
    os << ", serviceClass: " << ::android::internal::ToString(serviceClass);
    os << ", toa: " << ::android::internal::ToString(toa);
    os << ", number: " << ::android::internal::ToString(number);
    os << ", timeSeconds: " << ::android::internal::ToString(timeSeconds);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
