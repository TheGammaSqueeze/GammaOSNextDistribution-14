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
class AptxAdaptiveTimeToPlay {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t lowLowLatency = 0;
  int8_t highLowLatency = 0;
  int8_t lowHighQuality = 0;
  int8_t highHighQuality = 0;
  int8_t lowTws = 0;
  int8_t highTws = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AptxAdaptiveTimeToPlay& rhs) const {
    return std::tie(lowLowLatency, highLowLatency, lowHighQuality, highHighQuality, lowTws, highTws) != std::tie(rhs.lowLowLatency, rhs.highLowLatency, rhs.lowHighQuality, rhs.highHighQuality, rhs.lowTws, rhs.highTws);
  }
  inline bool operator<(const AptxAdaptiveTimeToPlay& rhs) const {
    return std::tie(lowLowLatency, highLowLatency, lowHighQuality, highHighQuality, lowTws, highTws) < std::tie(rhs.lowLowLatency, rhs.highLowLatency, rhs.lowHighQuality, rhs.highHighQuality, rhs.lowTws, rhs.highTws);
  }
  inline bool operator<=(const AptxAdaptiveTimeToPlay& rhs) const {
    return std::tie(lowLowLatency, highLowLatency, lowHighQuality, highHighQuality, lowTws, highTws) <= std::tie(rhs.lowLowLatency, rhs.highLowLatency, rhs.lowHighQuality, rhs.highHighQuality, rhs.lowTws, rhs.highTws);
  }
  inline bool operator==(const AptxAdaptiveTimeToPlay& rhs) const {
    return std::tie(lowLowLatency, highLowLatency, lowHighQuality, highHighQuality, lowTws, highTws) == std::tie(rhs.lowLowLatency, rhs.highLowLatency, rhs.lowHighQuality, rhs.highHighQuality, rhs.lowTws, rhs.highTws);
  }
  inline bool operator>(const AptxAdaptiveTimeToPlay& rhs) const {
    return std::tie(lowLowLatency, highLowLatency, lowHighQuality, highHighQuality, lowTws, highTws) > std::tie(rhs.lowLowLatency, rhs.highLowLatency, rhs.lowHighQuality, rhs.highHighQuality, rhs.lowTws, rhs.highTws);
  }
  inline bool operator>=(const AptxAdaptiveTimeToPlay& rhs) const {
    return std::tie(lowLowLatency, highLowLatency, lowHighQuality, highHighQuality, lowTws, highTws) >= std::tie(rhs.lowLowLatency, rhs.highLowLatency, rhs.lowHighQuality, rhs.highHighQuality, rhs.lowTws, rhs.highTws);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AptxAdaptiveTimeToPlay{";
    os << "lowLowLatency: " << ::android::internal::ToString(lowLowLatency);
    os << ", highLowLatency: " << ::android::internal::ToString(highLowLatency);
    os << ", lowHighQuality: " << ::android::internal::ToString(lowHighQuality);
    os << ", highHighQuality: " << ::android::internal::ToString(highHighQuality);
    os << ", lowTws: " << ::android::internal::ToString(lowTws);
    os << ", highTws: " << ::android::internal::ToString(highTws);
    os << "}";
    return os.str();
  }
};
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
