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
enum class DepthAvailableDepthStreamConfigurationsMaximumResolution : int32_t {
  ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION_OUTPUT = 0,
  ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION_INPUT = 1,
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
[[nodiscard]] static inline std::string toString(DepthAvailableDepthStreamConfigurationsMaximumResolution val) {
  switch(val) {
  case DepthAvailableDepthStreamConfigurationsMaximumResolution::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION_OUTPUT:
    return "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION_OUTPUT";
  case DepthAvailableDepthStreamConfigurationsMaximumResolution::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION_INPUT:
    return "ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION_INPUT";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::DepthAvailableDepthStreamConfigurationsMaximumResolution, 2> enum_values<aidl::android::hardware::camera::metadata::DepthAvailableDepthStreamConfigurationsMaximumResolution> = {
  aidl::android::hardware::camera::metadata::DepthAvailableDepthStreamConfigurationsMaximumResolution::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION_OUTPUT,
  aidl::android::hardware::camera::metadata::DepthAvailableDepthStreamConfigurationsMaximumResolution::ANDROID_DEPTH_AVAILABLE_DEPTH_STREAM_CONFIGURATIONS_MAXIMUM_RESOLUTION_INPUT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
