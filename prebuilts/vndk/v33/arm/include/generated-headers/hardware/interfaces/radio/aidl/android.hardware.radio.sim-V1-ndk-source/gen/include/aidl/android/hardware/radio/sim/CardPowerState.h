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
enum class CardPowerState : int32_t {
  POWER_DOWN = 0,
  POWER_UP = 1,
  POWER_UP_PASS_THROUGH = 2,
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
[[nodiscard]] static inline std::string toString(CardPowerState val) {
  switch(val) {
  case CardPowerState::POWER_DOWN:
    return "POWER_DOWN";
  case CardPowerState::POWER_UP:
    return "POWER_UP";
  case CardPowerState::POWER_UP_PASS_THROUGH:
    return "POWER_UP_PASS_THROUGH";
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
constexpr inline std::array<aidl::android::hardware::radio::sim::CardPowerState, 3> enum_values<aidl::android::hardware::radio::sim::CardPowerState> = {
  aidl::android::hardware::radio::sim::CardPowerState::POWER_DOWN,
  aidl::android::hardware::radio::sim::CardPowerState::POWER_UP,
  aidl::android::hardware::radio::sim::CardPowerState::POWER_UP_PASS_THROUGH,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
