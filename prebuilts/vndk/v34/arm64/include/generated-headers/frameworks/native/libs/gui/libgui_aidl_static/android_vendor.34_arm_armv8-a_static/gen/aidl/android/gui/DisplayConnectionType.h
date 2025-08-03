#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {
namespace gui {
enum class DisplayConnectionType : int32_t {
  Internal = 0,
  External = 1,
};
}  // namespace gui
}  // namespace android
namespace android {
namespace gui {
[[nodiscard]] static inline std::string toString(DisplayConnectionType val) {
  switch(val) {
  case DisplayConnectionType::Internal:
    return "Internal";
  case DisplayConnectionType::External:
    return "External";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gui
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::gui::DisplayConnectionType, 2> enum_values<::android::gui::DisplayConnectionType> = {
  ::android::gui::DisplayConnectionType::Internal,
  ::android::gui::DisplayConnectionType::External,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
