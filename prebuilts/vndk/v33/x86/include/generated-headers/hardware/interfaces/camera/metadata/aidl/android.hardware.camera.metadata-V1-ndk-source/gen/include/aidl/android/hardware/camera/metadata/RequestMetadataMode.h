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
enum class RequestMetadataMode : int32_t {
  ANDROID_REQUEST_METADATA_MODE_NONE = 0,
  ANDROID_REQUEST_METADATA_MODE_FULL = 1,
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
[[nodiscard]] static inline std::string toString(RequestMetadataMode val) {
  switch(val) {
  case RequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_NONE:
    return "ANDROID_REQUEST_METADATA_MODE_NONE";
  case RequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_FULL:
    return "ANDROID_REQUEST_METADATA_MODE_FULL";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::RequestMetadataMode, 2> enum_values<aidl::android::hardware::camera::metadata::RequestMetadataMode> = {
  aidl::android::hardware::camera::metadata::RequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_NONE,
  aidl::android::hardware::camera::metadata::RequestMetadataMode::ANDROID_REQUEST_METADATA_MODE_FULL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
