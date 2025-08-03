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
namespace frameworks {
namespace cameraservice {
namespace common {
enum class Status : int32_t {
  NO_ERROR = 0,
  PERMISSION_DENIED = 1,
  ALREADY_EXISTS = 2,
  ILLEGAL_ARGUMENT = 3,
  DISCONNECTED = 4,
  TIMED_OUT = 5,
  DISABLED = 6,
  CAMERA_IN_USE = 7,
  MAX_CAMERAS_IN_USE = 8,
  DEPRECATED_HAL = 9,
  INVALID_OPERATION = 10,
  UNKNOWN_ERROR = 11,
};

}  // namespace common
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace common {
[[nodiscard]] static inline std::string toString(Status val) {
  switch(val) {
  case Status::NO_ERROR:
    return "NO_ERROR";
  case Status::PERMISSION_DENIED:
    return "PERMISSION_DENIED";
  case Status::ALREADY_EXISTS:
    return "ALREADY_EXISTS";
  case Status::ILLEGAL_ARGUMENT:
    return "ILLEGAL_ARGUMENT";
  case Status::DISCONNECTED:
    return "DISCONNECTED";
  case Status::TIMED_OUT:
    return "TIMED_OUT";
  case Status::DISABLED:
    return "DISABLED";
  case Status::CAMERA_IN_USE:
    return "CAMERA_IN_USE";
  case Status::MAX_CAMERAS_IN_USE:
    return "MAX_CAMERAS_IN_USE";
  case Status::DEPRECATED_HAL:
    return "DEPRECATED_HAL";
  case Status::INVALID_OPERATION:
    return "INVALID_OPERATION";
  case Status::UNKNOWN_ERROR:
    return "UNKNOWN_ERROR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace common
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::frameworks::cameraservice::common::Status, 12> enum_values<aidl::android::frameworks::cameraservice::common::Status> = {
  aidl::android::frameworks::cameraservice::common::Status::NO_ERROR,
  aidl::android::frameworks::cameraservice::common::Status::PERMISSION_DENIED,
  aidl::android::frameworks::cameraservice::common::Status::ALREADY_EXISTS,
  aidl::android::frameworks::cameraservice::common::Status::ILLEGAL_ARGUMENT,
  aidl::android::frameworks::cameraservice::common::Status::DISCONNECTED,
  aidl::android::frameworks::cameraservice::common::Status::TIMED_OUT,
  aidl::android::frameworks::cameraservice::common::Status::DISABLED,
  aidl::android::frameworks::cameraservice::common::Status::CAMERA_IN_USE,
  aidl::android::frameworks::cameraservice::common::Status::MAX_CAMERAS_IN_USE,
  aidl::android::frameworks::cameraservice::common::Status::DEPRECATED_HAL,
  aidl::android::frameworks::cameraservice::common::Status::INVALID_OPERATION,
  aidl::android::frameworks::cameraservice::common::Status::UNKNOWN_ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
