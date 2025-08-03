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
enum class CameraMetadataSection : int32_t {
  ANDROID_COLOR_CORRECTION = 0,
  ANDROID_CONTROL = 1,
  ANDROID_DEMOSAIC = 2,
  ANDROID_EDGE = 3,
  ANDROID_FLASH = 4,
  ANDROID_FLASH_INFO = 5,
  ANDROID_HOT_PIXEL = 6,
  ANDROID_JPEG = 7,
  ANDROID_LENS = 8,
  ANDROID_LENS_INFO = 9,
  ANDROID_NOISE_REDUCTION = 10,
  ANDROID_QUIRKS = 11,
  ANDROID_REQUEST = 12,
  ANDROID_SCALER = 13,
  ANDROID_SENSOR = 14,
  ANDROID_SENSOR_INFO = 15,
  ANDROID_SHADING = 16,
  ANDROID_STATISTICS = 17,
  ANDROID_STATISTICS_INFO = 18,
  ANDROID_TONEMAP = 19,
  ANDROID_LED = 20,
  ANDROID_INFO = 21,
  ANDROID_BLACK_LEVEL = 22,
  ANDROID_SYNC = 23,
  ANDROID_REPROCESS = 24,
  ANDROID_DEPTH = 25,
  ANDROID_LOGICAL_MULTI_CAMERA = 26,
  ANDROID_DISTORTION_CORRECTION = 27,
  ANDROID_HEIC = 28,
  ANDROID_HEIC_INFO = 29,
  ANDROID_AUTOMOTIVE = 30,
  ANDROID_AUTOMOTIVE_LENS = 31,
  VENDOR_SECTION = 32768,
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
[[nodiscard]] static inline std::string toString(CameraMetadataSection val) {
  switch(val) {
  case CameraMetadataSection::ANDROID_COLOR_CORRECTION:
    return "ANDROID_COLOR_CORRECTION";
  case CameraMetadataSection::ANDROID_CONTROL:
    return "ANDROID_CONTROL";
  case CameraMetadataSection::ANDROID_DEMOSAIC:
    return "ANDROID_DEMOSAIC";
  case CameraMetadataSection::ANDROID_EDGE:
    return "ANDROID_EDGE";
  case CameraMetadataSection::ANDROID_FLASH:
    return "ANDROID_FLASH";
  case CameraMetadataSection::ANDROID_FLASH_INFO:
    return "ANDROID_FLASH_INFO";
  case CameraMetadataSection::ANDROID_HOT_PIXEL:
    return "ANDROID_HOT_PIXEL";
  case CameraMetadataSection::ANDROID_JPEG:
    return "ANDROID_JPEG";
  case CameraMetadataSection::ANDROID_LENS:
    return "ANDROID_LENS";
  case CameraMetadataSection::ANDROID_LENS_INFO:
    return "ANDROID_LENS_INFO";
  case CameraMetadataSection::ANDROID_NOISE_REDUCTION:
    return "ANDROID_NOISE_REDUCTION";
  case CameraMetadataSection::ANDROID_QUIRKS:
    return "ANDROID_QUIRKS";
  case CameraMetadataSection::ANDROID_REQUEST:
    return "ANDROID_REQUEST";
  case CameraMetadataSection::ANDROID_SCALER:
    return "ANDROID_SCALER";
  case CameraMetadataSection::ANDROID_SENSOR:
    return "ANDROID_SENSOR";
  case CameraMetadataSection::ANDROID_SENSOR_INFO:
    return "ANDROID_SENSOR_INFO";
  case CameraMetadataSection::ANDROID_SHADING:
    return "ANDROID_SHADING";
  case CameraMetadataSection::ANDROID_STATISTICS:
    return "ANDROID_STATISTICS";
  case CameraMetadataSection::ANDROID_STATISTICS_INFO:
    return "ANDROID_STATISTICS_INFO";
  case CameraMetadataSection::ANDROID_TONEMAP:
    return "ANDROID_TONEMAP";
  case CameraMetadataSection::ANDROID_LED:
    return "ANDROID_LED";
  case CameraMetadataSection::ANDROID_INFO:
    return "ANDROID_INFO";
  case CameraMetadataSection::ANDROID_BLACK_LEVEL:
    return "ANDROID_BLACK_LEVEL";
  case CameraMetadataSection::ANDROID_SYNC:
    return "ANDROID_SYNC";
  case CameraMetadataSection::ANDROID_REPROCESS:
    return "ANDROID_REPROCESS";
  case CameraMetadataSection::ANDROID_DEPTH:
    return "ANDROID_DEPTH";
  case CameraMetadataSection::ANDROID_LOGICAL_MULTI_CAMERA:
    return "ANDROID_LOGICAL_MULTI_CAMERA";
  case CameraMetadataSection::ANDROID_DISTORTION_CORRECTION:
    return "ANDROID_DISTORTION_CORRECTION";
  case CameraMetadataSection::ANDROID_HEIC:
    return "ANDROID_HEIC";
  case CameraMetadataSection::ANDROID_HEIC_INFO:
    return "ANDROID_HEIC_INFO";
  case CameraMetadataSection::ANDROID_AUTOMOTIVE:
    return "ANDROID_AUTOMOTIVE";
  case CameraMetadataSection::ANDROID_AUTOMOTIVE_LENS:
    return "ANDROID_AUTOMOTIVE_LENS";
  case CameraMetadataSection::VENDOR_SECTION:
    return "VENDOR_SECTION";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::CameraMetadataSection, 33> enum_values<aidl::android::hardware::camera::metadata::CameraMetadataSection> = {
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_COLOR_CORRECTION,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_CONTROL,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_DEMOSAIC,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_EDGE,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_FLASH,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_FLASH_INFO,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_HOT_PIXEL,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_JPEG,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_LENS,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_LENS_INFO,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_NOISE_REDUCTION,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_QUIRKS,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_REQUEST,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_SCALER,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_SENSOR,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_SENSOR_INFO,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_SHADING,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_STATISTICS,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_STATISTICS_INFO,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_TONEMAP,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_LED,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_INFO,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_BLACK_LEVEL,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_SYNC,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_REPROCESS,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_DEPTH,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_LOGICAL_MULTI_CAMERA,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_DISTORTION_CORRECTION,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_HEIC,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_HEIC_INFO,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_AUTOMOTIVE,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::ANDROID_AUTOMOTIVE_LENS,
  aidl::android::hardware::camera::metadata::CameraMetadataSection::VENDOR_SECTION,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
