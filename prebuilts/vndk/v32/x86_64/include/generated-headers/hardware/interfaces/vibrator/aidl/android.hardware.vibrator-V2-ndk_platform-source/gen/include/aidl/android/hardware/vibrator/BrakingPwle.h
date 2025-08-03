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
#include <aidl/android/hardware/vibrator/Braking.h>
namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class BrakingPwle {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::vibrator::Braking braking = ::aidl::android::hardware::vibrator::Braking(0);
  int32_t duration = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const BrakingPwle& rhs) const {
    return std::tie(braking, duration) != std::tie(rhs.braking, rhs.duration);
  }
  inline bool operator<(const BrakingPwle& rhs) const {
    return std::tie(braking, duration) < std::tie(rhs.braking, rhs.duration);
  }
  inline bool operator<=(const BrakingPwle& rhs) const {
    return std::tie(braking, duration) <= std::tie(rhs.braking, rhs.duration);
  }
  inline bool operator==(const BrakingPwle& rhs) const {
    return std::tie(braking, duration) == std::tie(rhs.braking, rhs.duration);
  }
  inline bool operator>(const BrakingPwle& rhs) const {
    return std::tie(braking, duration) > std::tie(rhs.braking, rhs.duration);
  }
  inline bool operator>=(const BrakingPwle& rhs) const {
    return std::tie(braking, duration) >= std::tie(rhs.braking, rhs.duration);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "BrakingPwle{";
    os << "braking: " << ::android::internal::ToString(braking);
    os << ", duration: " << ::android::internal::ToString(duration);
    os << "}";
    return os.str();
  }
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
