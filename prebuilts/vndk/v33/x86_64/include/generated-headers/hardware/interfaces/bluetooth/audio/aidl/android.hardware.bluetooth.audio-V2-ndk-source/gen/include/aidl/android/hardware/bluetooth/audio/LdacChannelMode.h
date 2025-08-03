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
enum class LdacChannelMode : int8_t {
  UNKNOWN = 0,
  STEREO = 1,
  DUAL = 2,
  MONO = 3,
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
[[nodiscard]] static inline std::string toString(LdacChannelMode val) {
  switch(val) {
  case LdacChannelMode::UNKNOWN:
    return "UNKNOWN";
  case LdacChannelMode::STEREO:
    return "STEREO";
  case LdacChannelMode::DUAL:
    return "DUAL";
  case LdacChannelMode::MONO:
    return "MONO";
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::LdacChannelMode, 4> enum_values<aidl::android::hardware::bluetooth::audio::LdacChannelMode> = {
  aidl::android::hardware::bluetooth::audio::LdacChannelMode::UNKNOWN,
  aidl::android::hardware::bluetooth::audio::LdacChannelMode::STEREO,
  aidl::android::hardware::bluetooth::audio::LdacChannelMode::DUAL,
  aidl::android::hardware::bluetooth::audio::LdacChannelMode::MONO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
