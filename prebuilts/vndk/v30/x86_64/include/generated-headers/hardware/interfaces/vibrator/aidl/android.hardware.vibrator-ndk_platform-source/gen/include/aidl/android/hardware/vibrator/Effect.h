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
namespace vibrator {
enum class Effect : int32_t {
  CLICK = 0,
  DOUBLE_CLICK = 1,
  TICK = 2,
  THUD = 3,
  POP = 4,
  HEAVY_CLICK = 5,
  RINGTONE_1 = 6,
  RINGTONE_2 = 7,
  RINGTONE_3 = 8,
  RINGTONE_4 = 9,
  RINGTONE_5 = 10,
  RINGTONE_6 = 11,
  RINGTONE_7 = 12,
  RINGTONE_8 = 13,
  RINGTONE_9 = 14,
  RINGTONE_10 = 15,
  RINGTONE_11 = 16,
  RINGTONE_12 = 17,
  RINGTONE_13 = 18,
  RINGTONE_14 = 19,
  RINGTONE_15 = 20,
  TEXTURE_TICK = 21,
};

static inline std::string toString(Effect val) {
  switch(val) {
  case Effect::CLICK:
    return "CLICK";
  case Effect::DOUBLE_CLICK:
    return "DOUBLE_CLICK";
  case Effect::TICK:
    return "TICK";
  case Effect::THUD:
    return "THUD";
  case Effect::POP:
    return "POP";
  case Effect::HEAVY_CLICK:
    return "HEAVY_CLICK";
  case Effect::RINGTONE_1:
    return "RINGTONE_1";
  case Effect::RINGTONE_2:
    return "RINGTONE_2";
  case Effect::RINGTONE_3:
    return "RINGTONE_3";
  case Effect::RINGTONE_4:
    return "RINGTONE_4";
  case Effect::RINGTONE_5:
    return "RINGTONE_5";
  case Effect::RINGTONE_6:
    return "RINGTONE_6";
  case Effect::RINGTONE_7:
    return "RINGTONE_7";
  case Effect::RINGTONE_8:
    return "RINGTONE_8";
  case Effect::RINGTONE_9:
    return "RINGTONE_9";
  case Effect::RINGTONE_10:
    return "RINGTONE_10";
  case Effect::RINGTONE_11:
    return "RINGTONE_11";
  case Effect::RINGTONE_12:
    return "RINGTONE_12";
  case Effect::RINGTONE_13:
    return "RINGTONE_13";
  case Effect::RINGTONE_14:
    return "RINGTONE_14";
  case Effect::RINGTONE_15:
    return "RINGTONE_15";
  case Effect::TEXTURE_TICK:
    return "TEXTURE_TICK";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::vibrator::Effect, 22> enum_values<aidl::android::hardware::vibrator::Effect> = {
  aidl::android::hardware::vibrator::Effect::CLICK,
  aidl::android::hardware::vibrator::Effect::DOUBLE_CLICK,
  aidl::android::hardware::vibrator::Effect::TICK,
  aidl::android::hardware::vibrator::Effect::THUD,
  aidl::android::hardware::vibrator::Effect::POP,
  aidl::android::hardware::vibrator::Effect::HEAVY_CLICK,
  aidl::android::hardware::vibrator::Effect::RINGTONE_1,
  aidl::android::hardware::vibrator::Effect::RINGTONE_2,
  aidl::android::hardware::vibrator::Effect::RINGTONE_3,
  aidl::android::hardware::vibrator::Effect::RINGTONE_4,
  aidl::android::hardware::vibrator::Effect::RINGTONE_5,
  aidl::android::hardware::vibrator::Effect::RINGTONE_6,
  aidl::android::hardware::vibrator::Effect::RINGTONE_7,
  aidl::android::hardware::vibrator::Effect::RINGTONE_8,
  aidl::android::hardware::vibrator::Effect::RINGTONE_9,
  aidl::android::hardware::vibrator::Effect::RINGTONE_10,
  aidl::android::hardware::vibrator::Effect::RINGTONE_11,
  aidl::android::hardware::vibrator::Effect::RINGTONE_12,
  aidl::android::hardware::vibrator::Effect::RINGTONE_13,
  aidl::android::hardware::vibrator::Effect::RINGTONE_14,
  aidl::android::hardware::vibrator::Effect::RINGTONE_15,
  aidl::android::hardware::vibrator::Effect::TEXTURE_TICK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
