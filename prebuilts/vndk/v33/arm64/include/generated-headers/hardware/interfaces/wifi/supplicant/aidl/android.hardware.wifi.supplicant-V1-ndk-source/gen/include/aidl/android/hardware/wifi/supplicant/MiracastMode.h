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
namespace wifi {
namespace supplicant {
enum class MiracastMode : int8_t {
  DISABLED = 0,
  SOURCE = 1,
  SINK = 2,
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
[[nodiscard]] static inline std::string toString(MiracastMode val) {
  switch(val) {
  case MiracastMode::DISABLED:
    return "DISABLED";
  case MiracastMode::SOURCE:
    return "SOURCE";
  case MiracastMode::SINK:
    return "SINK";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::MiracastMode, 3> enum_values<aidl::android::hardware::wifi::supplicant::MiracastMode> = {
  aidl::android::hardware::wifi::supplicant::MiracastMode::DISABLED,
  aidl::android::hardware::wifi::supplicant::MiracastMode::SOURCE,
  aidl::android::hardware::wifi::supplicant::MiracastMode::SINK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
