#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/messaging/CdmaSmsMessage.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
class CdmaSmsWriteArgs {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t status = 0;
  ::aidl::android::hardware::radio::messaging::CdmaSmsMessage message;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaSmsWriteArgs& rhs) const {
    return std::tie(status, message) != std::tie(rhs.status, rhs.message);
  }
  inline bool operator<(const CdmaSmsWriteArgs& rhs) const {
    return std::tie(status, message) < std::tie(rhs.status, rhs.message);
  }
  inline bool operator<=(const CdmaSmsWriteArgs& rhs) const {
    return std::tie(status, message) <= std::tie(rhs.status, rhs.message);
  }
  inline bool operator==(const CdmaSmsWriteArgs& rhs) const {
    return std::tie(status, message) == std::tie(rhs.status, rhs.message);
  }
  inline bool operator>(const CdmaSmsWriteArgs& rhs) const {
    return std::tie(status, message) > std::tie(rhs.status, rhs.message);
  }
  inline bool operator>=(const CdmaSmsWriteArgs& rhs) const {
    return std::tie(status, message) >= std::tie(rhs.status, rhs.message);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { STATUS_REC_UNREAD = 0 };
  enum : int32_t { STATUS_REC_READ = 1 };
  enum : int32_t { STATUS_STO_UNSENT = 2 };
  enum : int32_t { STATUS_STO_SENT = 3 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaSmsWriteArgs{";
    os << "status: " << ::android::internal::ToString(status);
    os << ", message: " << ::android::internal::ToString(message);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
