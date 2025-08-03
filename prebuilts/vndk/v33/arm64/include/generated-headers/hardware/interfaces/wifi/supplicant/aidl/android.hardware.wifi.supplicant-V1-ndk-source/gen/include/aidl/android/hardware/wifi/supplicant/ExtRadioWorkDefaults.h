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
enum class ExtRadioWorkDefaults : int32_t {
  TIMEOUT_IN_SECS = 10,
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
[[nodiscard]] static inline std::string toString(ExtRadioWorkDefaults val) {
  switch(val) {
  case ExtRadioWorkDefaults::TIMEOUT_IN_SECS:
    return "TIMEOUT_IN_SECS";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::ExtRadioWorkDefaults, 1> enum_values<aidl::android::hardware::wifi::supplicant::ExtRadioWorkDefaults> = {
  aidl::android::hardware::wifi::supplicant::ExtRadioWorkDefaults::TIMEOUT_IN_SECS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
