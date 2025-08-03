#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <array>
#include <android/binder_enums.h>
namespace aidl {
namespace android {
namespace hardware {
namespace power {
enum class Boost : int32_t {
  INTERACTION = 0,
  DISPLAY_UPDATE_IMMINENT = 1,
  ML_ACC = 2,
  AUDIO_LAUNCH = 3,
  CAMERA_LAUNCH = 4,
  CAMERA_SHOT = 5,
};

static inline std::string toString(Boost val) {
  switch(val) {
  case Boost::INTERACTION:
    return "INTERACTION";
  case Boost::DISPLAY_UPDATE_IMMINENT:
    return "DISPLAY_UPDATE_IMMINENT";
  case Boost::ML_ACC:
    return "ML_ACC";
  case Boost::AUDIO_LAUNCH:
    return "AUDIO_LAUNCH";
  case Boost::CAMERA_LAUNCH:
    return "CAMERA_LAUNCH";
  case Boost::CAMERA_SHOT:
    return "CAMERA_SHOT";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::power::Boost, 6> enum_values<aidl::android::hardware::power::Boost> = {
  aidl::android::hardware::power::Boost::INTERACTION,
  aidl::android::hardware::power::Boost::DISPLAY_UPDATE_IMMINENT,
  aidl::android::hardware::power::Boost::ML_ACC,
  aidl::android::hardware::power::Boost::AUDIO_LAUNCH,
  aidl::android::hardware::power::Boost::CAMERA_LAUNCH,
  aidl::android::hardware::power::Boost::CAMERA_SHOT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
