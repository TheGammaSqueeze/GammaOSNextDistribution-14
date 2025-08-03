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
enum class ColorMode : int32_t {
  NATIVE = 0,
  STANDARD_BT601_625 = 1,
  STANDARD_BT601_625_UNADJUSTED = 2,
  STANDARD_BT601_525 = 3,
  STANDARD_BT601_525_UNADJUSTED = 4,
  STANDARD_BT709 = 5,
  DCI_P3 = 6,
  SRGB = 7,
  ADOBE_RGB = 8,
  DISPLAY_P3 = 9,
  BT2020 = 10,
  BT2100_PQ = 11,
  BT2100_HLG = 12,
  DISPLAY_BT2020 = 13,
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
[[nodiscard]] static inline std::string toString(ColorMode val) {
  switch(val) {
  case ColorMode::NATIVE:
    return "NATIVE";
  case ColorMode::STANDARD_BT601_625:
    return "STANDARD_BT601_625";
  case ColorMode::STANDARD_BT601_625_UNADJUSTED:
    return "STANDARD_BT601_625_UNADJUSTED";
  case ColorMode::STANDARD_BT601_525:
    return "STANDARD_BT601_525";
  case ColorMode::STANDARD_BT601_525_UNADJUSTED:
    return "STANDARD_BT601_525_UNADJUSTED";
  case ColorMode::STANDARD_BT709:
    return "STANDARD_BT709";
  case ColorMode::DCI_P3:
    return "DCI_P3";
  case ColorMode::SRGB:
    return "SRGB";
  case ColorMode::ADOBE_RGB:
    return "ADOBE_RGB";
  case ColorMode::DISPLAY_P3:
    return "DISPLAY_P3";
  case ColorMode::BT2020:
    return "BT2020";
  case ColorMode::BT2100_PQ:
    return "BT2100_PQ";
  case ColorMode::BT2100_HLG:
    return "BT2100_HLG";
  case ColorMode::DISPLAY_BT2020:
    return "DISPLAY_BT2020";
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
constexpr inline std::array<aidl::android::hardware::graphics::composer3::ColorMode, 14> enum_values<aidl::android::hardware::graphics::composer3::ColorMode> = {
  aidl::android::hardware::graphics::composer3::ColorMode::NATIVE,
  aidl::android::hardware::graphics::composer3::ColorMode::STANDARD_BT601_625,
  aidl::android::hardware::graphics::composer3::ColorMode::STANDARD_BT601_625_UNADJUSTED,
  aidl::android::hardware::graphics::composer3::ColorMode::STANDARD_BT601_525,
  aidl::android::hardware::graphics::composer3::ColorMode::STANDARD_BT601_525_UNADJUSTED,
  aidl::android::hardware::graphics::composer3::ColorMode::STANDARD_BT709,
  aidl::android::hardware::graphics::composer3::ColorMode::DCI_P3,
  aidl::android::hardware::graphics::composer3::ColorMode::SRGB,
  aidl::android::hardware::graphics::composer3::ColorMode::ADOBE_RGB,
  aidl::android::hardware::graphics::composer3::ColorMode::DISPLAY_P3,
  aidl::android::hardware::graphics::composer3::ColorMode::BT2020,
  aidl::android::hardware::graphics::composer3::ColorMode::BT2100_PQ,
  aidl::android::hardware::graphics::composer3::ColorMode::BT2100_HLG,
  aidl::android::hardware::graphics::composer3::ColorMode::DISPLAY_BT2020,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
