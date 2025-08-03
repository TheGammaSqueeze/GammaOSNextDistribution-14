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
enum class RequestAvailableCapabilities : int32_t {
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE = 0,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR = 1,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING = 2,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW = 3,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING = 4,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS = 5,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE = 6,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING = 7,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT = 8,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO = 9,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING = 10,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA = 11,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME = 12,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA = 13,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA = 14,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING = 15,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR = 16,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING = 17,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT = 18,
  ANDROID_REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE = 19,
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
[[nodiscard]] static inline std::string toString(RequestAvailableCapabilities val) {
  switch(val) {
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT";
  case RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE:
    return "ANDROID_REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::RequestAvailableCapabilities, 20> enum_values<aidl::android::hardware::camera::metadata::RequestAvailableCapabilities> = {
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_RAW,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT,
  aidl::android::hardware::camera::metadata::RequestAvailableCapabilities::ANDROID_REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
