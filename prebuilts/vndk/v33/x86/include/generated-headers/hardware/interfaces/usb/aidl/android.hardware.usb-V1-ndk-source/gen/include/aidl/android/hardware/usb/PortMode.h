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
enum class PortMode : int8_t {
  NONE = 0,
  UFP = 1,
  DFP = 2,
  DRP = 3,
  AUDIO_ACCESSORY = 4,
  DEBUG_ACCESSORY = 5,
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace usb {
[[nodiscard]] static inline std::string toString(PortMode val) {
  switch(val) {
  case PortMode::NONE:
    return "NONE";
  case PortMode::UFP:
    return "UFP";
  case PortMode::DFP:
    return "DFP";
  case PortMode::DRP:
    return "DRP";
  case PortMode::AUDIO_ACCESSORY:
    return "AUDIO_ACCESSORY";
  case PortMode::DEBUG_ACCESSORY:
    return "DEBUG_ACCESSORY";
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
constexpr inline std::array<aidl::android::hardware::usb::PortMode, 6> enum_values<aidl::android::hardware::usb::PortMode> = {
  aidl::android::hardware::usb::PortMode::NONE,
  aidl::android::hardware::usb::PortMode::UFP,
  aidl::android::hardware::usb::PortMode::DFP,
  aidl::android::hardware::usb::PortMode::DRP,
  aidl::android::hardware::usb::PortMode::AUDIO_ACCESSORY,
  aidl::android::hardware::usb::PortMode::DEBUG_ACCESSORY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
