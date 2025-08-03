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
namespace usb {
enum class ContaminantProtectionMode : int8_t {
  NONE = 0,
  FORCE_SINK = 1,
  FORCE_SOURCE = 2,
  FORCE_DISABLE = 3,
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace usb {
[[nodiscard]] static inline std::string toString(ContaminantProtectionMode val) {
  switch(val) {
  case ContaminantProtectionMode::NONE:
    return "NONE";
  case ContaminantProtectionMode::FORCE_SINK:
    return "FORCE_SINK";
  case ContaminantProtectionMode::FORCE_SOURCE:
    return "FORCE_SOURCE";
  case ContaminantProtectionMode::FORCE_DISABLE:
    return "FORCE_DISABLE";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::usb::ContaminantProtectionMode, 4> enum_values<aidl::android::hardware::usb::ContaminantProtectionMode> = {
  aidl::android::hardware::usb::ContaminantProtectionMode::NONE,
  aidl::android::hardware::usb::ContaminantProtectionMode::FORCE_SINK,
  aidl::android::hardware::usb::ContaminantProtectionMode::FORCE_SOURCE,
  aidl::android::hardware::usb::ContaminantProtectionMode::FORCE_DISABLE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
