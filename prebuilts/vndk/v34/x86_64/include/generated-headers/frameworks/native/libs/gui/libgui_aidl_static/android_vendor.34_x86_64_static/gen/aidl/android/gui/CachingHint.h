#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {
namespace gui {
enum class CachingHint : int32_t {
  Disabled = 0,
  Enabled = 1,
};
}  // namespace gui
}  // namespace android
namespace android {
namespace gui {
[[nodiscard]] static inline std::string toString(CachingHint val) {
  switch(val) {
  case CachingHint::Disabled:
    return "Disabled";
  case CachingHint::Enabled:
    return "Enabled";
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
constexpr inline std::array<::android::gui::CachingHint, 2> enum_values<::android::gui::CachingHint> = {
  ::android::gui::CachingHint::Disabled,
  ::android::gui::CachingHint::Enabled,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
