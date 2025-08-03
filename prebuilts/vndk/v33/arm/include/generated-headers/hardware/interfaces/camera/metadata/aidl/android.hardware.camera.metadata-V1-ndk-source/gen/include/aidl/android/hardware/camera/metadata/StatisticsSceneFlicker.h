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
enum class StatisticsSceneFlicker : int32_t {
  ANDROID_STATISTICS_SCENE_FLICKER_NONE = 0,
  ANDROID_STATISTICS_SCENE_FLICKER_50HZ = 1,
  ANDROID_STATISTICS_SCENE_FLICKER_60HZ = 2,
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
[[nodiscard]] static inline std::string toString(StatisticsSceneFlicker val) {
  switch(val) {
  case StatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_NONE:
    return "ANDROID_STATISTICS_SCENE_FLICKER_NONE";
  case StatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_50HZ:
    return "ANDROID_STATISTICS_SCENE_FLICKER_50HZ";
  case StatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_60HZ:
    return "ANDROID_STATISTICS_SCENE_FLICKER_60HZ";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::StatisticsSceneFlicker, 3> enum_values<aidl::android::hardware::camera::metadata::StatisticsSceneFlicker> = {
  aidl::android::hardware::camera::metadata::StatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_NONE,
  aidl::android::hardware::camera::metadata::StatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_50HZ,
  aidl::android::hardware::camera::metadata::StatisticsSceneFlicker::ANDROID_STATISTICS_SCENE_FLICKER_60HZ,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
