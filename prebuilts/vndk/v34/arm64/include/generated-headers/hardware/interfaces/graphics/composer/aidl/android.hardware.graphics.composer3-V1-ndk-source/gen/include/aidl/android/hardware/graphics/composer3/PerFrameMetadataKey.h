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
enum class PerFrameMetadataKey : int32_t {
  DISPLAY_RED_PRIMARY_X = 0,
  DISPLAY_RED_PRIMARY_Y = 1,
  DISPLAY_GREEN_PRIMARY_X = 2,
  DISPLAY_GREEN_PRIMARY_Y = 3,
  DISPLAY_BLUE_PRIMARY_X = 4,
  DISPLAY_BLUE_PRIMARY_Y = 5,
  WHITE_POINT_X = 6,
  WHITE_POINT_Y = 7,
  MAX_LUMINANCE = 8,
  MIN_LUMINANCE = 9,
  MAX_CONTENT_LIGHT_LEVEL = 10,
  MAX_FRAME_AVERAGE_LIGHT_LEVEL = 11,
  HDR10_PLUS_SEI = 12,
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
[[nodiscard]] static inline std::string toString(PerFrameMetadataKey val) {
  switch(val) {
  case PerFrameMetadataKey::DISPLAY_RED_PRIMARY_X:
    return "DISPLAY_RED_PRIMARY_X";
  case PerFrameMetadataKey::DISPLAY_RED_PRIMARY_Y:
    return "DISPLAY_RED_PRIMARY_Y";
  case PerFrameMetadataKey::DISPLAY_GREEN_PRIMARY_X:
    return "DISPLAY_GREEN_PRIMARY_X";
  case PerFrameMetadataKey::DISPLAY_GREEN_PRIMARY_Y:
    return "DISPLAY_GREEN_PRIMARY_Y";
  case PerFrameMetadataKey::DISPLAY_BLUE_PRIMARY_X:
    return "DISPLAY_BLUE_PRIMARY_X";
  case PerFrameMetadataKey::DISPLAY_BLUE_PRIMARY_Y:
    return "DISPLAY_BLUE_PRIMARY_Y";
  case PerFrameMetadataKey::WHITE_POINT_X:
    return "WHITE_POINT_X";
  case PerFrameMetadataKey::WHITE_POINT_Y:
    return "WHITE_POINT_Y";
  case PerFrameMetadataKey::MAX_LUMINANCE:
    return "MAX_LUMINANCE";
  case PerFrameMetadataKey::MIN_LUMINANCE:
    return "MIN_LUMINANCE";
  case PerFrameMetadataKey::MAX_CONTENT_LIGHT_LEVEL:
    return "MAX_CONTENT_LIGHT_LEVEL";
  case PerFrameMetadataKey::MAX_FRAME_AVERAGE_LIGHT_LEVEL:
    return "MAX_FRAME_AVERAGE_LIGHT_LEVEL";
  case PerFrameMetadataKey::HDR10_PLUS_SEI:
    return "HDR10_PLUS_SEI";
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
constexpr inline std::array<aidl::android::hardware::graphics::composer3::PerFrameMetadataKey, 13> enum_values<aidl::android::hardware::graphics::composer3::PerFrameMetadataKey> = {
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::DISPLAY_RED_PRIMARY_X,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::DISPLAY_RED_PRIMARY_Y,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::DISPLAY_GREEN_PRIMARY_X,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::DISPLAY_GREEN_PRIMARY_Y,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::DISPLAY_BLUE_PRIMARY_X,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::DISPLAY_BLUE_PRIMARY_Y,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::WHITE_POINT_X,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::WHITE_POINT_Y,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::MAX_LUMINANCE,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::MIN_LUMINANCE,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::MAX_CONTENT_LIGHT_LEVEL,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::MAX_FRAME_AVERAGE_LIGHT_LEVEL,
  aidl::android::hardware::graphics::composer3::PerFrameMetadataKey::HDR10_PLUS_SEI,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
