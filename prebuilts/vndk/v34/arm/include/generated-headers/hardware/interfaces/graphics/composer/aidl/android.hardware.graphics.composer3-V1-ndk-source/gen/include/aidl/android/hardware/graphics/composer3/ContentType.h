#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
enum class ContentType : int32_t {
  NONE = 0,
  GRAPHICS = 1,
  PHOTO = 2,
  CINEMA = 3,
  GAME = 4,
};

}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
[[nodiscard]] static inline std::string toString(ContentType val) {
  switch(val) {
  case ContentType::NONE:
    return "NONE";
  case ContentType::GRAPHICS:
    return "GRAPHICS";
  case ContentType::PHOTO:
    return "PHOTO";
  case ContentType::CINEMA:
    return "CINEMA";
  case ContentType::GAME:
    return "GAME";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::composer3::ContentType, 5> enum_values<aidl::android::hardware::graphics::composer3::ContentType> = {
  aidl::android::hardware::graphics::composer3::ContentType::NONE,
  aidl::android::hardware::graphics::composer3::ContentType::GRAPHICS,
  aidl::android::hardware::graphics::composer3::ContentType::PHOTO,
  aidl::android::hardware::graphics::composer3::ContentType::CINEMA,
  aidl::android::hardware::graphics::composer3::ContentType::GAME,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
