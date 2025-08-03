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
class SsInfoData {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<int32_t> ssInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SsInfoData& rhs) const {
    return std::tie(ssInfo) != std::tie(rhs.ssInfo);
  }
  inline bool operator<(const SsInfoData& rhs) const {
    return std::tie(ssInfo) < std::tie(rhs.ssInfo);
  }
  inline bool operator<=(const SsInfoData& rhs) const {
    return std::tie(ssInfo) <= std::tie(rhs.ssInfo);
  }
  inline bool operator==(const SsInfoData& rhs) const {
    return std::tie(ssInfo) == std::tie(rhs.ssInfo);
  }
  inline bool operator>(const SsInfoData& rhs) const {
    return std::tie(ssInfo) > std::tie(rhs.ssInfo);
  }
  inline bool operator>=(const SsInfoData& rhs) const {
    return std::tie(ssInfo) >= std::tie(rhs.ssInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { SS_INFO_MAX = 4 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SsInfoData{";
    os << "ssInfo: " << ::android::internal::ToString(ssInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
