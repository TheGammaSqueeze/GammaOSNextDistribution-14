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
class SimRefreshResult {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t type = 0;
  int32_t efId = 0;
  std::string aid;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SimRefreshResult& rhs) const {
    return std::tie(type, efId, aid) != std::tie(rhs.type, rhs.efId, rhs.aid);
  }
  inline bool operator<(const SimRefreshResult& rhs) const {
    return std::tie(type, efId, aid) < std::tie(rhs.type, rhs.efId, rhs.aid);
  }
  inline bool operator<=(const SimRefreshResult& rhs) const {
    return std::tie(type, efId, aid) <= std::tie(rhs.type, rhs.efId, rhs.aid);
  }
  inline bool operator==(const SimRefreshResult& rhs) const {
    return std::tie(type, efId, aid) == std::tie(rhs.type, rhs.efId, rhs.aid);
  }
  inline bool operator>(const SimRefreshResult& rhs) const {
    return std::tie(type, efId, aid) > std::tie(rhs.type, rhs.efId, rhs.aid);
  }
  inline bool operator>=(const SimRefreshResult& rhs) const {
    return std::tie(type, efId, aid) >= std::tie(rhs.type, rhs.efId, rhs.aid);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { TYPE_SIM_FILE_UPDATE = 0 };
  enum : int32_t { TYPE_SIM_INIT = 1 };
  enum : int32_t { TYPE_SIM_RESET = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SimRefreshResult{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", efId: " << ::android::internal::ToString(efId);
    os << ", aid: " << ::android::internal::ToString(aid);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
