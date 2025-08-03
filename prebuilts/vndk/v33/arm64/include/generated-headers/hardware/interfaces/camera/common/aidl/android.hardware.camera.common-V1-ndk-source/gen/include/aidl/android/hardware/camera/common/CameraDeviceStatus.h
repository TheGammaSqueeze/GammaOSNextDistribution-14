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
namespace common {
enum class CameraDeviceStatus : int32_t {
  NOT_PRESENT = 0,
  PRESENT = 1,
  ENUMERATING = 2,
};

}  // namespace common
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace common {
[[nodiscard]] static inline std::string toString(CameraDeviceStatus val) {
  switch(val) {
  case CameraDeviceStatus::NOT_PRESENT:
    return "NOT_PRESENT";
  case CameraDeviceStatus::PRESENT:
    return "PRESENT";
  case CameraDeviceStatus::ENUMERATING:
    return "ENUMERATING";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace common
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::common::CameraDeviceStatus, 3> enum_values<aidl::android::hardware::camera::common::CameraDeviceStatus> = {
  aidl::android::hardware::camera::common::CameraDeviceStatus::NOT_PRESENT,
  aidl::android::hardware::camera::common::CameraDeviceStatus::PRESENT,
  aidl::android::hardware::camera::common::CameraDeviceStatus::ENUMERATING,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
