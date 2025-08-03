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
namespace bluetooth {
namespace audio {
class AptxSinkBuffering {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t minLowLatency = 0;
  int8_t maxLowLatency = 0;
  int8_t minHighQuality = 0;
  int8_t maxHighQuality = 0;
  int8_t minTws = 0;
  int8_t maxTws = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AptxSinkBuffering& rhs) const {
    return std::tie(minLowLatency, maxLowLatency, minHighQuality, maxHighQuality, minTws, maxTws) != std::tie(rhs.minLowLatency, rhs.maxLowLatency, rhs.minHighQuality, rhs.maxHighQuality, rhs.minTws, rhs.maxTws);
  }
  inline bool operator<(const AptxSinkBuffering& rhs) const {
    return std::tie(minLowLatency, maxLowLatency, minHighQuality, maxHighQuality, minTws, maxTws) < std::tie(rhs.minLowLatency, rhs.maxLowLatency, rhs.minHighQuality, rhs.maxHighQuality, rhs.minTws, rhs.maxTws);
  }
  inline bool operator<=(const AptxSinkBuffering& rhs) const {
    return std::tie(minLowLatency, maxLowLatency, minHighQuality, maxHighQuality, minTws, maxTws) <= std::tie(rhs.minLowLatency, rhs.maxLowLatency, rhs.minHighQuality, rhs.maxHighQuality, rhs.minTws, rhs.maxTws);
  }
  inline bool operator==(const AptxSinkBuffering& rhs) const {
    return std::tie(minLowLatency, maxLowLatency, minHighQuality, maxHighQuality, minTws, maxTws) == std::tie(rhs.minLowLatency, rhs.maxLowLatency, rhs.minHighQuality, rhs.maxHighQuality, rhs.minTws, rhs.maxTws);
  }
  inline bool operator>(const AptxSinkBuffering& rhs) const {
    return std::tie(minLowLatency, maxLowLatency, minHighQuality, maxHighQuality, minTws, maxTws) > std::tie(rhs.minLowLatency, rhs.maxLowLatency, rhs.minHighQuality, rhs.maxHighQuality, rhs.minTws, rhs.maxTws);
  }
  inline bool operator>=(const AptxSinkBuffering& rhs) const {
    return std::tie(minLowLatency, maxLowLatency, minHighQuality, maxHighQuality, minTws, maxTws) >= std::tie(rhs.minLowLatency, rhs.maxLowLatency, rhs.minHighQuality, rhs.maxHighQuality, rhs.minTws, rhs.maxTws);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AptxSinkBuffering{";
    os << "minLowLatency: " << ::android::internal::ToString(minLowLatency);
    os << ", maxLowLatency: " << ::android::internal::ToString(maxLowLatency);
    os << ", minHighQuality: " << ::android::internal::ToString(minHighQuality);
    os << ", maxHighQuality: " << ::android::internal::ToString(maxHighQuality);
    os << ", minTws: " << ::android::internal::ToString(minTws);
    os << ", maxTws: " << ::android::internal::ToString(maxTws);
    os << "}";
    return os.str();
  }
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
