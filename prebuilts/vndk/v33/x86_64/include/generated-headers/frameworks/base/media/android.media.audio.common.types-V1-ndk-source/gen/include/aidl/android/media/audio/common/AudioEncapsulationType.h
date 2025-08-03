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
enum class AudioEncapsulationType : int32_t {
  NONE = 0,
  IEC61937 = 1,
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
[[nodiscard]] static inline std::string toString(AudioEncapsulationType val) {
  switch(val) {
  case AudioEncapsulationType::NONE:
    return "NONE";
  case AudioEncapsulationType::IEC61937:
    return "IEC61937";
  default:
    return std::to_string(static_cast<int32_t>(val));
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
constexpr inline std::array<aidl::android::media::audio::common::AudioEncapsulationType, 2> enum_values<aidl::android::media::audio::common::AudioEncapsulationType> = {
  aidl::android::media::audio::common::AudioEncapsulationType::NONE,
  aidl::android::media::audio::common::AudioEncapsulationType::IEC61937,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
