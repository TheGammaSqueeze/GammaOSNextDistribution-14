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
enum class BtCoexistenceMode : int8_t {
  ENABLED = 0,
  DISABLED = 1,
  SENSE = 2,
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
[[nodiscard]] static inline std::string toString(BtCoexistenceMode val) {
  switch(val) {
  case BtCoexistenceMode::ENABLED:
    return "ENABLED";
  case BtCoexistenceMode::DISABLED:
    return "DISABLED";
  case BtCoexistenceMode::SENSE:
    return "SENSE";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::BtCoexistenceMode, 3> enum_values<aidl::android::hardware::wifi::supplicant::BtCoexistenceMode> = {
  aidl::android::hardware::wifi::supplicant::BtCoexistenceMode::ENABLED,
  aidl::android::hardware::wifi::supplicant::BtCoexistenceMode::DISABLED,
  aidl::android::hardware::wifi::supplicant::BtCoexistenceMode::SENSE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
