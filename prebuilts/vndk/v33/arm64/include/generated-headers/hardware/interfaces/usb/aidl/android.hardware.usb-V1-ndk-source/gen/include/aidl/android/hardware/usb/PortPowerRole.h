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
enum class PortPowerRole : int8_t {
  NONE = 0,
  SOURCE = 1,
  SINK = 2,
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace usb {
[[nodiscard]] static inline std::string toString(PortPowerRole val) {
  switch(val) {
  case PortPowerRole::NONE:
    return "NONE";
  case PortPowerRole::SOURCE:
    return "SOURCE";
  case PortPowerRole::SINK:
    return "SINK";
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
constexpr inline std::array<aidl::android::hardware::usb::PortPowerRole, 3> enum_values<aidl::android::hardware::usb::PortPowerRole> = {
  aidl::android::hardware::usb::PortPowerRole::NONE,
  aidl::android::hardware::usb::PortPowerRole::SOURCE,
  aidl::android::hardware::usb::PortPowerRole::SINK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
