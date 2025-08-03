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
enum class UsbDataStatus : int8_t {
  UNKNOWN = 0,
  ENABLED = 1,
  DISABLED_OVERHEAT = 2,
  DISABLED_CONTAMINANT = 3,
  DISABLED_DOCK = 4,
  DISABLED_FORCE = 5,
  DISABLED_DEBUG = 6,
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace usb {
[[nodiscard]] static inline std::string toString(UsbDataStatus val) {
  switch(val) {
  case UsbDataStatus::UNKNOWN:
    return "UNKNOWN";
  case UsbDataStatus::ENABLED:
    return "ENABLED";
  case UsbDataStatus::DISABLED_OVERHEAT:
    return "DISABLED_OVERHEAT";
  case UsbDataStatus::DISABLED_CONTAMINANT:
    return "DISABLED_CONTAMINANT";
  case UsbDataStatus::DISABLED_DOCK:
    return "DISABLED_DOCK";
  case UsbDataStatus::DISABLED_FORCE:
    return "DISABLED_FORCE";
  case UsbDataStatus::DISABLED_DEBUG:
    return "DISABLED_DEBUG";
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
constexpr inline std::array<aidl::android::hardware::usb::UsbDataStatus, 7> enum_values<aidl::android::hardware::usb::UsbDataStatus> = {
  aidl::android::hardware::usb::UsbDataStatus::UNKNOWN,
  aidl::android::hardware::usb::UsbDataStatus::ENABLED,
  aidl::android::hardware::usb::UsbDataStatus::DISABLED_OVERHEAT,
  aidl::android::hardware::usb::UsbDataStatus::DISABLED_CONTAMINANT,
  aidl::android::hardware::usb::UsbDataStatus::DISABLED_DOCK,
  aidl::android::hardware::usb::UsbDataStatus::DISABLED_FORCE,
  aidl::android::hardware::usb::UsbDataStatus::DISABLED_DEBUG,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
