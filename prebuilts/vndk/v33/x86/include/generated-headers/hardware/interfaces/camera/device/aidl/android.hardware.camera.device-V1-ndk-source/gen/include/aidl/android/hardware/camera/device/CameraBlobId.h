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
namespace device {
enum class CameraBlobId : int32_t {
  JPEG = 255,
  JPEG_APP_SEGMENTS = 256,
};

}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
[[nodiscard]] static inline std::string toString(CameraBlobId val) {
  switch(val) {
  case CameraBlobId::JPEG:
    return "JPEG";
  case CameraBlobId::JPEG_APP_SEGMENTS:
    return "JPEG_APP_SEGMENTS";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::device::CameraBlobId, 2> enum_values<aidl::android::hardware::camera::device::CameraBlobId> = {
  aidl::android::hardware::camera::device::CameraBlobId::JPEG,
  aidl::android::hardware::camera::device::CameraBlobId::JPEG_APP_SEGMENTS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
