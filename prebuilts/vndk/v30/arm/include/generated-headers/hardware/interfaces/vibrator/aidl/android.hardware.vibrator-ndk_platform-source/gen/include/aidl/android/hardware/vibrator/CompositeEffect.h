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
#include <aidl/android/hardware/vibrator/CompositePrimitive.h>
namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class CompositeEffect {
public:
  static const char* descriptor;

  int32_t delayMs = {};
  ::aidl::android::hardware::vibrator::CompositePrimitive primitive = ::aidl::android::hardware::vibrator::CompositePrimitive(0);
  float scale = {};

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
