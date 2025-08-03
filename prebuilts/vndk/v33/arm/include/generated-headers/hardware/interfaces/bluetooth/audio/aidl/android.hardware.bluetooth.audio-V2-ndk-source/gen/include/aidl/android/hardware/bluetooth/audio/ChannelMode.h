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
enum class ChannelMode : int8_t {
  UNKNOWN = 0,
  MONO = 1,
  STEREO = 2,
  DUALMONO = 3,
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
[[nodiscard]] static inline std::string toString(ChannelMode val) {
  switch(val) {
  case ChannelMode::UNKNOWN:
    return "UNKNOWN";
  case ChannelMode::MONO:
    return "MONO";
  case ChannelMode::STEREO:
    return "STEREO";
  case ChannelMode::DUALMONO:
    return "DUALMONO";
  default:
    return std::to_string(static_cast<int8_t>(val));
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::ChannelMode, 4> enum_values<aidl::android::hardware::bluetooth::audio::ChannelMode> = {
  aidl::android::hardware::bluetooth::audio::ChannelMode::UNKNOWN,
  aidl::android::hardware::bluetooth::audio::ChannelMode::MONO,
  aidl::android::hardware::bluetooth::audio::ChannelMode::STEREO,
  aidl::android::hardware::bluetooth::audio::ChannelMode::DUALMONO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
