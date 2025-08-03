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
enum class Status : int32_t {
  OK = 0,
  ILLEGAL_ARGUMENT = 1,
  CAMERA_IN_USE = 2,
  MAX_CAMERAS_IN_USE = 3,
  OPERATION_NOT_SUPPORTED = 4,
  CAMERA_DISCONNECTED = 5,
  INTERNAL_ERROR = 6,
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
[[nodiscard]] static inline std::string toString(Status val) {
  switch(val) {
  case Status::OK:
    return "OK";
  case Status::ILLEGAL_ARGUMENT:
    return "ILLEGAL_ARGUMENT";
  case Status::CAMERA_IN_USE:
    return "CAMERA_IN_USE";
  case Status::MAX_CAMERAS_IN_USE:
    return "MAX_CAMERAS_IN_USE";
  case Status::OPERATION_NOT_SUPPORTED:
    return "OPERATION_NOT_SUPPORTED";
  case Status::CAMERA_DISCONNECTED:
    return "CAMERA_DISCONNECTED";
  case Status::INTERNAL_ERROR:
    return "INTERNAL_ERROR";
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
constexpr inline std::array<aidl::android::hardware::camera::common::Status, 7> enum_values<aidl::android::hardware::camera::common::Status> = {
  aidl::android::hardware::camera::common::Status::OK,
  aidl::android::hardware::camera::common::Status::ILLEGAL_ARGUMENT,
  aidl::android::hardware::camera::common::Status::CAMERA_IN_USE,
  aidl::android::hardware::camera::common::Status::MAX_CAMERAS_IN_USE,
  aidl::android::hardware::camera::common::Status::OPERATION_NOT_SUPPORTED,
  aidl::android::hardware::camera::common::Status::CAMERA_DISCONNECTED,
  aidl::android::hardware::camera::common::Status::INTERNAL_ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
