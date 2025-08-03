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
namespace voice {
class CdmaT53ClirInfoRecord {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t cause = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaT53ClirInfoRecord& rhs) const {
    return std::tie(cause) != std::tie(rhs.cause);
  }
  inline bool operator<(const CdmaT53ClirInfoRecord& rhs) const {
    return std::tie(cause) < std::tie(rhs.cause);
  }
  inline bool operator<=(const CdmaT53ClirInfoRecord& rhs) const {
    return std::tie(cause) <= std::tie(rhs.cause);
  }
  inline bool operator==(const CdmaT53ClirInfoRecord& rhs) const {
    return std::tie(cause) == std::tie(rhs.cause);
  }
  inline bool operator>(const CdmaT53ClirInfoRecord& rhs) const {
    return std::tie(cause) > std::tie(rhs.cause);
  }
  inline bool operator>=(const CdmaT53ClirInfoRecord& rhs) const {
    return std::tie(cause) >= std::tie(rhs.cause);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaT53ClirInfoRecord{";
    os << "cause: " << ::android::internal::ToString(cause);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
