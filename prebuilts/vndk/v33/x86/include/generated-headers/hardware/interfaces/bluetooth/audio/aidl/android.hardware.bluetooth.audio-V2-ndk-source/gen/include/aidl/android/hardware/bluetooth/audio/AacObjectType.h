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
enum class AacObjectType : int8_t {
  MPEG2_LC = 0,
  MPEG4_LC = 1,
  MPEG4_LTP = 2,
  MPEG4_SCALABLE = 3,
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
[[nodiscard]] static inline std::string toString(AacObjectType val) {
  switch(val) {
  case AacObjectType::MPEG2_LC:
    return "MPEG2_LC";
  case AacObjectType::MPEG4_LC:
    return "MPEG4_LC";
  case AacObjectType::MPEG4_LTP:
    return "MPEG4_LTP";
  case AacObjectType::MPEG4_SCALABLE:
    return "MPEG4_SCALABLE";
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::AacObjectType, 4> enum_values<aidl::android::hardware::bluetooth::audio::AacObjectType> = {
  aidl::android::hardware::bluetooth::audio::AacObjectType::MPEG2_LC,
  aidl::android::hardware::bluetooth::audio::AacObjectType::MPEG4_LC,
  aidl::android::hardware::bluetooth::audio::AacObjectType::MPEG4_LTP,
  aidl::android::hardware::bluetooth::audio::AacObjectType::MPEG4_SCALABLE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
