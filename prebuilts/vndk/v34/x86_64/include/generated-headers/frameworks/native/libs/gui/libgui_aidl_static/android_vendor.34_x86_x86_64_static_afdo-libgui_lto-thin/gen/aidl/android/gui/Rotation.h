#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {
namespace gui {
enum class Rotation : int32_t {
  Rotation0 = 0,
  Rotation90 = 1,
  Rotation180 = 2,
  Rotation270 = 3,
};
}  // namespace gui
}  // namespace android
namespace android {
namespace gui {
[[nodiscard]] static inline std::string toString(Rotation val) {
  switch(val) {
  case Rotation::Rotation0:
    return "Rotation0";
  case Rotation::Rotation90:
    return "Rotation90";
  case Rotation::Rotation180:
    return "Rotation180";
  case Rotation::Rotation270:
    return "Rotation270";
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
constexpr inline std::array<::android::gui::Rotation, 4> enum_values<::android::gui::Rotation> = {
  ::android::gui::Rotation::Rotation0,
  ::android::gui::Rotation::Rotation90,
  ::android::gui::Rotation::Rotation180,
  ::android::gui::Rotation::Rotation270,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
