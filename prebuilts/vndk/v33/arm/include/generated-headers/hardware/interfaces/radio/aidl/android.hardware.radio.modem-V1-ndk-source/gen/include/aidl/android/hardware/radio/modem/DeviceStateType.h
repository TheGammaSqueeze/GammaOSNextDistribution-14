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
namespace modem {
enum class DeviceStateType : int32_t {
  POWER_SAVE_MODE = 0,
  CHARGING_STATE = 1,
  LOW_DATA_EXPECTED = 2,
};

}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
[[nodiscard]] static inline std::string toString(DeviceStateType val) {
  switch(val) {
  case DeviceStateType::POWER_SAVE_MODE:
    return "POWER_SAVE_MODE";
  case DeviceStateType::CHARGING_STATE:
    return "CHARGING_STATE";
  case DeviceStateType::LOW_DATA_EXPECTED:
    return "LOW_DATA_EXPECTED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::modem::DeviceStateType, 3> enum_values<aidl::android::hardware::radio::modem::DeviceStateType> = {
  aidl::android::hardware::radio::modem::DeviceStateType::POWER_SAVE_MODE,
  aidl::android::hardware::radio::modem::DeviceStateType::CHARGING_STATE,
  aidl::android::hardware::radio::modem::DeviceStateType::LOW_DATA_EXPECTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
