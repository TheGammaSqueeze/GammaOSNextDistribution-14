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
#include <aidl/android/hardware/light/LightType.h>
namespace aidl {
namespace android {
namespace hardware {
namespace light {
class HwLight {
public:
  static const char* descriptor;

  int32_t id = {};
  int32_t ordinal = {};
  ::aidl::android::hardware::light::LightType type = ::aidl::android::hardware::light::LightType(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace light
}  // namespace hardware
}  // namespace android
}  // namespace aidl
