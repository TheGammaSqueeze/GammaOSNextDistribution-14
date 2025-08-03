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
enum class LdacQualityIndex : int8_t {
  HIGH = 0,
  MID = 1,
  LOW = 2,
  ABR = 3,
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
[[nodiscard]] static inline std::string toString(LdacQualityIndex val) {
  switch(val) {
  case LdacQualityIndex::HIGH:
    return "HIGH";
  case LdacQualityIndex::MID:
    return "MID";
  case LdacQualityIndex::LOW:
    return "LOW";
  case LdacQualityIndex::ABR:
    return "ABR";
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
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::LdacQualityIndex, 4> enum_values<aidl::android::hardware::bluetooth::audio::LdacQualityIndex> = {
  aidl::android::hardware::bluetooth::audio::LdacQualityIndex::HIGH,
  aidl::android::hardware::bluetooth::audio::LdacQualityIndex::MID,
  aidl::android::hardware::bluetooth::audio::LdacQualityIndex::LOW,
  aidl::android::hardware::bluetooth::audio::LdacQualityIndex::ABR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
