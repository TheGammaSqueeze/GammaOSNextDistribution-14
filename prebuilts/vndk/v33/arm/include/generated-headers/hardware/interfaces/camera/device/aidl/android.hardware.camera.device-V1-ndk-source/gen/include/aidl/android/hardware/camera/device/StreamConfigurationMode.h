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
enum class StreamConfigurationMode : int32_t {
  NORMAL_MODE = 0,
  CONSTRAINED_HIGH_SPEED_MODE = 1,
  VENDOR_MODE_0 = 32768,
  VENDOR_MODE_1 = 32769,
  VENDOR_MODE_2 = 32770,
  VENDOR_MODE_3 = 32771,
  VENDOR_MODE_4 = 32772,
  VENDOR_MODE_5 = 32773,
  VENDOR_MODE_6 = 32774,
  VENDOR_MODE_7 = 32775,
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
[[nodiscard]] static inline std::string toString(StreamConfigurationMode val) {
  switch(val) {
  case StreamConfigurationMode::NORMAL_MODE:
    return "NORMAL_MODE";
  case StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE:
    return "CONSTRAINED_HIGH_SPEED_MODE";
  case StreamConfigurationMode::VENDOR_MODE_0:
    return "VENDOR_MODE_0";
  case StreamConfigurationMode::VENDOR_MODE_1:
    return "VENDOR_MODE_1";
  case StreamConfigurationMode::VENDOR_MODE_2:
    return "VENDOR_MODE_2";
  case StreamConfigurationMode::VENDOR_MODE_3:
    return "VENDOR_MODE_3";
  case StreamConfigurationMode::VENDOR_MODE_4:
    return "VENDOR_MODE_4";
  case StreamConfigurationMode::VENDOR_MODE_5:
    return "VENDOR_MODE_5";
  case StreamConfigurationMode::VENDOR_MODE_6:
    return "VENDOR_MODE_6";
  case StreamConfigurationMode::VENDOR_MODE_7:
    return "VENDOR_MODE_7";
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
constexpr inline std::array<aidl::android::hardware::camera::device::StreamConfigurationMode, 10> enum_values<aidl::android::hardware::camera::device::StreamConfigurationMode> = {
  aidl::android::hardware::camera::device::StreamConfigurationMode::NORMAL_MODE,
  aidl::android::hardware::camera::device::StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE,
  aidl::android::hardware::camera::device::StreamConfigurationMode::VENDOR_MODE_0,
  aidl::android::hardware::camera::device::StreamConfigurationMode::VENDOR_MODE_1,
  aidl::android::hardware::camera::device::StreamConfigurationMode::VENDOR_MODE_2,
  aidl::android::hardware::camera::device::StreamConfigurationMode::VENDOR_MODE_3,
  aidl::android::hardware::camera::device::StreamConfigurationMode::VENDOR_MODE_4,
  aidl::android::hardware::camera::device::StreamConfigurationMode::VENDOR_MODE_5,
  aidl::android::hardware::camera::device::StreamConfigurationMode::VENDOR_MODE_6,
  aidl::android::hardware::camera::device::StreamConfigurationMode::VENDOR_MODE_7,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
