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
enum class SensorInfoTimestampSource : int32_t {
  ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN = 0,
  ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME = 1,
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
[[nodiscard]] static inline std::string toString(SensorInfoTimestampSource val) {
  switch(val) {
  case SensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN:
    return "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN";
  case SensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME:
    return "ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::SensorInfoTimestampSource, 2> enum_values<aidl::android::hardware::camera::metadata::SensorInfoTimestampSource> = {
  aidl::android::hardware::camera::metadata::SensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN,
  aidl::android::hardware::camera::metadata::SensorInfoTimestampSource::ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
