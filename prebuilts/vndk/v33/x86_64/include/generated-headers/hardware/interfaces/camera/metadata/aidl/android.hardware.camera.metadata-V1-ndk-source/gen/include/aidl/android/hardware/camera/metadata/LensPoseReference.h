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
enum class LensPoseReference : int32_t {
  ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA = 0,
  ANDROID_LENS_POSE_REFERENCE_GYROSCOPE = 1,
  ANDROID_LENS_POSE_REFERENCE_UNDEFINED = 2,
  ANDROID_LENS_POSE_REFERENCE_AUTOMOTIVE = 3,
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
[[nodiscard]] static inline std::string toString(LensPoseReference val) {
  switch(val) {
  case LensPoseReference::ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA:
    return "ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA";
  case LensPoseReference::ANDROID_LENS_POSE_REFERENCE_GYROSCOPE:
    return "ANDROID_LENS_POSE_REFERENCE_GYROSCOPE";
  case LensPoseReference::ANDROID_LENS_POSE_REFERENCE_UNDEFINED:
    return "ANDROID_LENS_POSE_REFERENCE_UNDEFINED";
  case LensPoseReference::ANDROID_LENS_POSE_REFERENCE_AUTOMOTIVE:
    return "ANDROID_LENS_POSE_REFERENCE_AUTOMOTIVE";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::LensPoseReference, 4> enum_values<aidl::android::hardware::camera::metadata::LensPoseReference> = {
  aidl::android::hardware::camera::metadata::LensPoseReference::ANDROID_LENS_POSE_REFERENCE_PRIMARY_CAMERA,
  aidl::android::hardware::camera::metadata::LensPoseReference::ANDROID_LENS_POSE_REFERENCE_GYROSCOPE,
  aidl::android::hardware::camera::metadata::LensPoseReference::ANDROID_LENS_POSE_REFERENCE_UNDEFINED,
  aidl::android::hardware::camera::metadata::LensPoseReference::ANDROID_LENS_POSE_REFERENCE_AUTOMOTIVE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
