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
class SmsWriteArgs {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t status = 0;
  std::string pdu;
  std::string smsc;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SmsWriteArgs& rhs) const {
    return std::tie(status, pdu, smsc) != std::tie(rhs.status, rhs.pdu, rhs.smsc);
  }
  inline bool operator<(const SmsWriteArgs& rhs) const {
    return std::tie(status, pdu, smsc) < std::tie(rhs.status, rhs.pdu, rhs.smsc);
  }
  inline bool operator<=(const SmsWriteArgs& rhs) const {
    return std::tie(status, pdu, smsc) <= std::tie(rhs.status, rhs.pdu, rhs.smsc);
  }
  inline bool operator==(const SmsWriteArgs& rhs) const {
    return std::tie(status, pdu, smsc) == std::tie(rhs.status, rhs.pdu, rhs.smsc);
  }
  inline bool operator>(const SmsWriteArgs& rhs) const {
    return std::tie(status, pdu, smsc) > std::tie(rhs.status, rhs.pdu, rhs.smsc);
  }
  inline bool operator>=(const SmsWriteArgs& rhs) const {
    return std::tie(status, pdu, smsc) >= std::tie(rhs.status, rhs.pdu, rhs.smsc);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { STATUS_REC_UNREAD = 0 };
  enum : int32_t { STATUS_REC_READ = 1 };
  enum : int32_t { STATUS_STO_UNSENT = 2 };
  enum : int32_t { STATUS_STO_SENT = 3 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SmsWriteArgs{";
    os << "status: " << ::android::internal::ToString(status);
    os << ", pdu: " << ::android::internal::ToString(pdu);
    os << ", smsc: " << ::android::internal::ToString(smsc);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
