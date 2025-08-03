#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/voice/CdmaNumberInfoRecord.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class CdmaRedirectingNumberInfoRecord {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::voice::CdmaNumberInfoRecord redirectingNumber;
  int32_t redirectingReason = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaRedirectingNumberInfoRecord& rhs) const {
    return std::tie(redirectingNumber, redirectingReason) != std::tie(rhs.redirectingNumber, rhs.redirectingReason);
  }
  inline bool operator<(const CdmaRedirectingNumberInfoRecord& rhs) const {
    return std::tie(redirectingNumber, redirectingReason) < std::tie(rhs.redirectingNumber, rhs.redirectingReason);
  }
  inline bool operator<=(const CdmaRedirectingNumberInfoRecord& rhs) const {
    return std::tie(redirectingNumber, redirectingReason) <= std::tie(rhs.redirectingNumber, rhs.redirectingReason);
  }
  inline bool operator==(const CdmaRedirectingNumberInfoRecord& rhs) const {
    return std::tie(redirectingNumber, redirectingReason) == std::tie(rhs.redirectingNumber, rhs.redirectingReason);
  }
  inline bool operator>(const CdmaRedirectingNumberInfoRecord& rhs) const {
    return std::tie(redirectingNumber, redirectingReason) > std::tie(rhs.redirectingNumber, rhs.redirectingReason);
  }
  inline bool operator>=(const CdmaRedirectingNumberInfoRecord& rhs) const {
    return std::tie(redirectingNumber, redirectingReason) >= std::tie(rhs.redirectingNumber, rhs.redirectingReason);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { REDIRECTING_REASON_UNKNOWN = 0 };
  enum : int32_t { REDIRECTING_REASON_CALL_FORWARDING_BUSY = 1 };
  enum : int32_t { REDIRECTING_REASON_CALL_FORWARDING_NO_REPLY = 2 };
  enum : int32_t { REDIRECTING_REASON_CALLED_DTE_OUT_OF_ORDER = 9 };
  enum : int32_t { REDIRECTING_REASON_CALL_FORWARDING_BY_THE_CALLED_DTE = 10 };
  enum : int32_t { REDIRECTING_REASON_CALL_FORWARDING_UNCONDITIONAL = 15 };
  enum : int32_t { REDIRECTING_REASON_RESERVED = 16 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaRedirectingNumberInfoRecord{";
    os << "redirectingNumber: " << ::android::internal::ToString(redirectingNumber);
    os << ", redirectingReason: " << ::android::internal::ToString(redirectingReason);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
