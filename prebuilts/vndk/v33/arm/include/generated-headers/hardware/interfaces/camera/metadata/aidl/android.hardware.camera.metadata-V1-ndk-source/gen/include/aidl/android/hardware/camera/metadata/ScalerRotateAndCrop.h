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
enum class ScalerRotateAndCrop : int32_t {
  ANDROID_SCALER_ROTATE_AND_CROP_NONE = 0,
  ANDROID_SCALER_ROTATE_AND_CROP_90 = 1,
  ANDROID_SCALER_ROTATE_AND_CROP_180 = 2,
  ANDROID_SCALER_ROTATE_AND_CROP_270 = 3,
  ANDROID_SCALER_ROTATE_AND_CROP_AUTO = 4,
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
[[nodiscard]] static inline std::string toString(ScalerRotateAndCrop val) {
  switch(val) {
  case ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_NONE:
    return "ANDROID_SCALER_ROTATE_AND_CROP_NONE";
  case ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_90:
    return "ANDROID_SCALER_ROTATE_AND_CROP_90";
  case ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_180:
    return "ANDROID_SCALER_ROTATE_AND_CROP_180";
  case ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_270:
    return "ANDROID_SCALER_ROTATE_AND_CROP_270";
  case ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_AUTO:
    return "ANDROID_SCALER_ROTATE_AND_CROP_AUTO";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ScalerRotateAndCrop, 5> enum_values<aidl::android::hardware::camera::metadata::ScalerRotateAndCrop> = {
  aidl::android::hardware::camera::metadata::ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_NONE,
  aidl::android::hardware::camera::metadata::ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_90,
  aidl::android::hardware::camera::metadata::ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_180,
  aidl::android::hardware::camera::metadata::ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_270,
  aidl::android::hardware::camera::metadata::ScalerRotateAndCrop::ANDROID_SCALER_ROTATE_AND_CROP_AUTO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
