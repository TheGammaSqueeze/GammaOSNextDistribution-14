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
enum class LensFacing : int32_t {
  ANDROID_LENS_FACING_FRONT = 0,
  ANDROID_LENS_FACING_BACK = 1,
  ANDROID_LENS_FACING_EXTERNAL = 2,
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
[[nodiscard]] static inline std::string toString(LensFacing val) {
  switch(val) {
  case LensFacing::ANDROID_LENS_FACING_FRONT:
    return "ANDROID_LENS_FACING_FRONT";
  case LensFacing::ANDROID_LENS_FACING_BACK:
    return "ANDROID_LENS_FACING_BACK";
  case LensFacing::ANDROID_LENS_FACING_EXTERNAL:
    return "ANDROID_LENS_FACING_EXTERNAL";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::LensFacing, 3> enum_values<aidl::android::hardware::camera::metadata::LensFacing> = {
  aidl::android::hardware::camera::metadata::LensFacing::ANDROID_LENS_FACING_FRONT,
  aidl::android::hardware::camera::metadata::LensFacing::ANDROID_LENS_FACING_BACK,
  aidl::android::hardware::camera::metadata::LensFacing::ANDROID_LENS_FACING_EXTERNAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
