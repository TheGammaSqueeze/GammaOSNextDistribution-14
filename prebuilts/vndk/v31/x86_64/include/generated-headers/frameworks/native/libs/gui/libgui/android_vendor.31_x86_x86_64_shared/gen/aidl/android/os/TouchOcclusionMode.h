#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {

namespace os {

enum class TouchOcclusionMode : int32_t {
  BLOCK_UNTRUSTED = 0,
  USE_OPACITY = 1,
  ALLOW = 2,
};

[[nodiscard]] static inline std::string toString(TouchOcclusionMode val) {
  switch(val) {
  case TouchOcclusionMode::BLOCK_UNTRUSTED:
    return "BLOCK_UNTRUSTED";
  case TouchOcclusionMode::USE_OPACITY:
    return "USE_OPACITY";
  case TouchOcclusionMode::ALLOW:
    return "ALLOW";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}

}  // namespace os

}  // namespace android
namespace android {

namespace internal {

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::os::TouchOcclusionMode, 3> enum_values<::android::os::TouchOcclusionMode> = {
  ::android::os::TouchOcclusionMode::BLOCK_UNTRUSTED,
  ::android::os::TouchOcclusionMode::USE_OPACITY,
  ::android::os::TouchOcclusionMode::ALLOW,
};
#pragma clang diagnostic pop

}  // namespace internal

}  // namespace android
