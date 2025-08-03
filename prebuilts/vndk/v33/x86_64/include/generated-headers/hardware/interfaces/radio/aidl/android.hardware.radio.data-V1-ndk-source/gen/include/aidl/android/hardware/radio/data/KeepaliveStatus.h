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
namespace data {
class KeepaliveStatus {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t sessionHandle = 0;
  int32_t code = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const KeepaliveStatus& rhs) const {
    return std::tie(sessionHandle, code) != std::tie(rhs.sessionHandle, rhs.code);
  }
  inline bool operator<(const KeepaliveStatus& rhs) const {
    return std::tie(sessionHandle, code) < std::tie(rhs.sessionHandle, rhs.code);
  }
  inline bool operator<=(const KeepaliveStatus& rhs) const {
    return std::tie(sessionHandle, code) <= std::tie(rhs.sessionHandle, rhs.code);
  }
  inline bool operator==(const KeepaliveStatus& rhs) const {
    return std::tie(sessionHandle, code) == std::tie(rhs.sessionHandle, rhs.code);
  }
  inline bool operator>(const KeepaliveStatus& rhs) const {
    return std::tie(sessionHandle, code) > std::tie(rhs.sessionHandle, rhs.code);
  }
  inline bool operator>=(const KeepaliveStatus& rhs) const {
    return std::tie(sessionHandle, code) >= std::tie(rhs.sessionHandle, rhs.code);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { CODE_ACTIVE = 0 };
  enum : int32_t { CODE_INACTIVE = 1 };
  enum : int32_t { CODE_PENDING = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "KeepaliveStatus{";
    os << "sessionHandle: " << ::android::internal::ToString(sessionHandle);
    os << ", code: " << ::android::internal::ToString(code);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
