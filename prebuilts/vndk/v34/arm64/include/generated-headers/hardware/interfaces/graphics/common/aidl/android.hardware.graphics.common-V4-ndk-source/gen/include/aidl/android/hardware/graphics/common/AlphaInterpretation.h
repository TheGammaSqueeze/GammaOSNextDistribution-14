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
enum class AlphaInterpretation : int32_t {
  COVERAGE = 0,
  MASK = 1,
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
[[nodiscard]] static inline std::string toString(AlphaInterpretation val) {
  switch(val) {
  case AlphaInterpretation::COVERAGE:
    return "COVERAGE";
  case AlphaInterpretation::MASK:
    return "MASK";
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
constexpr inline std::array<aidl::android::hardware::graphics::common::AlphaInterpretation, 2> enum_values<aidl::android::hardware::graphics::common::AlphaInterpretation> = {
  aidl::android::hardware::graphics::common::AlphaInterpretation::COVERAGE,
  aidl::android::hardware::graphics::common::AlphaInterpretation::MASK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
