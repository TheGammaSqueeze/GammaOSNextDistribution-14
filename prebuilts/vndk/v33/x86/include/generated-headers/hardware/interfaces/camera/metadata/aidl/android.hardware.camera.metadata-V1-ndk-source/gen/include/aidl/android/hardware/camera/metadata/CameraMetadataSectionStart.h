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
enum class CameraMetadataSectionStart : int32_t {
  ANDROID_COLOR_CORRECTION_START = 0,
  ANDROID_CONTROL_START = 65536,
  ANDROID_DEMOSAIC_START = 131072,
  ANDROID_EDGE_START = 196608,
  ANDROID_FLASH_START = 262144,
  ANDROID_FLASH_INFO_START = 327680,
  ANDROID_HOT_PIXEL_START = 393216,
  ANDROID_JPEG_START = 458752,
  ANDROID_LENS_START = 524288,
  ANDROID_LENS_INFO_START = 589824,
  ANDROID_NOISE_REDUCTION_START = 655360,
  ANDROID_QUIRKS_START = 720896,
  ANDROID_REQUEST_START = 786432,
  ANDROID_SCALER_START = 851968,
  ANDROID_SENSOR_START = 917504,
  ANDROID_SENSOR_INFO_START = 983040,
  ANDROID_SHADING_START = 1048576,
  ANDROID_STATISTICS_START = 1114112,
  ANDROID_STATISTICS_INFO_START = 1179648,
  ANDROID_TONEMAP_START = 1245184,
  ANDROID_LED_START = 1310720,
  ANDROID_INFO_START = 1376256,
  ANDROID_BLACK_LEVEL_START = 1441792,
  ANDROID_SYNC_START = 1507328,
  ANDROID_REPROCESS_START = 1572864,
  ANDROID_DEPTH_START = 1638400,
  ANDROID_LOGICAL_MULTI_CAMERA_START = 1703936,
  ANDROID_DISTORTION_CORRECTION_START = 1769472,
  ANDROID_HEIC_START = 1835008,
  ANDROID_HEIC_INFO_START = 1900544,
  ANDROID_AUTOMOTIVE_START = 1966080,
  ANDROID_AUTOMOTIVE_LENS_START = 2031616,
  VENDOR_SECTION_START = -2147483648,
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
[[nodiscard]] static inline std::string toString(CameraMetadataSectionStart val) {
  switch(val) {
  case CameraMetadataSectionStart::ANDROID_COLOR_CORRECTION_START:
    return "ANDROID_COLOR_CORRECTION_START";
  case CameraMetadataSectionStart::ANDROID_CONTROL_START:
    return "ANDROID_CONTROL_START";
  case CameraMetadataSectionStart::ANDROID_DEMOSAIC_START:
    return "ANDROID_DEMOSAIC_START";
  case CameraMetadataSectionStart::ANDROID_EDGE_START:
    return "ANDROID_EDGE_START";
  case CameraMetadataSectionStart::ANDROID_FLASH_START:
    return "ANDROID_FLASH_START";
  case CameraMetadataSectionStart::ANDROID_FLASH_INFO_START:
    return "ANDROID_FLASH_INFO_START";
  case CameraMetadataSectionStart::ANDROID_HOT_PIXEL_START:
    return "ANDROID_HOT_PIXEL_START";
  case CameraMetadataSectionStart::ANDROID_JPEG_START:
    return "ANDROID_JPEG_START";
  case CameraMetadataSectionStart::ANDROID_LENS_START:
    return "ANDROID_LENS_START";
  case CameraMetadataSectionStart::ANDROID_LENS_INFO_START:
    return "ANDROID_LENS_INFO_START";
  case CameraMetadataSectionStart::ANDROID_NOISE_REDUCTION_START:
    return "ANDROID_NOISE_REDUCTION_START";
  case CameraMetadataSectionStart::ANDROID_QUIRKS_START:
    return "ANDROID_QUIRKS_START";
  case CameraMetadataSectionStart::ANDROID_REQUEST_START:
    return "ANDROID_REQUEST_START";
  case CameraMetadataSectionStart::ANDROID_SCALER_START:
    return "ANDROID_SCALER_START";
  case CameraMetadataSectionStart::ANDROID_SENSOR_START:
    return "ANDROID_SENSOR_START";
  case CameraMetadataSectionStart::ANDROID_SENSOR_INFO_START:
    return "ANDROID_SENSOR_INFO_START";
  case CameraMetadataSectionStart::ANDROID_SHADING_START:
    return "ANDROID_SHADING_START";
  case CameraMetadataSectionStart::ANDROID_STATISTICS_START:
    return "ANDROID_STATISTICS_START";
  case CameraMetadataSectionStart::ANDROID_STATISTICS_INFO_START:
    return "ANDROID_STATISTICS_INFO_START";
  case CameraMetadataSectionStart::ANDROID_TONEMAP_START:
    return "ANDROID_TONEMAP_START";
  case CameraMetadataSectionStart::ANDROID_LED_START:
    return "ANDROID_LED_START";
  case CameraMetadataSectionStart::ANDROID_INFO_START:
    return "ANDROID_INFO_START";
  case CameraMetadataSectionStart::ANDROID_BLACK_LEVEL_START:
    return "ANDROID_BLACK_LEVEL_START";
  case CameraMetadataSectionStart::ANDROID_SYNC_START:
    return "ANDROID_SYNC_START";
  case CameraMetadataSectionStart::ANDROID_REPROCESS_START:
    return "ANDROID_REPROCESS_START";
  case CameraMetadataSectionStart::ANDROID_DEPTH_START:
    return "ANDROID_DEPTH_START";
  case CameraMetadataSectionStart::ANDROID_LOGICAL_MULTI_CAMERA_START:
    return "ANDROID_LOGICAL_MULTI_CAMERA_START";
  case CameraMetadataSectionStart::ANDROID_DISTORTION_CORRECTION_START:
    return "ANDROID_DISTORTION_CORRECTION_START";
  case CameraMetadataSectionStart::ANDROID_HEIC_START:
    return "ANDROID_HEIC_START";
  case CameraMetadataSectionStart::ANDROID_HEIC_INFO_START:
    return "ANDROID_HEIC_INFO_START";
  case CameraMetadataSectionStart::ANDROID_AUTOMOTIVE_START:
    return "ANDROID_AUTOMOTIVE_START";
  case CameraMetadataSectionStart::ANDROID_AUTOMOTIVE_LENS_START:
    return "ANDROID_AUTOMOTIVE_LENS_START";
  case CameraMetadataSectionStart::VENDOR_SECTION_START:
    return "VENDOR_SECTION_START";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::CameraMetadataSectionStart, 33> enum_values<aidl::android::hardware::camera::metadata::CameraMetadataSectionStart> = {
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_COLOR_CORRECTION_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_CONTROL_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_DEMOSAIC_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_EDGE_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_FLASH_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_FLASH_INFO_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_HOT_PIXEL_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_JPEG_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_LENS_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_LENS_INFO_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_NOISE_REDUCTION_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_QUIRKS_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_REQUEST_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_SCALER_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_SENSOR_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_SENSOR_INFO_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_SHADING_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_STATISTICS_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_STATISTICS_INFO_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_TONEMAP_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_LED_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_INFO_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_BLACK_LEVEL_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_SYNC_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_REPROCESS_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_DEPTH_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_LOGICAL_MULTI_CAMERA_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_DISTORTION_CORRECTION_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_HEIC_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_HEIC_INFO_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_AUTOMOTIVE_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::ANDROID_AUTOMOTIVE_LENS_START,
  aidl::android::hardware::camera::metadata::CameraMetadataSectionStart::VENDOR_SECTION_START,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
