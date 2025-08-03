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
namespace bluetooth {
namespace audio {
enum class AptxAdaptiveInputMode : int32_t {
  STEREO = 0,
  DUAL_MONO = 1,
};

}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
[[nodiscard]] static inline std::string toString(AptxAdaptiveInputMode val) {
  switch(val) {
  case AptxAdaptiveInputMode::STEREO:
    return "STEREO";
  case AptxAdaptiveInputMode::DUAL_MONO:
    return "DUAL_MONO";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::AptxAdaptiveInputMode, 2> enum_values<aidl::android::hardware::bluetooth::audio::AptxAdaptiveInputMode> = {
  aidl::android::hardware::bluetooth::audio::AptxAdaptiveInputMode::STEREO,
  aidl::android::hardware::bluetooth::audio::AptxAdaptiveInputMode::DUAL_MONO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
