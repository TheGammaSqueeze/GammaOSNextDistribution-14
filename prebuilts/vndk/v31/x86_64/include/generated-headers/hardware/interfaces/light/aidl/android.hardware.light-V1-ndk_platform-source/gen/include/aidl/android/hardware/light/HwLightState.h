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
#include <aidl/android/hardware/light/BrightnessMode.h>
#include <aidl/android/hardware/light/FlashMode.h>
namespace aidl {
namespace android {
namespace hardware {
namespace light {
class HwLightState {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t color = 0;
  ::aidl::android::hardware::light::FlashMode flashMode = ::aidl::android::hardware::light::FlashMode(0);
  int32_t flashOnMs = 0;
  int32_t flashOffMs = 0;
  ::aidl::android::hardware::light::BrightnessMode brightnessMode = ::aidl::android::hardware::light::BrightnessMode(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HwLightState& rhs) const {
    return std::tie(color, flashMode, flashOnMs, flashOffMs, brightnessMode) != std::tie(rhs.color, rhs.flashMode, rhs.flashOnMs, rhs.flashOffMs, rhs.brightnessMode);
  }
  inline bool operator<(const HwLightState& rhs) const {
    return std::tie(color, flashMode, flashOnMs, flashOffMs, brightnessMode) < std::tie(rhs.color, rhs.flashMode, rhs.flashOnMs, rhs.flashOffMs, rhs.brightnessMode);
  }
  inline bool operator<=(const HwLightState& rhs) const {
    return std::tie(color, flashMode, flashOnMs, flashOffMs, brightnessMode) <= std::tie(rhs.color, rhs.flashMode, rhs.flashOnMs, rhs.flashOffMs, rhs.brightnessMode);
  }
  inline bool operator==(const HwLightState& rhs) const {
    return std::tie(color, flashMode, flashOnMs, flashOffMs, brightnessMode) == std::tie(rhs.color, rhs.flashMode, rhs.flashOnMs, rhs.flashOffMs, rhs.brightnessMode);
  }
  inline bool operator>(const HwLightState& rhs) const {
    return std::tie(color, flashMode, flashOnMs, flashOffMs, brightnessMode) > std::tie(rhs.color, rhs.flashMode, rhs.flashOnMs, rhs.flashOffMs, rhs.brightnessMode);
  }
  inline bool operator>=(const HwLightState& rhs) const {
    return std::tie(color, flashMode, flashOnMs, flashOffMs, brightnessMode) >= std::tie(rhs.color, rhs.flashMode, rhs.flashOnMs, rhs.flashOffMs, rhs.brightnessMode);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HwLightState{";
    os << "color: " << ::android::internal::ToString(color);
    os << ", flashMode: " << ::android::internal::ToString(flashMode);
    os << ", flashOnMs: " << ::android::internal::ToString(flashOnMs);
    os << ", flashOffMs: " << ::android::internal::ToString(flashOffMs);
    os << ", brightnessMode: " << ::android::internal::ToString(brightnessMode);
    os << "}";
    return os.str();
  }
};
}  // namespace light
}  // namespace hardware
}  // namespace android
}  // namespace aidl
