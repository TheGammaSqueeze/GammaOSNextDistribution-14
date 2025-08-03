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
enum class BatteryCapacityLevel : int32_t {
  UNSUPPORTED = -1,
  UNKNOWN = 0,
  CRITICAL = 1,
  LOW = 2,
  NORMAL = 3,
  HIGH = 4,
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
[[nodiscard]] static inline std::string toString(BatteryCapacityLevel val) {
  switch(val) {
  case BatteryCapacityLevel::UNSUPPORTED:
    return "UNSUPPORTED";
  case BatteryCapacityLevel::UNKNOWN:
    return "UNKNOWN";
  case BatteryCapacityLevel::CRITICAL:
    return "CRITICAL";
  case BatteryCapacityLevel::LOW:
    return "LOW";
  case BatteryCapacityLevel::NORMAL:
    return "NORMAL";
  case BatteryCapacityLevel::HIGH:
    return "HIGH";
  case BatteryCapacityLevel::FULL:
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
constexpr inline std::array<aidl::android::hardware::health::BatteryCapacityLevel, 7> enum_values<aidl::android::hardware::health::BatteryCapacityLevel> = {
  aidl::android::hardware::health::BatteryCapacityLevel::UNSUPPORTED,
  aidl::android::hardware::health::BatteryCapacityLevel::UNKNOWN,
  aidl::android::hardware::health::BatteryCapacityLevel::CRITICAL,
  aidl::android::hardware::health::BatteryCapacityLevel::LOW,
  aidl::android::hardware::health::BatteryCapacityLevel::NORMAL,
  aidl::android::hardware::health::BatteryCapacityLevel::HIGH,
  aidl::android::hardware::health::BatteryCapacityLevel::FULL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
