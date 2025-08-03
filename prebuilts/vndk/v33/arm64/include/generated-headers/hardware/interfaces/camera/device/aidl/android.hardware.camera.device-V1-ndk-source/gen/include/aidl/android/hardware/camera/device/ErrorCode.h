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
enum class ErrorCode : int32_t {
  ERROR_DEVICE = 1,
  ERROR_REQUEST = 2,
  ERROR_RESULT = 3,
  ERROR_BUFFER = 4,
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
[[nodiscard]] static inline std::string toString(ErrorCode val) {
  switch(val) {
  case ErrorCode::ERROR_DEVICE:
    return "ERROR_DEVICE";
  case ErrorCode::ERROR_REQUEST:
    return "ERROR_REQUEST";
  case ErrorCode::ERROR_RESULT:
    return "ERROR_RESULT";
  case ErrorCode::ERROR_BUFFER:
    return "ERROR_BUFFER";
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
constexpr inline std::array<aidl::android::hardware::camera::device::ErrorCode, 4> enum_values<aidl::android::hardware::camera::device::ErrorCode> = {
  aidl::android::hardware::camera::device::ErrorCode::ERROR_DEVICE,
  aidl::android::hardware::camera::device::ErrorCode::ERROR_REQUEST,
  aidl::android::hardware::camera::device::ErrorCode::ERROR_RESULT,
  aidl::android::hardware::camera::device::ErrorCode::ERROR_BUFFER,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
