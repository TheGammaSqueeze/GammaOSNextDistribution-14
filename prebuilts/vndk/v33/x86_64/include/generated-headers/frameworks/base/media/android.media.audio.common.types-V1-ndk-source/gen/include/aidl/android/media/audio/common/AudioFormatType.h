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
namespace audio {
namespace common {
enum class AudioFormatType : int8_t {
  DEFAULT = 0,
  NON_PCM = 0,
  PCM = 1,
  SYS_RESERVED_INVALID = -1,
};

}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
[[nodiscard]] static inline std::string toString(AudioFormatType val) {
  switch(val) {
  case AudioFormatType::DEFAULT:
    return "DEFAULT";
  case AudioFormatType::PCM:
    return "PCM";
  case AudioFormatType::SYS_RESERVED_INVALID:
    return "SYS_RESERVED_INVALID";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::media::audio::common::AudioFormatType, 4> enum_values<aidl::android::media::audio::common::AudioFormatType> = {
  aidl::android::media::audio::common::AudioFormatType::DEFAULT,
  aidl::android::media::audio::common::AudioFormatType::NON_PCM,
  aidl::android::media::audio::common::AudioFormatType::PCM,
  aidl::android::media::audio::common::AudioFormatType::SYS_RESERVED_INVALID,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
