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
enum class WpsConfigMethods : int32_t {
  USBA = 1,
  ETHERNET = 2,
  LABEL = 4,
  DISPLAY = 8,
  EXT_NFC_TOKEN = 16,
  INT_NFC_TOKEN = 32,
  NFC_INTERFACE = 64,
  PUSHBUTTON = 128,
  KEYPAD = 256,
  VIRT_PUSHBUTTON = 640,
  PHY_PUSHBUTTON = 1152,
  P2PS = 4096,
  VIRT_DISPLAY = 8200,
  PHY_DISPLAY = 16392,
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
[[nodiscard]] static inline std::string toString(WpsConfigMethods val) {
  switch(val) {
  case WpsConfigMethods::USBA:
    return "USBA";
  case WpsConfigMethods::ETHERNET:
    return "ETHERNET";
  case WpsConfigMethods::LABEL:
    return "LABEL";
  case WpsConfigMethods::DISPLAY:
    return "DISPLAY";
  case WpsConfigMethods::EXT_NFC_TOKEN:
    return "EXT_NFC_TOKEN";
  case WpsConfigMethods::INT_NFC_TOKEN:
    return "INT_NFC_TOKEN";
  case WpsConfigMethods::NFC_INTERFACE:
    return "NFC_INTERFACE";
  case WpsConfigMethods::PUSHBUTTON:
    return "PUSHBUTTON";
  case WpsConfigMethods::KEYPAD:
    return "KEYPAD";
  case WpsConfigMethods::VIRT_PUSHBUTTON:
    return "VIRT_PUSHBUTTON";
  case WpsConfigMethods::PHY_PUSHBUTTON:
    return "PHY_PUSHBUTTON";
  case WpsConfigMethods::P2PS:
    return "P2PS";
  case WpsConfigMethods::VIRT_DISPLAY:
    return "VIRT_DISPLAY";
  case WpsConfigMethods::PHY_DISPLAY:
    return "PHY_DISPLAY";
  default:
    return std::to_string(static_cast<int32_t>(val));
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::WpsConfigMethods, 14> enum_values<aidl::android::hardware::wifi::supplicant::WpsConfigMethods> = {
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::USBA,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::ETHERNET,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::LABEL,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::DISPLAY,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::EXT_NFC_TOKEN,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::INT_NFC_TOKEN,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::NFC_INTERFACE,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::PUSHBUTTON,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::KEYPAD,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::VIRT_PUSHBUTTON,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::PHY_PUSHBUTTON,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::P2PS,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::VIRT_DISPLAY,
  aidl::android::hardware::wifi::supplicant::WpsConfigMethods::PHY_DISPLAY,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
