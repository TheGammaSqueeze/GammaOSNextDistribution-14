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
namespace ir {
class ConsumerIrFreqRange {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t minHz = 0;
  int32_t maxHz = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ConsumerIrFreqRange& rhs) const {
    return std::tie(minHz, maxHz) != std::tie(rhs.minHz, rhs.maxHz);
  }
  inline bool operator<(const ConsumerIrFreqRange& rhs) const {
    return std::tie(minHz, maxHz) < std::tie(rhs.minHz, rhs.maxHz);
  }
  inline bool operator<=(const ConsumerIrFreqRange& rhs) const {
    return std::tie(minHz, maxHz) <= std::tie(rhs.minHz, rhs.maxHz);
  }
  inline bool operator==(const ConsumerIrFreqRange& rhs) const {
    return std::tie(minHz, maxHz) == std::tie(rhs.minHz, rhs.maxHz);
  }
  inline bool operator>(const ConsumerIrFreqRange& rhs) const {
    return std::tie(minHz, maxHz) > std::tie(rhs.minHz, rhs.maxHz);
  }
  inline bool operator>=(const ConsumerIrFreqRange& rhs) const {
    return std::tie(minHz, maxHz) >= std::tie(rhs.minHz, rhs.maxHz);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ConsumerIrFreqRange{";
    os << "minHz: " << ::android::internal::ToString(minHz);
    os << ", maxHz: " << ::android::internal::ToString(maxHz);
    os << "}";
    return os.str();
  }
};
}  // namespace ir
}  // namespace hardware
}  // namespace android
}  // namespace aidl
