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
#include <aidl/android/hardware/graphics/common/PlaneLayoutComponent.h>
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
class PlaneLayout {
public:
  static const char* descriptor;

  std::vector<::aidl::android::hardware::graphics::common::PlaneLayoutComponent> components;
  int64_t offsetInBytes = {};
  int64_t sampleIncrementInBits = {};
  int64_t strideInBytes = {};
  int64_t widthInSamples = {};
  int64_t heightInSamples = {};
  int64_t totalSizeInBytes = {};
  int64_t horizontalSubsampling = {};
  int64_t verticalSubsampling = {};

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
