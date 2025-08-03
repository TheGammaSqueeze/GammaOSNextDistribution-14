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
enum class BatteryStatus : int32_t {
  UNKNOWN = 1,
  CHARGING = 2,
  DISCHARGING = 3,
  NOT_CHARGING = 4,
  FULL = 5,
};

}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace health {
[[nodiscard]] static inline std::string toString(BatteryStatus val) {
  switch(val) {
  case BatteryStatus::UNKNOWN:
    return "UNKNOWN";
  case BatteryStatus::CHARGING:
    return "CHARGING";
  case BatteryStatus::DISCHARGING:
    return "DISCHARGING";
  case BatteryStatus::NOT_CHARGING:
    return "NOT_CHARGING";
  case BatteryStatus::FULL:
    return "FULL";
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
constexpr inline std::array<aidl::android::hardware::health::BatteryStatus, 5> enum_values<aidl::android::hardware::health::BatteryStatus> = {
  aidl::android::hardware::health::BatteryStatus::UNKNOWN,
  aidl::android::hardware::health::BatteryStatus::CHARGING,
  aidl::android::hardware::health::BatteryStatus::DISCHARGING,
  aidl::android::hardware::health::BatteryStatus::NOT_CHARGING,
  aidl::android::hardware::health::BatteryStatus::FULL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
