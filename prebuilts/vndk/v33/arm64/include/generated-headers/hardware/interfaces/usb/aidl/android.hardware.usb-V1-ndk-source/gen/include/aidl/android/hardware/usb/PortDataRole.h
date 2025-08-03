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
enum class PortDataRole : int8_t {
  NONE = 0,
  HOST = 1,
  DEVICE = 2,
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace usb {
[[nodiscard]] static inline std::string toString(PortDataRole val) {
  switch(val) {
  case PortDataRole::NONE:
    return "NONE";
  case PortDataRole::HOST:
    return "HOST";
  case PortDataRole::DEVICE:
    return "DEVICE";
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
constexpr inline std::array<aidl::android::hardware::usb::PortDataRole, 3> enum_values<aidl::android::hardware::usb::PortDataRole> = {
  aidl::android::hardware::usb::PortDataRole::NONE,
  aidl::android::hardware::usb::PortDataRole::HOST,
  aidl::android::hardware::usb::PortDataRole::DEVICE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
