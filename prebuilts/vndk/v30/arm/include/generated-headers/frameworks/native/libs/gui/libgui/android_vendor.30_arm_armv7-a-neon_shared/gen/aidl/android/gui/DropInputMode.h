#ifndef AIDL_GENERATED_ANDROID_GUI_DROP_INPUT_MODE_H_
#define AIDL_GENERATED_ANDROID_GUI_DROP_INPUT_MODE_H_

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {

namespace gui {

enum class DropInputMode : int32_t {
  NONE = 0,
  ALL = 1,
};

static inline std::string toString(DropInputMode val) {
  switch(val) {
  case DropInputMode::NONE:
    return "NONE";
  case DropInputMode::ALL:
    return "ALL";
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
constexpr inline std::array<::android::gui::DropInputMode, 2> enum_values<::android::gui::DropInputMode> = {
  ::android::gui::DropInputMode::NONE,
  ::android::gui::DropInputMode::ALL,
};
#pragma clang diagnostic pop

}  // namespace internal

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_GUI_DROP_INPUT_MODE_H_
