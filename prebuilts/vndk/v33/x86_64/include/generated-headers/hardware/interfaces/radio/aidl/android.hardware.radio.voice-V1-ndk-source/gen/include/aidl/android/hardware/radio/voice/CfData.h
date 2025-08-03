#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/voice/CallForwardInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class CfData {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::radio::voice::CallForwardInfo> cfInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CfData& rhs) const {
    return std::tie(cfInfo) != std::tie(rhs.cfInfo);
  }
  inline bool operator<(const CfData& rhs) const {
    return std::tie(cfInfo) < std::tie(rhs.cfInfo);
  }
  inline bool operator<=(const CfData& rhs) const {
    return std::tie(cfInfo) <= std::tie(rhs.cfInfo);
  }
  inline bool operator==(const CfData& rhs) const {
    return std::tie(cfInfo) == std::tie(rhs.cfInfo);
  }
  inline bool operator>(const CfData& rhs) const {
    return std::tie(cfInfo) > std::tie(rhs.cfInfo);
  }
  inline bool operator>=(const CfData& rhs) const {
    return std::tie(cfInfo) >= std::tie(rhs.cfInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { NUM_SERVICE_CLASSES = 7 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "CfData{";
    os << "cfInfo: " << ::android::internal::ToString(cfInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
