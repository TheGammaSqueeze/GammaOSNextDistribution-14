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
namespace vibrator {
class ActivePwle {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  float startAmplitude = 0.000000f;
  float startFrequency = 0.000000f;
  float endAmplitude = 0.000000f;
  float endFrequency = 0.000000f;
  int32_t duration = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ActivePwle& rhs) const {
    return std::tie(startAmplitude, startFrequency, endAmplitude, endFrequency, duration) != std::tie(rhs.startAmplitude, rhs.startFrequency, rhs.endAmplitude, rhs.endFrequency, rhs.duration);
  }
  inline bool operator<(const ActivePwle& rhs) const {
    return std::tie(startAmplitude, startFrequency, endAmplitude, endFrequency, duration) < std::tie(rhs.startAmplitude, rhs.startFrequency, rhs.endAmplitude, rhs.endFrequency, rhs.duration);
  }
  inline bool operator<=(const ActivePwle& rhs) const {
    return std::tie(startAmplitude, startFrequency, endAmplitude, endFrequency, duration) <= std::tie(rhs.startAmplitude, rhs.startFrequency, rhs.endAmplitude, rhs.endFrequency, rhs.duration);
  }
  inline bool operator==(const ActivePwle& rhs) const {
    return std::tie(startAmplitude, startFrequency, endAmplitude, endFrequency, duration) == std::tie(rhs.startAmplitude, rhs.startFrequency, rhs.endAmplitude, rhs.endFrequency, rhs.duration);
  }
  inline bool operator>(const ActivePwle& rhs) const {
    return std::tie(startAmplitude, startFrequency, endAmplitude, endFrequency, duration) > std::tie(rhs.startAmplitude, rhs.startFrequency, rhs.endAmplitude, rhs.endFrequency, rhs.duration);
  }
  inline bool operator>=(const ActivePwle& rhs) const {
    return std::tie(startAmplitude, startFrequency, endAmplitude, endFrequency, duration) >= std::tie(rhs.startAmplitude, rhs.startFrequency, rhs.endAmplitude, rhs.endFrequency, rhs.duration);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ActivePwle{";
    os << "startAmplitude: " << ::android::internal::ToString(startAmplitude);
    os << ", startFrequency: " << ::android::internal::ToString(startFrequency);
    os << ", endAmplitude: " << ::android::internal::ToString(endAmplitude);
    os << ", endFrequency: " << ::android::internal::ToString(endFrequency);
    os << ", duration: " << ::android::internal::ToString(duration);
    os << "}";
    return os.str();
  }
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
