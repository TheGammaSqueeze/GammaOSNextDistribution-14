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
enum class AptxAdaptiveChannelMode : int32_t {
  JOINT_STEREO = 0,
  MONO = 1,
  DUAL_MONO = 2,
  TWS_STEREO = 4,
  UNKNOWN = 255,
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
[[nodiscard]] static inline std::string toString(AptxAdaptiveChannelMode val) {
  switch(val) {
  case AptxAdaptiveChannelMode::JOINT_STEREO:
    return "JOINT_STEREO";
  case AptxAdaptiveChannelMode::MONO:
    return "MONO";
  case AptxAdaptiveChannelMode::DUAL_MONO:
    return "DUAL_MONO";
  case AptxAdaptiveChannelMode::TWS_STEREO:
    return "TWS_STEREO";
  case AptxAdaptiveChannelMode::UNKNOWN:
    return "UNKNOWN";
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::AptxAdaptiveChannelMode, 5> enum_values<aidl::android::hardware::bluetooth::audio::AptxAdaptiveChannelMode> = {
  aidl::android::hardware::bluetooth::audio::AptxAdaptiveChannelMode::JOINT_STEREO,
  aidl::android::hardware::bluetooth::audio::AptxAdaptiveChannelMode::MONO,
  aidl::android::hardware::bluetooth::audio::AptxAdaptiveChannelMode::DUAL_MONO,
  aidl::android::hardware::bluetooth::audio::AptxAdaptiveChannelMode::TWS_STEREO,
  aidl::android::hardware::bluetooth::audio::AptxAdaptiveChannelMode::UNKNOWN,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
