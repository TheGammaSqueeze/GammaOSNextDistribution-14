#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class CorrelationVector {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  double frequencyOffsetMps = 0.000000;
  double samplingWidthM = 0.000000;
  double samplingStartM = 0.000000;
  std::vector<int32_t> magnitude;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CorrelationVector& rhs) const {
    return std::tie(frequencyOffsetMps, samplingWidthM, samplingStartM, magnitude) != std::tie(rhs.frequencyOffsetMps, rhs.samplingWidthM, rhs.samplingStartM, rhs.magnitude);
  }
  inline bool operator<(const CorrelationVector& rhs) const {
    return std::tie(frequencyOffsetMps, samplingWidthM, samplingStartM, magnitude) < std::tie(rhs.frequencyOffsetMps, rhs.samplingWidthM, rhs.samplingStartM, rhs.magnitude);
  }
  inline bool operator<=(const CorrelationVector& rhs) const {
    return std::tie(frequencyOffsetMps, samplingWidthM, samplingStartM, magnitude) <= std::tie(rhs.frequencyOffsetMps, rhs.samplingWidthM, rhs.samplingStartM, rhs.magnitude);
  }
  inline bool operator==(const CorrelationVector& rhs) const {
    return std::tie(frequencyOffsetMps, samplingWidthM, samplingStartM, magnitude) == std::tie(rhs.frequencyOffsetMps, rhs.samplingWidthM, rhs.samplingStartM, rhs.magnitude);
  }
  inline bool operator>(const CorrelationVector& rhs) const {
    return std::tie(frequencyOffsetMps, samplingWidthM, samplingStartM, magnitude) > std::tie(rhs.frequencyOffsetMps, rhs.samplingWidthM, rhs.samplingStartM, rhs.magnitude);
  }
  inline bool operator>=(const CorrelationVector& rhs) const {
    return std::tie(frequencyOffsetMps, samplingWidthM, samplingStartM, magnitude) >= std::tie(rhs.frequencyOffsetMps, rhs.samplingWidthM, rhs.samplingStartM, rhs.magnitude);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CorrelationVector{";
    os << "frequencyOffsetMps: " << ::android::internal::ToString(frequencyOffsetMps);
    os << ", samplingWidthM: " << ::android::internal::ToString(samplingWidthM);
    os << ", samplingStartM: " << ::android::internal::ToString(samplingStartM);
    os << ", magnitude: " << ::android::internal::ToString(magnitude);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
