#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {

namespace os {

enum class InputEventInjectionSync : int32_t {
  NONE = 0,
  WAIT_FOR_RESULT = 1,
  WAIT_FOR_FINISHED = 2,
};

[[nodiscard]] static inline std::string toString(InputEventInjectionSync val) {
  switch(val) {
  case InputEventInjectionSync::NONE:
    return "NONE";
  case InputEventInjectionSync::WAIT_FOR_RESULT:
    return "WAIT_FOR_RESULT";
  case InputEventInjectionSync::WAIT_FOR_FINISHED:
    return "WAIT_FOR_FINISHED";
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
constexpr inline std::array<::android::os::InputEventInjectionSync, 3> enum_values<::android::os::InputEventInjectionSync> = {
  ::android::os::InputEventInjectionSync::NONE,
  ::android::os::InputEventInjectionSync::WAIT_FOR_RESULT,
  ::android::os::InputEventInjectionSync::WAIT_FOR_FINISHED,
};
#pragma clang diagnostic pop

}  // namespace internal

}  // namespace android
