#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <array>
#include <android/binder_enums.h>
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
enum class Compression : int64_t {
  NONE = 0L,
  DISPLAY_STREAM_COMPRESSION = 1L,
};

static inline std::string toString(Compression val) {
  switch(val) {
  case Compression::NONE:
    return "NONE";
  case Compression::DISPLAY_STREAM_COMPRESSION:
    return "DISPLAY_STREAM_COMPRESSION";
  default:
    return std::to_string(static_cast<int64_t>(val));
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
constexpr inline std::array<aidl::android::hardware::graphics::common::Compression, 2> enum_values<aidl::android::hardware::graphics::common::Compression> = {
  aidl::android::hardware::graphics::common::Compression::NONE,
  aidl::android::hardware::graphics::common::Compression::DISPLAY_STREAM_COMPRESSION,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
