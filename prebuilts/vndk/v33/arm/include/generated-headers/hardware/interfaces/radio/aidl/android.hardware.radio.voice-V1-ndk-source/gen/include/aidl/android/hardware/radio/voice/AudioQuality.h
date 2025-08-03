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
namespace radio {
namespace voice {
enum class AudioQuality : int32_t {
  UNSPECIFIED = 0,
  AMR = 1,
  AMR_WB = 2,
  GSM_EFR = 3,
  GSM_FR = 4,
  GSM_HR = 5,
  EVRC = 6,
  EVRC_B = 7,
  EVRC_WB = 8,
  EVRC_NW = 9,
};

}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
[[nodiscard]] static inline std::string toString(AudioQuality val) {
  switch(val) {
  case AudioQuality::UNSPECIFIED:
    return "UNSPECIFIED";
  case AudioQuality::AMR:
    return "AMR";
  case AudioQuality::AMR_WB:
    return "AMR_WB";
  case AudioQuality::GSM_EFR:
    return "GSM_EFR";
  case AudioQuality::GSM_FR:
    return "GSM_FR";
  case AudioQuality::GSM_HR:
    return "GSM_HR";
  case AudioQuality::EVRC:
    return "EVRC";
  case AudioQuality::EVRC_B:
    return "EVRC_B";
  case AudioQuality::EVRC_WB:
    return "EVRC_WB";
  case AudioQuality::EVRC_NW:
    return "EVRC_NW";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::voice::AudioQuality, 10> enum_values<aidl::android::hardware::radio::voice::AudioQuality> = {
  aidl::android::hardware::radio::voice::AudioQuality::UNSPECIFIED,
  aidl::android::hardware::radio::voice::AudioQuality::AMR,
  aidl::android::hardware::radio::voice::AudioQuality::AMR_WB,
  aidl::android::hardware::radio::voice::AudioQuality::GSM_EFR,
  aidl::android::hardware::radio::voice::AudioQuality::GSM_FR,
  aidl::android::hardware::radio::voice::AudioQuality::GSM_HR,
  aidl::android::hardware::radio::voice::AudioQuality::EVRC,
  aidl::android::hardware::radio::voice::AudioQuality::EVRC_B,
  aidl::android::hardware::radio::voice::AudioQuality::EVRC_WB,
  aidl::android::hardware::radio::voice::AudioQuality::EVRC_NW,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
