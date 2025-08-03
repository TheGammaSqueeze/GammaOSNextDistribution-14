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
enum class StatisticsFaceDetectMode : int32_t {
  ANDROID_STATISTICS_FACE_DETECT_MODE_OFF = 0,
  ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE = 1,
  ANDROID_STATISTICS_FACE_DETECT_MODE_FULL = 2,
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
[[nodiscard]] static inline std::string toString(StatisticsFaceDetectMode val) {
  switch(val) {
  case StatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_OFF:
    return "ANDROID_STATISTICS_FACE_DETECT_MODE_OFF";
  case StatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE:
    return "ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE";
  case StatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_FULL:
    return "ANDROID_STATISTICS_FACE_DETECT_MODE_FULL";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::StatisticsFaceDetectMode, 3> enum_values<aidl::android::hardware::camera::metadata::StatisticsFaceDetectMode> = {
  aidl::android::hardware::camera::metadata::StatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_OFF,
  aidl::android::hardware::camera::metadata::StatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_SIMPLE,
  aidl::android::hardware::camera::metadata::StatisticsFaceDetectMode::ANDROID_STATISTICS_FACE_DETECT_MODE_FULL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
