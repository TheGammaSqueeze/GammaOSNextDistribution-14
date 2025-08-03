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
enum class AudioInputFlags : int32_t {
  FAST = 0,
  HW_HOTWORD = 1,
  RAW = 2,
  SYNC = 3,
  MMAP_NOIRQ = 4,
  VOIP_TX = 5,
  HW_AV_SYNC = 6,
  DIRECT = 7,
  ULTRASOUND = 8,
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
[[nodiscard]] static inline std::string toString(AudioInputFlags val) {
  switch(val) {
  case AudioInputFlags::FAST:
    return "FAST";
  case AudioInputFlags::HW_HOTWORD:
    return "HW_HOTWORD";
  case AudioInputFlags::RAW:
    return "RAW";
  case AudioInputFlags::SYNC:
    return "SYNC";
  case AudioInputFlags::MMAP_NOIRQ:
    return "MMAP_NOIRQ";
  case AudioInputFlags::VOIP_TX:
    return "VOIP_TX";
  case AudioInputFlags::HW_AV_SYNC:
    return "HW_AV_SYNC";
  case AudioInputFlags::DIRECT:
    return "DIRECT";
  case AudioInputFlags::ULTRASOUND:
    return "ULTRASOUND";
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
constexpr inline std::array<aidl::android::media::audio::common::AudioInputFlags, 9> enum_values<aidl::android::media::audio::common::AudioInputFlags> = {
  aidl::android::media::audio::common::AudioInputFlags::FAST,
  aidl::android::media::audio::common::AudioInputFlags::HW_HOTWORD,
  aidl::android::media::audio::common::AudioInputFlags::RAW,
  aidl::android::media::audio::common::AudioInputFlags::SYNC,
  aidl::android::media::audio::common::AudioInputFlags::MMAP_NOIRQ,
  aidl::android::media::audio::common::AudioInputFlags::VOIP_TX,
  aidl::android::media::audio::common::AudioInputFlags::HW_AV_SYNC,
  aidl::android::media::audio::common::AudioInputFlags::DIRECT,
  aidl::android::media::audio::common::AudioInputFlags::ULTRASOUND,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
