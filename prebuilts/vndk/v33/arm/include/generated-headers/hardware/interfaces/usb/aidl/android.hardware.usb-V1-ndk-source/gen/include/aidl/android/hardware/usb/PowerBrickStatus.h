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
enum class PowerBrickStatus : int8_t {
  UNKNOWN = 0,
  CONNECTED = 1,
  NOT_CONNECTED = 2,
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace usb {
[[nodiscard]] static inline std::string toString(PowerBrickStatus val) {
  switch(val) {
  case PowerBrickStatus::UNKNOWN:
    return "UNKNOWN";
  case PowerBrickStatus::CONNECTED:
    return "CONNECTED";
  case PowerBrickStatus::NOT_CONNECTED:
    return "NOT_CONNECTED";
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
constexpr inline std::array<aidl::android::hardware::usb::PowerBrickStatus, 3> enum_values<aidl::android::hardware::usb::PowerBrickStatus> = {
  aidl::android::hardware::usb::PowerBrickStatus::UNKNOWN,
  aidl::android::hardware::usb::PowerBrickStatus::CONNECTED,
  aidl::android::hardware::usb::PowerBrickStatus::NOT_CONNECTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
