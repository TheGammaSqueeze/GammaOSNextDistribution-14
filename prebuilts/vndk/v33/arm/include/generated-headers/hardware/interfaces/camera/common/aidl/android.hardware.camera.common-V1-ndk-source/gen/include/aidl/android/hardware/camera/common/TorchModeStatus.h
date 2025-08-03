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
enum class TorchModeStatus : int32_t {
  NOT_AVAILABLE = 0,
  AVAILABLE_OFF = 1,
  AVAILABLE_ON = 2,
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
[[nodiscard]] static inline std::string toString(TorchModeStatus val) {
  switch(val) {
  case TorchModeStatus::NOT_AVAILABLE:
    return "NOT_AVAILABLE";
  case TorchModeStatus::AVAILABLE_OFF:
    return "AVAILABLE_OFF";
  case TorchModeStatus::AVAILABLE_ON:
    return "AVAILABLE_ON";
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
constexpr inline std::array<aidl::android::hardware::camera::common::TorchModeStatus, 3> enum_values<aidl::android::hardware::camera::common::TorchModeStatus> = {
  aidl::android::hardware::camera::common::TorchModeStatus::NOT_AVAILABLE,
  aidl::android::hardware::camera::common::TorchModeStatus::AVAILABLE_OFF,
  aidl::android::hardware::camera::common::TorchModeStatus::AVAILABLE_ON,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
