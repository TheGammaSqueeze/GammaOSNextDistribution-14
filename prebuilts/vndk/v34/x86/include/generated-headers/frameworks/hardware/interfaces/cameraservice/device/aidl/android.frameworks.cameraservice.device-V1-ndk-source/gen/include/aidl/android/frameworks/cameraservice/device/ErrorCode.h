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
namespace device {
enum class ErrorCode : int32_t {
  CAMERA_INVALID_ERROR = -1,
  CAMERA_DISCONNECTED = 0,
  CAMERA_DEVICE = 1,
  CAMERA_SERVICE = 2,
  CAMERA_REQUEST = 3,
  CAMERA_RESULT = 4,
  CAMERA_BUFFER = 5,
  CAMERA_DISABLED = 6,
  CAMERA_UNKNOWN_ERROR = 7,
};

}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
[[nodiscard]] static inline std::string toString(ErrorCode val) {
  switch(val) {
  case ErrorCode::CAMERA_INVALID_ERROR:
    return "CAMERA_INVALID_ERROR";
  case ErrorCode::CAMERA_DISCONNECTED:
    return "CAMERA_DISCONNECTED";
  case ErrorCode::CAMERA_DEVICE:
    return "CAMERA_DEVICE";
  case ErrorCode::CAMERA_SERVICE:
    return "CAMERA_SERVICE";
  case ErrorCode::CAMERA_REQUEST:
    return "CAMERA_REQUEST";
  case ErrorCode::CAMERA_RESULT:
    return "CAMERA_RESULT";
  case ErrorCode::CAMERA_BUFFER:
    return "CAMERA_BUFFER";
  case ErrorCode::CAMERA_DISABLED:
    return "CAMERA_DISABLED";
  case ErrorCode::CAMERA_UNKNOWN_ERROR:
    return "CAMERA_UNKNOWN_ERROR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::frameworks::cameraservice::device::ErrorCode, 9> enum_values<aidl::android::frameworks::cameraservice::device::ErrorCode> = {
  aidl::android::frameworks::cameraservice::device::ErrorCode::CAMERA_INVALID_ERROR,
  aidl::android::frameworks::cameraservice::device::ErrorCode::CAMERA_DISCONNECTED,
  aidl::android::frameworks::cameraservice::device::ErrorCode::CAMERA_DEVICE,
  aidl::android::frameworks::cameraservice::device::ErrorCode::CAMERA_SERVICE,
  aidl::android::frameworks::cameraservice::device::ErrorCode::CAMERA_REQUEST,
  aidl::android::frameworks::cameraservice::device::ErrorCode::CAMERA_RESULT,
  aidl::android::frameworks::cameraservice::device::ErrorCode::CAMERA_BUFFER,
  aidl::android::frameworks::cameraservice::device::ErrorCode::CAMERA_DISABLED,
  aidl::android::frameworks::cameraservice::device::ErrorCode::CAMERA_UNKNOWN_ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
