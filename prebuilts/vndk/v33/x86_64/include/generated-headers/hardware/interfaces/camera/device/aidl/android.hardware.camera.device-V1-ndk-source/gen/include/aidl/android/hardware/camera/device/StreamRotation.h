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
enum class StreamRotation : int32_t {
  ROTATION_0 = 0,
  ROTATION_90 = 1,
  ROTATION_180 = 2,
  ROTATION_270 = 3,
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
[[nodiscard]] static inline std::string toString(StreamRotation val) {
  switch(val) {
  case StreamRotation::ROTATION_0:
    return "ROTATION_0";
  case StreamRotation::ROTATION_90:
    return "ROTATION_90";
  case StreamRotation::ROTATION_180:
    return "ROTATION_180";
  case StreamRotation::ROTATION_270:
    return "ROTATION_270";
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
constexpr inline std::array<aidl::android::hardware::camera::device::StreamRotation, 4> enum_values<aidl::android::hardware::camera::device::StreamRotation> = {
  aidl::android::hardware::camera::device::StreamRotation::ROTATION_0,
  aidl::android::hardware::camera::device::StreamRotation::ROTATION_90,
  aidl::android::hardware::camera::device::StreamRotation::ROTATION_180,
  aidl::android::hardware::camera::device::StreamRotation::ROTATION_270,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
