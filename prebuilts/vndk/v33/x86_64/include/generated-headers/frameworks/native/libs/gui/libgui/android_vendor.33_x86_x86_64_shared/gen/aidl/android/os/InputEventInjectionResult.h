#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {
namespace os {
enum class InputEventInjectionResult : int32_t {
  PENDING = -1,
  SUCCEEDED = 0,
  TARGET_MISMATCH = 1,
  FAILED = 2,
  TIMED_OUT = 3,
};
}  // namespace os
}  // namespace android
namespace android {
namespace os {
[[nodiscard]] static inline std::string toString(InputEventInjectionResult val) {
  switch(val) {
  case InputEventInjectionResult::PENDING:
    return "PENDING";
  case InputEventInjectionResult::SUCCEEDED:
    return "SUCCEEDED";
  case InputEventInjectionResult::TARGET_MISMATCH:
    return "TARGET_MISMATCH";
  case InputEventInjectionResult::FAILED:
    return "FAILED";
  case InputEventInjectionResult::TIMED_OUT:
    return "TIMED_OUT";
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
constexpr inline std::array<::android::os::InputEventInjectionResult, 5> enum_values<::android::os::InputEventInjectionResult> = {
  ::android::os::InputEventInjectionResult::PENDING,
  ::android::os::InputEventInjectionResult::SUCCEEDED,
  ::android::os::InputEventInjectionResult::TARGET_MISMATCH,
  ::android::os::InputEventInjectionResult::FAILED,
  ::android::os::InputEventInjectionResult::TIMED_OUT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
