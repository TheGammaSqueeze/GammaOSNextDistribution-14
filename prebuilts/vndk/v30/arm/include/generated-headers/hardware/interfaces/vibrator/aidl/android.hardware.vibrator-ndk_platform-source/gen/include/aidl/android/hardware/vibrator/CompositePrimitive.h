#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <array>
#include <android/binder_enums.h>
namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
enum class CompositePrimitive : int32_t {
  NOOP = 0,
  CLICK = 1,
  THUD = 2,
  SPIN = 3,
  QUICK_RISE = 4,
  SLOW_RISE = 5,
  QUICK_FALL = 6,
  LIGHT_TICK = 7,
};

static inline std::string toString(CompositePrimitive val) {
  switch(val) {
  case CompositePrimitive::NOOP:
    return "NOOP";
  case CompositePrimitive::CLICK:
    return "CLICK";
  case CompositePrimitive::THUD:
    return "THUD";
  case CompositePrimitive::SPIN:
    return "SPIN";
  case CompositePrimitive::QUICK_RISE:
    return "QUICK_RISE";
  case CompositePrimitive::SLOW_RISE:
    return "SLOW_RISE";
  case CompositePrimitive::QUICK_FALL:
    return "QUICK_FALL";
  case CompositePrimitive::LIGHT_TICK:
    return "LIGHT_TICK";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::vibrator::CompositePrimitive, 8> enum_values<aidl::android::hardware::vibrator::CompositePrimitive> = {
  aidl::android::hardware::vibrator::CompositePrimitive::NOOP,
  aidl::android::hardware::vibrator::CompositePrimitive::CLICK,
  aidl::android::hardware::vibrator::CompositePrimitive::THUD,
  aidl::android::hardware::vibrator::CompositePrimitive::SPIN,
  aidl::android::hardware::vibrator::CompositePrimitive::QUICK_RISE,
  aidl::android::hardware::vibrator::CompositePrimitive::SLOW_RISE,
  aidl::android::hardware::vibrator::CompositePrimitive::QUICK_FALL,
  aidl::android::hardware::vibrator::CompositePrimitive::LIGHT_TICK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
