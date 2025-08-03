#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace metadata {
enum class DemosaicMode : int32_t {
  ANDROID_DEMOSAIC_MODE_FAST = 0,
  ANDROID_DEMOSAIC_MODE_HIGH_QUALITY = 1,
};

}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace metadata {
[[nodiscard]] static inline std::string toString(DemosaicMode val) {
  switch(val) {
  case DemosaicMode::ANDROID_DEMOSAIC_MODE_FAST:
    return "ANDROID_DEMOSAIC_MODE_FAST";
  case DemosaicMode::ANDROID_DEMOSAIC_MODE_HIGH_QUALITY:
    return "ANDROID_DEMOSAIC_MODE_HIGH_QUALITY";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::metadata::DemosaicMode, 2> enum_values<aidl::android::hardware::camera::metadata::DemosaicMode> = {
  aidl::android::hardware::camera::metadata::DemosaicMode::ANDROID_DEMOSAIC_MODE_FAST,
  aidl::android::hardware::camera::metadata::DemosaicMode::ANDROID_DEMOSAIC_MODE_HIGH_QUALITY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
