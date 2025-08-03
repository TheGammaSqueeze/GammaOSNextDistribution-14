#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/voice/LastCallFailCause.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class LastCallFailCauseInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::voice::LastCallFailCause causeCode = ::aidl::android::hardware::radio::voice::LastCallFailCause(0);
  std::string vendorCause;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const LastCallFailCauseInfo& rhs) const {
    return std::tie(causeCode, vendorCause) != std::tie(rhs.causeCode, rhs.vendorCause);
  }
  inline bool operator<(const LastCallFailCauseInfo& rhs) const {
    return std::tie(causeCode, vendorCause) < std::tie(rhs.causeCode, rhs.vendorCause);
  }
  inline bool operator<=(const LastCallFailCauseInfo& rhs) const {
    return std::tie(causeCode, vendorCause) <= std::tie(rhs.causeCode, rhs.vendorCause);
  }
  inline bool operator==(const LastCallFailCauseInfo& rhs) const {
    return std::tie(causeCode, vendorCause) == std::tie(rhs.causeCode, rhs.vendorCause);
  }
  inline bool operator>(const LastCallFailCauseInfo& rhs) const {
    return std::tie(causeCode, vendorCause) > std::tie(rhs.causeCode, rhs.vendorCause);
  }
  inline bool operator>=(const LastCallFailCauseInfo& rhs) const {
    return std::tie(causeCode, vendorCause) >= std::tie(rhs.causeCode, rhs.vendorCause);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "LastCallFailCauseInfo{";
    os << "causeCode: " << ::android::internal::ToString(causeCode);
    os << ", vendorCause: " << ::android::internal::ToString(vendorCause);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
