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
namespace hostapd {
enum class DebugLevel : int32_t {
  EXCESSIVE = 0,
  MSGDUMP = 1,
  DEBUG = 2,
  INFO = 3,
  WARNING = 4,
  ERROR = 5,
};

}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
[[nodiscard]] static inline std::string toString(DebugLevel val) {
  switch(val) {
  case DebugLevel::EXCESSIVE:
    return "EXCESSIVE";
  case DebugLevel::MSGDUMP:
    return "MSGDUMP";
  case DebugLevel::DEBUG:
    return "DEBUG";
  case DebugLevel::INFO:
    return "INFO";
  case DebugLevel::WARNING:
    return "WARNING";
  case DebugLevel::ERROR:
    return "ERROR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::hostapd::DebugLevel, 6> enum_values<aidl::android::hardware::wifi::hostapd::DebugLevel> = {
  aidl::android::hardware::wifi::hostapd::DebugLevel::EXCESSIVE,
  aidl::android::hardware::wifi::hostapd::DebugLevel::MSGDUMP,
  aidl::android::hardware::wifi::hostapd::DebugLevel::DEBUG,
  aidl::android::hardware::wifi::hostapd::DebugLevel::INFO,
  aidl::android::hardware::wifi::hostapd::DebugLevel::WARNING,
  aidl::android::hardware::wifi::hostapd::DebugLevel::ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
