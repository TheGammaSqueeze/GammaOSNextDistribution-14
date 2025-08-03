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
enum class BufferStatus : int32_t {
  OK = 0,
  ERROR = 1,
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
[[nodiscard]] static inline std::string toString(BufferStatus val) {
  switch(val) {
  case BufferStatus::OK:
    return "OK";
  case BufferStatus::ERROR:
    return "ERROR";
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
constexpr inline std::array<aidl::android::hardware::camera::device::BufferStatus, 2> enum_values<aidl::android::hardware::camera::device::BufferStatus> = {
  aidl::android::hardware::camera::device::BufferStatus::OK,
  aidl::android::hardware::camera::device::BufferStatus::ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
