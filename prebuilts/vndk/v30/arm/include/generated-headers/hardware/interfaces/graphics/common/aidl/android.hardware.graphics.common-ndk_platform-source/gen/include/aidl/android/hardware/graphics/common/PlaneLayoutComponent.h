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
#include <aidl/android/hardware/graphics/common/ExtendableType.h>
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
class PlaneLayoutComponent {
public:
  static const char* descriptor;

  ::aidl::android::hardware::graphics::common::ExtendableType type;
  int64_t offsetInBits = {};
  int64_t sizeInBits = {};

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
