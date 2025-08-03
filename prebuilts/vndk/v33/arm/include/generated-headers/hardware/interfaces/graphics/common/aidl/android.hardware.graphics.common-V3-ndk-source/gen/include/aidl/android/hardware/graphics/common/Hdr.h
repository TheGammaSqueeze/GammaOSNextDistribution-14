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
namespace common {
enum class Hdr : int32_t {
  DOLBY_VISION = 1,
  HDR10 = 2,
  HLG = 3,
  HDR10_PLUS = 4,
};

}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
[[nodiscard]] static inline std::string toString(Hdr val) {
  switch(val) {
  case Hdr::DOLBY_VISION:
    return "DOLBY_VISION";
  case Hdr::HDR10:
    return "HDR10";
  case Hdr::HLG:
    return "HLG";
  case Hdr::HDR10_PLUS:
    return "HDR10_PLUS";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::common::Hdr, 4> enum_values<aidl::android::hardware::graphics::common::Hdr> = {
  aidl::android::hardware::graphics::common::Hdr::DOLBY_VISION,
  aidl::android::hardware::graphics::common::Hdr::HDR10,
  aidl::android::hardware::graphics::common::Hdr::HLG,
  aidl::android::hardware::graphics::common::Hdr::HDR10_PLUS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
