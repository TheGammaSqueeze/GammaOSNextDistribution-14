#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <array>
#include <android/binder_enums.h>
namespace aidl {
namespace android {
namespace hardware {
namespace light {
enum class LightType : int8_t {
  BACKLIGHT = 0,
  KEYBOARD = 1,
  BUTTONS = 2,
  BATTERY = 3,
  NOTIFICATIONS = 4,
  ATTENTION = 5,
  BLUETOOTH = 6,
  WIFI = 7,
  MICROPHONE = 8,
};

static inline std::string toString(LightType val) {
  switch(val) {
  case LightType::BACKLIGHT:
    return "BACKLIGHT";
  case LightType::KEYBOARD:
    return "KEYBOARD";
  case LightType::BUTTONS:
    return "BUTTONS";
  case LightType::BATTERY:
    return "BATTERY";
  case LightType::NOTIFICATIONS:
    return "NOTIFICATIONS";
  case LightType::ATTENTION:
    return "ATTENTION";
  case LightType::BLUETOOTH:
    return "BLUETOOTH";
  case LightType::WIFI:
    return "WIFI";
  case LightType::MICROPHONE:
    return "MICROPHONE";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace light
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::light::LightType, 9> enum_values<aidl::android::hardware::light::LightType> = {
  aidl::android::hardware::light::LightType::BACKLIGHT,
  aidl::android::hardware::light::LightType::KEYBOARD,
  aidl::android::hardware::light::LightType::BUTTONS,
  aidl::android::hardware::light::LightType::BATTERY,
  aidl::android::hardware::light::LightType::NOTIFICATIONS,
  aidl::android::hardware::light::LightType::ATTENTION,
  aidl::android::hardware::light::LightType::BLUETOOTH,
  aidl::android::hardware::light::LightType::WIFI,
  aidl::android::hardware::light::LightType::MICROPHONE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
