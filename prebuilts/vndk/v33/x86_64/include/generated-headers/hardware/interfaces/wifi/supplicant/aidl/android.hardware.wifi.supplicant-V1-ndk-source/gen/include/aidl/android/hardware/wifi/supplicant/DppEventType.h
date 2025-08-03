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
enum class DppEventType : int32_t {
  CONFIGURATION_SENT = 0,
  CONFIGURATION_APPLIED = 1,
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
[[nodiscard]] static inline std::string toString(DppEventType val) {
  switch(val) {
  case DppEventType::CONFIGURATION_SENT:
    return "CONFIGURATION_SENT";
  case DppEventType::CONFIGURATION_APPLIED:
    return "CONFIGURATION_APPLIED";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::DppEventType, 2> enum_values<aidl::android::hardware::wifi::supplicant::DppEventType> = {
  aidl::android::hardware::wifi::supplicant::DppEventType::CONFIGURATION_SENT,
  aidl::android::hardware::wifi::supplicant::DppEventType::CONFIGURATION_APPLIED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
