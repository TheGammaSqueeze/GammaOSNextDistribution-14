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
namespace modem {
class RadioCapability {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t session = 0;
  int32_t phase = 0;
  int32_t raf = 0;
  std::string logicalModemUuid;
  int32_t status = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RadioCapability& rhs) const {
    return std::tie(session, phase, raf, logicalModemUuid, status) != std::tie(rhs.session, rhs.phase, rhs.raf, rhs.logicalModemUuid, rhs.status);
  }
  inline bool operator<(const RadioCapability& rhs) const {
    return std::tie(session, phase, raf, logicalModemUuid, status) < std::tie(rhs.session, rhs.phase, rhs.raf, rhs.logicalModemUuid, rhs.status);
  }
  inline bool operator<=(const RadioCapability& rhs) const {
    return std::tie(session, phase, raf, logicalModemUuid, status) <= std::tie(rhs.session, rhs.phase, rhs.raf, rhs.logicalModemUuid, rhs.status);
  }
  inline bool operator==(const RadioCapability& rhs) const {
    return std::tie(session, phase, raf, logicalModemUuid, status) == std::tie(rhs.session, rhs.phase, rhs.raf, rhs.logicalModemUuid, rhs.status);
  }
  inline bool operator>(const RadioCapability& rhs) const {
    return std::tie(session, phase, raf, logicalModemUuid, status) > std::tie(rhs.session, rhs.phase, rhs.raf, rhs.logicalModemUuid, rhs.status);
  }
  inline bool operator>=(const RadioCapability& rhs) const {
    return std::tie(session, phase, raf, logicalModemUuid, status) >= std::tie(rhs.session, rhs.phase, rhs.raf, rhs.logicalModemUuid, rhs.status);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { PHASE_CONFIGURED = 0 };
  enum : int32_t { PHASE_START = 1 };
  enum : int32_t { PHASE_APPLY = 2 };
  enum : int32_t { PHASE_UNSOL_RSP = 3 };
  enum : int32_t { PHASE_FINISH = 4 };
  enum : int32_t { STATUS_NONE = 0 };
  enum : int32_t { STATUS_SUCCESS = 1 };
  enum : int32_t { STATUS_FAIL = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "RadioCapability{";
    os << "session: " << ::android::internal::ToString(session);
    os << ", phase: " << ::android::internal::ToString(phase);
    os << ", raf: " << ::android::internal::ToString(raf);
    os << ", logicalModemUuid: " << ::android::internal::ToString(logicalModemUuid);
    os << ", status: " << ::android::internal::ToString(status);
    os << "}";
    return os.str();
  }
};
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
