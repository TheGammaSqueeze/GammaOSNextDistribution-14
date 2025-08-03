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
enum class CodecType : int32_t {
  UNKNOWN = 0,
  SBC = 1,
  AAC = 2,
  APTX = 3,
  APTX_HD = 4,
  LDAC = 5,
  LC3 = 6,
  VENDOR = 7,
  APTX_ADAPTIVE = 8,
  OPUS = 9,
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
[[nodiscard]] static inline std::string toString(CodecType val) {
  switch(val) {
  case CodecType::UNKNOWN:
    return "UNKNOWN";
  case CodecType::SBC:
    return "SBC";
  case CodecType::AAC:
    return "AAC";
  case CodecType::APTX:
    return "APTX";
  case CodecType::APTX_HD:
    return "APTX_HD";
  case CodecType::LDAC:
    return "LDAC";
  case CodecType::LC3:
    return "LC3";
  case CodecType::VENDOR:
    return "VENDOR";
  case CodecType::APTX_ADAPTIVE:
    return "APTX_ADAPTIVE";
  case CodecType::OPUS:
    return "OPUS";
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::CodecType, 10> enum_values<aidl::android::hardware::bluetooth::audio::CodecType> = {
  aidl::android::hardware::bluetooth::audio::CodecType::UNKNOWN,
  aidl::android::hardware::bluetooth::audio::CodecType::SBC,
  aidl::android::hardware::bluetooth::audio::CodecType::AAC,
  aidl::android::hardware::bluetooth::audio::CodecType::APTX,
  aidl::android::hardware::bluetooth::audio::CodecType::APTX_HD,
  aidl::android::hardware::bluetooth::audio::CodecType::LDAC,
  aidl::android::hardware::bluetooth::audio::CodecType::LC3,
  aidl::android::hardware::bluetooth::audio::CodecType::VENDOR,
  aidl::android::hardware::bluetooth::audio::CodecType::APTX_ADAPTIVE,
  aidl::android::hardware::bluetooth::audio::CodecType::OPUS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
