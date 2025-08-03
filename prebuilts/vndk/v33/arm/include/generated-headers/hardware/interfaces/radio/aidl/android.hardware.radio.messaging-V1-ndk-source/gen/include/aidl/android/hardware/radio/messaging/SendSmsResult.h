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
namespace messaging {
class SendSmsResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t messageRef = 0;
  std::string ackPDU;
  int32_t errorCode = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SendSmsResult& rhs) const {
    return std::tie(messageRef, ackPDU, errorCode) != std::tie(rhs.messageRef, rhs.ackPDU, rhs.errorCode);
  }
  inline bool operator<(const SendSmsResult& rhs) const {
    return std::tie(messageRef, ackPDU, errorCode) < std::tie(rhs.messageRef, rhs.ackPDU, rhs.errorCode);
  }
  inline bool operator<=(const SendSmsResult& rhs) const {
    return std::tie(messageRef, ackPDU, errorCode) <= std::tie(rhs.messageRef, rhs.ackPDU, rhs.errorCode);
  }
  inline bool operator==(const SendSmsResult& rhs) const {
    return std::tie(messageRef, ackPDU, errorCode) == std::tie(rhs.messageRef, rhs.ackPDU, rhs.errorCode);
  }
  inline bool operator>(const SendSmsResult& rhs) const {
    return std::tie(messageRef, ackPDU, errorCode) > std::tie(rhs.messageRef, rhs.ackPDU, rhs.errorCode);
  }
  inline bool operator>=(const SendSmsResult& rhs) const {
    return std::tie(messageRef, ackPDU, errorCode) >= std::tie(rhs.messageRef, rhs.ackPDU, rhs.errorCode);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SendSmsResult{";
    os << "messageRef: " << ::android::internal::ToString(messageRef);
    os << ", ackPDU: " << ::android::internal::ToString(ackPDU);
    os << ", errorCode: " << ::android::internal::ToString(errorCode);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
