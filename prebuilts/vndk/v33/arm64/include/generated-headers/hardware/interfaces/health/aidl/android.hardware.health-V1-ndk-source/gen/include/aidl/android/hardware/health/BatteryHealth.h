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
namespace health {
enum class BatteryHealth : int32_t {
  UNKNOWN = 1,
  GOOD = 2,
  OVERHEAT = 3,
  DEAD = 4,
  OVER_VOLTAGE = 5,
  UNSPECIFIED_FAILURE = 6,
  COLD = 7,
};

}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace health {
[[nodiscard]] static inline std::string toString(BatteryHealth val) {
  switch(val) {
  case BatteryHealth::UNKNOWN:
    return "UNKNOWN";
  case BatteryHealth::GOOD:
    return "GOOD";
  case BatteryHealth::OVERHEAT:
    return "OVERHEAT";
  case BatteryHealth::DEAD:
    return "DEAD";
  case BatteryHealth::OVER_VOLTAGE:
    return "OVER_VOLTAGE";
  case BatteryHealth::UNSPECIFIED_FAILURE:
    return "UNSPECIFIED_FAILURE";
  case BatteryHealth::COLD:
    return "COLD";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::health::BatteryHealth, 7> enum_values<aidl::android::hardware::health::BatteryHealth> = {
  aidl::android::hardware::health::BatteryHealth::UNKNOWN,
  aidl::android::hardware::health::BatteryHealth::GOOD,
  aidl::android::hardware::health::BatteryHealth::OVERHEAT,
  aidl::android::hardware::health::BatteryHealth::DEAD,
  aidl::android::hardware::health::BatteryHealth::OVER_VOLTAGE,
  aidl::android::hardware::health::BatteryHealth::UNSPECIFIED_FAILURE,
  aidl::android::hardware::health::BatteryHealth::COLD,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
