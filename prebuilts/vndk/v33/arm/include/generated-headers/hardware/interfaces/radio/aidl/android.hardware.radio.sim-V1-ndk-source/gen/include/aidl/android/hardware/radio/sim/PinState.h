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
namespace radio {
namespace sim {
enum class PinState : int32_t {
  UNKNOWN = 0,
  ENABLED_NOT_VERIFIED = 1,
  ENABLED_VERIFIED = 2,
  DISABLED = 3,
  ENABLED_BLOCKED = 4,
  ENABLED_PERM_BLOCKED = 5,
};

}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
[[nodiscard]] static inline std::string toString(PinState val) {
  switch(val) {
  case PinState::UNKNOWN:
    return "UNKNOWN";
  case PinState::ENABLED_NOT_VERIFIED:
    return "ENABLED_NOT_VERIFIED";
  case PinState::ENABLED_VERIFIED:
    return "ENABLED_VERIFIED";
  case PinState::DISABLED:
    return "DISABLED";
  case PinState::ENABLED_BLOCKED:
    return "ENABLED_BLOCKED";
  case PinState::ENABLED_PERM_BLOCKED:
    return "ENABLED_PERM_BLOCKED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::sim::PinState, 6> enum_values<aidl::android::hardware::radio::sim::PinState> = {
  aidl::android::hardware::radio::sim::PinState::UNKNOWN,
  aidl::android::hardware::radio::sim::PinState::ENABLED_NOT_VERIFIED,
  aidl::android::hardware::radio::sim::PinState::ENABLED_VERIFIED,
  aidl::android::hardware::radio::sim::PinState::DISABLED,
  aidl::android::hardware::radio::sim::PinState::ENABLED_BLOCKED,
  aidl::android::hardware::radio::sim::PinState::ENABLED_PERM_BLOCKED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
