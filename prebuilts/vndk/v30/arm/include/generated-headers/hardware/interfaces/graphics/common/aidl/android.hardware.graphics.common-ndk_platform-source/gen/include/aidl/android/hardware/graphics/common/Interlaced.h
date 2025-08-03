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
namespace graphics {
namespace common {
enum class Interlaced : int64_t {
  NONE = 0L,
  TOP_BOTTOM = 1L,
  RIGHT_LEFT = 2L,
};

static inline std::string toString(Interlaced val) {
  switch(val) {
  case Interlaced::NONE:
    return "NONE";
  case Interlaced::TOP_BOTTOM:
    return "TOP_BOTTOM";
  case Interlaced::RIGHT_LEFT:
    return "RIGHT_LEFT";
  default:
    return std::to_string(static_cast<int64_t>(val));
  }
}
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::common::Interlaced, 3> enum_values<aidl::android::hardware::graphics::common::Interlaced> = {
  aidl::android::hardware::graphics::common::Interlaced::NONE,
  aidl::android::hardware::graphics::common::Interlaced::TOP_BOTTOM,
  aidl::android::hardware::graphics::common::Interlaced::RIGHT_LEFT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
