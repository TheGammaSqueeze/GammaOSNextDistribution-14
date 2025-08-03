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
enum class StatisticsHistogramMode : int32_t {
  ANDROID_STATISTICS_HISTOGRAM_MODE_OFF = 0,
  ANDROID_STATISTICS_HISTOGRAM_MODE_ON = 1,
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
[[nodiscard]] static inline std::string toString(StatisticsHistogramMode val) {
  switch(val) {
  case StatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_OFF:
    return "ANDROID_STATISTICS_HISTOGRAM_MODE_OFF";
  case StatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_ON:
    return "ANDROID_STATISTICS_HISTOGRAM_MODE_ON";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::StatisticsHistogramMode, 2> enum_values<aidl::android::hardware::camera::metadata::StatisticsHistogramMode> = {
  aidl::android::hardware::camera::metadata::StatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_OFF,
  aidl::android::hardware::camera::metadata::StatisticsHistogramMode::ANDROID_STATISTICS_HISTOGRAM_MODE_ON,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
