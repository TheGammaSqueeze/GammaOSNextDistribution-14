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
namespace media {
namespace soundtrigger {
enum class AudioCapabilities : int32_t {
  ECHO_CANCELLATION = 1,
  NOISE_SUPPRESSION = 2,
};

}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
[[nodiscard]] static inline std::string toString(AudioCapabilities val) {
  switch(val) {
  case AudioCapabilities::ECHO_CANCELLATION:
    return "ECHO_CANCELLATION";
  case AudioCapabilities::NOISE_SUPPRESSION:
    return "NOISE_SUPPRESSION";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::media::soundtrigger::AudioCapabilities, 2> enum_values<aidl::android::media::soundtrigger::AudioCapabilities> = {
  aidl::android::media::soundtrigger::AudioCapabilities::ECHO_CANCELLATION,
  aidl::android::media::soundtrigger::AudioCapabilities::NOISE_SUPPRESSION,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
