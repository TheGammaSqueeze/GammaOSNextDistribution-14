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
enum class ScalerCroppingType : int32_t {
  ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY = 0,
  ANDROID_SCALER_CROPPING_TYPE_FREEFORM = 1,
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
[[nodiscard]] static inline std::string toString(ScalerCroppingType val) {
  switch(val) {
  case ScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY:
    return "ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY";
  case ScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_FREEFORM:
    return "ANDROID_SCALER_CROPPING_TYPE_FREEFORM";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::ScalerCroppingType, 2> enum_values<aidl::android::hardware::camera::metadata::ScalerCroppingType> = {
  aidl::android::hardware::camera::metadata::ScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_CENTER_ONLY,
  aidl::android::hardware::camera::metadata::ScalerCroppingType::ANDROID_SCALER_CROPPING_TYPE_FREEFORM,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
