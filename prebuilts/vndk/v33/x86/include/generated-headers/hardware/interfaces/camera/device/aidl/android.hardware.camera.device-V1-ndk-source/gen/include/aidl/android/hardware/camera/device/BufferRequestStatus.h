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
enum class BufferRequestStatus : int32_t {
  OK = 0,
  FAILED_PARTIAL = 1,
  FAILED_CONFIGURING = 2,
  FAILED_ILLEGAL_ARGUMENTS = 3,
  FAILED_UNKNOWN = 4,
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
[[nodiscard]] static inline std::string toString(BufferRequestStatus val) {
  switch(val) {
  case BufferRequestStatus::OK:
    return "OK";
  case BufferRequestStatus::FAILED_PARTIAL:
    return "FAILED_PARTIAL";
  case BufferRequestStatus::FAILED_CONFIGURING:
    return "FAILED_CONFIGURING";
  case BufferRequestStatus::FAILED_ILLEGAL_ARGUMENTS:
    return "FAILED_ILLEGAL_ARGUMENTS";
  case BufferRequestStatus::FAILED_UNKNOWN:
    return "FAILED_UNKNOWN";
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
constexpr inline std::array<aidl::android::hardware::camera::device::BufferRequestStatus, 5> enum_values<aidl::android::hardware::camera::device::BufferRequestStatus> = {
  aidl::android::hardware::camera::device::BufferRequestStatus::OK,
  aidl::android::hardware::camera::device::BufferRequestStatus::FAILED_PARTIAL,
  aidl::android::hardware::camera::device::BufferRequestStatus::FAILED_CONFIGURING,
  aidl::android::hardware::camera::device::BufferRequestStatus::FAILED_ILLEGAL_ARGUMENTS,
  aidl::android::hardware::camera::device::BufferRequestStatus::FAILED_UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
