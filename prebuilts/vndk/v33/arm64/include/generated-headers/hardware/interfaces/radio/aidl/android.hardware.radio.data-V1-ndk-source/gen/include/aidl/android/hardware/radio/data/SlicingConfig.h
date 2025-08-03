#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/data/SliceInfo.h>
#include <aidl/android/hardware/radio/data/UrspRule.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class SlicingConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::radio::data::UrspRule> urspRules;
  std::vector<::aidl::android::hardware::radio::data::SliceInfo> sliceInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SlicingConfig& rhs) const {
    return std::tie(urspRules, sliceInfo) != std::tie(rhs.urspRules, rhs.sliceInfo);
  }
  inline bool operator<(const SlicingConfig& rhs) const {
    return std::tie(urspRules, sliceInfo) < std::tie(rhs.urspRules, rhs.sliceInfo);
  }
  inline bool operator<=(const SlicingConfig& rhs) const {
    return std::tie(urspRules, sliceInfo) <= std::tie(rhs.urspRules, rhs.sliceInfo);
  }
  inline bool operator==(const SlicingConfig& rhs) const {
    return std::tie(urspRules, sliceInfo) == std::tie(rhs.urspRules, rhs.sliceInfo);
  }
  inline bool operator>(const SlicingConfig& rhs) const {
    return std::tie(urspRules, sliceInfo) > std::tie(rhs.urspRules, rhs.sliceInfo);
  }
  inline bool operator>=(const SlicingConfig& rhs) const {
    return std::tie(urspRules, sliceInfo) >= std::tie(rhs.urspRules, rhs.sliceInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SlicingConfig{";
    os << "urspRules: " << ::android::internal::ToString(urspRules);
    os << ", sliceInfo: " << ::android::internal::ToString(sliceInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
