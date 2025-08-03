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
class CdmaSmsAck {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool errorClass = false;
  int32_t smsCauseCode = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaSmsAck& rhs) const {
    return std::tie(errorClass, smsCauseCode) != std::tie(rhs.errorClass, rhs.smsCauseCode);
  }
  inline bool operator<(const CdmaSmsAck& rhs) const {
    return std::tie(errorClass, smsCauseCode) < std::tie(rhs.errorClass, rhs.smsCauseCode);
  }
  inline bool operator<=(const CdmaSmsAck& rhs) const {
    return std::tie(errorClass, smsCauseCode) <= std::tie(rhs.errorClass, rhs.smsCauseCode);
  }
  inline bool operator==(const CdmaSmsAck& rhs) const {
    return std::tie(errorClass, smsCauseCode) == std::tie(rhs.errorClass, rhs.smsCauseCode);
  }
  inline bool operator>(const CdmaSmsAck& rhs) const {
    return std::tie(errorClass, smsCauseCode) > std::tie(rhs.errorClass, rhs.smsCauseCode);
  }
  inline bool operator>=(const CdmaSmsAck& rhs) const {
    return std::tie(errorClass, smsCauseCode) >= std::tie(rhs.errorClass, rhs.smsCauseCode);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaSmsAck{";
    os << "errorClass: " << ::android::internal::ToString(errorClass);
    os << ", smsCauseCode: " << ::android::internal::ToString(smsCauseCode);
    os << "}";
    return os.str();
  }
};
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
