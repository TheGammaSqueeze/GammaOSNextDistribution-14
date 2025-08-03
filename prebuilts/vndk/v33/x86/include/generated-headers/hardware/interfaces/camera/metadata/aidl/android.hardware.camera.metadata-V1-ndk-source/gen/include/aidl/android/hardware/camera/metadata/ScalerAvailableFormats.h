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
enum class ScalerAvailableFormats : int32_t {
  ANDROID_SCALER_AVAILABLE_FORMATS_RAW16 = 32,
  ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE = 36,
  ANDROID_SCALER_AVAILABLE_FORMATS_YV12 = 842094169,
  ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP = 17,
  ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED = 34,
  ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888 = 35,
  ANDROID_SCALER_AVAILABLE_FORMATS_BLOB = 33,
  ANDROID_SCALER_AVAILABLE_FORMATS_RAW10 = 37,
  ANDROID_SCALER_AVAILABLE_FORMATS_RAW12 = 38,
  ANDROID_SCALER_AVAILABLE_FORMATS_Y8 = 538982489,
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
[[nodiscard]] static inline std::string toString(ScalerAvailableFormats val) {
  switch(val) {
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW16:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_RAW16";
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE";
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YV12:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_YV12";
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP";
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED";
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888";
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_BLOB:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_BLOB";
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW10:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_RAW10";
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW12:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_RAW12";
  case ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_Y8:
    return "ANDROID_SCALER_AVAILABLE_FORMATS_Y8";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ScalerAvailableFormats, 10> enum_values<aidl::android::hardware::camera::metadata::ScalerAvailableFormats> = {
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW16,
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW_OPAQUE,
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YV12,
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCrCb_420_SP,
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_IMPLEMENTATION_DEFINED,
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_YCbCr_420_888,
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_BLOB,
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW10,
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_RAW12,
  aidl::android::hardware::camera::metadata::ScalerAvailableFormats::ANDROID_SCALER_AVAILABLE_FORMATS_Y8,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
