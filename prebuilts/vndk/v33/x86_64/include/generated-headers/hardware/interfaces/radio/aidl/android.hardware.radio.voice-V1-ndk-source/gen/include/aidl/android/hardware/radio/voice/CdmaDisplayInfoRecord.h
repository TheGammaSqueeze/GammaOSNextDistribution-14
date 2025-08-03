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
class CdmaDisplayInfoRecord {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string alphaBuf;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaDisplayInfoRecord& rhs) const {
    return std::tie(alphaBuf) != std::tie(rhs.alphaBuf);
  }
  inline bool operator<(const CdmaDisplayInfoRecord& rhs) const {
    return std::tie(alphaBuf) < std::tie(rhs.alphaBuf);
  }
  inline bool operator<=(const CdmaDisplayInfoRecord& rhs) const {
    return std::tie(alphaBuf) <= std::tie(rhs.alphaBuf);
  }
  inline bool operator==(const CdmaDisplayInfoRecord& rhs) const {
    return std::tie(alphaBuf) == std::tie(rhs.alphaBuf);
  }
  inline bool operator>(const CdmaDisplayInfoRecord& rhs) const {
    return std::tie(alphaBuf) > std::tie(rhs.alphaBuf);
  }
  inline bool operator>=(const CdmaDisplayInfoRecord& rhs) const {
    return std::tie(alphaBuf) >= std::tie(rhs.alphaBuf);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { CDMA_ALPHA_INFO_BUFFER_LENGTH = 64 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaDisplayInfoRecord{";
    os << "alphaBuf: " << ::android::internal::ToString(alphaBuf);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
