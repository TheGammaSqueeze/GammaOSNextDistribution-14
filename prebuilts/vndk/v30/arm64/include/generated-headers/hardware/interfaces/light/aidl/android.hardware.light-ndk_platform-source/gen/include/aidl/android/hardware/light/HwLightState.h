#pragma once
#include <android/binder_interface_utils.h>

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
  static const char* descriptor;

  int32_t color = {};
  ::aidl::android::hardware::light::FlashMode flashMode = ::aidl::android::hardware::light::FlashMode(0);
  int32_t flashOnMs = {};
  int32_t flashOffMs = {};
  ::aidl::android::hardware::light::BrightnessMode brightnessMode = ::aidl::android::hardware::light::BrightnessMode(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace light
}  // namespace hardware
}  // namespace android
}  // namespace aidl
