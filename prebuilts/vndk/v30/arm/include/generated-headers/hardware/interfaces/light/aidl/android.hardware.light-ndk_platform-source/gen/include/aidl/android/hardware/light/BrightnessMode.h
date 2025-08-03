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
namespace light {
enum class BrightnessMode : int8_t {
  USER = 0,
  SENSOR = 1,
  LOW_PERSISTENCE = 2,
};

static inline std::string toString(BrightnessMode val) {
  switch(val) {
  case BrightnessMode::USER:
    return "USER";
  case BrightnessMode::SENSOR:
    return "SENSOR";
  case BrightnessMode::LOW_PERSISTENCE:
    return "LOW_PERSISTENCE";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace light
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::light::BrightnessMode, 3> enum_values<aidl::android::hardware::light::BrightnessMode> = {
  aidl::android::hardware::light::BrightnessMode::USER,
  aidl::android::hardware::light::BrightnessMode::SENSOR,
  aidl::android::hardware::light::BrightnessMode::LOW_PERSISTENCE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
