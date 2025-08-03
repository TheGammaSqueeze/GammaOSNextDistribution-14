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
namespace sim {
class SimApdu {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t sessionId = 0;
  int32_t cla = 0;
  int32_t instruction = 0;
  int32_t p1 = 0;
  int32_t p2 = 0;
  int32_t p3 = 0;
  std::string data;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SimApdu& rhs) const {
    return std::tie(sessionId, cla, instruction, p1, p2, p3, data) != std::tie(rhs.sessionId, rhs.cla, rhs.instruction, rhs.p1, rhs.p2, rhs.p3, rhs.data);
  }
  inline bool operator<(const SimApdu& rhs) const {
    return std::tie(sessionId, cla, instruction, p1, p2, p3, data) < std::tie(rhs.sessionId, rhs.cla, rhs.instruction, rhs.p1, rhs.p2, rhs.p3, rhs.data);
  }
  inline bool operator<=(const SimApdu& rhs) const {
    return std::tie(sessionId, cla, instruction, p1, p2, p3, data) <= std::tie(rhs.sessionId, rhs.cla, rhs.instruction, rhs.p1, rhs.p2, rhs.p3, rhs.data);
  }
  inline bool operator==(const SimApdu& rhs) const {
    return std::tie(sessionId, cla, instruction, p1, p2, p3, data) == std::tie(rhs.sessionId, rhs.cla, rhs.instruction, rhs.p1, rhs.p2, rhs.p3, rhs.data);
  }
  inline bool operator>(const SimApdu& rhs) const {
    return std::tie(sessionId, cla, instruction, p1, p2, p3, data) > std::tie(rhs.sessionId, rhs.cla, rhs.instruction, rhs.p1, rhs.p2, rhs.p3, rhs.data);
  }
  inline bool operator>=(const SimApdu& rhs) const {
    return std::tie(sessionId, cla, instruction, p1, p2, p3, data) >= std::tie(rhs.sessionId, rhs.cla, rhs.instruction, rhs.p1, rhs.p2, rhs.p3, rhs.data);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SimApdu{";
    os << "sessionId: " << ::android::internal::ToString(sessionId);
    os << ", cla: " << ::android::internal::ToString(cla);
    os << ", instruction: " << ::android::internal::ToString(instruction);
    os << ", p1: " << ::android::internal::ToString(p1);
    os << ", p2: " << ::android::internal::ToString(p2);
    os << ", p3: " << ::android::internal::ToString(p3);
    os << ", data: " << ::android::internal::ToString(data);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
