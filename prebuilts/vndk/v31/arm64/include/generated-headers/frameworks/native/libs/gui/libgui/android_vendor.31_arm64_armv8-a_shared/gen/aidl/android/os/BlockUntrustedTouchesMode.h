#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {

namespace os {

enum class BlockUntrustedTouchesMode : int32_t {
  DISABLED = 0,
  PERMISSIVE = 1,
  BLOCK = 2,
};

[[nodiscard]] static inline std::string toString(BlockUntrustedTouchesMode val) {
  switch(val) {
  case BlockUntrustedTouchesMode::DISABLED:
    return "DISABLED";
  case BlockUntrustedTouchesMode::PERMISSIVE:
    return "PERMISSIVE";
  case BlockUntrustedTouchesMode::BLOCK:
    return "BLOCK";
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
constexpr inline std::array<::android::os::BlockUntrustedTouchesMode, 3> enum_values<::android::os::BlockUntrustedTouchesMode> = {
  ::android::os::BlockUntrustedTouchesMode::DISABLED,
  ::android::os::BlockUntrustedTouchesMode::PERMISSIVE,
  ::android::os::BlockUntrustedTouchesMode::BLOCK,
};
#pragma clang diagnostic pop

}  // namespace internal

}  // namespace android
