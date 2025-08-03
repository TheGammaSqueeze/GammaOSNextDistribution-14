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
enum class FlashMode : int8_t {
  NONE = 0,
  TIMED = 1,
  HARDWARE = 2,
};

static inline std::string toString(FlashMode val) {
  switch(val) {
  case FlashMode::NONE:
    return "NONE";
  case FlashMode::TIMED:
    return "TIMED";
  case FlashMode::HARDWARE:
    return "HARDWARE";
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
constexpr inline std::array<aidl::android::hardware::light::FlashMode, 3> enum_values<aidl::android::hardware::light::FlashMode> = {
  aidl::android::hardware::light::FlashMode::NONE,
  aidl::android::hardware::light::FlashMode::TIMED,
  aidl::android::hardware::light::FlashMode::HARDWARE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
