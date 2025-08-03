#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {

namespace gui {

enum class DropInputMode : int32_t {
  NONE = 0,
  ALL = 1,
  OBSCURED = 2,
};

[[nodiscard]] static inline std::string toString(DropInputMode val) {
  switch(val) {
  case DropInputMode::NONE:
    return "NONE";
  case DropInputMode::ALL:
    return "ALL";
  case DropInputMode::OBSCURED:
    return "OBSCURED";
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
constexpr inline std::array<::android::gui::DropInputMode, 3> enum_values<::android::gui::DropInputMode> = {
  ::android::gui::DropInputMode::NONE,
  ::android::gui::DropInputMode::ALL,
  ::android::gui::DropInputMode::OBSCURED,
};
#pragma clang diagnostic pop

}  // namespace internal

}  // namespace android
