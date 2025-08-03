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
enum class HeicInfoSupported : int32_t {
  ANDROID_HEIC_INFO_SUPPORTED_FALSE = 0,
  ANDROID_HEIC_INFO_SUPPORTED_TRUE = 1,
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
[[nodiscard]] static inline std::string toString(HeicInfoSupported val) {
  switch(val) {
  case HeicInfoSupported::ANDROID_HEIC_INFO_SUPPORTED_FALSE:
    return "ANDROID_HEIC_INFO_SUPPORTED_FALSE";
  case HeicInfoSupported::ANDROID_HEIC_INFO_SUPPORTED_TRUE:
    return "ANDROID_HEIC_INFO_SUPPORTED_TRUE";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::HeicInfoSupported, 2> enum_values<aidl::android::hardware::camera::metadata::HeicInfoSupported> = {
  aidl::android::hardware::camera::metadata::HeicInfoSupported::ANDROID_HEIC_INFO_SUPPORTED_FALSE,
  aidl::android::hardware::camera::metadata::HeicInfoSupported::ANDROID_HEIC_INFO_SUPPORTED_TRUE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
